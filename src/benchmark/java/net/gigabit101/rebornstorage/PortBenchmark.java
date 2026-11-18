package net.gigabit101.rebornstorage;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.refinedmods.refinedstorage.RSItems;
import com.refinedmods.refinedstorage.api.autocrafting.task.ICalculationResult;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.item.PatternItem;
import com.refinedmods.refinedstorage.tile.ControllerTile;
import com.refinedmods.refinedstorage.tile.DiskDriveTile;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.gigabit101.rebornstorage.blockentities.BlockEntityMultiCrafter;
import net.gigabit101.rebornstorage.init.ModBlocks;
import net.gigabit101.rebornstorage.init.ModItems;
import net.gigabit101.rebornstorage.items.ItemRebornStorageCell;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Real Forge/RS workload. Excluded from the release jar; no production behavior changes. */
@Mod.EventBusSubscriber(modid = Constants.MOD_ID)
public class PortBenchmark {
  static MinecraftServer server;
  static ServerWorld world;
  static INetwork network;
  static final int SIZE = Integer.getInteger("bench.size", 4);
  static final int WINDOW = Integer.getInteger("bench.ticks", 240);
  static final int REPEATS = Integer.getInteger("bench.repeats", 3);
  static final int MAX_WINDOW_SECONDS = Integer.getInteger("bench.maxWindowSeconds", 60);
  static final int MAX_WARMUP_SECONDS = Integer.getInteger("bench.maxWarmupSeconds", 15);
  static final int CONCURRENT = Integer.getInteger("bench.concurrent", 1);
  static final boolean COUNT_LISTENERS = Boolean.getBoolean("bench.countListeners");
  static long monitorChanges, gridChanges, monitorStarted, gridStarted;
  static long snapshotsStarted, bytesStarted;
  static int peakTasks;
  static final int CPUS = (SIZE - 2) * (SIZE - 2) * (SIZE - 2) - 1;
  static final BlockPos STORAGE = new BlockPos(17, 81, 17);
  static final BlockPos CONTROLLER = new BlockPos(15, 80, 16);
  static String phase = "preload";
  static int phaseTicks, round, workload;
  static long phaseStarted, setupStarted, tickStarted, cpuStarted, processStarted, gcStarted;
  static long deliveredOutput;
  static long outputStarted, jobs, planningNs, heapPeak;
  static boolean collect;
  static List<Double> tickMs = new ArrayList<>(),
      cpuMs = new ArrayList<>(),
      networkMs = new ArrayList<>();
  static Map<String, Object> result = new LinkedHashMap<>();
  static List<Map<String, Object>> measurements = new ArrayList<>();
  static java.lang.reflect.Field networkTickCounter;
  static ThreadMXBean threads = ManagementFactory.getThreadMXBean();
  static com.sun.management.OperatingSystemMXBean os =
      (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

  static long usedHeap() {
    return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
  }

  static long gcTime() {
    long n = 0;
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans())
      n += Math.max(0, gc.getCollectionTime());
    return n;
  }

  static void write() throws Exception {
    result.put("phase", phase);
    result.put("measurements", measurements);
    Files.write(
        Paths.get("benchmark.json"),
        new GsonBuilder()
            .setPrettyPrinting()
            .create()
            .toJson(result)
            .getBytes(StandardCharsets.UTF_8));
  }

