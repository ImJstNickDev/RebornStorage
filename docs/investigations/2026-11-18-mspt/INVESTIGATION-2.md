# Indagine 2 — riesame dei casi esclusi e delle conclusioni

Seconda passata dello stesso autore, con domande diverse dalla prima: cosa
cambia con interfacce aperte, ordini concorrenti, tanti storage, reload e ricette
moddate? Non è una revisione indipendente svolta da un secondo agente.

## 1. Interfacce aperte: il caso assente più importante

Il profilo della prima indagine non conteneva giocatori o listener delle UI.
Un costo dell'1% nel ramo di notifica senza listener **non consente** di
considerare le notifiche poco importanti in gioco.

Percorso del monitor RS 1.9.17:

```
CraftingNode.update -> CraftingTask.onSingleDone
  -> CraftingManager.onTaskChanged
    -> CraftingMonitorContainer.onChanged
      -> sendTo(player, new CraftingMonitorUpdateMessage(...))
        -> encode: task visibili, richieste NBT, progressi, elementi del monitor
```

Il monitor wireless del nostro port usa proprio il container RS attraverso
`WirelessCraftingMonitorNetworkItemExt.onOpen`. Non abbiamo introdotto un
limite/accorpamento degli aggiornamenti visivi.

Percorso griglia:

```
Network.insertItem -> ItemStorageCache.add(..., batched=false)
  -> ItemGridStorageCacheListener.onChanged
    -> nuovo elenco delta -> GridItemDeltaMessage
```

RS ha un percorso di batching nello storage cache, ma `Network.insertItem`
non lo seleziona. Dire «RS ha già il batching» senza seguire il chiamante sarebbe
una conclusione errata.

### Misura aggiunta

Due campagne reali 16³, una con un ordine e una con quattro ordini concorrenti.
Stessi cinque pattern, controller, quantità per ordine, heap e finestre.
Il test registra due listener **che incrementano contatori soltanto**: non
apre UI, non serializza messaggi e non invia pacchetti. I numeri sono callback
verificati, non traffico di rete misurato né una previsione esatta dei ms/tick
con un client connesso. Il costo degli incrementi è presente in entrambi i casi.

Con un ordine annidato: circa **117.919 callback/s al monitor** e **19.600
callback/s alla griglia**. Per il semplice: circa **52.205** e **52.222**.
I callback griglia includono anche estrazione degli output/rifornimenti del
banco di prova; non tutti sono inserzioni prodotte dal crafter.
La tabella completa e il caso concorrente sono in [misure](measurements.json).

### Rimedio

- Nel nostro Super Wireless Monitor: container/listener con stato dirty e
  invio di uno snapshot coerente a fine tick, con primo stato immediato,
  aggiornamento finale e gestione corretta di chiusura/disconnessione.
  Aggiornare la UI non richiede un pacchetto per ciascun oggetto craftato.
- Per monitor/griglie RS normali e di altri addon: patch al percorso comune RS
  o integrazione compatibile. Correggere soltanto il nostro monitor non copre
  tutte le schermate che il giocatore può aprire.
- Griglia: aggregare delta per identità completa dell'item, mantenendo UUID,
  quantità, rimozioni e reinserzioni nello stesso tick. I delta contengono
  riferimenti a stack mutabili: accodare semplicemente gli oggetti esistenti
  può produrre dati sbagliati. Snapshot/copertura dei casi limite indispensabili.
- Misurare poi il costo reale con 1/2/4 giocatori, griglia e monitor aperti,
  byte/packet encoder e client frame time. La prova attuale evita deliberatamente
  di generare una tempesta di messaggi.

## 2. Il budget non è globale: il 16³ non ha un costo massimo di 5,7 ms

`CraftingTask.update` visita tutti i nodi di ogni task; `CraftingNode.update`
riapplica il budget del container per ciascuno. Quattro ordini dello stesso
pattern ricevono ciascuno il budget. Un solo provider aggregato evita provider
fisici duplicati, **non crea una quota condivisa fra task**.

Le prove misurano il picco di task realmente attivi, non solo quattro richieste
inviate. Il caso concorrente usa quattro ordini in parallelo, attende il loro
completamento e avvia il gruppo successivo; non rappresenta tutte le possibili
politiche di arrivo/scaglionamento. [Risultati](measurements.json).

| Carico 16³ | Un ordine, ms/tick | Quattro ordini, ms/tick | TPS con quattro ordini |
| --- | ---: | ---: | ---: |
| Semplice | 2,79 | 9,60 | 20,01 |
| Annidato | 5,88 | 23,10 | 20,00 |

Nel carico annidato con quattro task: 78.319 output/s, 471.600 callback/s al
monitor e 78.345 callback/s alla griglia. Il costo cresce quasi quattro volte,
così come il lavoro completato. Non è una regressione causata dalla struttura:
la configurazione di un solo ordine non ne descriveva la capacità di saturare
il server. Non ho eseguito quattro ordini sul 32³.

Conseguenze: per migliorare l'efficienza servono meno operazioni/copie per craft;
per contenere il lavoro totale servono contabilizzazione e fairness. Il getter
`getMaximumSuccessfulCraftingUpdates` non riceve informazioni su nodo/task e RS
lo richiama nella condizione del ciclo: introdurvi un decremento altera il
contratto e non conta affidabilmente i successi. Una quota richiede un punto
esplicito di consumo, priorità e politica anti-starvation. Non è stata introdotta
nessuna riduzione di velocità o soglia arbitraria.

## 3. Difetto locale trascurato: scansione quadratica delle pagine

