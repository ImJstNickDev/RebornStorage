# Ottimizzazioni locali 0.1.2 — verifica e misura

Implementazione `2d4e01f`. Refined Storage 1.9.17 è invariato, verificato anche tramite SHA256. [Modifiche](../../LOCAL-OPTIMIZATIONS-0.1.2.md), [metodo e limiti](METHOD.md).

## Codifica degli aggiornamenti del monitor

Confronto 8³, 215 CPU, un ordine alla volta. Entrambi i casi usano il codice 0.1.2: cambia soltanto la politica di notifica del monitor nel banco di prova. Encoder RS reale, senza invio su rete e rendering client. Tre finestre consecutive da 120 tick, una JVM per modalità.

| Carico | ms/tick immediato → accorpato | Snapshot/s immediato → accorpato | Oggetti/s immediato → accorpato |
| --- | ---: | ---: | ---: |
| Semplice | 0.812 → 0.646 | 4100 → 20 | 16391 → 16390 |
| Annidato | 1.799 → 0.689 | 9281 → 20 | 1541 → 1541 |

Entrambe le modalità mantengono circa 20 TPS. Il throughput rimane sostanzialmente uguale perché il budget di crafting non cambia. La differenza temporale tra JVM comprende rumore/JIT; il conteggio degli snapshot conferma direttamente il lavoro eliminato.

Non è una misura del 32³ o del modpack completo. Non implica che i circa 58 ms/tick del precedente 32³ senza monitor siano stati eliminati. Questa ottimizzazione riguarda il nostro Super Wireless Monitor; griglie e monitor standard RS mantengono il loro comportamento.

## Verifiche funzionali

- Due avvii server consecutivi: **128 controlli generali + 20 controlli locali per avvio**, tutti superati.
- Crafting semplice/annidato, due pagine, sicurezza, più viewer, rottura/ripresa, rete spenta/riaccesa, conflitti tra controller, inventari/NBT e recupero esatto di un task da 4.000 output.
- Cache: riuso del pattern non modificato, variazione FE senza rebuild, rilevamento delle modifiche NBT dirette, reload reale e uso della nuova istanza di ricetta installata.
- Inventario aggregato: 1.000 pagine/78.000 slot, mutazioni ed estrazioni ai confini delle pagine.
- Monitor: 100.000 notifiche accorpate, assenza di invii inutili, disconnessione, riconnessione, chiusura; codec RS originale.
- Client reale: tre schermate wireless e due pagine del crafter. Screenshot del monitor controllato visivamente.
- JAR di produzione su Forge 36.2.26 separato: un task salvato con 120 assi già prodotti termina con **4.000 assi e zero task pendenti**, verificati nei dati NBT salvati; uscita pulita.
- JAR: 60 classi Java 8, 95 risorse JSON valide, codice di test/benchmark escluso.
- Nessun 64³, nessuna modifica alla copia Prism del pack. Nessuna garanzia sul modpack completo o su integrazioni arbitrarie non esercitate.

## Artefatti

- [Confronto JSON](comparison.json), [CSV](comparison.csv).
- [Raw immediato](immediate-raw.json), [raw accorpato](coalesced-raw.json).
- [Host immediato](immediate-host.json), [host accorpato](coalesced-host.json).
- [Verifiche, hash del JAR e hash RS invariato](verification.json).
- `scripts/analyze_monitor_encoding.py` ricrea il confronto dai due casi. Log e mondi isolati restano sotto `run/` e `.local-investigation/`.
