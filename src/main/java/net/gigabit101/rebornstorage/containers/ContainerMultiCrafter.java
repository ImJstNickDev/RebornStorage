package net.gigabit101.rebornstorage.containers;

import com.refinedmods.refinedstorage.api.network.security.Permission;
import net.gigabit101.rebornstorage.blockentities.BlockEntityMultiCrafter;
import net.gigabit101.rebornstorage.client.SlotFiltered;
import net.gigabit101.rebornstorage.init.ModContainers;
import net.gigabit101.rebornstorage.multiblocks.MultiBlockCrafter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Each viewer owns its page; client slots are populated by vanilla container sync. */
public class ContainerMultiCrafter extends ContainerBase {
  public final BlockPos blockPos;
  public final int page;
  public final int pageCount;
  private final BlockEntityMultiCrafter tile;
  private final IItemHandler boundInventory;

  public ContainerMultiCrafter(int id, PlayerInventory inventory, PacketBuffer data) {
    this(
        id,
        inventory,
        data.readBlockPos(),
        data.readVarInt(),
        data.readVarInt(),
        null,
        new ItemStackHandler(78));
  }

  public ContainerMultiCrafter(int id, PlayerInventory inventory, BlockEntityMultiCrafter tile) {
    this(id, inventory, tile, 1);
  }

  public ContainerMultiCrafter(
      int id, PlayerInventory inventory, BlockEntityMultiCrafter tile, int page) {
    this(
        id,
        inventory,
        tile.getBlockPos(),
        page,
        tile.getMultiBlock().invs.size(),
        tile,
        tile.getMultiBlock().getInvForPage(page));
  }

  private ContainerMultiCrafter(
      int id,
      PlayerInventory inventory,
      BlockPos pos,
      int page,
      int pages,
      BlockEntityMultiCrafter tile,
      IItemHandler handler) {
    super(ModContainers.MULTI_CRAFTER_CONTAINER.get(), id);
    this.blockPos = pos;
    this.page = page;
    this.pageCount = pages;
    this.tile = tile;
    this.boundInventory = handler;
    if (handler == null || handler.getSlots() != 78)
      throw new IllegalArgumentException("Invalid crafter page");
    for (int row = 0; row < 6; row++)
      for (int col = 0; col < 13; col++)
        addSlot(new SlotFiltered(handler, row * 13 + col, 9 + col * 18, 21 + row * 18));
    drawPlayersInv(inventory, 45, 141);
    drawPlayersHotBar(inventory, 45, 199);
  }

  public static boolean canAccess(PlayerEntity player, BlockEntityMultiCrafter tile) {
    if (tile == null
        || tile.isRemoved()
        || player.distanceToSqr(
                tile.getBlockPos().getX() + .5,
                tile.getBlockPos().getY() + .5,
                tile.getBlockPos().getZ() + .5)
            > 64) return false;
    MultiBlockCrafter crafter = tile.getMultiBlock();
    return crafter != null
        && crafter.isAssembled()
        && !crafter.invs.isEmpty()
        && tile.getNode().getNetwork() != null
        && tile.getNode()
            .getNetwork()
            .getSecurityManager()
            .hasPermission(Permission.MODIFY, player);
  }

  @Override
  public boolean stillValid(PlayerEntity player) {
    return player.level.isClientSide
        || (canAccess(player, tile) && tile.getMultiBlock().getInvForPage(page) == boundInventory);
  }
}
