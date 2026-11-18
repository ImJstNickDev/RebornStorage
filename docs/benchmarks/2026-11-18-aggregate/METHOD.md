# Metodo del confronto 0.1.0 → 0.1.1

Riferimento: [metodo della campagna iniziale](../2026-11-18/METHOD.md).
Il confronto usa gli stessi carichi, dimensioni, conteggi CPU, cinque pattern,
quantità degli ordini, scorte, estrazione degli output e finestre di misura.

- Prima: produzione `5d8d619`, 0.1.0. Il 32³ proviene dalla ripetizione con buffer
  adeguato, non dal tentativo iniziale con rete spenta.
- Dopo: `264b5e4`, 0.1.1 (implementazione `b1b8221`, successivo import dell'API
  per la telemetria del benchmark). Nessuna modifica al codice durante i carichi.
- 4³, 8³, 16³: buffer RS 32.000 FE. 32³: 2.000.000 FE, in entrambe le versioni.
- Java 8, `-Xms1G -Xmx6G`, medesimi hardware e dipendenze della prima campagna.
- Server nuovi, eseguiti in sequenza, senza giocatori. Il server della visita
  dell'utente è stato arrestato prima della campagna comparativa.
- Tre finestre consecutive da 240 tick per carico, riscaldamento identico; una JVM
  per dimensione. Non sono tre repliche indipendenti della JVM o dell'avvio.
- 64³ escluso su richiesta dell'utente; nessuna estrapolazione dei risultati.

L'harness cambia soltanto il percorso di accesso all'inventario (ora distinto dal
provider aggregato) e aggiunge i conteggi di provider/nodi persistenti. La rete
contiene un controller creativo, un disk drive con disco 262144k e un multiblocco
pieno di CPU salvo un solo storage. Il carico semplice produce assi da tronchi;
quello annidato produce carrelli con tramoggia da tronchi e ferro.

I tempi medi sono quelli del tick completo del server, non del solo crafter.
Il collegamento comprende anche la cadenza dei tick: valori nell'ordine di pochi
millisecondi o di un tick non sono microbenchmark della sola funzione di rebuild.
Il P95 deriva dai campioni dei tick, gli oggetti/s dal tempo reale; il minimo e il
massimo dei throughput descrivono le tre finestre, non intervalli di confidenza.
Le letture TPS delle fasi baseline/inattiva non vengono usate per le conclusioni:
il GC esplicito può provocare recupero del ritmo. Le fasi attive usano TPS reali.

La sessione desktop e i programmi dell'utente rimangono attivi. I dati di carico
host sono registrati in `process-N.json`; piccoli scarti dei casi più leggeri
possono riflettere rumore/JIT. Non sono prove del modpack completo, di tanti task
simultanei o di migliaia di pattern. L'ottimizzazione del port non sostituisce
il motore di crafting RS e non introduce un budget temporale globale.

## Riproduzione

```sh
python3 scripts/benchmark_port.py --sizes 4 8 16 --tag aggregate
python3 scripts/benchmark_port.py --sizes 32 --controller-capacity 2000000 --tag aggregate-32
python3 scripts/analyze_benchmarks.py run/benchmarks/aggregate report --replace-case run/benchmarks/aggregate-32/size-32 --plot
python3 scripts/compare_benchmarks.py docs/benchmarks/2026-11-18 report --plot
```

`--plot` richiede matplotlib. Per Gradle usare JDK 17, per Minecraft il progetto
seleziona JDK 8. Il runner richiede l'EULA già accettata per il server di prova.
