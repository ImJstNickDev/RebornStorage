package net.gigabit101.rebornstorage.blockentities;

import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.network.node.INetworkNodeManager;
import com.refinedmods.refinedstorage.api.network.node.INetworkNodeProxy;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.capability.NetworkNodeProxyCapability;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.gigabit101.rebornstorage.containers.ContainerMultiCrafter;
import net.gigabit101.rebornstorage.core.multiblock.MultiblockControllerBase;
import net.gigabit101.rebornstorage.core.multiblock.MultiblockValidationException;
import net.gigabit101.rebornstorage.core.multiblock.rectangular.RectangularMultiblockTileEntityBase;
import net.gigabit101.rebornstorage.init.ModBlocks;
import net.gigabit101.rebornstorage.multiblocks.MultiBlockCrafter;
import net.gigabit101.rebornstorage.nodes.CraftingNode;
import net.gigabit101.rebornstorage.nodes.PatternStorageNode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public class BlockEntityMultiCrafter extends RectangularMultiblockTileEntityBase
    implements INamedContainerProvider, INetworkNodeProxy<CraftingNode> {
  @Override
  public void isGoodForFrame() throws MultiblockValidationException {
    if (level == null) return;
    if (getBlockPos() == null) return;
    if (level.getBlockState(getBlockPos()) == null) return;

    Block block = level.getBlockState(getBlockPos()).getBlock();
    if (block != ModBlocks.BLOCK_MULTI_FRAME.get()) {
      throw new MultiblockValidationException(
          block.getDescriptionId() + " is not valid for the frame of the block" + getBlockPos());
    }
  }

  @Override
  public void isGoodForSides() throws MultiblockValidationException {
    if (level == null) return;
    if (getBlockPos() == null) return;
    if (level.getBlockState(getBlockPos()) == null) return;

    Block block = level.getBlockState(getBlockPos()).getBlock();
    if (block != ModBlocks.BLOCK_MULTI_HEAT.get()) {
      throw new MultiblockValidationException(
          block.getDescriptionId() + " is not valid for the sides of the block" + getBlockPos());
    }
  }

  @Override
  public void isGoodForTop() throws MultiblockValidationException {
    if (level == null) return;
    if (getBlockPos() == null) return;
    if (level.getBlockState(getBlockPos()) == null) return;

    Block block = level.getBlockState(getBlockPos()).getBlock();
    if (block != ModBlocks.BLOCK_MULTI_HEAT.get()) {
      throw new MultiblockValidationException(
          block.getDescriptionId() + " is not valid for the top of the block" + getBlockPos());
    }
  }

  @Override
  public void isGoodForBottom() throws MultiblockValidationException {
    if (level == null) return;
    if (getBlockPos() == null) return;
    if (level.getBlockState(getBlockPos()) == null) return;

    Block block = level.getBlockState(getBlockPos()).getBlock();

    if (block != ModBlocks.BLOCK_MULTI_HEAT.get()) {
      throw new MultiblockValidationException(
          block.getDescriptionId()
              + " is not valid for the bottom of the block, BlockPos:"
              + getBlockPos());
    }
  }

  @Override
  public void isGoodForInterior() throws MultiblockValidationException {
    if (level == null) return;
    if (getBlockPos() == null) return;
    if (level.getBlockState(getBlockPos()) == null) return;

    Block block = level.getBlockState(getBlockPos()).getBlock();

    if (block != ModBlocks.BLOCK_MULTI_CPU.get() && block != ModBlocks.BLOCK_MULTI_STORAGE.get()) {
      throw new MultiblockValidationException(
          block.getDescriptionId() + " is not valid for the inside of the block" + getBlockPos());
    }
  }

  @Override
  public void onMachineActivated() {
    if (getMultiBlock() != null) getMultiBlock().markPatternsDirty();
  }

  @Override
  public void onMachineDeactivated() {
    if (getMultiBlock() != null) getMultiBlock().markPatternsDirty();
  }

  @Override
  public MultiblockControllerBase createNewMultiblock() {
    return new MultiBlockCrafter(getLevel());
  }

  @Override
  public Class<? extends MultiblockControllerBase> getMultiblockControllerType() {
    return MultiBlockCrafter.class;
  }

  public MultiBlockCrafter getMultiBlock() {
    return (MultiBlockCrafter) getMultiblockController();
  }

  public Optional<Integer> page = Optional.empty();

  public BlockEntityMultiCrafter() {
    super(ModBlocks.CRAFTER_TILE.get());
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (!level.isClientSide && getBlockState().getBlock() != ModBlocks.BLOCK_MULTI_STORAGE.get()) {
      INetworkNodeManager manager = API.instance().getNetworkNodeManager((ServerWorld) level);
      INetworkNode old = manager.getNode(worldPosition);
      // Remove obsolete empty structural records from the first port, preserving any unexpected
      // items.
      if (old instanceof PatternStorageNode && ((PatternStorageNode) old).isEmpty()) {
        manager.removeNode(worldPosition);
        manager.markForSaving();
      }
    }
  }

  @Override
  public void load(BlockState state, CompoundNBT data) {
    super.load(state, data);

    if (data.get("page") != null) {
      page = Optional.of(data.getInt("page"));
    }
  }

  @Override
  public CompoundNBT save(CompoundNBT data) {
    super.save(data);
    if (page.isPresent()) {
      data.putInt("page", page.get());
    }
    return data;
  }

  @Override
  public CompoundNBT getUpdateTag() {
    CompoundNBT tag = super.getUpdateTag();
    save(tag);
    return tag;
  }

  // All assembled parts expose the same graph node. Only storage blocks own persistent inventories.
  @Override
  public CraftingNode getNode() {
    MultiBlockCrafter crafter = getMultiBlock();
    return crafter == null ? null : crafter.getNode();
  }

  private LazyOptional<INetworkNodeProxy<CraftingNode>> networkProxy = createNetworkProxy();

  private LazyOptional<INetworkNodeProxy<CraftingNode>> createNetworkProxy() {
    return LazyOptional.of(
        () ->
            () -> {
              MultiBlockCrafter crafter = getMultiBlock();
              return crafter != null && crafter.hasNetworkNode() ? crafter.getNode() : null;
            });
  }

  public PatternStorageNode getStorageNode() {
    if (level.isClientSide) throw new IllegalStateException("Storage inventories are server-side");
    INetworkNodeManager manager = API.instance().getNetworkNodeManager((ServerWorld) level);
    INetworkNode existing = manager.getNode(worldPosition);
    if (existing instanceof PatternStorageNode) return (PatternStorageNode) existing;
    if (getBlockState().getBlock() != ModBlocks.BLOCK_MULTI_STORAGE.get()) return null;
    PatternStorageNode storage = new PatternStorageNode(level, worldPosition);
    manager.setNode(worldPosition, storage);
    manager.markForSaving();
    return storage;
  }

  @Override
  public void invalidateCaps() {
    super.invalidateCaps();
    networkProxy.invalidate();
  }

  @Override
  public void reviveCaps() {
    super.reviveCaps();
    networkProxy = createNetworkProxy();
  }

  @Nonnull
  @Override
  public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability) {
    if (capability == NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY) {
      return networkProxy.cast();
    }
    return super.getCapability(capability);
  }

  @Nonnull
  @Override
  public <T> LazyOptional<T> getCapability(
      @Nonnull Capability<T> capability, @Nullable Direction side) {
    if (capability == NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY) {
      return networkProxy.cast();
    }
    return super.getCapability(capability, side);
  }

  @javax.annotation.Nullable
  @Override
  public Container createMenu(int id, PlayerInventory playerInventory, PlayerEntity player) {
    return new ContainerMultiCrafter(id, playerInventory, this);
  }

  @Override
  public ITextComponent getDisplayName() {
    return new StringTextComponent("MultiBlock Crafter");
  }
}
