package net.gigabit101.rebornstorage;

import com.mojang.authlib.GameProfile;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.API;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import net.gigabit101.rebornstorage.blockentities.BlockEntityMultiCrafter;
import net.gigabit101.rebornstorage.init.ModBlocks;
import net.gigabit101.rebornstorage.items.ItemRebornStorageCell;
import net.gigabit101.rebornstorage.storage.DiskTier;
import net.gigabit101.rebornstorage.blockentities.BlockEntityAdvancedWirelessTransmitter;
import net.gigabit101.rebornstorage.nodes.AdvancedWirelessTransmitterNode;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Runs only in runServer -PintegrationTest, never included in the production jar. */
@Mod.EventBusSubscriber(modid = Constants.MOD_ID)
public class PortIntegrationTest {
  private static MinecraftServer server;
  private static int tick;
  private static int checks;
  private static boolean restoringTask;
  private static int restoreTicks;
  private static com.refinedmods.refinedstorage.api.network.INetwork scenarioNetwork;
  private static int pausedOutput;
  private static BlockEntityMultiCrafter pausedPart;
  private static ItemStack secondPattern;
  private static com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern cachedPattern;
  private static java.util.concurrent.CompletableFuture<Void> reloadFuture;
  private static final BlockPos SECOND_STORAGE = new BlockPos(3, 81, 2);
  private static final BlockPos STORAGE = new BlockPos(3, 81, 1);

