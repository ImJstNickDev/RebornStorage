package net.gigabit101.rebornstorage;

import com.mojang.authlib.GameProfile;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingManager;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.inventory.player.PlayerSlot;
import com.refinedmods.refinedstorage.tile.craftingmonitor.WirelessCraftingMonitor;
import java.util.*;
import net.gigabit101.rebornstorage.containers.CoalescingCraftingMonitorContainer;
import net.gigabit101.rebornstorage.nodes.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayerFactory;

final class LocalOptimizationTest {
  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
    RebornStorage.logger.info("LOCAL OPT PASS: " + message);
  }

  static void run(ServerWorld world, INetwork network, MinecraftServer server) {
    PlayerEntity player =
        FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "MonitorTest"));
    ICraftingManager[] manager = {network.getCraftingManager()};
    ItemStack linked = new ItemStack(Items.STICK);
    linked.getOrCreateTag().putString("Dimension", "minecraft:overworld");
    linked.getOrCreateTag().putInt("NodeX", network.getPosition().getX());
    linked.getOrCreateTag().putInt("NodeY", network.getPosition().getY());
    linked.getOrCreateTag().putInt("NodeZ", network.getPosition().getZ());
    WirelessCraftingMonitor monitor =
        new WirelessCraftingMonitor(linked, server, new PlayerSlot(0)) {
          @Override
          public ICraftingManager getCraftingManager() {
            return manager[0];
          }

          @Override
          public void onClosed(PlayerEntity viewer) {}

          @Override
          public Collection<com.refinedmods.refinedstorage.api.autocrafting.task.ICraftingTask>
              getTasks() {
            return manager[0] == null ? Collections.emptyList() : manager[0].getTasks();
          }
        };
    class Menu extends CoalescingCraftingMonitorContainer {
      int snapshots;

      Menu() {
        super(monitor, player, 77);
      }

      @Override
      protected void sendSnapshot() {
        net.minecraft.network.PacketBuffer buffer =
            new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());
        try {
          com.refinedmods.refinedstorage.network.craftingmonitor.CraftingMonitorUpdateMessage
              .encode(
                  new com.refinedmods.refinedstorage.network.craftingmonitor
                      .CraftingMonitorUpdateMessage(monitor),
                  buffer);
          if (com.refinedmods.refinedstorage.network.craftingmonitor.CraftingMonitorUpdateMessage
                  .decode(buffer)
                  .getTasks()
                  .size()
              != monitor.getTasks().size())
            throw new AssertionError("Monitor snapshot differs from current tasks");
          snapshots++;
        } finally {
          buffer.release();
        }
      }
    }
    Menu menu = new Menu();
    player.containerMenu = menu;
    menu.broadcastChanges();
    CoalescingCraftingMonitorContainer.flushPending();
    check(menu.snapshots == 1, "initial monitor snapshot");
    for (int i = 0; i < 100000; i++) manager[0].onTaskChanged();
    check(menu.snapshots == 1, "100000 callbacks do not encode intermediate snapshots");
    CoalescingCraftingMonitorContainer.flushPending();
    check(menu.snapshots == 2, "one snapshot for 100000 callbacks");
    CoalescingCraftingMonitorContainer.flushPending();
    check(menu.snapshots == 2, "no redundant snapshot when unchanged");
    manager[0] = null;
    menu.broadcastChanges();
    CoalescingCraftingMonitorContainer.flushPending();
    int offline = menu.snapshots;
    network.getCraftingManager().onTaskChanged();
    CoalescingCraftingMonitorContainer.flushPending();
    check(menu.snapshots == offline, "offline monitor detaches old manager");
    manager[0] = network.getCraftingManager();
    menu.broadcastChanges();
    CoalescingCraftingMonitorContainer.flushPending();
    check(menu.snapshots == offline + 1, "monitor reconnects and sends fresh state");
    manager[0].onTaskChanged();
    menu.removed(player);
    CoalescingCraftingMonitorContainer.flushPending();
    manager[0].onTaskChanged();
    CoalescingCraftingMonitorContainer.flushPending();
    check(menu.snapshots == offline + 1, "closed monitor drops pending and future notifications");
    player.containerMenu = player.inventoryMenu;

    // Validate the real inventory wrapper at boundaries across many pages, including mutation.
    List<PatternStorageNode> pages = new ArrayList<>();
    for (int i = 0; i < 1000; i++)
      pages.add(new PatternStorageNode(world, new BlockPos(1000 + i, 80, 0)));
    CraftingNode node = new CraftingNode(world, new BlockPos(1000, 80, 0), null, UUID.randomUUID());
    node.configure(1, 1, pages);
    check(node.getPatternInventory().getSlots() == 78000, "1000 pages expose 78000 slots");
    for (int slot : new int[] {0, 77, 78, 155, 77844, 77999}) {
      node.getPatternInventory().setStackInSlot(slot, new ItemStack(Items.DIAMOND));
      check(
          pages.get(slot / 78).patterns.getStackInSlot(slot % 78).getItem() == Items.DIAMOND,
          "constant-time page mapping at slot " + slot);
      check(
          node.getPatternInventory().extractItem(slot, 1, false).getItem() == Items.DIAMOND,
          "aggregate extraction at slot " + slot);
    }
    node.invalidate();
  }
}
