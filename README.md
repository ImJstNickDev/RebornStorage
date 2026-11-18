# Reborn Storage — Forge 1.16.5 backport

Unofficial, focused backport of [TechReborn/RebornStorage](https://github.com/TechReborn/RebornStorage), based on its `1.18.2` branch. Targeted at Cuboid Outpost LE 1.1.2. Upstream authorship and MIT license are preserved.

## Included

- Multiblock crafter: frame, heat conductor, crafting CPU and pattern storage; 78 patterns per storage block, with independently paged player menus.
- Super Wireless Grid: crafting/items, fluids and crafting monitor in one item. Normal rechargeable and creative variants.
- Four **new item disk tiers**: 32768k, 65536k, 131072k and 262144k. `k` means 1,000 items.
- The components, processors, recipes, disk recovery factory and empty-disk disassembly needed by those features.

Existing lower item disks, fluid disks and the Advanced Wireless Transmitter are excluded. ExtraStorage supplies the lower tiers; RSInfinityBooster supplies unlimited/interdimensional wireless access. The Super Wireless Grid overlaps the ordinary wireless crafting grid's crafting function, but additionally combines fluid access and the crafting monitor.

## Requirements and installation

Tested with Minecraft **1.16.5**, Forge **36.2.26**, Refined Storage **1.9.17**, ExtraStorage **1.6.1**, RSInfinityBooster **1.16.5-1.1+13** and Curios **4.0.8.2**, on Java 8.

Refined Storage and ExtraStorage are required. RSInfinityBooster and Curios are optional integrations. Install the built Reborn Storage JAR on **both server and clients**, alongside the required mods. This is a content mod, not a server-only recipe script. Version 0.1.1 remains an initial backport; the complete Cuboid Outpost pack and long-running production worlds have not been tested.

## Disk progression

| New disk | Item capacity | Previous component required |
| --- | ---: | --- |
| 32768k | 32,768,000 | 3 × ExtraStorage 16384k |
| 65536k | 65,536,000 | 3 × Reborn 32768k |
| 131072k | 131,072,000 | 3 × Reborn 65536k |
| 262144k | 262,144,000 | 3 × Reborn 131072k |

Each component also costs four RS advanced processors, one quartz-enriched iron and one redstone, following ExtraStorage's component recipe layout. The first tier explicitly requires `extrastorage:storagepart_16384k`; subsequent tiers require the preceding Reborn component. A component plus an RS storage housing makes the disk. Filled disks are never recipe ingredients. Shift-right-click an empty initialized disk to recover its component and housing.

Storage uses RS's normal item disk implementation, with a dedicated factory ID so RS disk recovery preserves the correct tier. Capacities fit a signed 32-bit integer; this port does not change RS's aggregate numeric limits.

## Crafter and wireless use

Build a solid rectangular structure: frame blocks on edges/corners, heat conductors on face interiors, and CPU/storage blocks filling the interior. Default external bounds are 3×3×3 through 16×16×16 and can be configured in `rebornstorage-common.toml`. Connect a powered RS network to the structure. Pattern pages are accessible by right-clicking it; Shift-click a page arrow to move ten pages.

Each assembled multiblock exposes one RS provider through its outer surfaces. Only storage blocks retain inventory records; existing pattern inventories and page order remain readable. Pattern edits and topology changes trigger coalesced updates at the end of the server tick.

The multiblock handles crafting recipes. Use ordinary RS/ExtraStorage crafters facing machines for processing patterns.

This backport follows the newer Reborn CPU policy: update interval `max(5 − CPU count, 1)` ticks; successful crafting-update budget `max(CPU count, 1)` per update. These are scheduler budgets, **not measured items per second**. Output quantity, dependencies, concurrent tasks, RS scheduling and server TPS affect actual throughput.

Bind the Super Wireless Grid to a network by right-clicking its controller. Use Shift-right-click or Ctrl+H to cycle modes; Ctrl+G opens it. Bindings can be changed in Controls. RSInfinityBooster cards go in the normal RS wireless transmitter. The normal grid stores 100,000 FE and follows the corresponding RS mode's energy settings and permissions. Curios adds an optional grid slot without changing other accessory slot counts.

## Build

Install JDK 17 for Gradle and JDK 8 for Minecraft development runs. Set `JAVA_HOME` to JDK 17, then:

```sh
./gradlew build
```

Dependencies are version-pinned in `build.gradle` and downloaded from Forge Maven, Curse Maven and Curios Maven. No manual `libs/` copies are required. Output: `build/libs/rebornstorage-1.16.5-0.1.1.jar`, Java 8 bytecode. The test source sets are not packaged.

## Verification

```sh
# Requires acceptance of the Minecraft EULA for this disposable test server.
python3 scripts/test_port.py --accept-eula
# Client test, after the server tests; requires an available display.
python3 scripts/test_port.py --client
# Headless Linux alternative:
xvfb-run -a python3 scripts/test_port.py --client
```

The server suite runs twice, including a real save/restart. The client suite uses a disposable copy of that world, exercises the actual networked menus and captures screenshots. All test data stays in the ignored `run/` directory; the tests do not touch Prism instances. See [port and verification notes](docs/PORT-1.16.5.md).

## Performance measurements

The [0.1.0 baseline](docs/benchmarks/2026-11-18/README.md) and [0.1.1 comparison](docs/benchmarks/2026-11-18-aggregate/README.md) measure solid 4³, 8³, 16³ and 32³ structures at idle and during continuous simple and nested crafting. They include raw results, tick timings, memory, startup costs and the [diagnosis and proposed remedies](docs/benchmarks/2026-11-18/ANALYSIS.md). The 64³ run was cancelled before launch. These are isolated dedicated-server tests, not a full Cuboid Outpost world.

```sh
python3 scripts/benchmark_port.py --sizes 4 8 16 --tag local-benchmark
python3 scripts/benchmark_port.py --sizes 32 --controller-capacity 2000000 --tag local-benchmark-32
```

RS 1.9.17 checks the controller's energy buffer even for a creative controller. Large structures need a sufficiently large buffer as well as adequate power. Version 0.1.1 coalesces pattern updates and exposes one aggregate RS node per multiblock. The comparison uses matching workloads and controller capacities for both versions; the full modpack has not been benchmarked. Benchmark sources are excluded from the release JAR.