  private static void placeAsPlayer(ServerWorld world, BlockPos pos, String id) {
    net.minecraftforge.common.util.BlockSnapshot snapshot =
        net.minecraftforge.common.util.BlockSnapshot.create(world.dimension(), world, pos);
    world.setBlockAndUpdate(
        pos, ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id)).defaultBlockState());
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
        new net.minecraftforge.event.world.BlockEvent.EntityPlaceEvent(
            snapshot, world.getBlockState(pos.below()), player(world)));
  }

  private static void check(boolean ok, String description) {
    if (!ok) throw new AssertionError(description);
    checks++;
    RebornStorage.logger.info("PORT TEST PASS: " + description);
  }

  @SubscribeEvent
  public static void started(FMLServerStartedEvent event) {
    server = event.getServer();
    restoringTask = Files.exists(Paths.get("pending-craft-marker.txt"));
  }

  @SubscribeEvent
  public static void tick(TickEvent.ServerTickEvent event) {
    if (server == null || event.phase != TickEvent.Phase.END) return;
    try {
      ServerWorld world = server.overworld();
      if (restoringTask) {
        restoreTicks++;
        if (restoreTicks == 1) world.getChunk(STORAGE);
        if (restoreTicks < 240) return;
        BlockEntityMultiCrafter restoredTile =
            (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
        com.refinedmods.refinedstorage.api.network.INetwork restoredNetwork =
            restoredTile.getNode().getNetwork();
        check(
            restoredNetwork != null
                && restoredNetwork
                        .extractItem(
                            new ItemStack(
                                Files.exists(Paths.get("nested-restart.txt"))
                                    ? Items.STICK
                                    : Items.OAK_PLANKS),
                            Integer.MAX_VALUE,
                            Action.PERFORM)
                        .getCount()
                    == 4000,
            "in-flight crafting task survives actual server restart with exactly 4000 output");
        check(
            restoredNetwork.getCraftingManager().getTasks().isEmpty(),
            "restored crafting task completes");
        if (Files.deleteIfExists(Paths.get("nested-restart.txt"))) {
          restoredTile.getStorageNode().patterns.setStackInSlot(20, ItemStack.EMPTY);
          restoredTile.getNode().applyPatternChanges();
          restoredNetwork.getCraftingManager().invalidate();
        }
        Files.delete(Paths.get("pending-craft-marker.txt"));
        restoringTask = false;
        return;
      }
      tick++;
      if (tick == 1) {
        // The fixture is at the origin; a fresh world's spawn may be elsewhere.
        // Keep both chunks containing its controller/security manager ticking without players.
        world.setChunkForced(0, 0, true);
        world.setChunkForced(0, -1, true);
        if (Files.exists(Paths.get("restart-marker.txt"))) {
          BlockEntityMultiCrafter restored =
              (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
          check(
              restored != null
                  && restored.getStorageNode().patterns.getStackInSlot(0).getItem()
                      == com.refinedmods.refinedstorage.RSItems.PATTERN.get(),
              "pattern survives actual server restart");
          com.refinedmods.refinedstorage.tile.DiskDriveTile drive =
              (com.refinedmods.refinedstorage.tile.DiskDriveTile)
                  world.getBlockEntity(new BlockPos(0, 80, 0));
          ItemStack savedDisk = drive.getNode().getDisks().getStackInSlot(0);
          check(
              savedDisk.getItem() == net.gigabit101.rebornstorage.init.ModItems.DISK_32768K.get()
                  && API.instance().getStorageDiskManager(world).getByStack(savedDisk) != null,
              "disk UUID resolves after actual server restart");
        }
        if (Files.exists(Paths.get("restart-marker.txt"))) {
          AdvancedWirelessTransmitterNode tx = ((BlockEntityAdvancedWirelessTransmitter) world.getBlockEntity(new BlockPos(1,81,0))).getNode();
          check(tx.getRange() == 1500, "advanced transmitter upgrade survives actual restart");
          ItemStack fluidStack = ((com.refinedmods.refinedstorage.tile.DiskDriveTile) world.getBlockEntity(new BlockPos(0,80,0))).getNode().getDisks().getStackInSlot(1);
          IStorageDisk<net.minecraftforge.fluids.FluidStack> fluidDisk = API.instance().getStorageDiskManager(world).getByStack(fluidStack);
          check(fluidDisk.getFactoryId().equals(net.gigabit101.rebornstorage.storage.RebornFluidDiskFactory.ID)
              && fluidDisk.getStored() == 1000000000 && fluidDisk.getCapacity() == 1048576000, "billion-mB drive survives actual restart");
        }
        disks(world);
        for (int x = 0; x < 6; x++)
          for (int y = 80; y < 84; y++)
            for (int z = -1; z < 4; z++)
              world.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        for (int x = 0; x < 4; x++)
          for (int y = 0; y < 4; y++)
            for (int z = 0; z < 4; z++) {
              int edges =
                  (x == 0 || x == 3 ? 1 : 0)
                      + (y == 0 || y == 3 ? 1 : 0)
                      + (z == 0 || z == 3 ? 1 : 0);
              Block b =
                  edges >= 2
                      ? ModBlocks.BLOCK_MULTI_FRAME.get()
                      : edges == 1
                          ? ModBlocks.BLOCK_MULTI_HEAT.get()
                          : ModBlocks.BLOCK_MULTI_CPU.get();
              if (x == 1 && y == 1 && (z == 1 || z == 2)) b = ModBlocks.BLOCK_MULTI_STORAGE.get();
              world.setBlockAndUpdate(new BlockPos(2 + x, 80 + y, z), b.defaultBlockState());
            }
        Block controller =
            ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("refinedstorage:creative_controller"));
        world.setBlockAndUpdate(new BlockPos(1, 80, 0), controller.defaultBlockState());
        world.setBlockAndUpdate(
            new BlockPos(1, 80, -1),
            ForgeRegistries.BLOCKS
                .getValue(new ResourceLocation("refinedstorage:security_manager"))
                .defaultBlockState());
        world.setBlockAndUpdate(
            new BlockPos(0, 80, 1),
            ForgeRegistries.BLOCKS
                .getValue(new ResourceLocation("refinedstorage:disk_manipulator"))
                .defaultBlockState());
        ItemStack sourceDisk =
            new ItemStack(net.gigabit101.rebornstorage.init.ModItems.DISK_262144K.get());
        ((ItemRebornStorageCell) sourceDisk.getItem())
            .inventoryTick(sourceDisk, world, player(world), 0, false);
        API.instance()
            .getStorageDiskManager(world)
            .getByStack(sourceDisk)
            .insert(new ItemStack(Items.IRON_INGOT), 16, Action.PERFORM);
        ((com.refinedmods.refinedstorage.tile.DiskManipulatorTile)
                world.getBlockEntity(new BlockPos(0, 80, 1)))
            .getNode()
            .getInputDisks()
            .insertItem(0, sourceDisk, false);
        Block transmitter = ModBlocks.ADVANCED_TRANSMITTER.get();
        world.setBlockAndUpdate(
            new BlockPos(1, 81, 0),
            transmitter
                .defaultBlockState()
                .setValue(
                    com.refinedmods.refinedstorage.block.BlockDirection.ANY.getProperty(),
                    net.minecraft.util.Direction.DOWN));
        world.setBlockAndUpdate(
            new BlockPos(0, 80, 0),
            ForgeRegistries.BLOCKS
                .getValue(new ResourceLocation("refinedstorage:disk_drive"))
                .defaultBlockState());
        ItemStack storageDisk =
            new ItemStack(net.gigabit101.rebornstorage.init.ModItems.DISK_32768K.get());
        ((ItemRebornStorageCell) storageDisk.getItem())
            .inventoryTick(storageDisk, world, player(world), 0, false);
        ((com.refinedmods.refinedstorage.tile.DiskDriveTile)
                world.getBlockEntity(new BlockPos(0, 80, 0)))
            .getNode()
            .getDisks()
            .insertItem(0, storageDisk, false);
        ItemStack fluidDisk = new ItemStack(DiskTier.FLUID_1048576.disk().get());
        ((ItemRebornStorageCell) fluidDisk.getItem()).inventoryTick(fluidDisk, world, player(world), 0, false);
        ((com.refinedmods.refinedstorage.tile.DiskDriveTile) world.getBlockEntity(new BlockPos(0,80,0)))
            .getNode().getDisks().insertItem(1, fluidDisk, false);
      }
      if (tick == 60) {
        BlockEntityMultiCrafter tile = (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
        check(
            tile.getMultiBlock() != null && tile.getMultiBlock().isAssembled(), "4x4x4 assembles");
        check(
            tile.getMultiBlock().pages == 2 && tile.getMultiBlock().invs.size() == 2,
            "two independent storage pages");
        check(tile.getNode().getCraftingCpus() == 6, "six CPUs counted");
        check(
            tile.getNode().getUpdateInterval() == 1
                && tile.getNode().getMaximumSuccessfulCraftingUpdates() == 6,
            "recent Reborn CPU budget");
        check(tile.getNode().getNetwork() != null, "connected to RS controller");
        net.minecraftforge.fluids.FluidStack water = new net.minecraftforge.fluids.FluidStack(net.minecraft.fluid.Fluids.WATER, 1);
        check(tile.getNode().getNetwork().insertFluid(water, 1000000000, Action.PERFORM).isEmpty()
            && tile.getNode().getNetwork().extractFluid(water, 1000000001, Action.SIMULATE).getAmount() == 1000000000,
            "drive accepts one billion mB through RS network");
        wireless(world, tile.getNode().getNetwork());
        viewersAndSecurity(world, tile);
        UUID owner = UUID.randomUUID();
        tile.getStorageNode().setOwner(owner);
        check(
            owner.equals(tile.getNode().getOwner()),
            "aggregate owner delegates to persistent storage");
        tile.getStorageNode().patterns.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        CompoundNBT saved = tile.getStorageNode().write(new CompoundNBT());
        net.gigabit101.rebornstorage.nodes.PatternStorageNode restored =
            new net.gigabit101.rebornstorage.nodes.PatternStorageNode(world, STORAGE);
        restored.read(saved);
        check(
            restored.patterns.getStackInSlot(0).getItem() == Items.DIAMOND,
            "node inventory NBT round trip");
        check(owner.equals(restored.getOwner()), "node owner NBT round trip");
        check(
            restored.getProviderId().equals(tile.getStorageNode().getProviderId()),
            "aggregate UUID persists in storage NBT");
        world.setBlockAndUpdate(new BlockPos(2, 80, 0), Blocks.AIR.defaultBlockState());
      }
      if (tick == 80) {
        BlockEntityMultiCrafter tile = (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
        check(
            !tile.getMultiBlock().isAssembled() && !tile.getNode().isActive(),
            "breaking frame invalidates crafter");
        world.setBlockAndUpdate(
            new BlockPos(2, 80, 0), ModBlocks.BLOCK_MULTI_FRAME.get().defaultBlockState());
      }
      if (tick == 100) {
        BlockEntityMultiCrafter tile = (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
        check(tile.getMultiBlock().isAssembled(), "crafter reassembles");
        check(
            tile.getStorageNode().patterns.getStackInSlot(0).getItem() == Items.DIAMOND,
            "frame break preserves patterns");
        ItemStack pattern = new ItemStack(com.refinedmods.refinedstorage.RSItems.PATTERN.get());
        com.refinedmods.refinedstorage.item.PatternItem.setToCurrentVersion(pattern);
        com.refinedmods.refinedstorage.item.PatternItem.setInputSlot(
            pattern, 0, new ItemStack(Items.OAK_LOG));
        com.refinedmods.refinedstorage.item.PatternItem.setOutputSlot(
            pattern, 0, new ItemStack(Items.OAK_PLANKS, 4));
        tile.getStorageNode().patterns.setStackInSlot(0, pattern);
        tile.getNode().rebuildPatterns("integration test");
        check(tile.getNode().getPatterns().size() == 1, "encoded crafting pattern recognized");
        CompoundNBT legacyPattern =
            com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.SerializationUtil
                .writePatternToNbt(tile.getNode().getPatterns().get(0));
        legacyPattern.putLong("ContainerPos", SECOND_STORAGE.asLong());
        check(
            com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.SerializationUtil
                .readPatternFromNbt(legacyPattern, world)
                .equals(tile.getNode().getPatterns().get(0)),
            "legacy task pattern resolves from a non-anchor storage position");
        check(
            tile.getNode()
                .getNetwork()
                .insertItem(new ItemStack(Items.OAK_LOG), 100, Action.PERFORM)
                .isEmpty(),
            "disk drive accepts new disk and input items");
      }
      if (tick == 120) {
        com.refinedmods.refinedstorage.api.network.INetwork network =
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE)).getNode().getNetwork();
        check(
            network.getCraftingManager().request(null, new ItemStack(Items.OAK_PLANKS), 400)
                != null,
            "real autocrafting task starts");
      }
      if (tick == 220) {
        com.refinedmods.refinedstorage.api.network.INetwork network =
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE)).getNode().getNetwork();
        check(
            network.extractItem(new ItemStack(Items.OAK_PLANKS), 400, Action.PERFORM).getCount()
                == 400,
            "multiblock crafts 400 planks from 100 logs");
        check(network.getCraftingManager().getTasks().isEmpty(), "autocrafting task completes");
        check(
            network.extractItem(new ItemStack(Items.IRON_INGOT), 16, Action.PERFORM).getCount()
                == 16,
            "Disk Manipulator transfers new disk contents into RS");
        check(
            ((com.refinedmods.refinedstorage.tile.DiskManipulatorTile)
                        world.getBlockEntity(new BlockPos(0, 80, 1)))
                    .getNode()
                    .getOutputDisks()
                    .getStackInSlot(0)
                    .getItem()
                == net.gigabit101.rebornstorage.init.ModItems.DISK_262144K.get(),
            "Disk Manipulator returns original tier");
        scenarioNetwork = network;
        LocalOptimizationTest.run(world, network, server);
        cachedPattern =
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE))
                .getNode()
                .getPatterns()
                .get(0);
        check(
            network.getNodeGraph().all().stream()
                    .filter(
                        e -> e.getNode() instanceof net.gigabit101.rebornstorage.nodes.CraftingNode)
                    .count()
                == 1,
            "one aggregate graph provider for the entire multiblock");
        check(
            API.instance().getNetworkNodeManager(world).all().stream()
                    .filter(n -> n instanceof net.gigabit101.rebornstorage.nodes.PatternStorageNode)
                    .count()
                == 2,
            "only the two storage inventories persist as RS nodes");
        check(
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE))
                    .getNode()
                    .getPatternInventory()
                    .getSlots()
                == 156,
            "aggregate exposes both inventory pages");
        secondPattern = new ItemStack(com.refinedmods.refinedstorage.RSItems.PATTERN.get());
        com.refinedmods.refinedstorage.item.PatternItem.setToCurrentVersion(secondPattern);
        com.refinedmods.refinedstorage.item.PatternItem.setInputSlot(
            secondPattern, 0, new ItemStack(Items.OAK_PLANKS));
        com.refinedmods.refinedstorage.item.PatternItem.setInputSlot(
            secondPattern, 3, new ItemStack(Items.OAK_PLANKS));
        com.refinedmods.refinedstorage.item.PatternItem.setOutputSlot(
            secondPattern, 0, new ItemStack(Items.STICK, 4));
        ((BlockEntityMultiCrafter) world.getBlockEntity(SECOND_STORAGE))
            .getStorageNode()
            .patterns
            .setStackInSlot(0, secondPattern);
        network.insertItem(new ItemStack(Items.OAK_LOG), 4, Action.PERFORM);
        placeAsPlayer(world, new BlockPos(6, 80, 0), "refinedstorage:disk_drive");
      }
      if (tick == 230) {
        check(
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE)).getNode().getPatterns().get(0)
                == cachedPattern,
            "editing another page preserves the unchanged cached pattern");
        BlockEntityMultiCrafter cacheTile = (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
        ItemStack original = cacheTile.getStorageNode().patterns.getStackInSlot(0).copy();
        com.refinedmods.refinedstorage.item.PatternItem.setInputSlot(
            cacheTile.getStorageNode().patterns.getStackInSlot(0),
            0,
            new ItemStack(Items.BIRCH_LOG));
        ((BlockEntityMultiCrafter) world.getBlockEntity(SECOND_STORAGE))
            .getStorageNode()
            .patterns
            .setStackInSlot(77, new ItemStack(Items.DIAMOND));
        cacheTile.getNode().applyPatternChanges();
        check(
            cacheTile.getNode().getPatterns().get(0).getOutputs().get(0).getItem()
                == Items.BIRCH_PLANKS,
            "in-place pattern NBT mutation detected during inventory refresh");
        cacheTile.getStorageNode().patterns.setStackInSlot(0, original);
        ((BlockEntityMultiCrafter) world.getBlockEntity(SECOND_STORAGE))
            .getStorageNode()
            .patterns
            .setStackInSlot(77, ItemStack.EMPTY);
        cacheTile.getNode().applyPatternChanges();
        check(
            ((com.refinedmods.refinedstorage.tile.DiskDriveTile)
                        world.getBlockEntity(new BlockPos(6, 80, 0)))
                    .getNode()
                    .getNetwork()
                == scenarioNetwork,
            "network traverses aggregate to the opposite surface");
        check(
            scenarioNetwork.getCraftingManager().request(null, new ItemStack(Items.STICK), 32)
                != null,
            "second storage page pattern is discovered automatically");
      }
      if (tick == 260) {
        check(
            scenarioNetwork.extractItem(new ItemStack(Items.STICK), 32, Action.PERFORM).getCount()
                == 32,
            "nested craft spans two storage pages");
        ((BlockEntityMultiCrafter) world.getBlockEntity(SECOND_STORAGE))
            .getStorageNode()
            .patterns
            .setStackInSlot(0, ItemStack.EMPTY);
        scenarioNetwork.insertItem(new ItemStack(Items.OAK_LOG), 150, Action.PERFORM);
        check(
            scenarioNetwork.getCraftingManager().request(null, new ItemStack(Items.OAK_PLANKS), 600)
                != null,
            "craft before structural break starts");
      }
      if (tick == 263) {
        check(
            scenarioNetwork.getCraftingManager().getPattern(new ItemStack(Items.STICK)) == null,
            "removed pattern disappears without periodic polling");
        world.setBlockAndUpdate(new BlockPos(2, 80, 0), Blocks.AIR.defaultBlockState());
        pausedOutput =
            scenarioNetwork
                .extractItem(new ItemStack(Items.OAK_PLANKS), 1000, Action.SIMULATE)
                .getCount();
        check(
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE))
                    .getNode()
                    .getMaximumSuccessfulCraftingUpdates()
                == 0,
            "break immediately disables stale task container");
      }
      if (tick == 285) {
        check(
            scenarioNetwork
                    .extractItem(new ItemStack(Items.OAK_PLANKS), 1000, Action.SIMULATE)
                    .getCount()
                == pausedOutput,
            "disassembled crafter produces no further output");
        world.setBlockAndUpdate(
            new BlockPos(2, 80, 0), ModBlocks.BLOCK_MULTI_FRAME.get().defaultBlockState());
      }
      if (tick == 330) {
        check(
            scenarioNetwork
                    .extractItem(new ItemStack(Items.OAK_PLANKS), 1000, Action.PERFORM)
                    .getCount()
                == 600,
            "pending task resumes after reconstruction without lost or duplicate output");
        ((com.refinedmods.refinedstorage.apiimpl.network.Network) scenarioNetwork)
            .setRedstoneMode(com.refinedmods.refinedstorage.tile.config.RedstoneMode.HIGH);
        scenarioNetwork.insertItem(new ItemStack(Items.OAK_LOG), 10, Action.PERFORM);
      }
      if (tick == 360) {
        check(!scenarioNetwork.canRun(), "controller disabled by redstone");
        check(
            scenarioNetwork.getCraftingManager().request(null, new ItemStack(Items.OAK_PLANKS), 40)
                != null,
            "task can wait on disabled network");
      }
      if (tick == 380) {
        check(
            scenarioNetwork
                .extractItem(new ItemStack(Items.OAK_PLANKS), 100, Action.SIMULATE)
                .isEmpty(),
            "disabled network does not craft");
        ((com.refinedmods.refinedstorage.apiimpl.network.Network) scenarioNetwork)
            .setRedstoneMode(com.refinedmods.refinedstorage.tile.config.RedstoneMode.IGNORE);
      }
      if (tick == 430) {
        check(
            scenarioNetwork
                    .extractItem(new ItemStack(Items.OAK_PLANKS), 100, Action.PERFORM)
                    .getCount()
                == 40,
            "network restart resumes pending craft");
        pausedPart = (BlockEntityMultiCrafter) world.getBlockEntity(new BlockPos(5, 83, 3));
        net.gigabit101.rebornstorage.nodes.CraftingNode oldProvider =
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE)).getNode();
        pausedPart.onChunkUnloaded();
        check(
            oldProvider.getMaximumSuccessfulCraftingUpdates() == 0,
            "chunk-unload callback immediately disables stale provider");
      }
      if (tick == 440) {
        pausedPart.onLoad();
      }
      if (tick == 460) {
        BlockEntityMultiCrafter tile = (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
        net.gigabit101.rebornstorage.multiblocks.MultiBlockCrafter multiblock =
            tile.getMultiBlock();
        net.gigabit101.rebornstorage.nodes.CraftingNode previous = tile.getNode();
        previous.invalidate();
        net.gigabit101.rebornstorage.nodes.CraftingNode replacement =
            new net.gigabit101.rebornstorage.nodes.CraftingNode(
                world, previous.getPos(), multiblock, previous.getUuid());
        replacement.configure(
            6,
            50,
            java.util.Arrays.asList(
                tile.getStorageNode(),
                ((BlockEntityMultiCrafter) world.getBlockEntity(SECOND_STORAGE)).getStorageNode()));
        java.lang.reflect.Field providerField =
            net.gigabit101.rebornstorage.multiblocks.MultiBlockCrafter.class.getDeclaredField(
                "node");
        providerField.setAccessible(true);
        providerField.set(multiblock, replacement);
      }
      if (tick == 470) {
        check(
            scenarioNetwork.getCraftingManager().getPattern(new ItemStack(Items.OAK_PLANKS))
                != null,
            "part reload restores aggregate patterns");
        check(
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE)).getNode().getNetwork()
                == scenarioNetwork,
            "recreated provider at the same anchor receives its network connection");
        cachedPattern =
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE))
                .getNode()
                .getPatterns()
                .get(0);
        RebornStorageConfig.CPU_COST.set(7);
      }
      if (tick == 475) {
        check(
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE)).getNode().getPatterns().get(0)
                == cachedPattern,
            "energy-only configuration preserves the pattern cache");
        check(
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE)).getNode().getEnergyUsage()
                == 62,
            "cached energy follows configuration changes");
        RebornStorageConfig.CPU_COST.set(5);
        world.setBlockAndUpdate(new BlockPos(6, 80, 0), Blocks.AIR.defaultBlockState());
        placeAsPlayer(world, new BlockPos(6, 80, 0), "refinedstorage:creative_controller");
      }
      if (tick == 510) {
        check(
            world.getBlockState(new BlockPos(6, 80, 0)).isAir()
                || !((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE))
                    .getMultiBlock()
                    .isAssembled(),
            "conflicting second network is rejected instead of sharing a provider");
        world.setBlockAndUpdate(new BlockPos(6, 80, 0), Blocks.AIR.defaultBlockState());
        for (int x = 2; x <= 5; x++)
          for (int y = 80; y <= 83; y++)
            for (int z = 0; z <= 3; z++) {
              if (world.getBlockState(new BlockPos(x, y, z)).isAir()) {
                int edges =
                    (x == 2 || x == 5 ? 1 : 0)
                        + (y == 80 || y == 83 ? 1 : 0)
                        + (z == 0 || z == 3 ? 1 : 0);
                world.setBlockAndUpdate(
                    new BlockPos(x, y, z),
                    (edges >= 2
                            ? ModBlocks.BLOCK_MULTI_FRAME.get()
                            : ModBlocks.BLOCK_MULTI_HEAT.get())
                        .defaultBlockState());
              }
            }
      }
      if (tick == 520) {
        cachedPattern =
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE))
                .getNode()
                .getPatterns()
                .get(0);
        reloadFuture = server.reloadResources(server.getPackRepository().getSelectedIds());
      }
      if (tick == 521) {
        if (!reloadFuture.isDone()) {
          tick--;
          return;
        }
        reloadFuture.join();
      }
      if (tick == 525) {
        check(
            ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE)).getNode().getPatterns().get(0)
                != cachedPattern,
            "actual recipe reload refreshes cached patterns");
        java.lang.reflect.Field recipe =
            com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingPattern.class
                .getDeclaredField("recipe");
        recipe.setAccessible(true);
        check(
            recipe.get(
                    ((BlockEntityMultiCrafter) world.getBlockEntity(STORAGE))
                        .getNode()
                        .getPatterns()
                        .get(0))
                == world
                    .getRecipeManager()
                    .byKey(new ResourceLocation("minecraft:oak_planks"))
                    .get(),
            "cached pattern uses the newly installed recipe manager after reload");
      }
      if (tick == 550) {
        BlockEntityMultiCrafter tile = (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
        if (net.gigabit101.rebornstorage.crafting.CraftingExperiment.mode()
            != net.gigabit101.rebornstorage.crafting.CraftingExperiment.Mode.OFF)
          ExperimentalCraftingTest.run(tile, scenarioNetwork);
        check(
            tile.getMultiBlock().isAssembled() && tile.getNode().getNetwork() == scenarioNetwork,
            "aggregate reconnects to original network after conflict repair");
        check(
            tile.getStorageNode().patterns.getStackInSlot(0).getItem()
                == com.refinedmods.refinedstorage.RSItems.PATTERN.get(),
            "network conflict preserves storage inventory");
        scenarioNetwork.extractItem(
            new ItemStack(Items.OAK_PLANKS), Integer.MAX_VALUE, Action.PERFORM);
        scenarioNetwork.insertItem(new ItemStack(Items.OAK_LOG), 1000, Action.PERFORM);
        boolean nested =
            net.gigabit101.rebornstorage.crafting.CraftingExperiment.mode()
                == net.gigabit101.rebornstorage.crafting.CraftingExperiment.Mode.TASK;
        if (nested) {
          tile.getStorageNode().patterns.setStackInSlot(20, secondPattern.copy());
          tile.getNode().applyPatternChanges();
          scenarioNetwork.getCraftingManager().invalidate();
          scenarioNetwork.extractItem(
              new ItemStack(Items.STICK), Integer.MAX_VALUE, Action.PERFORM);
          Files.write(Paths.get("nested-restart.txt"), new byte[] {1});
        }
        check(
            scenarioNetwork
                    .getCraftingManager()
                    .request(null, new ItemStack(nested ? Items.STICK : Items.OAK_PLANKS), 4000)
                != null,
            "start long craft for restart persistence test");
      }
      if (tick == 555) {
        if (net.gigabit101.rebornstorage.crafting.CraftingExperiment.mode()
            == net.gigabit101.rebornstorage.crafting.CraftingExperiment.Mode.TASK)
          check(
              scenarioNetwork.getCraftingManager().getTasks().stream()
                  .allMatch(
                      t ->
                          t instanceof net.gigabit101.rebornstorage.crafting.ExperimentalTask
                              && ((net.gigabit101.rebornstorage.crafting.ExperimentalTask) t)
                                      .getIntermediatePlanCount()
                                  == 1),
              "experimental factory selected for saved pending order");
        check(
            !scenarioNetwork.getCraftingManager().getTasks().isEmpty(),
            "save while crafting task is still in flight");
        Files.write(Paths.get("pending-craft-marker.txt"), new byte[] {1});
        Files.write(
            Paths.get("port-test-result.txt"),
            ("PASS: " + checks + " checks\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(Paths.get("restart-marker.txt"), new byte[] {1});
        server.halt(false);
        server = null;
      }
    } catch (Throwable failure) {
      RebornStorage.logger.error("PORT TEST FAILED", failure);
      try {
        Files.write(
            Paths.get("port-test-result.txt"),
            ("FAIL: " + failure + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      } catch (Exception ignored) {
      }
      server.halt(false);
      server = null;
    }
  }

  private static FakePlayer player(ServerWorld world) {
    return FakePlayerFactory.get(
        world,
        new GameProfile(
            UUID.nameUUIDFromBytes(
                ("RebornPortTest" + world.dimension().location())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "PortTest"));
  }

  private static void viewersAndSecurity(ServerWorld world, BlockEntityMultiCrafter tile) {
    FakePlayer first = player(world);
    FakePlayer second =
        FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "SecondViewer"));
    first.setPos(3, 83, 1);
    second.setPos(3, 83, 1);
    net.gigabit101.rebornstorage.containers.ContainerMultiCrafter a =
        new net.gigabit101.rebornstorage.containers.ContainerMultiCrafter(
            1, first.inventory, tile, 1);
    net.gigabit101.rebornstorage.containers.ContainerMultiCrafter b =
        new net.gigabit101.rebornstorage.containers.ContainerMultiCrafter(
            2, second.inventory, tile, 2);
    check(
        a.stillValid(first) && b.stillValid(second) && a.page == 1 && b.page == 2,
        "concurrent viewers keep independent pages");
    first.setPos(100, 80, 100);
    check(!a.stillValid(first), "crafter closes outside interaction range");
    first.setPos(3, 83, 1);
    com.refinedmods.refinedstorage.apiimpl.network.node.SecurityManagerNetworkNode security =
        ((com.refinedmods.refinedstorage.tile.SecurityManagerTile)
                world.getBlockEntity(new BlockPos(1, 80, -1)))
            .getNode();
    ItemStack deny = new ItemStack(com.refinedmods.refinedstorage.RSItems.SECURITY_CARD.get());
    com.refinedmods.refinedstorage.item.SecurityCardItem.setPermission(
        deny, com.refinedmods.refinedstorage.api.network.security.Permission.MODIFY, false);
    security.getCardsItems().setStackInSlot(0, deny);
    tile.getNode().getNetwork().getSecurityManager().invalidate();
    check(!a.stillValid(first), "crafter enforces RS security");
    ItemStack grid =
        new ItemStack(net.gigabit101.rebornstorage.init.ModItems.CREATIVE_WIRELESS_GRID.get());
    net.gigabit101.rebornstorage.items.ItemWirelessGrid item =
        (net.gigabit101.rebornstorage.items.ItemWirelessGrid) grid.getItem();
    for (net.gigabit101.rebornstorage.items.ItemWirelessGrid.MODE mode :
        net.gigabit101.rebornstorage.items.ItemWirelessGrid.MODE.values()) {
      item.setMode(grid, mode);
      check(
          !item.provide(
                  tile.getNode().getNetwork().getNetworkItemManager(),
                  first,
                  grid,
                  new com.refinedmods.refinedstorage.inventory.player.PlayerSlot(0))
              .onOpen(tile.getNode().getNetwork()),
          mode + " respects denied MODIFY permission");
    }
    security.getCardsItems().setStackInSlot(0, ItemStack.EMPTY);
    tile.getNode().getNetwork().getSecurityManager().invalidate();
  }

  private static void wireless(
      ServerWorld world, com.refinedmods.refinedstorage.api.network.INetwork network) {
    AdvancedWirelessTransmitterNode transmitter =
        ((BlockEntityAdvancedWirelessTransmitter)
                world.getBlockEntity(new BlockPos(1, 81, 0)))
            .getNode();
    check(
        transmitter.getNetwork() == network && transmitter.isActive(),
        "wireless transmitter connected");
    check(transmitter.getRange() == 1000 && transmitter.getEnergyUsage() == 100, "advanced transmitter defaults");
    ItemStack upgrade = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("refinedstorage:range_upgrade")));
    for (int slot = 0; slot < 4; slot++) transmitter.getUpgrades().insertItem(slot, upgrade.copy(), false);
    check(transmitter.getRange() == 3000, "all four range upgrades apply");
    for (int slot = 0; slot < 4; slot++) transmitter.getUpgrades().extractItem(slot, 1, false);
    for (String card : new String[] {"infinity_card", "dimension_card"}) {
      transmitter
          .getUpgrades()
          .setStackInSlot(
              0,
              new ItemStack(
                  ForgeRegistries.ITEMS.getValue(new ResourceLocation("rsinfinitybooster", card))));
      FakePlayer player =
          player(
              card.equals("dimension_card")
                  ? server.getLevel(net.minecraft.world.World.NETHER)
                  : world);
      check(
          player.level.dimension()
              == (card.equals("dimension_card")
                  ? net.minecraft.world.World.NETHER
                  : net.minecraft.world.World.OVERWORLD),
          card + " test player is in intended dimension");
      player.setPos(100000, 80, 100000);
      net.gigabit101.rebornstorage.items.ItemWirelessGrid grid =
          (net.gigabit101.rebornstorage.items.ItemWirelessGrid)
              net.gigabit101.rebornstorage.init.ModItems.WIRELESS_GRID.get();
      ItemStack stack = new ItemStack(grid);
      CompoundNBT nbt = stack.getOrCreateTag();
      nbt.putInt("NodeX", 1);
      nbt.putInt("NodeY", 80);
      nbt.putInt("NodeZ", 0);
      nbt.putString("Dimension", "minecraft:overworld");
      player.inventory.setItem(0, stack);
      net.minecraftforge.energy.IEnergyStorage energy =
          stack
              .getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY)
              .orElseThrow(() -> new AssertionError("energy capability"));
      energy.receiveEnergy(100000, false);
      for (net.gigabit101.rebornstorage.items.ItemWirelessGrid.MODE mode :
          net.gigabit101.rebornstorage.items.ItemWirelessGrid.MODE.values()) {
        grid.setMode(stack, mode);
        int before = energy.getEnergyStored();
        network
            .getNetworkItemManager()
            .open(player, stack, new com.refinedmods.refinedstorage.inventory.player.PlayerSlot(0));
        if (network.getNetworkItemManager().getItem(player) == null)
          RebornStorage.logger.error(
              "WIRELESS TEST DIAGNOSTIC runnable={} active={} range={} permission={} energy={}",
              network.canRun(),
              transmitter.isActive(),
              transmitter.getRange(),
              network
                  .getSecurityManager()
                  .hasPermission(
                      com.refinedmods.refinedstorage.api.network.security.Permission.MODIFY,
                      player),
              energy.getEnergyStored());
        check(
            network.getNetworkItemManager().getItem(player) != null,
            card + " opens " + mode + " from " + player.level.dimension().location());
        check(energy.getEnergyStored() < before, card + " " + mode + " consumes energy");
        network.getNetworkItemManager().close(player);
      }
      energy.extractEnergy(100000, false);
      grid.setMode(stack, net.gigabit101.rebornstorage.items.ItemWirelessGrid.MODE.CRAFTING);
      network
          .getNetworkItemManager()
          .open(player, stack, new com.refinedmods.refinedstorage.inventory.player.PlayerSlot(0));
      check(
          network.getNetworkItemManager().getItem(player) == null, card + " rejects empty battery");
    }
    transmitter.getUpgrades().setStackInSlot(0, upgrade);
  }

  @SuppressWarnings("unchecked")
  private static void testDiskChanges(IStorageDisk<ItemStack> disk) {
    ItemStack original = new ItemStack(Items.STICK);
    ItemStack tagged = original.copy();
    tagged.getOrCreateTag().putString("variant", "tagged");
    int[] callbacks = {0};
    disk.setSettings(
        () -> {
          callbacks[0]++;
          if (callbacks[0] == 1) {
            check(disk.getStored() == 2, "disk listener sees committed insert");
            disk.insert(tagged, 3, Action.PERFORM);
          }
        },
        () -> com.refinedmods.refinedstorage.api.storage.AccessType.INSERT_EXTRACT);
    disk.insert(original, 2, Action.PERFORM);
    check(disk.getStored() == 5 && callbacks[0] == 2, "reentrant insert updates both quantities");
    int rejected = Integer.MAX_VALUE - (disk.getCapacity() - 5);
    check(
        disk.insert(original, Integer.MAX_VALUE, Action.SIMULATE).getCount() == rejected
            && callbacks[0] == 2,
        "partial simulated insert does not overflow or notify");
    check(
        disk.insert(original, Integer.MAX_VALUE, Action.PERFORM).getCount() == rejected
            && callbacks[0] == 3,
        "partial real insert notifies once");
    check(
        disk.extract(tagged, 100, 1, Action.SIMULATE).getCount() == 3 && callbacks[0] == 3,
        "exact simulated extraction retains tagged variant");
    check(
        disk.extract(original, Integer.MAX_VALUE, 1, Action.PERFORM).getCount()
            == disk.getCapacity() - 3,
        "plain extraction preserves tagged items");
    check(
        disk.extract(tagged, 10, 1, Action.PERFORM).getCount() == 3
            && disk.getStored() == 0
            && callbacks[0] == 5,
        "final tagged extraction empties disk");
    disk.insert(original, disk.getCapacity() - 1, Action.PERFORM);
    disk.setSettings(
        () -> disk.getStacks().iterator().next().getOrCreateTag().putString("hook", "partial"),
        () -> com.refinedmods.refinedstorage.api.storage.AccessType.INSERT_EXTRACT);
    ItemStack remainder = disk.insert(original, 2, Action.PERFORM);
    check(
        remainder.getCount() == 1 && remainder.getTag().getString("hook").equals("partial"),
        "partial remainder observes the committed disk listener");
    disk.setSettings(
        () -> disk.getStacks().iterator().next().getOrCreateTag().putString("hook", "extract"),
        () -> com.refinedmods.refinedstorage.api.storage.AccessType.INSERT_EXTRACT);
    ItemStack taken = disk.extract(original, 1, 0, Action.PERFORM);
    check(
        taken.getTag().getString("hook").equals("extract"),
        "extracted stack observes the committed disk listener");
    disk.setSettings(
        null, () -> com.refinedmods.refinedstorage.api.storage.AccessType.INSERT_EXTRACT);
    disk.extract(original, Integer.MAX_VALUE, 0, Action.PERFORM);
    disk.setSettings(
        () -> {}, () -> com.refinedmods.refinedstorage.api.storage.AccessType.INSERT_EXTRACT);
  }

  private static void disks(ServerWorld world) {
    FakePlayer player =
        FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "PortTest"));
    StorageContentTest.recipes(world, player);
    for (DiskTier tier : DiskTier.values()) {
      if (tier.type == com.refinedmods.refinedstorage.api.storage.StorageType.FLUID) {
        StorageContentTest.fluid(world, player, tier);
        continue;
      }
      int k = tier.capacity / 1000;
      ItemRebornStorageCell item = (ItemRebornStorageCell) tier.disk().get();
      ItemStack stack = new ItemStack(item);
      stack.setHoverName(new net.minecraft.util.text.StringTextComponent("Keep my name"));
      item.inventoryTick(stack, world, player, 0, false);
      check(
          item.isValid(stack) && stack.hasCustomHoverName(),
          k + "k initializes named disk without losing NBT");
      IStorageDisk<ItemStack> disk = API.instance().getStorageDiskManager(world).getByStack(stack);
      disk.setSettings(
          () -> {}, () -> com.refinedmods.refinedstorage.api.storage.AccessType.INSERT_EXTRACT);
      if (k == 32768
          && net.gigabit101.rebornstorage.crafting.CraftingExperiment.mode()
              == net.gigabit101.rebornstorage.crafting.CraftingExperiment.Mode.TASK) {
        testDiskChanges(disk);
      }
      check(disk.getCapacity() == k * 1000, k + "k exact decimal capacity");
      ItemStack stone = new ItemStack(Items.COBBLESTONE);
      disk.insert(stone, 3, Action.PERFORM);
      int hugeRemainder = Integer.MAX_VALUE - disk.getCapacity() + 3;
      check(disk.insert(stone, Integer.MAX_VALUE, Action.SIMULATE).getCount() == hugeRemainder
              && disk.getStored() == 3
              && disk.insert(stone, Integer.MAX_VALUE, Action.PERFORM).getCount() == hugeRemainder
              && disk.extract(stone, Integer.MAX_VALUE, 0, Action.PERFORM).getCount() == disk.getCapacity(),
          k + "k maximum transfer keeps total and remainder exact");
      check(
          disk.insert(new ItemStack(Items.COBBLESTONE), k * 1000, Action.PERFORM).isEmpty(),
          k + "k fills to capacity");
      check(
          disk.insert(new ItemStack(Items.COBBLESTONE), 1, Action.PERFORM).getCount() == 1,
          k + "k rejects overflow");
      check(disk.getStored() == k * 1000, k + "k stored count stable");
      IStorageDisk<ItemStack> restored =
          net.gigabit101.rebornstorage.storage.RebornItemDiskFactory.INSTANCE.createFromNbt(
              world, disk.writeToNbt());
      check(
          restored.getStored() == k * 1000 && restored.getCapacity() == k * 1000,
          k + "k persisted contents and capacity");
      ItemStack recovered =
          net.gigabit101.rebornstorage.storage.RebornItemDiskFactory.INSTANCE.createDiskItem(
              restored, item.getId(stack));
      check(
          recovered.getItem() == item && item.getId(recovered).equals(item.getId(stack)),
          k + "k recovery preserves tier and UUID");
      player.setShiftKeyDown(true);
      player.setItemInHand(net.minecraft.util.Hand.MAIN_HAND, stack);
      check(
          item.use(world, player, net.minecraft.util.Hand.MAIN_HAND).getObject() == stack,
          k + "k full disk refuses disassembly");
      check(
          disk.extract(new ItemStack(Items.COBBLESTONE), k * 1000, 0, Action.PERFORM).getCount()
              == k * 1000,
          k + "k full extraction");
      check(disk.getStored() == 0, k + "k empties exactly");
      check(
          item.use(world, player, net.minecraft.util.Hand.MAIN_HAND).getObject().getItem()
              == com.refinedmods.refinedstorage.RSItems.STORAGE_HOUSING.get(),
          k + "k empty disk disassembles");
      player.setShiftKeyDown(false);
      check(player.inventory.contains(new ItemStack(tier.part().get())), k + "k recovers its own component");
    }
  }
}
