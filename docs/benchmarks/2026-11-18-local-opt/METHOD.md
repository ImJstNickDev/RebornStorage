# Monitor encoding comparison

Production code: `2d4e01f`, Reborn Storage 0.1.2. Refined Storage is unchanged.
Two fresh, serial dedicated Forge servers use the same solid 8³ structure,
215 CPUs, five vanilla patterns, a single order at a time and 32,000 FE buffer.
Java 8, 1–6 GiB heap, three consecutive windows of 120 ticks per workload.
No profiler. This is one JVM per mode, not three independent JVM replicas.

Both cases execute the actual RS `CraftingMonitorUpdateMessage.encode` into a
fresh Netty buffer and release it. Immediate mode encodes on every RS callback,
as the old container did. Coalesced mode uses the new production container and
encodes once at tick end when dirty. One simulated viewer; no sockets, network
compression, client decoding/rendering or real player's inventory tick. Full
monitor lifecycle and the actual client screen are tested separately.

This isolates the redundant encoding work, not all multiplayer overhead.
Both modes use production 0.1.2, so this is an A/B of monitor notification policy,
not a measurement of the combined effect of all changes since 0.1.1.
The outputs and CPU budgets are identical. Nested = hopper minecart from logs
and iron, six recipe executions per final item. Output is extracted by the
harness; planning/submission, sampling and encoder time are included in the
server tick measurements. Up to one snapshot at a window boundary may fall in
the neighboring window depending on END-event order.

The new page inventory is verified functionally with 1,000 pages/78,000 slots;
its constant-time page mapping follows directly from index / 78. No throughput
claim for a full 1,000-storage world is made. Pattern reuse is verified by
object identity plus in-place NBT mutation and real resource reload tests.

The full modpack, external storage, fluid processing, many viewers, and complete
chunk eviction are outside this comparison. No 32³ or 64³ case is launched here.

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 8 --ticks 120 --property bench.monitor=immediate --tag local-opt-immediate
JAVA_HOME=/usr/lib/jvm/java-17-openjdk python scripts/benchmark_port.py --sizes 8 --ticks 120 --property bench.monitor=coalesced --tag local-opt-coalesced
```
