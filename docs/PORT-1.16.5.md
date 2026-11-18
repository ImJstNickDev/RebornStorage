# Reborn Storage: Forge 1.16.5 port preparation

Status: **project preparation only; implementation paused at the user's request**.
The user will change model before implementation resumes. No Java sources or
runtime resources have been ported, no working artifact has been produced, and
nothing has been installed in the user's modpack. An exploratory compile was
interrupted; the provisional build configuration is local and unvalidated.

## Baseline and target

- Upstream: https://github.com/TechReborn/RebornStorage
- Baseline branch: `1.18.2`
- Baseline commit: `fcbd0496e360ba370d1115910f6e6dd61d19ab52`
- Work branch: `port/forge-1.16.5`
- Target: Minecraft 1.16.5, Forge 36.2.26, Refined Storage 1.9.17.
- Existing addons: ExtraStorage 1.6.1, Refined Storage Addons 0.7.4,
  RSInfinityBooster 1.1+13.
- Preserve upstream license and attribution. This is an unofficial backport.

## Accepted scope

1. Multiblock crafter, including all four structural blocks, pattern storage,
   GUI, persistence, networking and RS integration.
2. Super Wireless Crafting Grid with crafting/items, fluids and crafting-monitor
   modes. Verify all modes with RSInfinityBooster's same-dimension Infinity Card
   and interdimensional Dimension Card; do not claim compatibility from class
   inheritance alone.
3. Four NEW item-disk tiers: **32768k, 65536k, 131072k, 262144k** (decimal k = 1000).
   Reuse RS's normal storage APIs; no changes to RS numeric types are planned.
4. Supporting components actually needed by the retained features.

Exclude duplicate Reborn item/fluid disk tiers, their unused components and the
Advanced Wireless Transmitter (range is already covered by RSInfinityBooster).
The old guide book is not registered upstream. Decide whether the Creative
variant of the retained grid is useful for testing; do not add unrelated content.

## Recipe requirement from the user

The new disks must not be easier to obtain than ExtraStorage's 16384k disk.
Preserve the existing progression: the first new component must depend on the
existing 16384k component, and later components on the previous tier. Inspect
both the installed JAR recipes AND the pack's KubeJS changes before settling
quantities and additional ingredients. Do not craft using filled disks in a way
that loses their contents; prefer storage components and empty housings.

Observed ExtraStorage recipe for `storagepart_16384k`: pattern `DID/GRG/DGD`,
where G is `#refinedstorage:parts/items/4096k` (three components), D is an advanced
processor (four), I is quartz-enriched iron and R is redstone. This observation
is not yet a complete audit of KubeJS overrides or a final new recipe design.

The pack also has an endgame 9x9 Extended Crafting recipe for RS's Creative
Storage Disk; the new tiers should fit between ordinary disks and that goal.

## Findings to retain, not runtime verification

- RS 1.9.17 already exposes `getUpdateInterval()` and
  `getMaximumSuccessfulCraftingUpdates()`, along with pattern/node APIs.
- `createDefaultItemDisk` and disk counters use `int`. The selected four tiers
  are representable; numeric-boundary and aggregate behavior still merit tests.
- Upstream 1.18.2 has 50 Java files / about 6674 physical lines; the multiblock
  framework is about 2830 lines. Estimates are not implementation progress.
- 1.18.2 crafting speed divides CPU count by 5; 1.20.1/1.20.4 return CPU count.
  Explicitly choose and document the backport speed policy; do not silently
  inherit the slower formula or promise benchmark results from API budgets.
- Upstream branches named 1.16.1 and 1.14.4 still target 1.12.2 internally and
  are not useful completed ports.
- Porting hotspots: block entity lifecycle, NBT, chunk/world events, GUI/menu
  providers, Forge network packages, RS implementation-class names, Java syntax.
- The inherited build references an absent access transformer and obsolete
  dependency infrastructure; replace it deliberately when implementation starts.

## Verification needed during implementation

- Compile/reobfuscate against the target RS and Forge versions.
- Dedicated-server startup and client login; verify client-only isolation.
- Assemble/break/reassemble crafter; save/restart and chunk unload/reload.
- Pattern inventory, GUI pages, permissions, concurrent players, crafting tasks.
- All three grid modes with both RSInfinity cards, power use and security.
- Disk insertion/extraction, empty-disk disassembly, Disk Manipulator transfers,
  persistence and capacity boundaries; validate all recipe IDs and ingredients.
- Real throughput measurements at stable TPS, separate from theoretical limits.

## Local workspace

Checkout: `/home/nico/code/personal/reborn-storage-1.16.5`.
The ignored `libs/` directory holds local copies of RS 1.9.17 and
RSInfinityBooster 1.1+13 for development, not redistribution.
Ignored `.local-investigation/` holds the interrupted Gradle log, Mojang mappings
and a first build draft. None are part of the public source deliverable.

The actual modpack has not been changed. Do not install or start the real pack
as a side effect of preparing or building the project.
