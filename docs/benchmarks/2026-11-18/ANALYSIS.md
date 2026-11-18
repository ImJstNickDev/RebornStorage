# Cause e possibili correzioni

Analisi del codice del port `5d8d619` e di Refined Storage **1.9.17** effettivamente
usato nei test. I sorgenti RS sono stati letti dal JAR locale rimappato/decompilato,
non da una versione diversa. Le proposte qui sotto **non sono implementate**.
I tempi dei benchmark restano riferiti al port originale.

## Il blocco iniziale: responsabilità del port

La sequenza osservata nei campionamenti del thread server è:

```
RS NetworkNodeGraph.invalidate
  per ciascun nodo nuovo: onConnected
    Reborn CraftingNode.onConnected -> super.onConnected
      Reborn CraftingNode.onConnectedStateChange
        RS CraftingManager.invalidate
          getContainers: visita tutta la rete e ordina i container
```

La stessa chiamata diretta avviene da `NetworkNode.update` quando cambia lo stato
operativo, quindi anche all'accensione/spegnimento per energia. Gli stack salvati
in [connection-stacks.txt](connection-stacks.txt) documentano entrambi i percorsi.

Non è una visita inevitabilmente lenta di 32.768 blocchi: il port richiede una
**ricostruzione globale per ciascun blocco**. Inoltre tutti i suoi blocchi,
comprese cornici e CPU senza pattern, implementano `ICraftingPatternContainer`.
RS li include nella raccolta e nell'ordinamento. Con N blocchi, una serie di N
callback provoca N scansioni di N nodi, più gli ordinamenti: almeno un costo
quadratico in questa configurazione, con ulteriore costo di ordinamento.
Non è una curva empirica esatta né una previsione dei tempi del 64³.

La chiamata diretta `invalidate()` era già presente nell'upstream 1.18.2.
Nel port abbiamo ripristinato `super.onConnected/onDisconnected` ed eliminato i
campi che nascondevano lo stato della classe base, necessari per la corretta
integrazione RS. Questo rende effettivi callback che prima potevano essere
saltati. **La mancata aggregazione di questi callback nel nostro adattamento è
responsabilità nostra**, anche se parte del codice è ereditata. Non va risolta
rimuovendo `super` o tornando a ignorare lo stato/energia della rete.

## Limite energetico RS e errore nell'assunzione del benchmark

`Network.canRun()` confronta energia nel buffer e consumo della rete. Il
controller creativo si ricarica fino alla propria capacità, ma **non aggira
questo confronto**. Con capacità predefinita 32.000 FE e consumo 135.006 FE/t del
32³, la rete non può rimanere operativa. Il log del caso originale conferma:

- 07:06:45: rete operativa, inizio collegamento;
- 07:12:54: collegamento riconosciuto dopo 368,94 secondi;
- subito dopo: rete non operativa, nuova invalidazione del grafo.

Il successivo stallo durante il riscaldamento somma quindi il limite energetico
RS alla nostra gestione troppo costosa delle transizioni. Gli eventuali zeri di
throughput di quella prova non misurano la capacità di crafting: la rete è spenta.
L'assunzione iniziale dell'harness «creativo elimina sempre il vincolo energetico»
era errata. La ripetizione 32³ aumenta soltanto il buffer RS nel mondo di prova.

La ripetizione con buffer adeguato è terminata: 403,65 s per la connessione,
31,29 ms/tick e circa 20 TPS nel carico semplice; 66,68 ms/tick e 14,94 TPS nel
carico annidato. L'eliminazione dello stallo di startup non basta quindi, da sola,
a garantire 20 TPS durante il nested crafting del 32³. Non è stato effettuato un
profilo di attribuzione CPU durante le finestre di throughput: le opportunità
di ottimizzazione del ciclo RS derivano dall'ispezione del codice, mentre la
saturazione del tick è misurata direttamente.

Nelle tre finestre nested del 32³ il tempo GC registrato è 282 ms su circa
48 secondi di lavoro: meno dell'1% del tempo reale. Non spiega da solo il costo
medio di 66,68 ms/tick. La CPU dell'intera JVM corrisponde a circa 1,07 core:
una percentuale modesta rispetto ai 24 core logici della macchina può quindi
coesistere con un tick server saturo. Non basta guardare la CPU totale dell'host.

