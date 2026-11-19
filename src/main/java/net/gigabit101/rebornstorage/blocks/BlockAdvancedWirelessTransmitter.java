package net.gigabit101.rebornstorage.blocks;

import com.refinedmods.refinedstorage.block.WirelessTransmitterBlock;
import com.refinedmods.refinedstorage.container.factory.PositionalTileContainerProvider;
import com.refinedmods.refinedstorage.util.NetworkUtils;
import net.gigabit101.rebornstorage.blockentities.BlockEntityAdvancedWirelessTransmitter;
import net.gigabit101.rebornstorage.containers.AdvancedWirelessTransmitterContainer;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

public class BlockAdvancedWirelessTransmitter extends WirelessTransmitterBlock {
  @Override
  public TileEntity createTileEntity(BlockState state, IBlockReader world) {
    return new BlockEntityAdvancedWirelessTransmitter();
  }

  @Override
  public ActionResultType use(
      BlockState state,
      World world,
      BlockPos pos,
      PlayerEntity player,
      Hand hand,
      BlockRayTraceResult hit) {
    if (world.isClientSide) return ActionResultType.SUCCESS;
    return NetworkUtils.attemptModify(
        world,
        pos,
        player,
        () ->
            NetworkHooks.openGui(
                (ServerPlayerEntity) player,
                new PositionalTileContainerProvider<BlockEntityAdvancedWirelessTransmitter>(
                    new TranslationTextComponent("gui.rebornstorage.advanced_wireless_transmitter"),
                    (tile, id, inventory, viewer) ->
                        new AdvancedWirelessTransmitterContainer(tile, viewer, id),
                    pos),
                pos));
  }
}
