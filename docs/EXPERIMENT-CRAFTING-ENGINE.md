# Crafting engine experiment — separate branch, not approved for merge

Branch: `experiment/crafting-engine-1.16.5`, based on stable `00928a2`.
The user requires explicit authorization before merging into `port/forge-1.16.5`.
Experimental artifact: `rebornstorage-1.16.5-0.1.3-experimental.1.jar`.

## Three comparable modes

`general.experimental_crafting` in `rebornstorage-common.toml`, with a server
restart after changing it:

- `OFF` (default): original 0.1.2 crafting execution.
- `PATTERN`: reusable per-thread recipe inventory for the exact vanilla
  `ShapedRecipe` and `ShapelessRecipe` classes. All output/remainder calls still
  execute; dynamic/subclassed/custom recipes use the original implementation.
- `TASK`: PATTERN plus a dedicated task factory for orders rooted in our patterns.
  Fixed-variant ingredient plans are compiled once. Availability checks read
  the task's internal RS item store without copying stacks; actual extraction
  still uses RS. Duplicate ingredient requirements are combined. Multi-variant
  requirements and processing nodes use the ordinary RS node path.

Tests can override the configuration with `-Dreborn.craftingExperiment=OFF`,
`PATTERN` or `TASK`. This is an opt-in experiment; the Prism pack is not changed.

No crafting budgets, update intervals, recipe definitions or item quantities
are changed. There is no batching, universal result cache, background storage
access, or suppression of per-craft task notifications in this prototype.

## Integration boundary

The RS calculator, preview, storage interfaces, processing nodes and monitor
element factory are reused. An independently registered task type adapts the
RS v6 lifecycle; its source attribution/license is in `NOTICE-RS.md` and the JAR.
No RS class is patched, no Mixin/access transformer is added and the global
`refinedstorage:v6` task factory remains registered to RS.

This is **version-specific integration with RS 1.9.17**, not an API-only claim:
two read-only reflected fields provide the selected recipe and the calculated
node plan (`CraftingPattern.recipe`, `CraftingTask.nodes`). Missing/unsupported
fields fall back to the ordinary path; benchmarks assert that the optimized
path is actually selected. The task NBT schema and several RS implementation
classes are also version-specific.

The fresh-plan conversion preserves the exact pattern/recipe references selected
by RS, even if an encoded stack has since changed. Reconstructing a fresh plan
only through its NBT was rejected after a differential integration failure.
Disk restore continues to resolve patterns using RS's persisted container
positions, as in the original engine. Remaining ingredient counts are written
in the ordinary v6 node schema, including partial-progress counts.

The factory is selected from the root requested pattern. Orders rooted in an
ordinary RS/addon crafter can still use Reborn subrecipes through RS's engine;
they do not automatically gain the dedicated executor. Old RS tasks remain
readable. Switching the experiment off allows its registered factory to read
existing experimental task NBT through the original RS task implementation.
Removing this experimental JAR entirely while its task type is saved is a
different operation; the stable branch does not register that new type.

## Validation approach

Run the existing integration suite twice per mode, including a real restart
with an unfinished order, plus differential node tests against the actual RS
implementation. Cover exact ingredients/NBT, mixed variants, duplicate
ingredients, cake/bucket remainders, dynamic recipe calls, missing ingredients,
partial progress and node NBT interoperability, full/partial output insertion,
unchanged CPU budgets and unchanged per-craft callbacks.

Fresh test worlds now explicitly force the two fixture chunks to tick. Without
that fixture setup, a world whose spawn is away from the origin can leave the
controller stopped; this was caught by the wireless test before any crafting.

Performance runs compare the three modes in separate JVMs with equal sizes,
inputs, CPU budgets and controller capacities. The
[completed campaign](benchmarks/2026-11-18-experiment/README.md) contains
42 complete crafting windows and 10,080 active ticks. At 32³, PATTERN reduces
simple/nested tick time by 6.1%/8.7%; PATTERN plus TASK reduces it by
46.4%/44.7%, restoring 20 TPS for the nested load. The 64³ nested case improves
from 520.70 to 265.12 ms/tick against the preceding stable campaign, but still
runs at only 3.76 TPS. Measured output counts per fixed tick window are unchanged.

Both modes pass the two-start server suite and differential checks. Client
screen tests pass, and the reobfuscated production JAR resumes and completes
the saved 4,000-plank order on dedicated Forge. The report records the scope
and remaining limitations; this does not establish full-modpack, mixed
processing-machine or long-running multiplayer compatibility. No merge has
been performed or authorized.