## Vettori di risoluzione

| Intervento | Responsabilità / livello | Effetto atteso e limiti | Complessità relativa |
| --- | --- | --- | --- |
| Accodare e deduplicare ogni invalidazione dei pattern per rete, inclusi i callback di connessione/energia | **Port** | Elimina le migliaia di ricostruzioni globali nello stesso aggiornamento. Priorità principale; rimane il costo di una ricostruzione. | Bassa–media |
| Richiedere il rebuild solo da storage e transizioni effettive del multiblocco, evitando rebuild al primo tick dei blocchi senza pattern | **Port** | Riduce richieste e controlli inutili. Va preservata la notifica quando la rimozione di una CPU/cornice invalida tutta la struttura: non basta ignorare ogni evento dei blocchi senza inventario. | Bassa–media |
| Rendere la coda un insieme/mappa per identità del manager, senza creare una lista tramite stream a ogni richiesta; scaricarla in un punto stabile dopo gli aggiornamenti | **Port** | Riduce allocazioni e scansioni nella coda; rende esplicita la garanzia di una ricostruzione per rete e tick. Da sola non basta finché resta la chiamata diretta. | Bassa–media |
| Separare i nodi di struttura dai veri container di pattern; inventario di 78 slot soltanto negli storage | **Port**, design ereditato | RS continua a visitare il grafo, ma ordina solo i container reali. Risparmia inventari/list/map vuoti e lavoro sui blocchi senza ricette. | Media |
| Un solo nodo/provider aggregato per multiblocco, con porte o proxy ai punti di connessione | **Port**, revisione architetturale | Riduce anche i nodi RS visitati ogni tick. Più incisivo del solo filtraggio dei pattern; richiede gestione di reti unite/separate, pagine, UUID e salvataggi. | Alta |
| Memorizzare tipo di blocco, consumo e riferimento valido al controller; leggere il conteggio CPU una volta per revisione strutturale | **Port** | Evita ripetute ricerche tile/world. Oggi `getEnergyUsage()` richiama più volte `getBlock/getTile` e il budget CPU viene riletto nel ciclo interno RS. Miglioramento a regime da misurare. | Media |
| Aggiornare salvataggi e stato strutturale solo quando cambiano | **Port**, design ereditato | `MultiBlockCrafter.updateServer()` restituisce sempre true: il framework marca i chunk sporchi ogni tick. Può ridurre I/O/autosave; non spiega lo stack di ordinamento osservato. | Media |
| Buffer controller almeno pari al consumo della rete, con margine; alimentazione sufficiente nel caso survival | **Configurazione RS** | Risolve l'impossibilità energetica del 32³. Un buffer maggiore non corregge le invalidazioni né accelera il crafting. Disabilitare il consumo, da solo, non aggira necessariamente il confronto con la capacità in questa implementazione. | Bassa |
| Cache incrementale dei container/pattern, ordinamento solo quando cambia la topologia | **Interni RS** | Riduce il costo della singola invalidazione. `ICraftingManager` 1.9.17 espone l'invalidazione globale, non un aggiornamento pubblico per singolo provider. Serve patch/fork o integrazione più invasiva; superfluo come primo rimedio alla tempesta provocata da noi. | Alta |
| Budget di crafting condiviso per multiblocco/rete, eventualmente adattivo al tempo del tick | **Port + contratto scheduler RS** | Protegge i TPS con molti task/pattern. Il budget attuale è applicato da RS per nodo della ricetta/container, non è un tetto globale condiviso. Un limite fisso è una scelta di bilanciamento; quello temporale richiede contabilizzare i successi senza consumare quote nelle semplici letture del getter. Può ridurre il picco di throughput. | Media–alta |
| Esecuzione in blocchi di più craft e notifiche aggregate al monitor | **Interni RS**, eventuale motore alternativo nel port | RS esegue ogni craft separatamente: simulazione/estrazione ingredienti, output, sottoprodotti, inserimento, progresso e listener. Si potrebbe aggregare solo quando equivalenza della ricetta è garantita. NBT, contenitori residui, ricette dinamiche e integrazioni rendono scorretto moltiplicare indiscriminatamente l'output. | Alta–molto alta |
| Ottimizzare pianificazione, ricerca pattern e dipendenze per grandi cataloghi e ordini concorrenti | **Prevalentemente RS** | Rilevante per modpack complessi, ma non dimostrato come collo di bottiglia dominante da cinque pattern e un ordine per volta. Richiede benchmark dedicati prima di una patch. | Alta |
| Suddividere carico/reti o scegliere un crafter più piccolo; mantenere caricati i chunk necessari | **Uso/configurazione** | Mitiga picchi e ricollegamenti; non ripara l'algoritmo. Più crafter sulla stessa rete e thread possono ancora saturare lo stesso tick; reti separate non diventano thread separati. | Bassa, con compromessi |

