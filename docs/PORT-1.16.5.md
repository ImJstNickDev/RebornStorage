# Port and verification notes

## Baseline and scope

Version `1.16.5-0.1.4` targets Forge 36.2.26 and Refined Storage 1.9.17. The upstream content baseline is `TechReborn/RebornStorage` 1.18.2 at `fcbd0496e360ba370d1115910f6e6dd61d19ab52`. This unofficial port includes the original four item tiers, four fluid tiers, components, processors, transmitter, multiblock, wireless grids and optional Patchouli guide. It also retains the four extended item tiers and the opt-in crafting engine.

The [installation and recipe guide](../README.md) lists all capacities, configurations and optional integrations.

## Implementation decisions

- Java 8 bytecode; JDK 17 runs Gradle 7.4 / ForgeGradle 5.1.77. Development game
  processes use JDK 8. Forge, RS and Curios APIs use the 1.16.5 names/signatures.
- Server registration is separated from client screens/keybindings.
- The RS node factory reads the full node state. Node inventories suppress dirty
  notifications while deserializing, avoiding recursive SavedData loading.
  Storage ownership/configuration and provider UUIDs are saved through the storage node.
  Empty structural records from 0.1.0 are removed as their tiles load.
- A transient aggregate provider represents the entire assembled multiblock in the RS graph.
  Surface ports preserve connections from any side. Persistent storage nodes perform no polling and are never graph entries.
  They retain an inert crafting-container adapter because RS 1.9.17 deserializes
  in-flight tasks by looking up the saved node at the pattern container position. A cached composite inventory exposes
  their pages to RS; the player GUI still binds only its selected 78-slot page.
- Pattern inventories remain in RS's node saved data; each storage tile persists
  its page ordering. Page ownership is per container/viewer. GUI packets require
  an existing matching menu, interaction distance and RS MODIFY permission.
- Pattern/provider/graph changes are deduplicated by identity and processed at server tick END.
  Pending pattern changes suspend crafting until their new cache is ready. Structural changes
  immediately disable stale containers, and queued graph work avoids recursive rebuilds.
  CPU counts, costs and topology are cached until structure/configuration changes. Static
  multiblocks no longer mark chunks dirty every tick.
- Disassembly/chunk pause invalidate the crafting pattern cache. Block breaking
  drops stored patterns on the server and removes the RS node. Four block loot
  tables return the structural blocks themselves.
- CPU policy deliberately follows newer Reborn versions: interval
  `max(5-cpus,1)`, successful-update budget `max(cpus,1)`. The old 1.18.2
  `cpus/5` policy was not retained. RS applies this budget per recipe node/container,
  not as a shared time budget; aggregation avoids multiplying it for duplicate storage providers.
- The advanced transmitter extends the native RS transmitter node. This lets both RSInfinityBooster cards use the same inventory, power accounting and network hooks as the ordinary transmitter. The advanced node visits the attached face in all six orientations.
- Reborn disks store their UUID in the normal RS disk item tag. The item and fluid factories restore the original tier, owner and tagged contents. Disk type disambiguates equal item and fluid capacities. Disassembly requires an empty disk and returns the matching component.
- Every extended item tier follows the Reborn component chain. The original crafting patterns use an inverted vertical layout to coexist with ExtraStorage. Shared RS tags remain available to other mods.
- The upstream standalone book item was commented out in the 1.18.2 registry. The optional guide uses Patchouli's native book item and a conditional recipe.

## Verification of 0.1.4 on Forge 36.2.26

- Two dedicated server starts with the TASK engine and ExtraStorage absent passed 189 and 195 integration assertions. Each start also checked the twelve disk and component recipes, RS tags, fluid capacity limits, NBT and disk recovery. A drive stored 1,000,000,000 mB and retained it across the real restart.
- Two dedicated server starts with the default engine and ExtraStorage 1.6.1 passed 179 and 185 integration assertions. Each Reborn component and each advertised assembly layout had exactly one crafting-table result.
- The client opened all three wireless modes, both pattern pages and the advanced transmitter menu. Its displayed range changed from 1,500 to 1,000 blocks after upgrade removal. The active monitor displayed 35 distinct counts with a maximum observed gap of one client tick.
- A production Forge server with only the release JAR and RS 1.9.17 upgraded a copy of an experimental.4 world. A pending 4,000-stick order resumed from 96 sticks and finished with exactly 4,000. The 32768k disk kept its capacity and UUID.
- A full Cuboid Outpost LE 1.1.2 client joined a separate dedicated server using the release JAR. A grid equipped in Curios opened all three modes. During a nested 32,000-stick order the structure's chunk was absent for 45 consecutive server ticks. Reload restored the disk and order UUIDs. The client showed 32,000 sticks and the server had zero logs, planks or pending orders.
- On a fresh Cuboid dedicated server, after KubeJS finished loading, the crafting-table check found a single matching recipe for each of the twelve components and twelve disks. It also verified all RS tier tags, the transmitter recipe and the Patchouli guide result with its book ID.
- The tested release JAR has SHA-256 `2bcd51569621ee1a18f7fab1373b0221e262c4c838affb426e3aee64efefe296` and class-file version 52 (Java 8).

