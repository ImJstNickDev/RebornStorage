# Indagine 1 — costo del crafting a regime

Produzione esaminata: `23eaa8d`, identica al codice 0.1.1 dei benchmark precedenti.
Sorgenti RS: JAR locale 1.9.17 rimappato/decompilato, Forge 36.2.26 e Curios
4.0.8.2 effettivamente caricati. Nessuna modifica al codice di produzione.
Questa prima passata riguarda il carico già riproducibile: 32³, un ordine alla
volta, cinque pattern, nessuna schermata aperta, buffer 2.000.000 FE.

## Evidenza runtime

Campagna `run/benchmarks/investigation1-jfr32`, Java 8, heap 1–6 GiB,
Java Flight Recorder `settings=profile`, stack depth 128. Tre finestre da
240 tick per carico, oltre al riscaldamento. Timeline delle fasi registrata in
millisecondi epoch; il riepilogo include solo gli ExecutionSample del thread
`Server thread` nelle fasi attive. I campioni non sono misure esatte del costo
esclusivo di ogni istruzione e risentono di inlining/JIT e bias del campionatore.
Le percentuali seguenti hanno per denominatore i campioni, non i millisecondi.

| Percorso esclusivo classificato nello stack | Semplice, 1.176 campioni | Annidato, 2.452 campioni |
| --- | ---: | ---: |
| Preparazione requisiti `NodeRequirements.getSingleItemRequirementSet` | 22,1% | 21,5% |
| Simulazione/estrazione `IoUtil.extractFromInternalItemStorage` | 25,5% | 32,8% |
| Calcolo output `CraftingPattern.getOutput` | 7,4% | 9,0% |
| Calcolo residui `CraftingPattern.getByproducts` | 12,8% | 15,0% |
| Inserimento dell'output finale nella rete | 28,6% | 5,3% |
| Notifica `CraftingTask.onSingleDone`, senza ascoltatori UI | 1,0% | 1,1% |
| Altro, inclusi inserimenti intermedi e controllo del ciclo | 2,6% | 15,4% |

La classificazione prende il primo ramo riconosciuto risalendo dalla foglia;
le righe sono disgiunte. Le statistiche inclusive nel JSON invece si
sovrappongono e **non vanno sommate**. Il percorso `CraftingTask.update` compare
nel 98,6% dei campioni semplici e nel 99,3% di quelli annidati. Ciò include
Minecraft, Forge e Curios chiamati da RS: non significa che ogni costo sia
codice scritto da RS, né che ulteriori ottimizzazioni nel port siano impossibili.

- Semplice: 24,19 ms/tick; timer interno rete RS 23,87 ms.
- Annidato: 57,52 ms/tick; timer interno rete RS 57,49 ms.
- Pianificazione annidata: 9,65 ms totali per 51 nuovi ordini, su 41,69 s.
- GC annidato: 271 ms su 41,69 s, circa 0,65%. Il timer GC non separa
  tutte le attività concorrenti; non è una misura del costo delle allocazioni.
- Eventi TLAB del thread server: circa 138,7 GB assegnati durante le fasi
  annidate, circa 3,33 GB/s. È una stima da dimensioni TLAB più allocazioni
  esterne, non heap trattenuto né conteggio esatto di byte per classe.
- Il riferimento senza profiler era 58,65 ms/tick. La piccola differenza
  **non costituisce un miglioramento del codice**: nessuna ottimizzazione è
  stata applicata e sono JVM diverse.

## Perché costa

RS `task/v6/node/CraftingNode.update` applica il budget a ogni nodo di ricetta
per ogni task. Ogni esecuzione compie:

1. Generazione dei requisiti e verifica tramite estrazioni simulate.
2. Nuova generazione dei requisiti e consumo tramite estrazioni reali.
3. Costruzione di un `DummyCraftingInventory` per `recipe.assemble`.
4. Inserimento dell'output nella memoria del task oppure nella rete.
5. Secondo `DummyCraftingInventory` per `recipe.getRemainingItems`.
6. Avanzamento del task e notifica dei listener.

