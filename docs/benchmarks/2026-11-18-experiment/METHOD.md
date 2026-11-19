# Experimental crafting comparison

Stable base: `00928a2` (0.1.2). Experimental implementation: `2d1e35f`.
Branch `experiment/crafting-engine-1.16.5`; no merge authorized or performed.
Production JAR: `rebornstorage-1.16.5-0.1.3-experimental.1.jar`, SHA256
`5df91f056af5ff9a8dc54b9766631eb8b1b2f4e70d7781ee87e17aa5a10a4bfc`.

## A/B/C workload

Three modes in separate Java 8 processes: OFF, PATTERN and TASK. Same RS 1.9.17,
Forge 36.2.26, ExtraStorage 1.6.1, RSInfinityBooster 1.1+13 and Curios 4.0.8.2.
No modified dependency JARs. Exact source and mode are recorded in each host/raw
JSON. All measured game processes run serially, with heap 1–6 GiB and no profiler.

The existing benchmark builds a physically solid crafter with one pattern
storage and the remaining interior filled with CPUs. The original update
interval and successful-craft budget remain unchanged. Simple load: logs to
planks. Nested load: hopper minecarts from logs and iron, five patterns and six
recipe executions per final item. Inputs are replenished and final output is
drained; one order at a time, no GUI or encoded monitor traffic.

Each active load has three consecutive 240-tick measurement windows after
warmup. Each window contains 238 full-tick timer samples after excluding its
boundaries. Report TPS/rates from total ticks/output divided by elapsed real
time, and MSPT/P95 from pooled full-tick samples. The repetitions are windows
within one JVM per case, not three independent JVM experiments.

At 32³ and 64³ the isolated controller buffer is 2,000,000 FE. Smaller cases
use its default capacity. No energy shortage is allowed. The runner stops
for host available RAM below 1 GiB or a 1,200-second case timeout. At 64³ the
phase wall limits are extended to 120-second warmup / 240-second measurement
to allow full tick windows at low TPS, as in the stable 64³ campaign.

TASK runs assert that the dedicated task is actually created and that every
benchmark crafting node takes the specialized ingredient path: one for the
simple load, five for the nested load. Passing status alone is insufficient;
the report additionally checks complete windows, positive output and exit code.

## Validation and limits

Both experimental modes pass the existing server suite on two starts, including
an actual save/restart with an unfinished order. Each start adds 114 differential
checks against the original RS node: remaining NBT, output and ingredient counts,
callbacks and CPU quotas, exact tagged inputs, mixed-variant fallback, cake bucket
remainders, dynamic recipe evaluation and missing-input stalls. Partial/full
output acceptance is tested with a proxy at the network insertion boundary.
This is not a full external-storage mod integration test.

Client tests open all three wireless screens and both multiblock pages in a
copy of the task-mode world. The screenshots show an idle monitor after the
restored order completed; they are not evidence of a player watching a large
active order. The production JAR is checked for Java 8 classes, valid JSON,
excluded test classes and included RS attribution. Full-modpack, multiplayer,
mixed processing-machine workloads and extended-duration runs remain outside
this experiment's validation.

The reobfuscated JAR also runs on a standalone Forge 36.2.26 server under Java 8,
using an isolated copy of the saved TASK test world. Before startup its actual
storage disk contains 120 oak planks and one pending `rebornstorage:experimental_v6`
task; after completion and a clean stop it contains exactly 4,000 oak planks and
zero pending tasks. This verifies production-JAR task restoration and completion,
not just the development classpath. Evidence is recorded in `validation.json`.

An initial OFF-32 run is excluded from the controlled comparison: its outer
driver/runner terminated with status 143 while the detached game continued to
completion. Its files remain in `run/benchmarks/experiment-off-32-2026-11-18`,
with an explicit `orchestration.json` explanation. The accepted reference is
the direct-runner repetition `experiment-off-32-r2-2026-11-18`.

## Commands

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/test_port.py --accept-eula --tag experimental-task-final --property reborn.craftingExperiment=TASK
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/test_port.py --accept-eula --tag experimental-pattern-final --property reborn.craftingExperiment=PATTERN
JAVA_HOME=/usr/lib/jvm/java-17-openjdk xvfb-run -a python scripts/test_port.py --client --tag experimental-task-final --property reborn.craftingExperiment=TASK
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 32 --controller-capacity 2000000 --property reborn.craftingExperiment=OFF --tag experiment-off-32-r2-2026-11-18
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 32 --controller-capacity 2000000 --property reborn.craftingExperiment=PATTERN --tag experiment-pattern-32-2026-11-18
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 32 --controller-capacity 2000000 --property reborn.craftingExperiment=TASK --tag experiment-task-32-2026-11-18
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 64 --controller-capacity 2000000 --property reborn.craftingExperiment=TASK --property bench.maxWindowSeconds=240 --property bench.maxWarmupSeconds=120 --tag experiment-task-64-2026-11-18
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 4 8 16 --property reborn.craftingExperiment=TASK --tag experiment-task-small-2026-11-18
```

Use new tags when rerunning. Configuration/modes must be compared within the
same campaign; small differences can reflect host activity, JIT and temperature.
