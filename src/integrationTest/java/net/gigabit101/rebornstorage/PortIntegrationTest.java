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
  private static final BlockPos STORAGE = new BlockPos(3, 81, 1);

  private static void check(boolean ok, String description) {
    if (!ok) throw new AssertionError(description);
    checks++;
    RebornStorage.logger.info("PORT TEST PASS: " + description);
  }

  @SubscribeEvent
  public static void started(FMLServerStartedEvent event) {
    server = event.getServer();
  }

  @SubscribeEvent
  public static void tick(TickEvent.ServerTickEvent event) {
    if (server == null || event.phase != TickEvent.Phase.END) return;
    try {
      tick++;
      ServerWorld world = server.overworld();
      if (tick == 1) {
        if (Files.exists(Paths.get("restart-marker.txt"))) {
          BlockEntityMultiCrafter restored =
              (BlockEntityMultiCrafter) world.getBlockEntity(STORAGE);
          check(
              restored != null
                  && restored.getNode().patterns.getStackInSlot(0).getItem()
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
        Block transmitter =
            ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("refinedstorage:wireless_transmitter"));
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
        wireless(world, tile.getNode().getNetwork());
        viewersAndSecurity(world, tile);
        UUID owner = UUID.randomUUID();
        tile.getNode().setOwner(owner);
        tile.getNode().patterns.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        CompoundNBT saved = tile.getNode().write(new CompoundNBT());
        net.gigabit101.rebornstorage.nodes.CraftingNode restored =
            new net.gigabit101.rebornstorage.nodes.CraftingNode(world, STORAGE);
        restored.read(saved);
        check(
            restored.patterns.getStackInSlot(0).getItem() == Items.DIAMOND,
            "node inventory NBT round trip");
        check(owner.equals(restored.getOwner()), "node owner NBT round trip");
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
            tile.getNode().patterns.getStackInSlot(0).getItem() == Items.DIAMOND,
            "frame break preserves patterns");
        ItemStack pattern = new ItemStack(com.refinedmods.refinedstorage.RSItems.PATTERN.get());
        com.refinedmods.refinedstorage.item.PatternItem.setToCurrentVersion(pattern);
        com.refinedmods.refinedstorage.item.PatternItem.setInputSlot(
            pattern, 0, new ItemStack(Items.OAK_LOG));
        com.refinedmods.refinedstorage.item.PatternItem.setOutputSlot(
            pattern, 0, new ItemStack(Items.OAK_PLANKS, 4));
        tile.getNode().patterns.setStackInSlot(0, pattern);
        tile.getNode().rebuildPatterns("integration test");
        check(tile.getNode().getPatterns().size() == 1, "encoded crafting pattern recognized");
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
    com.refinedmods.refinedstorage.apiimpl.network.node.WirelessTransmitterNetworkNode transmitter =
        ((com.refinedmods.refinedstorage.tile.WirelessTransmitterTile)
                world.getBlockEntity(new BlockPos(1, 81, 0)))
            .getNode();
    check(
        transmitter.getNetwork() == network && transmitter.isActive(),
        "wireless transmitter connected");
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
  }

  @SuppressWarnings("unchecked")
  private static void disks(ServerWorld world) {
    FakePlayer player =
        FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "PortTest"));
    for (int k : new int[] {32768, 65536, 131072, 262144}) {
      ItemRebornStorageCell item =
          (ItemRebornStorageCell)
              ForgeRegistries.ITEMS.getValue(new ResourceLocation("rebornstorage:disk_" + k + "k"));
      ItemStack stack = new ItemStack(item);
      stack.setHoverName(new net.minecraft.util.text.StringTextComponent("Keep my name"));
      item.inventoryTick(stack, world, player, 0, false);
      check(
          item.isValid(stack) && stack.hasCustomHoverName(),
          k + "k initializes named disk without losing NBT");
      IStorageDisk<ItemStack> disk = API.instance().getStorageDiskManager(world).getByStack(stack);
      disk.setSettings(
          () -> {}, () -> com.refinedmods.refinedstorage.api.storage.AccessType.INSERT_EXTRACT);
      check(disk.getCapacity() == k * 1000, k + "k exact decimal capacity");
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
      check(
          world
              .getRecipeManager()
              .byKey(new ResourceLocation("rebornstorage:disk_" + k + "k"))
              .isPresent(),
          k + "k disk recipe loaded");
      check(
          world
              .getRecipeManager()
              .byKey(new ResourceLocation("rebornstorage:parts/storagepart_" + k + "k"))
              .isPresent(),
          k + "k component recipe loaded");
    }
  }
}
