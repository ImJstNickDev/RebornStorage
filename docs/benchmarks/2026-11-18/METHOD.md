# Metodo dei benchmark

Implementazione misurata: **5d8d619**, Forge 1.16.5, prima versione del port.
Il codice di produzione (`src/main`) non viene modificato dai benchmark.
Gli harness sono source set separati ed esclusi dal JAR della mod.

## Ambiente

- Intel Core Ultra 9 275HX, 24 CPU logiche; macchina Linux con circa 32 GB RAM.
- Java 8 `1.8.0_512`, `-Xms1G -Xmx6G`, garbage collector predefinito della JVM.
- Forge 36.2.26, RS 1.9.17, ExtraStorage 1.6.1, RSInfinityBooster 1.1+13,
  Curios 4.0.8.2. Non è il modpack completo.
- Un solo server di benchmark alla volta; la sessione desktop e altri programmi
  dell'utente rimangono attivi. Il carico della macchina è campionato nel log.
- Un processo JVM e un mondo nuovo per ciascuna dimensione. Seed 12345, mondo
  piatto, nessun giocatore connesso, mob e random ticks disabilitati, chunk della
  struttura mantenuti caricati. Nessuna modifica a istanze Prism o mondi reali.

## Strutture e carichi

| Caso | Dimensioni esterne | Blocchi totali | CPU | Storage pattern |
| --- | --- | ---: | ---: | ---: |
| Normale | 4×4×4 | 64 | 7 | 1 |
| Medio | 8×8×8 | 512 | 215 | 1 |
| Massimo predefinito | 16×16×16 | 4.096 | 2.743 | 1 |
| Overkill 1 | 32×32×32 | 32.768 | 26.999 | 1 |
| Overkill 2, annullato dall'utente | 64×64×64 | 262.144 | 238.327 | 1 |

Ogni struttura è completamente piena: cornice e dissipatori all'esterno,
uno storage e CPU in tutte le altre posizioni interne. Cinque pattern nello
storage; non si tratta di un benchmark con migliaia di pattern diversi.

Rete dedicata con controller creativo e un Disk Drive contenente il disco
Reborn 262144k. Il controller creativo fornisce energia, ma in RS 1.9.17 resta
necessario un buffer almeno pari al consumo. Il primo 32³ ha evidenziato questo
limite: rete spenta con buffer predefinito da 32.000 FE. Quella prova è conservata
separatamente in `diagnostic-default-buffer` ed esclusa dalle misure di crafting.
Il 32³ viene ripetuto con **2.000.000 FE di capacità** nel solo mondo di prova;
4³, 8³ e 16³ usano 32.000 FE. Il costo teorico con i valori usati è **5 × CPU + 11 FE/t**
(CPU, storage, un disco). Il campo `configured_fe_per_tick` nei raw JSON è una
lettura fatta immediatamente alla connessione, prima del primo aggiornamento
energetico, e non rappresenta il consumo a regime: non viene usato nel report.
Il riepilogo calcola il costo dalle configurazioni effettive di ciascun test.

Carichi:

1. Server senza struttura, con gli stessi chunk caricati: baseline.
2. Struttura completa, collegata e inattiva.
3. Crafting semplice: tronco di quercia → 4 assi.
4. Crafting annidato e ramificato: tronchi → assi → casse → tramogge;
   lingotti → carrelli; tramogge + carrelli → carrelli con tramoggia.
   Quattro livelli di dipendenze, cinque pattern distinti e sei esecuzioni di
   ricetta per oggetto finale (due per le assi). Occorrono 2 tronchi e 10 lingotti
   per carrello con tramoggia. Gli intermedi vengono realmente craftati da RS.

Le scorte iniziali contengono solo le materie prime; vengono rifornite prima dei
nuovi ordini se necessario. Gli oggetti finali vengono contati ed estratti a fine
tick, così lo spazio disponibile non falsifica il risultato. I lotti crescono con
le CPU: `max(256, CPU×80)` assi e `max(8, CPU×5)` carrelli con tramoggia. Una coda
continua di ordini, un ordine alla volta; non migliaia di richieste di giocatori.
Il tempo di pianificazione e avvio degli ordini è incluso nel tick complessivo.

## Campionamento e definizioni

- Riscaldamento iniziale e prima del carico; per il carico fino a 100 tick o
  15 secondi, poi tre finestre consecutive per ciascun workload.
