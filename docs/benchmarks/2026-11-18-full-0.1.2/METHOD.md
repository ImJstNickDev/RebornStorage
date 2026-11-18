# Full 0.1.2 campaign including 64³

Requested again after the local 0.1.2 optimizations, now explicitly including 64³.
The previous exclusion of 64³ is superseded for this campaign; historical
reports describe their original scope and are not rewritten.

Production implementation: `2d4e01f`, release `1.16.5-0.1.2`.
Campaign source: `4de54ad` (based on `a38cb6a`, adding only configurable benchmark
phase timeouts and explicit window-completion fields). Production code and
release JAR are unchanged during the measurements. JAR SHA256:
`ef9bf7f12b3ff89fd787c2df84c2415e7da990a94311a091fc0da38905bc45f3`.

## Same baseline workload

- Solid 4³, 8³, 16³, 32³, 64³; edges frame, faces heat blocks, interior one
  pattern storage and remaining positions CPUs. One RS aggregate provider.
- CPU counts: 7, 215, 2,743, 26,999, 238,327.
- FE/t with default block costs and controller/drive: 46, 1,086, 13,726,
  135,006, 1,191,646. Controller buffer 32,000 FE for 4–16; 2,000,000 for 32/64.
  Creative controllers still need adequate capacity in RS 1.9.17.
- Fresh dedicated Forge 36.2.26 worlds, Java 8, `-Xms1G -Xmx6G`, run serially.
  Same dependencies and recipe set as the earlier scaling campaigns.
- No JFR, no open GUI or simulated monitor encoder, no concurrent orders.
  This repeats the original full scaling workload. It does not repeat the
  separate UI-encoding comparison, which is in `../2026-11-18-local-opt/`.
- Baseline without structure, placement/assembly/connection, idle, simple
  crafting and nested crafting; memory after explicit GC and peak process RSS.
- Simple: logs → planks. Nested: logs/iron → planks/chest/hopper/minecart/hopper
  minecart, five patterns and six recipe executions per final item.
- Order size: max(256, CPU × 80) planks or max(8, CPU × 5) hopper minecarts.
  One order at a time, inputs refilled and outputs drained. Disk capacity is
  262,144,000 items. All quantities remain below signed 32-bit limits at 64³.
- Each active load has three consecutive windows targeting 240 ticks. This is
  one JVM per size, not three independent JVM repetitions. The full-tick timer
  excludes two boundary samples (238 samples for a full 240-tick window).
- 4–32 keep original phase safeguards: warmup 100 ticks or 15 wall seconds;
  measurement 240 ticks or 60 wall seconds. For 64, wall limits are extended to
  120 seconds warmup and 240 seconds per measurement, so slow ticks can finish
  the target. Record actual ticks and `window_stop_reason`; a truncated window
  is not described as a complete 240-tick repetition.
- Outer runner limit: 1,200 wall seconds per server; stop if host available RAM
  falls below 1 GiB. These limits protect the experiment, not the mod's crafting
  budget. The normal desktop session remains active; host load is recorded.

Startup timings include tick scheduling and are not isolated function
microbenchmarks. Baseline/idle TPS can be distorted by catch-up after explicit
GC; conclusions about TPS use the active loads. Raw GC/core counters distinguish
thread saturation, allocation/GC and host memory. No extrapolated 64³ results.

All five processes exited with code 0 after final save and shutdown. Startup
logs include the same non-terminating RuntimeDistCleaner messages, failed RS
update-URL check and `Not a registry ops` message seen in the earlier baseline.
No new benchmark failure or runtime exception occurred during measurements.
The 64³ logs also contain `Can't keep up` warnings, consistent with measured TPS.

## Reproduction

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 4 8 16 --tag full012-small-2026-11-18
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 32 --controller-capacity 2000000 --tag full012-32-2026-11-18
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 64 --controller-capacity 2000000 --property bench.maxWindowSeconds=240 --property bench.maxWarmupSeconds=120 --tag full012-64-2026-11-18
python scripts/analyze_benchmarks.py run/benchmarks/full012-small-2026-11-18 report --replace-case run/benchmarks/full012-32-2026-11-18/size-32 --replace-case run/benchmarks/full012-64-2026-11-18/size-64 --plot
python scripts/compare_benchmarks.py docs/benchmarks/2026-11-18-aggregate report
```

Use new tags for a rerun. Plotting requires matplotlib; the existing local
plotting environment can be used. This is an isolated dependency set, not the
full Cuboid Outpost pack. Many patterns/storage pages, many players and requests,
external storage, processing machines and full chunk eviction are separate axes.
