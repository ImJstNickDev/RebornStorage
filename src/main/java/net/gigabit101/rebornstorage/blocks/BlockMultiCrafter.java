package net.gigabit101.rebornstorage.blocks;

import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.network.node.INetworkNodeManager;
import com.refinedmods.refinedstorage.apiimpl.API;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.gigabit101.rebornstorage.blockentities.BlockEntityMultiCrafter;
import net.gigabit101.rebornstorage.packet.PacketGui;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ContainerBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public class BlockMultiCrafter extends ContainerBlock {
  // TODO Disabled by Rid

  //    public static final BooleanProperty UP_DOWN_CONNECTION = BooleanProperty.create("up_down");
  //    public static final BooleanProperty LEFT_RIGHT_CONNECTION =
  // BooleanProperty.create("left_right");

  public BlockMultiCrafter() {
    super(Properties.of(Material.METAL).strength(2.0F));
    // TODO Disabled by Rid

    //        this.registerDefaultState(getStateDefinition().any().setValue(UP_DOWN_CONNECTION,
    // false).setValue(LEFT_RIGHT_CONNECTION, false));
  }

  @Override
  public @Nonnull BlockRenderType getRenderShape(BlockState state) {
    return BlockRenderType.MODEL;
  }

  @Nullable
  @Override
  public BlockState getStateForPlacement(@Nonnull BlockItemUseContext blockPlaceContext) {
    World level = blockPlaceContext.getLevel();
    BlockPos pos = blockPlaceContext.getClickedPos();

    // TODO Disabled by Rid

    //        if(level.getBlockState(pos.below()).getBlock() == ModBlocks.BLOCK_MULTI_FRAME.get() &&
    // level.getBlockState(pos.above()).getBlock() == ModBlocks.BLOCK_MULTI_FRAME.get())
    //        {
    //            return super.getStateForPlacement(blockPlaceContext).setValue(UP_DOWN_CONNECTION,
    // true);
    //        }
    //        if(level.getBlockState(pos.north()).getBlock() == ModBlocks.BLOCK_MULTI_FRAME.get() &&
    // level.getBlockState(pos.south()).getBlock() == ModBlocks.BLOCK_MULTI_FRAME.get())
    //        {
    //            return
    // super.getStateForPlacement(blockPlaceContext).setValue(LEFT_RIGHT_CONNECTION, true);
    //        }
    //        if(level.getBlockState(pos.east()).getBlock() == ModBlocks.BLOCK_MULTI_FRAME.get() &&
    // level.getBlockState(pos.west()).getBlock() == ModBlocks.BLOCK_MULTI_FRAME.get())
    //        {
    //            return
    // super.getStateForPlacement(blockPlaceContext).setValue(LEFT_RIGHT_CONNECTION, true);
    //        }

    // TODO
    return defaultBlockState();
  }

  // TODO Disabled by Rid

  //    @Override
  //    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder)
  //    {
  //        builder.add(UP_DOWN_CONNECTION, LEFT_RIGHT_CONNECTION);
  //    }

  @javax.annotation.Nullable
  @Override
  public TileEntity newBlockEntity(net.minecraft.world.IBlockReader world) {
    return new BlockEntityMultiCrafter();
  }

  @Override
  public ActionResultType use(
      BlockState blockState,
      World level,
      BlockPos blockPos,
      PlayerEntity player,
      Hand hand,
      BlockRayTraceResult blockHitResult) {
    if (level.getBlockEntity(blockPos) == null) return ActionResultType.FAIL;
    BlockEntityMultiCrafter tile = (BlockEntityMultiCrafter) level.getBlockEntity(blockPos);
    if (tile == null) return ActionResultType.FAIL;

    //        System.out.println("isAssembled " + tile.getMultiBlock().isAssembled() +
    // (level.isClientSide ? " Client" : " Sever"));
    //        tile.load(tile.getUpdateTag());

    if (tile.getMultiblockController() != null) {
      if (!tile.getMultiblockController().isAssembled()) {
        if (tile.getMultiblockController().getLastValidationException() != null) {
          if (player.getItemInHand(hand).isEmpty()) {
            if (level.isClientSide) {
              player.sendMessage(
                  new StringTextComponent(
                      tile.getMultiblockController().getLastValidationException().getMessage()),
                  Util.NIL_UUID);
            }

            return ActionResultType.SUCCESS;
          }
        }
      } else {
        if (!level.isClientSide) PacketGui.Handler.openGUI(level, player, blockPos);
        return ActionResultType.SUCCESS;
      }
      return ActionResultType.SUCCESS;
    } else {
      return super.use(blockState, level, blockPos, player, hand, blockHitResult);
    }
  }

  @Override
  public void setPlacedBy(
      @Nonnull World level,
      @Nonnull BlockPos blockPos,
      @Nonnull BlockState blockState,
      @javax.annotation.Nullable LivingEntity livingEntity,
      @Nonnull ItemStack itemStack) {
    super.setPlacedBy(level, blockPos, blockState, livingEntity, itemStack);
    if (!level.isClientSide) {
      API.instance().getNetworkNodeManager((ServerWorld) level).getNode(blockPos);
    }
  }

  @Override
  public void onRemove(
      @Nonnull BlockState blockState,
      World level,
      @Nonnull BlockPos blockPos,
      @Nonnull BlockState blockState2,
      boolean p_60519_) {
    if (blockState.getBlock() == blockState2.getBlock()) return;
    if (level.isClientSide) {
      super.onRemove(blockState, level, blockPos, blockState2, p_60519_);
      return;
    }
    TileEntity blockEntity = level.getBlockEntity(blockPos);
    if (blockEntity instanceof BlockEntityMultiCrafter) {
      BlockEntityMultiCrafter blockEntityMultiCrafter = (BlockEntityMultiCrafter) blockEntity;
      net.gigabit101.rebornstorage.nodes.PatternStorageNode storage =
          blockEntityMultiCrafter.getStorageNode();
      if (storage != null) {
        for (int i = 0; i < storage.patterns.getSlots(); i++) {
          ItemStack stack = storage.patterns.getStackInSlot(i);
          if (!stack.isEmpty()) {
            InventoryHelper.dropItemStack(
                level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), stack);
          }
        }
      }
      if (blockEntityMultiCrafter.getMultiBlock() != null) {
        blockEntityMultiCrafter.getMultiBlock().detachBlock(blockEntityMultiCrafter, false);
      }
    }

    INetworkNodeManager manager = API.instance().getNetworkNodeManager((ServerWorld) level);
    INetworkNode node = manager.getNode(blockPos);
    if (node != null) {
      manager.removeNode(blockPos);
      manager.markForSaving();
    }

    level.removeBlockEntity(blockPos);

    super.onRemove(blockState, level, blockPos, blockState2, p_60519_);
  }
}
