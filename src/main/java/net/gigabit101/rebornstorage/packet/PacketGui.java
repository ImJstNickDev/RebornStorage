package net.gigabit101.rebornstorage.packet;

import java.util.function.Supplier;
import net.gigabit101.rebornstorage.blockentities.BlockEntityMultiCrafter;
import net.gigabit101.rebornstorage.containers.ContainerMultiCrafter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkHooks;

public class PacketGui {
  private final int page;
  private final BlockPos blockPos;

  public PacketGui(int page, BlockPos pos) {
    this.page = page;
    this.blockPos = pos;
  }

  public static void encode(PacketGui msg, PacketBuffer buf) {
    buf.writeInt(msg.page);
    buf.writeBlockPos(msg.blockPos);
  }

  public static PacketGui decode(PacketBuffer buf) {
    return new PacketGui(buf.readInt(), buf.readBlockPos());
  }

  public static class Handler {
    public static void handle(PacketGui msg, Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
          .enqueueWork(
              () -> {
                ServerPlayerEntity player = ctx.get().getSender();
                if (player == null || !(player.containerMenu instanceof ContainerMultiCrafter))
                  return;
                ContainerMultiCrafter menu = (ContainerMultiCrafter) player.containerMenu;
                if (!menu.blockPos.equals(msg.blockPos) || !menu.stillValid(player)) return;
                openGUI(player.level, player, msg.blockPos, msg.page);
              });
      ctx.get().setPacketHandled(true);
    }

    public static void openGUI(World world, PlayerEntity player, BlockPos pos) {
      openGUI(world, player, pos, 1);
    }

    public static void openGUI(World world, PlayerEntity player, BlockPos pos, int page) {
      if (!(player instanceof ServerPlayerEntity) || !world.hasChunkAt(pos)) return;
      TileEntity entity = world.getBlockEntity(pos);
      if (!(entity instanceof BlockEntityMultiCrafter)) return;
      BlockEntityMultiCrafter tile = (BlockEntityMultiCrafter) entity;
      if (!ContainerMultiCrafter.canAccess(player, tile)) return;
      int pages = tile.getMultiBlock().invs.size();
      if (page < 1 || page > pages) return;
      NetworkHooks.openGui(
          (ServerPlayerEntity) player,
          new SimpleNamedContainerProvider(
              (id, inv, p) -> new ContainerMultiCrafter(id, inv, tile, page),
              tile.getDisplayName()),
          buf -> {
            buf.writeBlockPos(pos);
            buf.writeVarInt(page);
            buf.writeVarInt(pages);
          });
    }
  }
}
