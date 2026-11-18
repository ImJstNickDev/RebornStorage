package net.gigabit101.rebornstorage;

import com.mojang.authlib.GameProfile;
import com.refinedmods.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorListener;
import com.refinedmods.refinedstorage.inventory.player.PlayerSlot;
import com.refinedmods.refinedstorage.network.craftingmonitor.CraftingMonitorUpdateMessage;
import com.refinedmods.refinedstorage.tile.craftingmonitor.WirelessCraftingMonitor;
import java.util.UUID;
import net.gigabit101.rebornstorage.containers.CoalescingCraftingMonitorContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Real RS encoder, without sockets/client rendering. Same workload for both modes. */
final class MonitorEncodingProbe {
  static long snapshots, bytes;

  static void install(String mode) {
    if (mode.equals("none")) return;
    if (!mode.equals("immediate") && !mode.equals("coalesced"))
      throw new IllegalArgumentException(mode);
    ItemStack stack = new ItemStack(Items.STICK);
    stack.getOrCreateTag().putString("Dimension", "minecraft:overworld");
    stack.getOrCreateTag().putInt("NodeX", PortBenchmark.CONTROLLER.getX());
    stack.getOrCreateTag().putInt("NodeY", PortBenchmark.CONTROLLER.getY());
    stack.getOrCreateTag().putInt("NodeZ", PortBenchmark.CONTROLLER.getZ());
    WirelessCraftingMonitor monitor =
        new WirelessCraftingMonitor(stack, PortBenchmark.server, new PlayerSlot(0));
    if (mode.equals("immediate")) {
      PortBenchmark.network
          .getCraftingManager()
          .addListener(
              new ICraftingMonitorListener() {
                public void onAttached() {
                  onChanged();
                }

                public void onChanged() {
                  encode(monitor);
                }
              });
    } else {
      net.minecraftforge.common.util.FakePlayer player =
          FakePlayerFactory.get(
              PortBenchmark.world, new GameProfile(UUID.randomUUID(), "MonitorProbe"));
      CoalescingCraftingMonitorContainer menu =
          new CoalescingCraftingMonitorContainer(monitor, player, 42) {
            @Override
            protected void sendSnapshot() {
              encode(monitor);
            }
          };
      player.containerMenu = menu;
      menu.broadcastChanges();
    }
  }

  static void encode(WirelessCraftingMonitor monitor) {
    PacketBuffer buffer = new PacketBuffer(io.netty.buffer.Unpooled.buffer());
    try {
      CraftingMonitorUpdateMessage.encode(new CraftingMonitorUpdateMessage(monitor), buffer);
      bytes += buffer.readableBytes();
      snapshots++;
    } finally {
      buffer.release();
    }
  }
}
