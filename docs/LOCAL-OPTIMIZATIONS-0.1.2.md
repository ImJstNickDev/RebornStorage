# Local optimizations in 0.1.2

Refined Storage remains the original **1.9.17** dependency. No mixins, patches,
replacement RS JAR, changed crafting budget, or cached crafting output.

## Super Wireless Crafting Monitor

The server uses a subclass of RS's monitor container with the same container
registration, client screen, settings, cancellation, inventory synchronization,
permissions, wireless integration and packet format. Per-craft `onChanged`
notifications set a dirty flag; one current snapshot is encoded at server tick
end. Initial attachment, manager changes and reconnection request fresh state.
Closing removes the listener and discards pending updates. Intermediate
snapshots within the same tick are omitted; final state remains observable.

This affects our Super Wireless Monitor only. Regular RS/addon monitors and
item/fluid grids retain their existing notification behavior.

## Pattern pages and rebuilds

- The combined inventory maps fixed 78-slot pages in constant time. All inventory
  operations retain the existing Forge wrapper behavior.
- Pattern reconstruction iterates pages directly. Parsed pattern definitions are
  retained by slot; modified slots are reconstructed and invalid patterns removed.
- On a queued refresh, a linear comparison with copied stacks also detects
  in-place NBT edits, including edits in another slot than the one notifying us.
  There is no idle polling. Third-party pattern providers are refreshed on each
  rebuild because their definitions may depend on external state.
- Topology/state changes and explicit rebuilds refresh the whole cache. A
  server-wide `OnDatapackSyncEvent` invalidates cached definitions **after** the
  new recipe/tag resources are installed; individual player login does not.
- Changing FE costs updates only energy, without rebuilding the inventory or
  patterns. CPU budget, recipe output, byproducts, storage format and ownership
  remain unchanged. RS still evaluates output and remaining items for each craft.

The incremental cache avoids repeated parsing of unchanged pattern definitions;
RS's global pattern-manager invalidation still runs once when required. This is
not an incremental rewrite of RS's internal index or execution engine.

## Validation

The server suite covers nested crafting, removal/replacement of patterns, two
pages, multiple viewers/security, network stop/restart, structural break/repair,
conflicting networks, NBT and exact recovery of an interrupted 4,000-item task.
Added checks cover unchanged pattern identity after editing another page and FE
configuration changes, in-place NBT changes, a real resource reload and the
identity of the installed recipe object, 1,000-page inventory boundary operations,
and monitor coalescing/offline/reconnect/close with real RS packet encoding.

The client suite exercises all three wireless screens and both crafter pages.
Production JAR startup and the monitor-encoding comparison are documented in
the [validation report](benchmarks/2026-11-18-local-opt/README.md). The full Cuboid Outpost pack, arbitrary third-party
providers and a long-running multiplayer world remain outside this test suite.
