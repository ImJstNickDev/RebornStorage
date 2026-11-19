# Reborn Storage experimental.4 su Forge 1.16.5

Con il server pack completo, la .4 riduce il tempo medio del 64³ del 15,6% sulle assi e del 5,4% sui carrelli con tramoggia. Il carico annidato raggiunge 19,99 TPS nel banco ridotto e 5,00 TPS in Cuboid.

## Cuboid Outpost LE 1.1.2 su Forge dedicato

Sul Core Ultra 9 275HX abbiamo usato i 158 JAR del server pack ufficiale, configurazioni, script KubeJS e CraftTweaker del server e la ricetta KubeJS locale aggiuntiva dell’istanza. Il mondo nuovo di ciascuna versione usa il generatore SkyblockBuilder del pack. Il banco imposta a 64 il limite per asse, che nel port è 16 di default. Il 64³ contiene 238.327 CPU e un blocco per i pattern. In entrambi i mondi usiamo lo stesso seed, un disco Reborn da 262144k e un controller con buffer da 2.000.000 FE. Ogni versione esegue tre finestre di 80 tick per carico con monitor attivo. Heap 2–8 GiB e nessun giocatore connesso. Sono state aggiunte esclusivamente classi di misura a copie dei due JAR reobfuscati. Le classi e le risorse di produzione di ciascuna copia sono state confrontate byte per byte con il JAR di distribuzione. La registrazione delle eccezioni di avvio termina prima delle finestre attive. [Server pack ufficiale 1.1.2, Forge 36.2.26](https://www.curseforge.com/minecraft/modpacks/cuboid-outpost-le/files/3777751).

| Carico 64³ | .3 ms/tick | .4 ms/tick | .3 TPS | .4 TPS | .4 p95 ms |
|---|---:|---:|---:|---:|---:|
| Assi | 154,17 | 130,07 | 6,46 | 7,66 | 145,64 |
| Carrelli con tramoggia | 210,31 | 198,87 | 4,73 | 5,00 | 324,55 |

Nella .4 il controllo dei listener a runtime restituisce `plain_output_hooks=false`. Il deposito di un risultato intermedio continua a rispettare la creazione dello stack e il confronto completo di Forge e RS. Il backend veloce del disco Reborn resta attivo. La .3 e la .4 consegnano entrambe 217.354.224 assi nei 240 tick del carico semplice e 20.257.795 carrelli nei 240 tick del carico annidato. In ogni finestra coincidono produzione, notifiche della griglia e del task, snapshot del monitor e totale dei byte codificati. KubeJS segnala due ricette fallite nel riepilogo di caricamento del pack. Tutti e cinque i pattern misurati sono stati accettati dal crafter e i relativi ordini completano le finestre. Una prima prova con i JAR del client Prism e un mondo piatto si è fermata al caricamento dei datapack ed è esclusa dai tempi.

[Dati grezzi .3](raw/cuboid-3-64.json) · [Dati grezzi .4](raw/cuboid-4-64.json)

### Profilo CPU del server Cuboid

Una terza JVM esegue la .4 con Java Flight Recorder, due finestre da 60 tick per carico e stack profondi fino a 128 frame. Le misure dei tempi in tabella provengono dalle due JVM precedenti. Abbiamo attribuito ciascun campione del thread server alla fase attiva usando i timestamp del banco.

- **Assi.** 592 campioni su 709 (83,5%) passano dall’inserimento pubblico RS. Di questi, 411 attraversano il drive e 169 l’aggiornamento della cache di rete.
- **Carrelli.** 608 campioni su 1.101 (55,2%) passano dal confronto e deposito degli intermedi nell’inventario del task, 225 (20,4%) dall’inserimento finale in rete e 113 (10,3%) dall’avanzamento del task. Sono rami distinti.
- **Capability Forge.** La raccolta delle capability compare in 266 campioni su 709 per le assi e in 640 su 1.101 per i carrelli. Questi campioni sono inclusi nei rami precedenti. Nel carico annidato, 530 dei 608 campioni degli intermedi passano da questa raccolta. Negli stack compaiono anche i controlli di Quark e Curious Jetpacks e la costruzione di dati di ricetta di Polymorph. Riutilizzare indiscriminatamente il primo stack degli intermedi salterebbe chiamate che il pack esegue davvero. [Campioni raggruppati](cuboid-profile.json)

Il banco registra la decisione dei listener con `-Dbench.inspectCapabilities=true` sulla .4. Per replicare la misura su un server dedicato, aggiungere `jar { from sourceSets.benchmark.output }` al build di una copia di lavoro e compilare con Gradle. Usare i JAR così strumentati solo nei mondi di prova. Le proprietà JVM delle finestre sono `-Dbench.size=64 -Dbench.ticks=80 -Dbench.repeats=3 -Dbench.maxWindowSeconds=180 -Dbench.maxWarmupSeconds=120 -Dbench.countListeners=true -Dbench.monitor=coalesced -Dreborn.craftingExperiment=TASK`. Nel confronto tra versioni si usano le stesse classi del banco con la sonda dei listener abilitata solo sulla .4.

## Banco ridotto, dal 4³ al 64³

Forge 36.2.26, RS 1.9.17, Java 8, 2.000.000 FE nel buffer del controller. Un ordine attivo alla volta. Prova semplice da tronchi ad assi, prova annidata da tronchi e ferro a carrelli con tramoggia con cinque pattern. Il monitor Super Wireless codifica un aggiornamento per tick attivo.

| Lato | Semplice .3 | Semplice .4 | Annidato .3 | Annidato .4 | p95 annidato .4 | TPS annidato .4 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4 | 0,25 | 0,39 | 0,23 | 0,32 | 0,45 | 20,00 |
| 8 | 0,41 | 0,39 | 0,38 | 0,37 | 0,49 | 20,00 |
| 16 | 0,85 | 0,79 | 0,88 | 0,65 | 0,91 | 20,00 |
| 32 | 7,97 | 5,81 | 7,09 | 3,85 | 6,09 | 20,00 |
| 64 | 63,96 | 57,43 | 59,50 | 32,28 | 50,08 | 19,99 |

Tempi in ms/tick, media e p95 su 234 campioni. Tre finestre di 80 tick per carico con una JVM per taglia. TPS e quantità consegnate al secondo usano tutti i 240 tick e il tempo reale. Il 4³–32³ è stato misurato a `02873ae`. Il 64³ e la prova ExtraStorage usano la revisione finale `606a860`, che include anche la compatibilità con Curios 4.0.8.0 e l’ordine di copia del resto dopo le notifiche del disco. La prima esecuzione sul 64³ misurava 53,49 e 34,09 ms/tick. Il confronto .3 usa i [dati della campagna precedente](../2026-11-18-guarded/README.md).

| 64³, dischi effettivi | Semplice ms/tick | TPS | Annidato ms/tick | TPS | p95 annidato |
|---|---:|---:|---:|---:|---:|
| 1 × Reborn 262144k | 57,43 | 17,32 | 32,28 | 19,99 | 50,08 |
| 8 × ExtraStorage 16384k | 70,84 | 14,05 | 38,99 | 19,98 | 58,91 |

Il confronto ExtraStorage usa otto dischi fisici per contenere gli stessi ingredienti. Cambiano anche il numero e l’ordine degli storage sondati da RS. Nel 64³ con disco Reborn la velocità di esecuzione del tick migliora di 1,11× nel semplice e 1,84× nell’annidato. I campioni sopra 50 ms sono rispettivamente 222 e 12 su 234.

Per tutte le taglie il confronto sul disco Reborn verifica, finestra per finestra, produzione, notifiche del task e della griglia, numero di snapshot e totale dei byte codificati. La prova ExtraStorage confronta anche i conteggi del task e la produzione con il 64³ Reborn. Il campionamento misura il tempo dell’intero tick.

Per ripetere, usare `scripts/benchmark_port.py --sizes 64 32 16 8 4 --ticks 80 --repeats 3 --controller-capacity 2000000 --property reborn.craftingExperiment=TASK --property bench.monitor=coalesced --property bench.countListeners=true` con un tag nuovo. Per ExtraStorage aggiungere `--property bench.extraStorageDisks=true`.

## Verifica funzionale

Ai due avvii il server supera 134 e 138 controlli generali e 140 controlli differenziali per avvio. Un listener si registra al terzo craft dello stesso tick e assegna stati Forge diversi agli output seguenti. Le quantità delle quattro varianti coincidono con RS. Sul disco sono verificati anche rientranza, simulazione, saturazione con una richiesta da 2.147.483.647 pezzi, estrazione parziale e NBT osservabile dopo una callback.

Il client Minecraft con server integrato registra 35 quantità decrescenti su un ordine da 12.000 assi. L’intervallo massimo osservato è di 1 tick client (51 ms). Il JAR di distribuzione riprende su Forge dedicato un ordine .3 e un ordine .4, consegnando esattamente 4.000 bastoni e lasciando zero task in entrambi i mondi. SHA-256 del JAR `1a05e7d15643d18ee2e70afe440fd1da8b74710ffabd9fe32e6126348b57a5e9`. [Esiti delle verifiche](validation.json)
