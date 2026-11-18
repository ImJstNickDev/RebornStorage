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
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
  private IItemHandlerModifiable inventory = new PatternInventory();
  private final List<PatternStorageNode> storagePages = new ArrayList<>();
  private final Map<PatternStorageNode, Integer> pageIndices = new IdentityHashMap<>();
  private final Map<Integer, ICraftingPattern> cachedPatterns = new TreeMap<>();
  private final Map<Integer, ItemStack> observedStacks = new java.util.HashMap<>();
  private final BitSet dirtySlots = new BitSet(), dynamicSlots = new BitSet();
  private boolean fullRefresh = true;

  /** Storage pages have a fixed size: preserve the RS inventory API with constant-time lookup. */
  private static final class PatternInventory extends CombinedInvWrapper {
    PatternInventory(IItemHandlerModifiable... handlers) {
      super(handlers);
      for (IItemHandlerModifiable handler : handlers)
        if (handler.getSlots() != 78) throw new IllegalArgumentException("Invalid storage page");
    }

    @Override
    protected int getIndexForSlot(int slot) {
      return slot < 0 || slot >= getSlots() ? -1 : slot / 78;
    }
  }

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
    storagePages.clear();
    storagePages.addAll(storages);
    pageIndices.clear();
    for (int i = 0; i < storages.size(); i++) pageIndices.put(storages.get(i), i);
    this.inventory =
        new PatternInventory(
            storages.stream().map(s -> s.patterns).toArray(IItemHandlerModifiable[]::new));
    valid = true;
    refreshAllPatterns();
  }

  public void invalidate() {
    if (!valid) return;
    valid = false;
    patterns.clear();
    RebornStorageEventHandler.graph(network);
    refreshAllPatterns();
  }

  public void setEnergyCost(int energy) {
    this.energy = energy;
  }

  public void refreshAllPatterns() {
    fullRefresh = true;
    RebornStorageEventHandler.patterns(this);
  }

  public void patternChanged(PatternStorageNode storage, int slot) {
    Integer page = pageIndices.get(storage);
    if (page == null) {
      refreshAllPatterns();
      return;
    }
    dirtySlots.set(page * 78 + slot);
    RebornStorageEventHandler.patterns(this);
  }

  /** Explicit rebuild remains a full refresh for callers and reloads. */
  public void rebuildPatterns(String reason) {
    fullRefresh = true;
    applyPatternChanges();
  }

  public void applyPatternChanges() {
    patterns.clear();
    if (isActive()) {
      if (fullRefresh) {
        cachedPatterns.clear();
        observedStacks.clear();
        dynamicSlots.clear();
        dirtySlots.clear();
        dirtySlots.set(0, inventory.getSlots());
      } else {
        dirtySlots.or(dynamicSlots); // Custom providers may depend on external state.
        // Preserve detection of in-place stack/NBT edits when another inventory change causes
        // a refresh. No idle polling; direct page access keeps this comparison linear.
        for (int page = 0; page < storagePages.size(); page++)
          for (int local = 0; local < 78; local++) {
            int index = page * 78 + local;
            if (!ItemStack.matches(
                observedStacks.getOrDefault(index, ItemStack.EMPTY),
                storagePages.get(page).patterns.getStackInSlot(local))) dirtySlots.set(index);
          }
      }
      for (int slot = dirtySlots.nextSetBit(0); slot >= 0; slot = dirtySlots.nextSetBit(slot + 1)) {
        ItemStack stack = storagePages.get(slot / 78).patterns.getStackInSlot(slot % 78);
        cachedPatterns.remove(slot);
        if (stack.isEmpty()) observedStacks.remove(slot);
        else observedStacks.put(slot, stack.copy());
        dynamicSlots.clear(slot);
        if (!stack.isEmpty() && stack.getItem() instanceof ICraftingPatternProvider) {
          if (stack.getItem() != com.refinedmods.refinedstorage.RSItems.PATTERN.get())
            dynamicSlots.set(slot);
          ICraftingPattern pattern =
              ((ICraftingPatternProvider) stack.getItem()).create(world, stack, this);
          if (pattern.isValid()) cachedPatterns.put(slot, pattern);
        }
      }
      patterns.addAll(cachedPatterns.values());
      fullRefresh = false;
    } else {
      observedStacks.clear();
      cachedPatterns.clear();
      dynamicSlots.clear();
      fullRefresh = true;
    }
    dirtySlots.clear();
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
    refreshAllPatterns();
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
    refreshAllPatterns();
  }

  @Override
  public void setRedstoneMode(RedstoneMode mode) {
    super.setRedstoneMode(mode);
    if (ownerStorage != null) ownerStorage.setRedstoneMode(mode);
    refreshAllPatterns();
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
