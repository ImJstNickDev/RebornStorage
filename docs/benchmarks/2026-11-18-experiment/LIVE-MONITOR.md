# Monitor attivo e costo del crafting in experimental.2

Il Super Wireless Grid invia un pacchetto RS a fine tick quando il contenuto cambia. Il gestore degli ordini riceve un evento per ogni craft completato. Nel nuovo inventario privato ogni ingrediente viene cercato e consumato sullo stack effettivo. Le ricette calcolano ancora il risultato e i residui a ogni esecuzione. Un inserimento sulla rete pubblica conserva la quantità, il momento e la possibilità di rifiuto del singolo craft.

| Configurazione | Semplice (ms/tick) | Annidato (ms/tick) | TPS annidato |
| --- | ---: | ---: | ---: |
| 32³, .1, monitor chiuso | 12,36 | 26,27 | 19,98 |
| 32³, .2, monitor chiuso | 8,68 | 14,64 | 20,00 |
| 32³, .1, monitor aperto | 12,85 | 26,92 | 19,98 |
| 32³, .2 finale, monitor aperto | 8,72 | 13,50 | 20,00 |
| 64³, .2, monitor aperto | 80,16 | 124,46 | 8,00 |

In ogni caso un listener RS conta tutte le notifiche del monitor e un secondo listener conta i cambiamenti della griglia. Con monitor aperto il benchmark usa il menu del Super Wireless Grid, un giocatore Forge simulato e il vero encoder del pacchetto RS. Ogni finestra attiva ha registrato 80 snapshot su 80 tick. Nei confronti appaiati del 32³ coincidono, finestra per finestra, gli output, entrambi i conteggi delle notifiche, il numero di snapshot e i byte totali codificati. La verifica dei byte riguarda la lunghezza complessiva.

Nel test client separato abbiamo aperto il menu e richiesto 12.000 assi. Il client Minecraft con server integrato ha letto 35 quantità residue distinte e decrescenti dagli elementi visualizzati nella schermata. Il massimo intervallo osservato fra due cambiamenti è stato di 1 tick client (50 ms). È una prova locale, su un ordine e nella finestra osservata. La cadenza reale dipende anche dai tick del server, dalla rete e dal client.

I test differenziali confrontano lo stato di RS e del port dentro ogni notifica, inclusi requisiti residui, inventario con NBT e avanzamento. Due prove di storage esterno verificano anche i requisiti letti durante l'inserimento parziale o rifiutato. Entrambi gli avvii dedicati hanno superato 121 controlli sperimentali oltre alla suite esistente. Su una copia del mondo salvato con .1, il JAR finale ha ripreso l'ordine pendente e portato il disco da 120 a 4.000 assi con zero ordini residui.

## Riproduzione

JVM Java 8 separate e seriali sullo stesso host, heap 1–6 GiB, un ordine alla volta e buffer del controller di 2.000.000 FE. Tre finestre successive da 80 tick per ciascun carico, dopo il riscaldamento. I tempi medi usano i campioni di tick completi. Per il 32³ la base è `ff4fa7b`, il primo rework è `c5538c8` e la verifica finale con monitor aperto è `d75c5e2`. Il 64³ misura il rework a `c5538c8`. Il commit successivo allinea il momento in cui gli hook di inserimento vedono il consumo dei requisiti. Il modpack completo e una connessione multiplayer remota restano fuori da queste misure.

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 32 --ticks 80 --repeats 3 --controller-capacity 2000000 --property reborn.craftingExperiment=TASK --property bench.countListeners=true --property bench.monitor=coalesced --tag local-live-monitor
```

Per il caso con monitor chiuso omettere `--property bench.monitor=coalesced`. Per il 64³ cambiare la taglia e aggiungere `--property bench.maxWarmupSeconds=120 --property bench.maxWindowSeconds=180`. Usare un tag nuovo per ogni esecuzione. I dati per finestra sono disponibili nel [confronto JSON](live-monitor.json).

JAR verificato (SHA-256) **614b903631ac7a976ae27cf4cb148009eb5af76b493ee1e412771a61fac41927**.