  static void phase(String next) throws Exception {
    Files.write(
        Paths.get("phase-timeline.jsonl"),
        ("{\"phase\":\"" + next + "\",\"epoch_ms\":" + System.currentTimeMillis() + "}\n")
            .getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
    phase = next;
    phaseTicks = 0;
    phaseStarted = System.nanoTime();
    RebornStorage.logger.info("BENCH PHASE " + next + " size=" + SIZE);
    write();
  }

  static void check(boolean ok, String why) {
    if (!ok) throw new IllegalStateException(why);
  }

  @SubscribeEvent
  public static void started(FMLServerStartedEvent event) {
    server = event.getServer();
    world = server.overworld();
    check(CONCURRENT >= 1 && CONCURRENT <= 4, "Diagnostic concurrency must be 1..4");
    result.put("concurrent_orders", CONCURRENT);
    result.put(
        "crafting_experiment",
        net.gigabit101.rebornstorage.crafting.CraftingExperiment.mode().name());
    result.put("count_listeners", COUNT_LISTENERS);
    result.put("monitor_encoding", System.getProperty("bench.monitor", "none"));
    result.put("size", SIZE);
    result.put("blocks", SIZE * SIZE * SIZE);
    result.put("cpus", CPUS);
    result.put("storage_blocks", 1);
    check(MAX_WINDOW_SECONDS > 0 && MAX_WARMUP_SECONDS > 0, "Positive phase timeouts required");
    result.put("window_ticks", WINDOW);
    result.put("max_window_seconds", MAX_WINDOW_SECONDS);
    result.put("max_warmup_seconds", MAX_WARMUP_SECONDS);
    result.put("repeats", REPEATS);
    result.put("java", System.getProperty("java.version"));
    result.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
    result.put("status", "running");
    threads.setThreadCpuTimeEnabled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void start(TickEvent.ServerTickEvent event) {
    if (server == null || event.phase != TickEvent.Phase.START) return;
    tickStarted = System.nanoTime();
    cpuStarted = threads.getCurrentThreadCpuTime();
    // Last completed server tick includes benchmark submission and normal autosave work.
    if (collect && phaseTicks > 1)
      tickMs.add(server.tickTimes[(server.getTickCount() - 1) % server.tickTimes.length] / 1e6);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void end(TickEvent.ServerTickEvent event) {
    if (server == null || event.phase != TickEvent.Phase.END) return;
    try {
      if (collect) {
        if (network != null)
          peakTasks = Math.max(peakTasks, network.getCraftingManager().getTasks().size());
        cpuMs.add((threads.getCurrentThreadCpuTime() - cpuStarted) / 1e6);
        heapPeak = Math.max(heapPeak, usedHeap());
        if (network != null) {
          long[] times = network.getTickTimes();
          // Read RS's own completed-update slot; its counter is independent of world time.
          networkMs.add(times[(networkTickCounter.getInt(network) - 1) % times.length] / 1e6);
        }
      }
      phaseTicks++;
      if (phase.equals("craft_warmup") || phase.equals("simple") || phase.equals("nested"))
        clearOutput();
      if (phase.equals("preload")) {
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        server
            .getCommands()
            .performCommand(server.createCommandSourceStack(), "gamerule randomTickSpeed 0");
        world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        for (int x = 0; x <= (16 + SIZE) / 16; x++)
          for (int z = 0; z <= (16 + SIZE) / 16; z++) {
            world.getChunk(x, z);
            world.setChunkForced(x, z, true);
          }
        RebornStorageConfig.MULTIBLOCK_MAX_XSIZE.set(64);
        RebornStorageConfig.MULTIBLOCK_MAX_YSIZE.set(64);
        RebornStorageConfig.MULTIBLOCK_MAX_ZSIZE.set(64);
        phase("baseline_warmup");
        return;
      }
      if (phase.equals("baseline_warmup") && phaseTicks >= 100) {
        beginMeasure("baseline", 0);
        return;
      }
      if (phase.equals("baseline") && phaseTicks >= WINDOW) {
        endMeasure(null);
        setupStarted = System.nanoTime();
        phase("placing");
        long start = System.nanoTime();
        for (int x = 0; x < SIZE; x++)
          for (int y = 0; y < SIZE; y++)
            for (int z = 0; z < SIZE; z++) {
              int edges =
                  (x == 0 || x == SIZE - 1 ? 1 : 0)
                      + (y == 0 || y == SIZE - 1 ? 1 : 0)
                      + (z == 0 || z == SIZE - 1 ? 1 : 0);
              Block b =
                  edges >= 2
                      ? ModBlocks.BLOCK_MULTI_FRAME.get()
                      : edges == 1
                          ? ModBlocks.BLOCK_MULTI_HEAT.get()
                          : ModBlocks.BLOCK_MULTI_CPU.get();
              if (x == 1 && y == 1 && z == 1) b = ModBlocks.BLOCK_MULTI_STORAGE.get();
              world.setBlockAndUpdate(new BlockPos(16 + x, 80 + y, 16 + z), b.defaultBlockState());
            }
        result.put("bulk_placement_ms", (System.nanoTime() - start) / 1e6);
        phase("assembling");
        return;
      }
      if (phase.equals("assembling")) {
        BlockEntityMultiCrafter tile = (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
        if (tile.getMultiBlock() != null && tile.getMultiBlock().isAssembled()) {
          result.put("assembly_ms", (System.nanoTime() - phaseStarted) / 1e6);
          result.put("assembly_heap_bytes", usedHeap());
          check(tile.getMultiBlock().speed == CPUS, "CPU count mismatch");
          installPatterns(tile);
          world.setBlockAndUpdate(
              CONTROLLER,
              ForgeRegistries.BLOCKS
                  .getValue(new ResourceLocation("refinedstorage:creative_controller"))
                  .defaultBlockState());
          world.setBlockAndUpdate(
              CONTROLLER.west(),
              ForgeRegistries.BLOCKS
                  .getValue(new ResourceLocation("refinedstorage:disk_drive"))
                  .defaultBlockState());
          ItemStack disk = new ItemStack(ModItems.DISK_262144K.get());
          ((ItemRebornStorageCell) disk.getItem())
              .inventoryTick(
                  disk,
                  world,
                  FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "Benchmark")),
                  0,
                  false);
          ((DiskDriveTile) world.getBlockEntity(CONTROLLER.west()))
              .getNode()
              .getDisks()
              .insertItem(0, disk, false);
          phase("connecting");
        } else if (phaseTicks > 400)
          throw new IllegalStateException(
              "Structure never assembled: " + tile.getMultiBlock().getLastValidationException());
        return;
      }
      if (phase.equals("connecting")) {
        network = ((ControllerTile) world.getBlockEntity(CONTROLLER)).getNetwork();
        if (networkTickCounter == null) {
          networkTickCounter = network.getClass().getDeclaredField("tickCounter");
          networkTickCounter.setAccessible(true);
        }
        BlockEntityMultiCrafter tile = (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
        if (tile.getNode().getNetwork() == network
            && network.getCraftingManager().getPattern(new ItemStack(Items.HOPPER_MINECART))
                != null) {
          result.put("network_connection_ms", (System.nanoTime() - phaseStarted) / 1e6);
          result.put("network_nodes", network.getNodeGraph().all().size());
          result.put(
              "persistent_rs_nodes", API.instance().getNetworkNodeManager(world).all().size());
          result.put(
              "crafter_providers",
              network.getNodeGraph().all().stream()
                  .filter(
                      e -> e.getNode() instanceof net.gigabit101.rebornstorage.nodes.CraftingNode)
                  .count());
          result.put("configured_fe_per_tick", network.getEnergyUsage());
          if (COUNT_LISTENERS) installCountingListeners();
          MonitorEncodingProbe.install(System.getProperty("bench.monitor", "none"));
          phase("idle_warmup");
        } else if (phaseTicks > 400) throw new IllegalStateException("Network never connected");
        return;
      }
      if (phase.equals("idle_warmup") && phaseTicks >= 100) {
        beginMeasure("idle", 0);
        return;
      }
      if (phase.equals("idle") && phaseTicks >= WINDOW) {
        endMeasure(null);
        workload = 0;
        round = 0;
        resetStorage();
        phase("craft_warmup");
        return;
      }
      if (phase.equals("craft_warmup")) {
        keepBusy();
        if (phaseTicks >= 100
            || System.nanoTime() - phaseStarted > MAX_WARMUP_SECONDS * 1_000_000_000L) {
          clearOutput();
          beginMeasure(workload == 0 ? "simple" : "nested", ++round);
        }
        return;
      }
      if (phase.equals("simple") || phase.equals("nested")) {
        keepBusy();
        if (phaseTicks >= WINDOW
            || System.nanoTime() - phaseStarted > MAX_WINDOW_SECONDS * 1_000_000_000L) {
          endMeasure(outputItem());
          if (round < REPEATS) {
            clearOutput();
            beginMeasure(workload == 0 ? "simple" : "nested", ++round);
          } else {
            network.getCraftingManager().cancel(null);
            phase("draining");
          }
        }
        return;
      }
      if (phase.equals("draining")) {
        network.getCraftingManager().cancel(null);
        if (phaseTicks < 5) return;
        check(network.getCraftingManager().getTasks().isEmpty(), "Tasks failed to cancel");
        if (workload == 0) {
          workload = 1;
          round = 0;
          resetStorage();
          phase("craft_warmup");
        } else {
          result.put("heap_after_work_bytes", usedHeap());
          phase("saving");
          long start = System.nanoTime();
          server.saveAllChunks(false, true, true);
          result.put("explicit_save_ms", (System.nanoTime() - start) / 1e6);
          result.put("status", "pass");
          phase("done");
          server.halt(false);
          server = null;
        }
      }
    } catch (Throwable t) {
      RebornStorage.logger.error("BENCH FAILED phase=" + phase, t);
      result.put("status", "failed");
      result.put("error", t.toString());
      try {
        write();
      } catch (Exception ignored) {
      }
      server.halt(false);
      server = null;
    }
  }

  static void beginMeasure(String label, int repeat) throws Exception {
    collect = false;
    if (network != null) {
      result.put("observed_fe_per_tick", network.getEnergyUsage());
      result.put("controller_capacity", network.getEnergyStorage().getMaxEnergyStored());
      result.put("network_can_run", network.canRun());
    }
    if (label.equals("baseline") || label.equals("idle")) {
      long g = System.nanoTime();
      System.gc();
      result.put(label + "_forced_gc_ms", (System.nanoTime() - g) / 1e6);
      result.put(label + "_retained_heap_bytes", usedHeap());
    }
    phase(label);
    result.put("active_repeat", repeat);
    tickMs.clear();
    cpuMs.clear();
    networkMs.clear();
    snapshotsStarted = MonitorEncodingProbe.snapshots;
    bytesStarted = MonitorEncodingProbe.bytes;
    monitorStarted = monitorChanges;
    gridStarted = gridChanges;
    peakTasks = 0;
    jobs = 0;
    planningNs = 0;
    heapPeak = usedHeap();
    outputStarted = deliveredOutput;
    processStarted = os.getProcessCpuTime();
    gcStarted = gcTime();
    collect = true;
  }

  static Map<String, Object> stats(List<Double> values) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (values.isEmpty()) return out;
    List<Double> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    double sum = 0;
    int slow = 0;
    for (double d : sorted) {
      sum += d;
      if (d > 50) slow++;
    }
    out.put("mean", sum / sorted.size());
    out.put("p50", sorted.get(sorted.size() / 2));
    out.put(
        "p95", sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * .95) - 1)));
    out.put(
        "p99", sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * .99) - 1)));
    out.put("max", sorted.get(sorted.size() - 1));
    out.put("ticks_over_50ms", slow);
    out.put("samples", sorted.size());
    return out;
  }

  static void endMeasure(Item output) throws Exception {
    collect = false;
    double seconds = (System.nanoTime() - phaseStarted) / 1e9;
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("phase", phase);
    m.put("repeat", round);
    m.put("ticks", phaseTicks);
    m.put("window_complete", phaseTicks >= WINDOW);
    m.put("window_stop_reason", phaseTicks >= WINDOW ? "tick_target" : "wall_time_limit");
    m.put("seconds", seconds);
    m.put("observed_tps", phaseTicks / seconds);
    m.put("tick_ms_samples", new ArrayList<>(tickMs));
    m.put("tick_ms", stats(tickMs));
    m.put("server_tick_cpu_ms", stats(cpuMs));
    m.put("rs_network_ms", stats(networkMs));
    long produced = output == null ? 0 : deliveredOutput - outputStarted;
    m.put("output", output == null ? "" : output.getRegistryName().toString());
    m.put("produced", produced);
    m.put("items_per_second", produced / seconds);
    m.put("monitor_snapshots", MonitorEncodingProbe.snapshots - snapshotsStarted);
    m.put("monitor_encoded_bytes", MonitorEncodingProbe.bytes - bytesStarted);
    m.put("jobs_submitted", jobs);
    m.put("peak_concurrent_tasks", peakTasks);
    m.put("monitor_change_callbacks", monitorChanges - monitorStarted);
    m.put("grid_change_callbacks", gridChanges - gridStarted);
    m.put("planning_ms", planningNs / 1e6);
    m.put("heap_peak_bytes", heapPeak);
    m.put("heap_end_bytes", usedHeap());
    m.put("gc_ms", gcTime() - gcStarted);
    m.put(
        "process_cpu_core_equivalents", (os.getProcessCpuTime() - processStarted) / 1e9 / seconds);
    measurements.add(m);
    RebornStorage.logger.info("BENCH RESULT " + new GsonBuilder().create().toJson(m));
    write();
  }

  static Item outputItem() {
    return workload == 0 ? Items.OAK_PLANKS : Items.HOPPER_MINECART;
  }

  static int count(Item item) {
    return network.extractItem(new ItemStack(item), Integer.MAX_VALUE, Action.SIMULATE).getCount();
  }

  static void clearOutput() {
    deliveredOutput +=
        network
            .extractItem(new ItemStack(outputItem()), Integer.MAX_VALUE, Action.PERFORM)
            .getCount();
  }

  static void resetStorage() {
    for (Item i :
        new Item[] {
          Items.OAK_LOG,
          Items.OAK_PLANKS,
          Items.CHEST,
          Items.IRON_INGOT,
          Items.HOPPER,
          Items.MINECART,
          Items.HOPPER_MINECART
        }) network.extractItem(new ItemStack(i), Integer.MAX_VALUE, Action.PERFORM);
    check(
        network.insertItem(new ItemStack(Items.OAK_LOG), 40_000_000, Action.PERFORM).isEmpty(),
        "Input log capacity");
    if (workload == 1)
      check(
          network.insertItem(new ItemStack(Items.IRON_INGOT), 40_000_000, Action.PERFORM).isEmpty(),
          "Input iron capacity");
  }

  static void keepBusy() {
    check(
        network.canRun(), "Network cannot run: insufficient controller buffer or disabled network");
    if (!network.getCraftingManager().getTasks().isEmpty()) return;
    int qty = workload == 0 ? Math.max(256, CPUS * 80) : Math.max(8, CPUS * 5);
    int logs = count(Items.OAK_LOG);
    if (logs < CONCURRENT * (workload == 0 ? qty / 4 : qty * 2))
      check(
          network
              .insertItem(new ItemStack(Items.OAK_LOG), 40_000_000 - logs, Action.PERFORM)
              .isEmpty(),
          "Input refill failed");
    if (workload == 1) {
      int iron = count(Items.IRON_INGOT);
      if (iron < CONCURRENT * qty * 10)
        check(
            network
                .insertItem(new ItemStack(Items.IRON_INGOT), 40_000_000 - iron, Action.PERFORM)
                .isEmpty(),
            "Iron refill failed");
    }
    long start = System.nanoTime();
    for (int order = 0; order < CONCURRENT; order++) {
      ICalculationResult calc =
          network.getCraftingManager().create(new ItemStack(outputItem()), qty);
      check(calc.isOk(), "Craft calculation failed " + calc.getType());
      if (net.gigabit101.rebornstorage.crafting.CraftingExperiment.mode()
          == net.gigabit101.rebornstorage.crafting.CraftingExperiment.Mode.TASK) {
        check(
            calc.getTask() instanceof net.gigabit101.rebornstorage.crafting.ExperimentalTask,
            "Experimental task was not selected");
        long optimized =
            ((net.gigabit101.rebornstorage.crafting.ExperimentalTask) calc.getTask())
                .getOptimizedNodeCount();
        check(
            optimized == (workload == 0 ? 1 : 5),
            "Expected all benchmark recipe nodes to use optimized requirements");
        long plans =
            ((net.gigabit101.rebornstorage.crafting.ExperimentalTask) calc.getTask())
                .getIntermediatePlanCount();
        check(
            plans == (workload == 0 ? 0 : 4),
            "Expected all four nested intermediates to use guarded plans, got " + plans);
        result.put("verified_intermediate_plans_" + (workload == 0 ? "simple" : "nested"), plans);
        result.put("verified_optimized_nodes_" + (workload == 0 ? "simple" : "nested"), optimized);
      }
      network.getCraftingManager().start(calc.getTask());
      jobs++;
    }
    planningNs += System.nanoTime() - start;
  }

  // Count real RS callbacks without generating packets or opening a player connection.
  static void installCountingListeners() {
    network
        .getCraftingManager()
        .addListener(
            new com.refinedmods.refinedstorage.api.autocrafting.craftingmonitor
                .ICraftingMonitorListener() {
              public void onAttached() {}

              public void onChanged() {
                monitorChanges++;
              }
            });
    network
        .getItemStorageCache()
        .addListener(
            new com.refinedmods.refinedstorage.api.storage.cache.IStorageCacheListener<
                ItemStack>() {
              public void onAttached() {}

              public void onInvalidated() {}

              public void onChanged(
                  com.refinedmods.refinedstorage.api.util.StackListResult<ItemStack> delta) {
                gridChanges++;
              }

              public void onChangedBulk(
                  List<com.refinedmods.refinedstorage.api.util.StackListResult<ItemStack>> deltas) {
                gridChanges++;
              }
            });
  }

  static void pattern(
      BlockEntityMultiCrafter tile, int slot, Item result, int count, Item... inputs) {
    ItemStack p = new ItemStack(RSItems.PATTERN.get());
    PatternItem.setToCurrentVersion(p);
    for (int i = 0; i < inputs.length; i++)
      if (inputs[i] != null) PatternItem.setInputSlot(p, i, new ItemStack(inputs[i]));
    PatternItem.setOutputSlot(p, 0, new ItemStack(result, count));
    tile.getStorageNode().patterns.setStackInSlot(slot, p);
  }

  static void installPatterns(BlockEntityMultiCrafter tile) {
    pattern(tile, 0, Items.OAK_PLANKS, 4, Items.OAK_LOG);
    pattern(
        tile,
        1,
        Items.CHEST,
        1,
        Items.OAK_PLANKS,
        Items.OAK_PLANKS,
        Items.OAK_PLANKS,
        Items.OAK_PLANKS,
        null,
        Items.OAK_PLANKS,
        Items.OAK_PLANKS,
        Items.OAK_PLANKS,
        Items.OAK_PLANKS);
    pattern(
        tile,
        2,
        Items.HOPPER,
        1,
        Items.IRON_INGOT,
        null,
        Items.IRON_INGOT,
        Items.IRON_INGOT,
        Items.CHEST,
        Items.IRON_INGOT,
        null,
        Items.IRON_INGOT,
        null);
    pattern(
        tile,
        3,
        Items.MINECART,
        1,
        Items.IRON_INGOT,
        null,
        Items.IRON_INGOT,
        Items.IRON_INGOT,
        Items.IRON_INGOT,
        Items.IRON_INGOT);
    pattern(tile, 4, Items.HOPPER_MINECART, 1, Items.HOPPER, null, null, Items.MINECART);
    tile.getNode().rebuildPatterns("benchmark patterns");
    check(tile.getNode().getPatterns().size() == 5, "Invalid benchmark patterns");
  }
}
