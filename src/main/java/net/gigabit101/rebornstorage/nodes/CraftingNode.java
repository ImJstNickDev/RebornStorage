package net.gigabit101.rebornstorage.nodes;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPatternProvider;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.INetworkNodeVisitor;
import com.refinedmods.refinedstorage.apiimpl.network.node.ConnectivityStateChangeCause;
import com.refinedmods.refinedstorage.apiimpl.network.node.NetworkNode;
import com.refinedmods.refinedstorage.tile.config.RedstoneMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.gigabit101.rebornstorage.Constants;
import net.gigabit101.rebornstorage.RebornStorageEventHandler;
import net.gigabit101.rebornstorage.init.ModBlocks;
import net.gigabit101.rebornstorage.multiblocks.MultiBlockCrafter;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

/**
 * One transient RS provider per assembled multiblock. Persistent inventories live in storage nodes.
 */
public final class CraftingNode extends NetworkNode
    implements ICraftingPatternContainer, INetworkNodeVisitor {
  public static int craftingSpeed = 5;
  private final MultiBlockCrafter crafter;
  private final UUID uuid;
  private final List<ICraftingPattern> patterns = new ArrayList<>();
  private IItemHandlerModifiable inventory = new CombinedInvWrapper();
  private int cpus, pages, energy;
  private boolean valid;
  private boolean patternsReady;
  private PatternStorageNode ownerStorage;

  public CraftingNode(World world, BlockPos pos, MultiBlockCrafter crafter, UUID uuid) {
    super(world, pos);
    this.crafter = crafter;
    this.uuid = uuid;
  }

  public void configure(int cpus, int energy, List<PatternStorageNode> storages) {
    this.cpus = cpus;
    this.energy = energy;
    this.pages = storages.size();
    this.ownerStorage = storages.isEmpty() ? null : storages.get(0);
    this.redstoneMode = ownerStorage == null ? RedstoneMode.IGNORE : ownerStorage.getRedstoneMode();
    this.inventory =
        new CombinedInvWrapper(
            storages.stream().map(s -> s.patterns).toArray(IItemHandlerModifiable[]::new));
    valid = true;
    RebornStorageEventHandler.patterns(this);
  }

  public void invalidate() {
    if (!valid) return;
    valid = false;
    patterns.clear();
    RebornStorageEventHandler.graph(network);
    RebornStorageEventHandler.patterns(this);
  }

  public void rebuildPatterns(String reason) {
    patterns.clear();
    if (isActive()) {
      for (int slot = 0; slot < inventory.getSlots(); slot++) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (!stack.isEmpty() && stack.getItem() instanceof ICraftingPatternProvider) {
          ICraftingPattern pattern =
              ((ICraftingPatternProvider) stack.getItem()).create(world, stack, this);
          if (pattern.isValid()) patterns.add(pattern);
        }
      }
    }
    patternsReady = isActive();
    RebornStorageEventHandler.queue(network);
  }

  public void deferPatterns() {
    patternsReady = false;
  }

  @Override
  public void visit(INetworkNodeVisitor.Operator operator) {
    if (valid) crafter.visitPorts(operator);
  }

  @Override
  public void onConnected(INetwork network) {
    super.onConnected(network);
    RebornStorageEventHandler.patterns(this);
  }

  @Override
  public void onDisconnected(INetwork network) {
    // A stale graph can release an old reference after a new connection was established.
    if (this.network == network) super.onDisconnected(network);
    RebornStorageEventHandler.queue(network);
  }

  @Override
  protected void onConnectedStateChange(
      INetwork network, boolean state, ConnectivityStateChangeCause cause) {
    RebornStorageEventHandler.queue(network);
    RebornStorageEventHandler.patterns(this);
  }

  @Override
  public void setRedstoneMode(RedstoneMode mode) {
    super.setRedstoneMode(mode);
    if (ownerStorage != null) ownerStorage.setRedstoneMode(mode);
    RebornStorageEventHandler.patterns(this);
  }

  @Override
  public void update() {
    if (redstoneMode != RedstoneMode.IGNORE) setRedstonePowered(world.hasNeighborSignal(pos));
    super.update();
  }

  @Override
  public boolean isActive() {
    return valid && super.isActive();
  }

  @Override
  public int getEnergyUsage() {
    return valid ? energy : 0;
  }

  @Override
  public ItemStack getItemStack() {
    return new ItemStack(ModBlocks.BLOCK_MULTI_STORAGE.get());
  }

  @Override
  public ResourceLocation getId() {
    return Constants.MULTI_BLOCK_ID;
  }

  @Override
  public int getUpdateInterval() {
    return Math.max(craftingSpeed - cpus, 1);
  }

  @Override
  public int getMaximumSuccessfulCraftingUpdates() {
    return patternsReady && isActive() ? Math.max(cpus, 1) : 0;
  }

  @Override
  public void setOwner(UUID owner) {
    if (ownerStorage != null) ownerStorage.setOwner(owner);
    else super.setOwner(owner);
  }

  @Override
  public UUID getOwner() {
    return ownerStorage == null ? super.getOwner() : ownerStorage.getOwner();
  }

  public int getCraftingCpus() {
    return valid ? cpus : 0;
  }

  public int getStorage() {
    return valid ? pages : 0;
  }

  @Override
  public List<ICraftingPattern> getPatterns() {
    return patterns;
  }

  @Override
  public IItemHandlerModifiable getPatternInventory() {
    return isActive() ? inventory : null;
  }

  @Override
  public ITextComponent getName() {
    return new StringTextComponent("MultiBlock Crafter");
  }

  @Override
  public BlockPos getPosition() {
    return pos;
  }

  @Override
  public UUID getUuid() {
    return uuid;
  }

  @Override
  public ICraftingPatternContainer getRootContainer() {
    return null;
  }

  @Override
  public IItemHandler getConnectedInventory() {
    return null;
  }

  @Override
  public IFluidHandler getConnectedFluidInventory() {
    return null;
  }

  @Override
  public TileEntity getConnectedTile() {
    return null;
  }

  @Override
  public TileEntity getFacingTile() {
    return null;
  }

  @Override
  public Direction getDirection() {
    return null;
  }

  @Override
  public void unlock() {}
}