## Verification of the original 0.1.2 baseline

Verified on 2026-11-18: build/reobfuscation succeeds; the server suite passes
**128 checks plus 20 local-optimization checks on each of two consecutive starts**, including an in-flight
crafting task saved and resumed across the real restart; the client suite passes all
three wireless screens and both crafter pages. Screenshots were visually
inspected. Release classes are Java 8 (major 52), all JSON resources parse, and
neither test source set is present in the release JAR. The reobfuscated release
JAR also starts successfully on a separately installed Forge 36.2.26 dedicated
server with the production dependency JARs, followed by a clean shutdown. Its
saved NBT confirms that the pending 4,000-plank order completes exactly, with no
remaining task (120 planks were already present before startup).

Server suite (`src/integrationTest`, excluded from the release JAR):

- Real dedicated server startup with the target dependencies on Java 8.
- Four capacities, full insertion/extraction, excess rejection, NBT round trips,
  UUID/tier recovery, preservation of custom names, empty-only disassembly,
  and loading of all eight new disk/component recipes.
- 4×4×4 assembly with six CPUs and two storage blocks; exact scheduler budget,
  RS connection, break/reassembly and pattern preservation.
- Full node NBT round trip, including owner; pattern and disk UUID persistence
  through a real server shutdown/restart.
- One aggregate graph provider, two persistent storage inventories, opposite-surface
  traversal, automatic pattern edits, nested crafting across pages, no output while
  disassembled, and task resumption after reconstruction and network shutdown.
- Rejection of a conflicting second network, inventory preservation, configuration
  cost refresh, provider replacement at the same graph position, legacy task deserialization from a non-anchor storage position,
  and exactly 4,000 outputs from a task interrupted by an actual server restart.
- Immediate invalidation through the chunk-unload callback and part reattachment;
  this callback test does not simulate a complete chunk being evicted from memory.
- Independent concurrent viewer pages, distance validation and denied RS MODIFY
  permission; denial also checked for all three wireless modes.
- Infinity Card at coordinates 100000/100000 in the Overworld and Dimension Card
  with a distinct test player in the Nether: all three modes open, consume energy
  and refuse an empty battery. The tests assert the player's actual dimension.
- An actual RS crafting task makes 400 oak planks from 100 oak logs using a new
  disk in the disk drive. A Disk Manipulator transfers contents from a 262144k
  disk and returns the original disk tier.

Client suite (`src/clientTest`, excluded from the release JAR):

- Loads a copy of the saved integration world in a real Minecraft client.
- Opens crafting, fluid and monitor screens through the network item flow.
- Opens the multiblock menu and changes to page two through the custom packet.
- Saves screenshots under `run/client/screenshots/` for visual inspection.

`scripts/test_port.py` exports the ForgeGradle launch then starts the game as an
isolated process, checks its exit status and fresh PASS result, and rejects node
loading failures. This also avoids the Gradle-daemon shutdown issue observed when
running Minecraft directly as a child of Gradle in this environment. Server tests
run twice. Logs are in `run/integration/test-1.log` and `test-2.log`; client logs
and screenshots stay in `run/client/`.

## Scope of the 0.1.2 tests

The targeted dependency set is tested; the full Cuboid Outpost pack is not.
Client GUI testing uses an integrated server, while the separate dedicated-server
suite uses simulated players. A full multiplayer session, forced chunk
unload/reload and long-running production workloads remain manual validation.
Dedicated-server throughput tests, including nested crafting and solid structures
from 4³ through 64³, are documented in the [complete 0.1.2 campaign](benchmarks/2026-11-18-full-0.1.2/README.md).
The [0.1.0 baseline](benchmarks/2026-11-18/README.md) and
[0.1.1 comparison](benchmarks/2026-11-18-aggregate/README.md) preserve the earlier
measurements through 32³. The latest campaign repeats those workloads and
controller capacities and adds the explicitly requested 64³ case.
Curios equipment/keybinding interaction was not exercised by
the automated screen suite. There is no supported save migration from other
Minecraft versions or upstream Reborn disk IDs. RS's numeric and performance
limits remain those of RS 1.9.17.

See [0.1.2 local optimizations](LOCAL-OPTIMIZATIONS-0.1.2.md) for monitor snapshot coalescing, direct page indexing, incremental pattern definitions and server recipe-reload invalidation. RS output/byproduct evaluation and CPU budgets remain unchanged.
