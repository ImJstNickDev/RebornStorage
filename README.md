# Reborn Storage — Forge 1.16.5 backport

Unofficial backport of Reborn Storage for Minecraft 1.16.5 and Forge 36.2.26. Version `1.16.5-0.1.4` includes the upstream 1.18.2 gameplay content and four additional item disk tiers. [Original project](https://github.com/TechReborn/RebornStorage)

## Requirements and installation

Install Refined Storage 1.9.17 and the Reborn Storage JAR on both the server and clients. ExtraStorage, RSInfinityBooster, Curios and Patchouli are optional. The multiblock uses the standard RS crafting engine by default. Set `experimental_crafting = "TASK"` under `[general]` in `config/rebornstorage-common.toml` and restart to use the optimized engine. [Crafting engine and compatibility](docs/EXPERIMENT-CRAFTING-ENGINE.md)

## Included content

- A multiblock crafter with frames, heat conductors, crafting CPUs and pattern storage. Each storage block holds 78 patterns. Players can browse pages independently.
- The rechargeable and creative Super Wireless Grids with item crafting, fluid access and a crafting monitor.
- An Advanced Wireless Transmitter with a default range of 1,000 blocks. Four Range Upgrades add 500 blocks each, up to 3,000. Base power draw is 100 FE/t plus installed upgrade costs. The range and base cost are configurable.
- Eight item disks, four fluid disks, their twelve components, and the raw and smelted Super Advanced Processor.
- With Patchouli installed, a book and Refined Storage silicon craft the Reborn Storage guide.

## Disk progression

| Chain | Starting RS component | Reborn tiers in crafting order |
| --- | --- | --- |
| Items | 64k storage part | 256k → 1024k → 4096k → 16384k → 32768k → 65536k → 131072k → 262144k |
| Fluids | 4096k fluid storage part | 16384k → 65536k → 262144k → 1048576k |

Each component takes three components of the preceding tier, four RS advanced processors, one quartz-enriched iron and either redstone (items) or an empty bucket (fluids). In particular, `rebornstorage:parts/storagepart_32768k` uses three `rebornstorage:larger_item_disk_part`. One k holds 1,000 items or 1,000 mB. The largest fluid disk holds 1,048,576 buckets.

The eight original disks can be assembled from glass, redstone, quartz-enriched iron and the matching Reborn component. When ExtraStorage is present, use the shaped Reborn recipe with the iron across the top. ExtraStorage owns a shapeless housing recipe that accepts components from either mod. With ExtraStorage absent, a housing and the component also assemble the original Reborn disk. The four extended item tiers always use a housing and their matching component.

Shift-right-click an empty initialized disk to recover its component and housing. Crafting the next tier uses loose components, so disks containing items or fluids stay intact. The extended disks keep their previous registry IDs, UUIDs and storage format.

## Crafter and wireless use

Build a solid rectangular structure: frame blocks on edges/corners, heat conductors on face interiors, and CPU/storage blocks filling the interior. Default external bounds are 3×3×3 through 16×16×16 and can be configured in `rebornstorage-common.toml`. Connect a powered RS network to the structure. Pattern pages are accessible by right-clicking it; Shift-click a page arrow to move ten pages.

Each assembled multiblock exposes one RS provider through its outer surfaces. Only storage blocks retain inventory records; existing pattern inventories and page order remain readable. Pattern edits and topology changes trigger coalesced updates at the end of the server tick.

The multiblock handles crafting recipes. Use ordinary RS/ExtraStorage crafters facing machines for processing patterns.

This backport follows the newer Reborn CPU policy: update interval `max(5 − CPU count, 1)` ticks; successful crafting-update budget `max(CPU count, 1)` per update. These are scheduler budgets, **not measured items per second**. Output quantity, dependencies, concurrent tasks, RS scheduling and server TPS affect actual throughput.

Bind the Super Wireless Grid to a network by right-clicking its controller. Use Shift-right-click or Ctrl+H to cycle modes; Ctrl+G opens it. Bindings can be changed in Controls. RSInfinityBooster cards work in both the standard and advanced wireless transmitters. The normal grid stores 100,000 FE and follows the corresponding RS mode's energy settings and permissions. Curios adds an optional grid slot without changing other accessory slot counts.

## Build

Install JDK 17 for Gradle and JDK 8 for Minecraft development runs. Set `JAVA_HOME` to JDK 17, then:

```sh
./gradlew build
```

The build produces `build/libs/rebornstorage-1.16.5-0.1.4.jar` with Java 8 bytecode. Gradle resolves the pinned dependencies. The benchmark and integration test classes are excluded from the JAR.

## Verification

```sh
# Requires acceptance of the Minecraft EULA for this disposable test server.
python3 scripts/test_port.py --accept-eula
# Second installation with ExtraStorage, using a separate world.
python3 scripts/test_port.py --accept-eula --with-extra-storage --tag extra
# Client test, after the server tests; requires an available display.
python3 scripts/test_port.py --client
# Headless Linux alternative:
xvfb-run -a python3 scripts/test_port.py --client
```

The server suite runs twice, including a real save/restart. The client suite uses a disposable copy of that world, exercises the actual networked menus and captures screenshots. All test data stays in the ignored `run/` directory; the tests do not touch Prism instances. See [port and verification notes](docs/PORT-1.16.5.md).

## Performance measurements

The guarded crafting engine from experimental.4 is included in 0.1.4. Its measurements cover Cuboid Outpost LE, a reduced Forge installation and a network using only ExtraStorage disks. [Full-pack and 64³ results](docs/benchmarks/2026-11-18-release4/README.md)

The [complete 0.1.2 campaign](docs/benchmarks/2026-11-18-full-0.1.2/README.md) measures solid **4³, 8³, 16³, 32³ and 64³** structures at idle and during continuous simple and nested crafting. It includes raw results, tick timings, memory, startup costs and a comparison with 0.1.1. These are isolated dedicated-server tests, not a full Cuboid Outpost world.

The [0.1.0 baseline](docs/benchmarks/2026-11-18/README.md), [0.1.1 comparison](docs/benchmarks/2026-11-18-aggregate/README.md) and [original diagnosis](docs/benchmarks/2026-11-18/ANALYSIS.md) retain their historical scope through 32³. The previously excluded 64³ was explicitly requested and measured in the 0.1.2 campaign.

```sh
python3 scripts/benchmark_port.py --sizes 4 8 16 --tag local-benchmark
python3 scripts/benchmark_port.py --sizes 32 --controller-capacity 2000000 --tag local-benchmark-32
python3 scripts/benchmark_port.py --sizes 64 --controller-capacity 2000000 --property bench.maxWindowSeconds=240 --property bench.maxWarmupSeconds=120 --tag local-benchmark-64
```

RS 1.9.17 checks the controller's energy buffer even for a creative controller. Large structures need a sufficiently large buffer as well as adequate power. Version 0.1.1 coalesces pattern updates and exposes one aggregate RS node per multiblock. The comparison uses matching workloads and controller capacities for both versions; that comparison used the reduced Forge installation. Benchmark sources are excluded from the release JAR.

The [two ms/tick investigations](docs/investigations/2026-11-18-mspt/README.md) add a JFR profile, listener-count probes and a one-versus-four-order comparison. They identify remaining optimization work; production code and the release JAR are unchanged by those investigations.

[Local optimizations in 0.1.2](docs/LOCAL-OPTIMIZATIONS-0.1.2.md) coalesce Super Wireless Monitor updates, index pages directly and reuse unchanged pattern definitions, with full refresh after server recipe reloads. Refined Storage itself is unchanged.