Le stime di complessità confrontano gli interventi tra loro, non sono promesse di
ore di lavoro o di guadagni prestazionali già dimostrati.

Restituire una lista di pattern vuota o `null` come inventario non esclude un
blocco dall'ordinamento RS: il filtro iniziale usa l'interfaccia
`ICraftingPatternContainer` e lo stato attivo. Per eliminare davvero quel costo
occorre separare i tipi di nodo o modificare il filtro interno RS.

RS offre anche `NetworkNodeGraph.runActionWhenPossible`: può rinviare il lavoro
fino alla fine dell'invalidazione del grafo. Non basta passarvi una lambda nuova
per ogni blocco: l'insieme di callback non le deduplica automaticamente come
«stesso manager». Inoltre le transizioni di energia avvengono anche fuori da
quel passaggio. Una coda per identità della rete rimane la soluzione più generale.

## Limiti strutturali e rimedi che non risolvono la causa

Il tick server e le operazioni sul mondo/storage sono eseguiti sul thread server.
I blocchi chiamati «CPU» aumentano il budget logico, **non creano thread hardware**.
Aggiungere core, RAM o flag JVM non elimina N ricostruzioni globali. La RAM può
evitarne l'esaurimento e il GC può influire sui picchi, ma gli stack osservati
mostrano lavoro Java sui container, non un'attesa di I/O o un deadlock dimostrato.

Spostare il crafting o il grafo direttamente su thread paralleli non è una
correzione locale sicura: accessi al mondo, storage, task e callback non hanno
qui un contratto thread-safe. Si potrebbero elaborare snapshot puri e poi
validarli/applicarli sul thread server, con un progetto molto più ampio.

La dimensione del disco non accelera il motore di crafting. Nei test lo spazio
è sufficiente, le materie prime sono rifornite e gli output rimossi. Un nested
crafting produce meno oggetti finali perché ciascuno richiede più operazioni;
non si possono confrontare direttamente assi/s e carrelli/s come stessa unità di
lavoro. Anche dopo le ottimizzazioni del collegamento, il costo per ricetta e
le dipendenze restano.

Per questi casi non emerge un overflow numerico: il 32³ usa ordini da 2.159.920
assi o 134.995 carrelli, e un costo di 135.006 FE/t, tutti ben entro un intero
signed a 32 bit. I limiti numerici complessivi di RS restano da rispettare, ma
non spiegano questo stallo.

## Ordine proposto e verifiche necessarie

1. Correggere aggregazione delle invalidazioni e dimensionare il buffer del test;
   ripetere le stesse misure prima di attribuire un beneficio.
2. Separare i provider dai blocchi strutturali e togliere lookup/allocazioni
   inutili. Misurare separatamente startup, inattività e crafting.
3. Profilare il carico a regime con molti pattern, task concorrenti e monitor
   aperti; soltanto allora decidere se servono provider aggregati, un budget
   condiviso o modifiche interne a RS.

Per ogni modifica: connessione/disconnessione, rottura durante crafting,
ricostruzione, energia che manca/ritorna, cambio pattern, più reti/dimensioni,
chunk unload/reload, salvataggio/riavvio. La deduplicazione non deve lasciare
ricette fantasma, perdere pattern o far craftare un multiblocco smontato.
Il 64³ non viene avviato su richiesta dell'utente.
