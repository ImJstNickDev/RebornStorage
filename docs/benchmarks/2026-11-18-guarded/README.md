# Prestazioni del crafter multiblocco in experimental.3

Confronto Forge dedicato della .2 e .3 in modalità TASK, con un ordine alla volta, il monitor e la griglia in ascolto degli eventi RS e la codifica dei pacchetti del Super Wireless Grid attiva. Il 32³ ha come soglia prefissata 7,19 ms/tick nel carico annidato.

| Lato | Semplice .2 | Semplice .3 | Annidato .2 | Annidato .3 | Accelerazione annidato | TPS annidato .3 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4 | 0,28 | 0,25 | 0,10 | 0,23 | 0,45× | 20,00 |
| 8 | 0,17 | 0,41 | 0,19 | 0,38 | 0,52× | 20,00 |
| 16 | 1,10 | 0,85 | 1,75 | 0,88 | 1,98× | 20,01 |
| 32 | 9,50 | 7,97 | 14,51 | 7,09 | 2,05× | 20,00 |
| 64 | 78,62 | 63,96 | 125,90 | 59,50 | 2,12× | 16,73 |

Tempi medi in ms/tick. Sul 32³ la prima esecuzione .2 ha registrato 12,03 ms nel semplice e 14,40 ms nell’annidato. Per la tabella abbiamo ripetuto il riferimento a macchina libera perché una compilazione si è sovrapposta a parte del primo caso. Le tre finestre annidate della .3 sul 32³ misurano 7,54, 6,84, 6,88 ms/tick.

| Lato | Idle .3 ms/tick | P95 semplice .3 | P95 annidato .3 | TPS semplice .3 | Assi/s .3 | Carrelli con tramoggia/s .3 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4 | 0,22 | 0,39 | 0,33 | 20,00 | 532 | 50 |
| 8 | 0,25 | 0,57 | 0,48 | 20,01 | 16.345 | 1.523 |
| 16 | 0,07 | 1,03 | 1,35 | 20,00 | 208.507 | 19.437 |
| 32 | 0,09 | 10,22 | 11,26 | 20,00 | 2.052.318 | 191.236 |
| 64 | 0,10 | 71,93 | 93,30 | 15,57 | 14.105.374 | 1.412.327 |

## Metodo e integrità

Tutte le strutture sono piene con un blocco pattern e CPU nel resto dell’interno. Lavori semplici da tronchi ad assi e annidati da tronchi e ferro a carrelli con tramoggia, cinque pattern. Dopo il riscaldamento ogni carico viene misurato in tre finestre consecutive da 80 tick. Ogni combinazione di versione e taglia usa una JVM distinta. Il tempo del tick arriva dal buffer del server, con 78 campioni interni per finestra di 80 tick. TPS e oggetti al secondo usano tutti gli 80 tick e il tempo reale trascorso. CPU Intel Core Ultra 9 275HX, heap 1–6 GiB. Buffer del controller 2.000.000 FE uguale per entrambi. Il server usa Java 8, Forge 36.2.26 e RS 1.9.17. Il banco mantiene un ordine attivo e preleva dal disco RS gli oggetti consegnati a ogni tick. Le misure sono locali e senza profiler.

Nel confronto appaiato tutte le 60 finestre attive terminano con output positivo. Confrontiamo per ogni finestra quantità consegnate, conteggi delle notifiche dei task e della griglia, numero di snapshot del monitor e byte complessivi codificati. Tutti questi valori coincidono. Ogni finestra produce 80 snapshot. Il contatore dei piani verifica quattro intermedi qualificati per ciascun ordine annidato su tutte le taglie. Il craft semplice ha solo il nodo finale.

Per ripetere il confronto, usare due checkout alle revisioni `e25a25c` e `4e8aceb`, con EULA accettata nel server di prova e un tag nuovo per versione.

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 4 8 16 32 64 --ticks 80 --repeats 3 --controller-capacity 2000000 --property reborn.craftingExperiment=TASK --property bench.countListeners=true --property bench.monitor=coalesced --property bench.maxWarmupSeconds=120 --property bench.maxWindowSeconds=180 --tag local-guarded
```

Il riepilogo comprende revisioni, hash dei dati grezzi, throughput, percentili, medie per ripetizione e risultati delle prove di ripristino e del client. Include anche il primo riferimento sul 32³. [Confronto per finestra](comparison.json) · [Dati grezzi](raw/)

## Interpretazione

Nel 32³ annidato la media di 7,09 ms/tick raggiunge la soglia di 7,19. Il rapporto sul riferimento ripetuto è 2,05×. La prima finestra .3 supera la soglia individualmente. Sul 16³ e sul 32³ il server raggiungeva già circa 20 TPS. A parità di lavoro per tick il beneficio è tempo disponibile sul thread del server.

Nel 64³ annidato i TPS salgono da 7,91 a 16,73. Il 95° percentile della .3 è 93,30 ms. Il benchmark semplice sul 64³ resta a 63,96 ms/tick. L’inserimento pubblico nella rete continua ad avvenire a ogni craft finale.

Nei casi leggeri l’annidato costa di più su 4³ e 8³ in questa campagna, con medie inferiori a mezzo millisecondo ed entrambe le versioni a circa 20 TPS. Le tre finestre provengono dalla stessa JVM per ciascuna misura. I dati descrivono queste esecuzioni e non isolano la causa delle differenze sotto il millisecondo.

## Correttezza

Entrambi gli avvii del server superano la suite e i controlli differenziali. Un listener sostituisce il prossimo ingrediente con uno stack con NBT diverso o cambia solo una capability serializzabile. In entrambi i casi il successivo craft si ferma come in RS. Un vero ItemExternalStorage RS su un handler Forge accetta due oggetti e rifiuta gli altri. I 22 oggetti residui restano nel task. Dopo salvataggio e riavvio un ordine annidato attivo termina con 4.000 bastoni. Il JAR reobfuscato riprende sia il salvataggio della .2 sia quello annidato della .3 su Forge dedicato.

Il client integrato su un ordine da 12.000 assi osserva 35 quantità distinte e decrescenti. Il massimo intervallo tra due cambiamenti nella finestra osservata è di 1 tick client (51 ms). Cuboid Outpost completo e latenza di rete remota restano fuori dal banco di prova.