`NodeRequirements` possiede già una cache simulata, ma il passaggio PERFORM la
azzera: non evita la ricostruzione al craft successivo. `IoUtil` costruisce una
lista di risultati anche nel percorso PERFORM dove il chiamante la ignora.
`ItemStorageDisk.extract` restituisce copie di ItemStack anche per la simulazione.
Il multimap Guava crea wrapper/iteratori nelle ricerche ripetute. Nei campioni
compaiono costruttori ItemStack, attach delle capability Forge/Curios e due
inventari fittizi. Il problema non è soltanto il tempo del GC dopo le allocazioni.

## Interventi derivati dalla prima passata

| Intervento | Dove | Opportunità e condizioni |
| --- | --- | --- |
| Preparare requisiti immutabili una volta e separare i contatori residui | RS task/NodeRequirements o esecutore dedicato | Primo bersaglio del profilo. Gestire cambi di variante, NBT/capability, quantità residue, ingredienti duplicati e ripristino NBT; non basta conservare una lista mutabile |
| Verifica di disponibilità senza copiare stack, consumo con risultati aggregati | RS IoUtil/storage interno | Evitare liste e copie non utilizzate; mantenere atomicità sul thread server e l'identica semantica simulate/perform. Le API attuali restituiscono stack, quindi il solo provider del port non elimina questo costo |
| Evitare ricostruzione doppia dell'inventario ricetta; cache solo per ricette dimostrate pure | Pattern adapter del port oppure RS | API `ICraftingPattern` offre getOutput/getByproducts. Nessun caching indiscriminato: ricette dinamiche, hook Forge, capability, remainder e reload possono cambiare il risultato. Chiave completa e copie dei risultati; whitelist/fallback verificati |
| Eseguire più craft equivalenti per operazione | RS loop oppure nuovo task factory del port | Potenziale maggiore, ma modifica più rischiosa. Calcolare batch minimo tra budget, ingredienti, residuo ordine e spazio; preservare remainder, backpressure, progressi, cancellazione e crash/restart. Non moltiplicare solo l'output |
| Aggregare inserimenti e aggiornamenti cache | RS rete/storage, o esecutore consapevole del batch | Il semplice spende circa il 29% dei campioni nell'inserimento finale. Rispettare priorità, filtri, external storage, inserimenti parziali, UUID e quantità a 32 bit |
| Budget condiviso/fairness per rete o multiblocco | Scheduler/integrazione RS | Protegge i TPS, ma è controllo del carico, non prova di maggiore efficienza. Non ridurre arbitrariamente la velocità né consumare budget con effetti collaterali dentro un getter |

Le percentuali sono aree di indagine, **non guadagni promessi**: le modifiche
si sovrappongono. Non si può sommare 54% di requisiti/estrazioni a 24% di output
per promettere quel risparmio. Il batching tocca entrambi e cambia i profili.

## Cosa non emerge come priorità da questa prova

- Nuove micro-ottimizzazioni di `getEnergyUsage`, scansione delle CPU o gestione
  di migliaia di nodi: nel port attuale quelle scansioni non avvengono nel ciclo
  per craft e il costo inattivo è già piccolo.
- Pianificazione: insignificante qui, ma con cinque pattern e inventario povero
  non è rappresentativa di un modpack con tanti tipi/NBT.
- Aumentare RAM, capacità disco o numero di core: non elimina il ciclo sul thread
  server. Non spostare accessi a mondo/storage su thread senza contratto thread-safe.
- Il 64³ non è stato avviato.

## Fonti locali riproducibili

- `scripts/benchmark_port.py --sizes 32 --controller-capacity 2000000 --jfr --tag investigation1-jfr32`
- `run/benchmarks/investigation1-jfr32/size-32/profile.jfr` e `phase-timeline.jsonl`.
- [Riepilogo campioni](jfr-summary.json), [misure della prova profilata](profiled-32.json).
- RS locale: `apiimpl/autocrafting/task/v6/node/{CraftingNode,NodeRequirements}.java`,
  `task/v6/{IoUtil,CraftingTask}.java`, `autocrafting/CraftingPattern.java`,
  `storage/disk/ItemStorageDisk.java`, `network/Network.java` sotto
  `.local-investigation/rs-source/com/refinedmods/refinedstorage/`.