- Ogni finestra attiva mira a 240 tick (12 secondi a 20 TPS), con un limite di
  60 secondi se il server rallenta. Quantità e durata reali sono nei raw JSON.
  Le finestre non sono tre avvii indipendenti della JVM.
- **MSPT**: durata dei tick completati, dal ring buffer di MinecraftServer,
  comprendente lavoro del server, harness e pause GC naturali. Le prime due
  transizioni di finestra sono escluse dai campioni dei tempi.
- **P95**: il 95% dei tick campionati dura al massimo quel valore. 50 ms è il
  budget di un tick a 20 TPS. Il massimo è registrato separatamente.
- **Oggetti/s**: oggetti finali effettivamente consegnati dalla rete, divisi per
  tempo reale trascorso. Assi/s e carrelli/s non sono carichi equivalenti.
- **TPS**: tick effettivi nella finestra / secondi trascorsi, non una previsione
  ricavata dagli MSPT. Il confronto TPS usa le finestre di crafting.
- **Heap trattenuto**: memoria dopo un GC esplicito prima della baseline e della
  misura inattiva. La differenza stima la memoria aggiuntiva della struttura e
  della sua rete, non tutti i picchi di allocazione. Questi due GC di misura
  sono esclusi dai tempi dei tick; il successivo recupero del ritmo può far
  apparire TPS >20 nelle sole finestre baseline/inattive. Non è throughput
  sostenibile e non viene usato per le conclusioni sul crafting.
- **RSS**: memoria residente dell'intero processo, campionata ogni secondo;
  include JVM, heap, codice e strutture native, anche durante avvio e salvataggio.
- **CPU**: tempo CPU dell'intero processo / durata reale, espresso in equivalenti
  di un core. Può superare 1 per GC e thread ausiliari. Sono disponibili anche
  tempo CPU del thread server e temporizzazioni interne del controller RS;
  queste ultime non includono tutto il costo degli altri nodi della rete.
- Posizionamento massivo, attesa di assemblaggio, attesa di connessione e
  salvataggio sono misurati separatamente dai carichi a regime. Le attese
  comprendono la cadenza dei tick, non sono tempi CPU puri. Il salvataggio
  riguarda tutto il mondo di prova, non soltanto il multiblocco.

Il runner limita ogni caso a 20 minuti e interrompe se la memoria disponibile
sull'host scende sotto 1 GiB. Il 64³ è stato annullato dall'utente prima dell'avvio.
Per bloccarne l'avvio nel runner già in esecuzione è stata precreata la cartella
con `skipped.json`: il runner originale termina con `FileExistsError` dopo aver
salvato l'esito del 32³. Nessun processo Minecraft 64³ è stato avviato. Il runner
salvato nel progetto ora riconosce esplicitamente tale marker di esclusione.

## Riproduzione

Con JDK 17 per Gradle e JDK 8 disponibile per Minecraft, dopo aver accettato
l'EULA per il server di prova (`run/integration/eula.txt`):

```sh
python3 scripts/benchmark_port.py --sizes 4 8 16 --tag replica
python3 scripts/benchmark_port.py --sizes 32 --controller-capacity 2000000 --tag replica-32
python3 scripts/analyze_benchmarks.py run/benchmarks/replica report
# Per unire la ripetizione con buffer adeguato:
python3 scripts/analyze_benchmarks.py run/benchmarks/replica report --replace-case run/benchmarks/replica-32/size-32
# Con matplotlib disponibile, aggiungere --plot per PNG e SVG.
```

Il runner usa `run/benchmarks/<tag>/size-N`. Log e campioni di processo restano
locali; nel report sono conservati raw JSON, riepiloghi CSV e provenienza.
Le prove preliminari `pilot`, `pilot2`, `full-*` sono escluse dalla campagna
finale: servivano a verificare le ricette, la gestione delle richieste RS ancora
accodate e l'assenza di limiti artificiali nei lotti/nel disco.

Il primo harness segnava `pass` al completamento delle fasi anche con rete spenta
e zero output. Non è una prova di crafting riuscito: l'analizzatore lo identifica
come `non_operational`. Prima della ripetizione sono stati aggiunti un controllo
esplicito `network.canRun()` e la registrazione di buffer/consumo effettivi.
Nessuna modifica al codice della mod o alle ricette tra le due campagne.
