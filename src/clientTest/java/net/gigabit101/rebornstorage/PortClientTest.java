package net.gigabit101.rebornstorage;

import java.nio.file.Files;
import java.nio.file.Paths;
import net.gigabit101.rebornstorage.containers.ContainerMultiCrafter;
import net.gigabit101.rebornstorage.init.ModItems;
import net.gigabit101.rebornstorage.items.ItemWirelessGrid;
import net.gigabit101.rebornstorage.packet.PacketGui;
import net.gigabit101.rebornstorage.packet.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Hand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Visual and network smoke test in an isolated copy of the integration world. */
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class PortClientTest {
  private static boolean loading;
  private static int age;
  private static int totalTicks;
  private static long lastChangeNanos, maxGapNanos;
  private static int previousRemaining, firstProgressTick, lastProgressTick, changes, longestGap;

  private static int remainingPlanks(
      com.refinedmods.refinedstorage.screen.CraftingMonitorScreen screen) {
    for (com.refinedmods.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement
        element : screen.getElements()) {
      if (!(element
          instanceof
          com.refinedmods.refinedstorage.apiimpl.autocrafting.craftingmonitor
              .ItemCraftingMonitorElement)) continue;
      net.minecraft.network.PacketBuffer buffer =
          new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());
      try {
        element.write(buffer);
        ItemStack item = com.refinedmods.refinedstorage.util.StackUtils.readItemStack(buffer);
        if (item.getItem() == net.minecraft.item.Items.OAK_PLANKS)
          return buffer.getInt(buffer.writerIndex() - Integer.BYTES);
      } finally {
        buffer.release();
      }
    }
    return 0;
  }

  private static void check(boolean ok, String label) {
    if (!ok) throw new AssertionError(label);
    RebornStorage.logger.info("PORT CLIENT PASS: " + label);
  }

  private static void screenshot(Minecraft mc, String name) {
    ScreenShotHelper.grab(
        mc.gameDirectory,
        name + ".png",
        mc.getWindow().getWidth(),
        mc.getWindow().getHeight(),
        mc.getMainRenderTarget(),
        m -> RebornStorage.logger.info(m.getString()));
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    Minecraft mc = Minecraft.getInstance();
    try {
      if (++totalTicks > 2400)
        throw new AssertionError("Client smoke test timed out: " + mc.screen);
      if (!loading && mc.screen instanceof MainMenuScreen) {
        loading = true;
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance = 3;
        mc.loadLevel("PortTest");
      }
      if (mc.player == null || mc.getSingleplayerServer() == null) return;
      age++;
      if (age == 20) {
        mc.getSingleplayerServer()
            .execute(
                () -> {
                  ServerPlayerEntity p =
                      mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                  p.setGameMode(net.minecraft.world.GameType.CREATIVE);
                  p.teleportTo(1, 84, 0);
                  ItemStack stack = new ItemStack(ModItems.CREATIVE_WIRELESS_GRID.get());
                  CompoundNBT tag = stack.getOrCreateTag();
                  tag.putInt("NodeX", 1);
                  tag.putInt("NodeY", 80);
                  tag.putInt("NodeZ", 0);
                  tag.putString("Dimension", "minecraft:overworld");
                  p.inventory.setItem(0, stack);
                  int slot = 1;
                  for (net.minecraftforge.fml.RegistryObject<net.minecraft.item.Item> item :
                      ModItems.ITEMS.getEntries())
                    if (slot < 36) p.inventory.setItem(slot++, new ItemStack(item.get()));
                });
      }
      if (age == 60 || age == 120 || age == 180)
        mc.gameMode.useItem(mc.player, mc.level, Hand.MAIN_HAND);
      if (age == 80 || age == 140 || age == 200) {
        String expected = age == 200 ? "CraftingMonitorScreen" : "GridScreen";
        check(
            mc.screen != null && mc.screen.getClass().getSimpleName().equals(expected),
            "wireless mode "
                + (age == 80 ? "CRAFTING" : age == 140 ? "FLUID" : "MONITOR")
                + " client screen");
        screenshot(
            mc,
            age == 80 ? "wireless-crafting" : age == 140 ? "wireless-fluid" : "wireless-monitor");
      }
      if (age == 100 || age == 160) {
        mc.player.closeContainer();
        final ItemWirelessGrid.MODE mode =
            age == 100 ? ItemWirelessGrid.MODE.FLUID : ItemWirelessGrid.MODE.MONITOR;
        mc.getSingleplayerServer()
            .execute(
                () -> {
                  ServerPlayerEntity p =
                      mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                  ((ItemWirelessGrid) p.getMainHandItem().getItem())
                      .setMode(p.getMainHandItem(), mode);
                });
      }
      if (age == 182) {
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        server.execute(
            () -> {
              net.gigabit101.rebornstorage.blockentities.BlockEntityMultiCrafter tile =
                  (net.gigabit101.rebornstorage.blockentities.BlockEntityMultiCrafter)
                      server.overworld().getBlockEntity(new BlockPos(3, 81, 1));
              com.refinedmods.refinedstorage.api.network.INetwork network =
                  tile.getNode().getNetwork();
              network.insertItem(
                  new ItemStack(net.minecraft.item.Items.OAK_LOG),
                  3000,
                  com.refinedmods.refinedstorage.api.util.Action.PERFORM);
              network
                  .getCraftingManager()
                  .request(null, new ItemStack(net.minecraft.item.Items.OAK_PLANKS), 12000);
            });
      }
      if (age >= 185
          && age < 220
          && mc.screen instanceof com.refinedmods.refinedstorage.screen.CraftingMonitorScreen) {
        int remaining =
            remainingPlanks(
                (com.refinedmods.refinedstorage.screen.CraftingMonitorScreen) mc.screen);
        if (remaining > 0 && remaining != previousRemaining) {
          long now = System.nanoTime();
          if (changes == 0) firstProgressTick = age;
          else {
            check(remaining < previousRemaining, "visible craft count decreases");
            maxGapNanos = Math.max(maxGapNanos, now - lastChangeNanos);
            longestGap = Math.max(longestGap, age - lastProgressTick);
          }
          lastChangeNanos = now;
          lastProgressTick = age;
          previousRemaining = remaining;
          changes++;
        }
      }
      if (age == 220) {
        check(
            changes >= 12
                && firstProgressTick <= 200
                && age - lastProgressTick < 10
                && longestGap < 10,
            "active monitor: "
                + changes
                + " distinct counts; longest observed gap "
                + longestGap
                + " client ticks, "
                + (maxGapNanos / 1000000)
                + " ms");
        mc.player.closeContainer();
        mc.getSingleplayerServer()
            .execute(
                () -> {
                  ServerPlayerEntity p =
                      mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                  PacketGui.Handler.openGUI(p.level, p, new BlockPos(2, 80, 0));
                });
      }
      if (age == 240) {
        check(mc.player.containerMenu instanceof ContainerMultiCrafter, "multiblock client menu");
        check(((ContainerMultiCrafter) mc.player.containerMenu).page == 1, "first storage page");
        screenshot(mc, "multiblock-page-1");
      }
      if (age == 260) PacketHandler.sendToServer(new PacketGui(2, new BlockPos(2, 80, 0)));
      if (age == 280) {
        check(
            mc.player.containerMenu instanceof ContainerMultiCrafter
                && ((ContainerMultiCrafter) mc.player.containerMenu).page == 2,
            "page packet synchronizes second page");
        screenshot(mc, "multiblock-page-2");
      }
      if (age == 290) {
        mc.player.closeContainer();
        mc.getSingleplayerServer().execute(() -> {
          ServerPlayerEntity player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
          BlockPos pos = new BlockPos(1, 81, 0);
          net.minecraft.block.BlockState state = player.level.getBlockState(pos);
          state.use(player.level, player, Hand.MAIN_HAND, new net.minecraft.util.math.BlockRayTraceResult(
              net.minecraft.util.math.vector.Vector3d.atCenterOf(pos), net.minecraft.util.Direction.UP, pos, false));
        });
      }
      if (age == 305) {
        check(mc.screen instanceof net.gigabit101.rebornstorage.client.screens.AdvancedWirelessTransmitterScreen,
            "advanced transmitter screen opens");
        check(net.gigabit101.rebornstorage.blockentities.BlockEntityAdvancedWirelessTransmitter.RANGE.getValue() == 1500,
            "saved range synchronized to client");
        screenshot(mc, "advanced-transmitter");
        mc.getSingleplayerServer().execute(() -> {
          net.gigabit101.rebornstorage.blockentities.BlockEntityAdvancedWirelessTransmitter tile =
              (net.gigabit101.rebornstorage.blockentities.BlockEntityAdvancedWirelessTransmitter)
                  mc.getSingleplayerServer().overworld().getBlockEntity(new BlockPos(1,81,0));
          tile.getNode().getUpgrades().extractItem(0, 1, false);
        });
      }
      if (age == 320) {
        check(net.gigabit101.rebornstorage.blockentities.BlockEntityAdvancedWirelessTransmitter.RANGE.getValue() == 1000,
            "open transmitter screen updates range after upgrade removal");
        for (net.minecraftforge.fml.RegistryObject<net.minecraft.item.Item> entry : ModItems.ITEMS.getEntries()) {
          check(mc.getItemRenderer().getModel(new ItemStack(entry.get()), mc.level, mc.player)
              != mc.getModelManager().getMissingModel(), "baked model for " + entry.getId());
        }
        Files.write(
            Paths.get("port-client-result.txt"),
            "PASS: wireless screens, crafter pages, advanced transmitter range and item models\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mc.stop();
      }
    } catch (Throwable failure) {
      RebornStorage.logger.error("PORT CLIENT FAILED", failure);
      try {
        Files.write(
            Paths.get("port-client-result.txt"),
            ("FAIL: " + failure + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      } catch (Exception ignored) {
      }
      mc.stop();
    }
  }
}