Il nostro `CraftingNode.rebuildPatterns` scorre tutti gli slot dell'inventario
aggregato. `CombinedInvWrapper.getStackInSlot` di Forge 36.2.26 cerca il relativo
handler con un ciclo lineare dall'inizio. Con S storage, ognuno da 78 slot,
la scansione completa richiede **78 × S × (S+1) / 2** confronti degli indici:

| Storage | Slot visitati | Confronti degli indici attuali |
| --- | ---: | ---: |
| 1 | 78 | 78 |
| 100 | 7.800 | 393.900 |
| 1.000 | 78.000 | 39.039.000 |

È una proprietà verificata del codice, non una misura di latenza. Il primo
benchmark aveva un solo storage: non poteva evidenziarla.

Correzione locale semplice: ricostruire i pattern iterando direttamente gli
handler delle pagine. Conservare l'inventario aggregato per il contratto API;
se necessario sostituire il mapping con divisione/modulo (pagine tutte da 78)
o ricerca binaria per pagine di dimensione variabile. Non serve cambiare RS.

In più, una modifica a uno slot ricrea oggi tutti i pattern e poi invalida tutto
il manager RS. Proposta: cache per slot/pagina, insieme degli slot sporchi,
ricostruzione totale solo su topologia/reload, invalidazione globale RS comunque
una volta per tick quando richiesta. La ricostruzione dei pattern include ricerca
di ricette ed espansione dei tag: l'indice della pagina non è l'unico costo.

Separare anche l'aggiornamento del solo costo FE da `CraftingNode.configure`:
attualmente una variazione dei quattro costi richiama configure e invalida i
pattern pur senza cambiare ricette o inventario. È un costo di transizione,
non una spiegazione del profilo stabile.

## 4. Cataloghi, inventari NBT e richieste brevi

RS `CraftingCalculator.calculate` copia le liste item/fluid della rete per ogni
pianificazione. `CraftingManager.getPattern` cerca linearmente nei pattern;
`CraftingPattern.hashCode` ricalcola input/output/tag. La prima indagine aveva
cinque pattern e pochissimi tipi di item, quindi i 9,65 ms di pianificazione
totali non escludono un collo di bottiglia con inventari grandi e molti piccoli
ordini. Serve una matrice separata con pattern unici, varianti NBT, sostituzioni
di tag, inventari esterni e richieste concorrenti. Non usare mille copie dello
stesso pattern come equivalente di mille ricette diverse.

Interventi possibili: indice per output con successiva verifica del comparer,
chiavi/codici hash immutabili, snapshot di pianificazione con versione. Le sole
chiavi item-ID non distinguono NBT/capability; la cache dei piani deve invalidarsi
su inventario, ricette, tag e struttura e preservare la disponibilità reale.

## 5. Riesame della fattibilità e della sicurezza delle ottimizzazioni

- «È nel percorso RS» non implica necessariamente dover distribuire un fork RS.
  `ICraftingPattern` consente un adattatore per output/residui, e
  `getCraftingTaskFactoryId`/il registro delle factory consentono task dedicati.
  Tuttavia sostituire il loop attraverso una nuova factory è un progetto ampio:
  reti miste, salvataggi, task vecchi, monitor, processing e cancellazione.
  Una patch circoscritta alle versioni RS supportate potrebbe essere più piccola,
  ma introduce dipendenze dagli internals. Sono alternative, non due addon da
  installare insieme per fare la stessa cosa.
- Non esiste una garanzia generale che una ricetta sia pura perché appare
  shaped/shapeless. Hook Forge, capability e mod possono cambiare residui e NBT.
  Caching generico o eliminazione della simulazione non sono ottimizzazioni
  sicure. Primo esperimento: riuso controllato di strutture temporanee mantenendo
  le chiamate originali; batching solo per percorsi con equivalenza verificata.
- Una nuova cache deve avere una generazione per reload di ricette/tag. Nel port
  non c'è un handler esplicito di reload per questi pattern: il comportamento
  attuale va verificato prima di aggiungere altra cache. Non è un bug di reload
  riprodotto in queste prove.
- I task caricati da disco ricostruiscono il pattern tramite il provider
  persistente: un wrapper applicato solo ai pattern nuovi potrebbe non accelerare
  i task ripristinati. Testare entrambe le strade.
- `markDirty` del disco ad ogni inserzione marca dati da salvare: non implica
  una scrittura su disco per craft. Non eliminarlo per inseguire I/O inesistente.
- Le nostre CPU/cornici non sono tile tickanti individualmente; il vecchio
  `MultiblockServerTickHandler` non è registrato. Non emerge un doppio tick
  della struttura da correggere. Costi di porte/superficie e assemblaggio
  rimangono rilevanti durante ricollegamenti, non nel ciclo stabile profilato.
- Le prove da 240 tick registrano 238 campioni del timer del tick completo:
  vengono escluse transizioni ai confini. TPS/output usano la finestra reale
  completa. Non sottrarre medie di timer con finestre diverse per attribuire
  pochi microsecondi al port. Gli eventi TLAB non attribuiscono byte esatti a
  ogni classe e le percentuali inclusive JFR si sovrappongono.

## Delta rispetto alla prima passata

La seconda indagine cambia la priorità per l'uso reale: aggiornamenti UI e
rebuild delle pagine sono due bersagli concreti prima assenti dal profilo.
Conferma che la struttura aggregata non limita il lavoro totale di più ordini.
Raffina inoltre la fattibilità: alcuni interventi restano nel port tramite API,
altri richiedono un esecutore dedicato o una patch RS. Nessun risultato del 16³
con un ordine va presentato come garanzia per il modpack completo.
