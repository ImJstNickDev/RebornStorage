// Adapted from Refined Storage 1.9.17 (MIT), Copyright 2015-2020 Refined Mods.
// See NOTICE-RS.md. Only node construction and root pattern adaptation differ from its lifecycle.
package net.gigabit101.rebornstorage.crafting;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.refinedmods.refinedstorage.api.autocrafting.task.CraftingTaskReadException;
import com.refinedmods.refinedstorage.api.autocrafting.task.ICraftingRequestInfo;
import com.refinedmods.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IStackList;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.IoUtil;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.SerializationUtil;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.monitor.CraftingMonitorElementFactory;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.Node;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeList;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeListener;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.ProcessingNode;
import com.refinedmods.refinedstorage.apiimpl.storage.disk.factory.FluidStorageDiskFactory;
import com.refinedmods.refinedstorage.apiimpl.storage.disk.factory.ItemStorageDiskFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraftforge.fluids.FluidStack;

public final class ExperimentalTask implements ICraftingTask, NodeListener {
  public long getOptimizedNodeCount() {
    return nodes.all().stream()
        .filter(
            n ->
                n instanceof ExperimentalCraftingNode
                    && ((ExperimentalCraftingNode) n).isOptimized())
        .count();
  }

  private static final String NBT_REQUESTED = "Requested";
  private static final String NBT_QUANTITY = "Quantity";
  private static final String NBT_PATTERN = "Pattern";
  private static final String NBT_TICKS = "Ticks";
  private static final String NBT_ID = "Id";
  private static final String NBT_EXECUTION_STARTED = "ExecutionStarted";
  private static final String NBT_INTERNAL_STORAGE = "InternalStorage";
  private static final String NBT_INTERNAL_FLUID_STORAGE = "InternalFluidStorage";
  private static final String NBT_TO_EXTRACT_INITIAL = "ToExtractInitial";
  private static final String NBT_TO_EXTRACT_INITIAL_FLUIDS = "ToExtractInitialFluids";
  private static final String NBT_CRAFTS = "Crafts";
  private static final String NBT_TOTAL_STEPS = "TotalSteps";
  private static final String NBT_CURRENT_STEP = "CurrentStep";
  private final IStorageDisk<ItemStack> internalStorage;
  private final IStorageDisk<FluidStack> internalFluidStorage;
  private final INetwork network;
  private final ICraftingRequestInfo requested;
  private final int quantity;
  private final ICraftingPattern pattern;
  private final UUID id;
  private final NodeList nodes;
  private final IStackList<ItemStack> toExtractInitial;
  private final IStackList<FluidStack> toExtractInitialFluids;
  private int ticks;
  private long startTime = -1L;
  private int totalSteps;
  private int currentStep;
  private final CraftingMonitorElementFactory craftingMonitorElementFactory =
      new CraftingMonitorElementFactory();

  public ExperimentalTask(INetwork network, CompoundNBT tag) throws CraftingTaskReadException {
    this(network, tag, null, null);
  }

  ExperimentalTask(
      INetwork network, CompoundNBT tag, ICraftingPattern originalPattern, NodeList originalNodes)
      throws CraftingTaskReadException {
    this.network = network;
    this.requested = API.instance().createCraftingRequestInfo(tag.getCompound(NBT_REQUESTED));
    this.quantity = tag.getInt(NBT_QUANTITY);
    this.pattern =
        ExperimentalPattern.wrap(
            originalPattern == null
                ? SerializationUtil.readPatternFromNbt(
                    tag.getCompound(NBT_PATTERN), network.getWorld())
                : originalPattern,
            true);
    this.id = tag.getUUID(NBT_ID);
    this.nodes = new NodeList();
    this.ticks = tag.getInt(NBT_TICKS);
    this.startTime = tag.getLong(NBT_EXECUTION_STARTED);
    this.totalSteps = tag.getInt(NBT_TOTAL_STEPS);
    this.currentStep = tag.getInt(NBT_CURRENT_STEP);
    this.internalStorage =
        new ItemStorageDiskFactory().createFromNbt(null, tag.getCompound(NBT_INTERNAL_STORAGE));
    this.internalFluidStorage =
        new FluidStorageDiskFactory()
            .createFromNbt(null, tag.getCompound(NBT_INTERNAL_FLUID_STORAGE));
    this.toExtractInitial =
        SerializationUtil.readItemStackList(tag.getList(NBT_TO_EXTRACT_INITIAL, 10));
    this.toExtractInitialFluids =
        SerializationUtil.readFluidStackList(tag.getList(NBT_TO_EXTRACT_INITIAL_FLUIDS, 10));
    if (originalNodes != null) {
      for (Node original : originalNodes.all()) {
        Node node =
            original instanceof ProcessingNode
                ? original
                : new ExperimentalCraftingNode(original.getPattern(), original.writeToNbt());
        this.nodes.put(node.getPattern(), node);
      }
      return;
    }
    ListNBT nodeList = tag.getList(NBT_CRAFTS, 10);
    for (int i = 0; i < nodeList.size(); ++i) {
      CompoundNBT nodeTag = nodeList.getCompound(i);
      Node node =
          nodeTag.getBoolean("IsProcessing")
              ? Node.fromNbt(network, nodeTag)
              : new ExperimentalCraftingNode(network, nodeTag);
      this.nodes.put(node.getPattern(), node);
    }
  }

  @Override
  public CompoundNBT writeToNbt(CompoundNBT tag) {
    tag.put(NBT_REQUESTED, (INBT) this.requested.writeToNbt());
    tag.putInt(NBT_QUANTITY, this.quantity);
    tag.put(NBT_PATTERN, (INBT) SerializationUtil.writePatternToNbt(this.pattern));
    tag.putInt(NBT_TICKS, this.ticks);
    tag.putUUID(NBT_ID, this.id);
    tag.putLong(NBT_EXECUTION_STARTED, this.startTime);
    tag.put(NBT_INTERNAL_STORAGE, (INBT) this.internalStorage.writeToNbt());
    tag.put(NBT_INTERNAL_FLUID_STORAGE, (INBT) this.internalFluidStorage.writeToNbt());
    tag.put(
        NBT_TO_EXTRACT_INITIAL, (INBT) SerializationUtil.writeItemStackList(this.toExtractInitial));
    tag.put(
        NBT_TO_EXTRACT_INITIAL_FLUIDS,
        (INBT) SerializationUtil.writeFluidStackList(this.toExtractInitialFluids));
    tag.putInt(NBT_TOTAL_STEPS, this.totalSteps);
    tag.putInt(NBT_CURRENT_STEP, this.currentStep);
    ListNBT nodeList = new ListNBT();
    for (Node node : this.nodes.all()) {
      nodeList.add(node.writeToNbt());
    }
    tag.put(NBT_CRAFTS, (INBT) nodeList);
    return tag;
  }

  @Override
  public void start() {
    this.nodes
        .all()
        .forEach(
            node -> {
              this.totalSteps += node.getQuantity();
              node.onCalculationFinished();
            });
    this.startTime = System.currentTimeMillis();
    IoUtil.extractItemsFromNetwork(this.toExtractInitial, this.network, this.internalStorage);
    IoUtil.extractFluidsFromNetwork(
        this.toExtractInitialFluids, this.network, this.internalFluidStorage);
  }

  @Override
  public int getCompletionPercentage() {
    if (this.totalSteps == 0) {
      return 0;
    }
    return (int) ((float) this.currentStep * 100.0f / (float) this.totalSteps);
  }

  @Override
  public boolean update() {
    ++this.ticks;
    if (this.nodes.isEmpty()) {
      ArrayList<Runnable> toPerform = new ArrayList<Runnable>();
      for (ItemStack stack : this.internalStorage.getStacks()) {
        ItemStack remainder = this.network.insertItem(stack, stack.getCount(), Action.PERFORM);
        toPerform.add(
            () ->
                this.internalStorage.extract(
                    stack, stack.getCount() - remainder.getCount(), 1, Action.PERFORM));
      }
      for (FluidStack stack : this.internalFluidStorage.getStacks()) {
        FluidStack remainder = this.network.insertFluid(stack, stack.getAmount(), Action.PERFORM);
        toPerform.add(
            () ->
                this.internalFluidStorage.extract(
                    stack, stack.getAmount() - remainder.getAmount(), 1, Action.PERFORM));
      }
      toPerform.forEach(Runnable::run);
      return this.internalStorage.getStacks().isEmpty()
          && this.internalFluidStorage.getStacks().isEmpty();
    }
    IoUtil.extractItemsFromNetwork(this.toExtractInitial, this.network, this.internalStorage);
    IoUtil.extractFluidsFromNetwork(
        this.toExtractInitialFluids, this.network, this.internalFluidStorage);
    for (Node node : this.nodes.all()) {
      node.update(
          this.network,
          this.ticks,
          this.nodes,
          this.internalStorage,
          this.internalFluidStorage,
          this);
    }
    this.nodes.removeMarkedForRemoval();
    return false;
  }

  @Override
  public void onCancelled() {
    this.nodes.unlockAll(this.network);
    for (ItemStack remainder : this.internalStorage.getStacks()) {
      this.network.insertItem(remainder, remainder.getCount(), Action.PERFORM);
    }
    for (FluidStack remainder : this.internalFluidStorage.getStacks()) {
      this.network.insertFluid(remainder, remainder.getAmount(), Action.PERFORM);
    }
  }

  @Override
  public int getQuantity() {
    return this.quantity;
  }

  @Override
  public ICraftingRequestInfo getRequested() {
    return this.requested;
  }

  @Override
  public int onTrackedInsert(ItemStack stack, int size) {
    for (Node node : this.nodes.all()) {
      ProcessingNode processing;
      int needed;
      if (!(node instanceof ProcessingNode)
          || (needed = (processing = (ProcessingNode) node).getNeeded(stack)) <= 0) continue;
      if (needed > size) {
        needed = size;
      }
      processing.markReceived(stack, needed);
      size -= needed;
      if (!processing.isRoot()) {
        this.internalStorage.insert(stack, needed, Action.PERFORM);
      } else {
        ItemStack remainder = this.network.insertItem(stack, needed, Action.PERFORM);
        this.internalStorage.insert(remainder, remainder.getCount(), Action.PERFORM);
      }
      this.network.getCraftingManager().onTaskChanged();
      if (size != 0) continue;
      return 0;
    }
    return size;
  }

  @Override
  public int onTrackedInsert(FluidStack stack, int size) {
    for (Node node : this.nodes.all()) {
      ProcessingNode processing;
      int needed;
      if (!(node instanceof ProcessingNode)
          || (needed = (processing = (ProcessingNode) node).getNeeded(stack)) <= 0) continue;
      if (needed > size) {
        needed = size;
      }
      processing.markReceived(stack, needed);
      size -= needed;
      if (!processing.isRoot()) {
        this.internalFluidStorage.insert(stack, needed, Action.PERFORM);
      } else {
        FluidStack remainder = this.network.insertFluid(stack, needed, Action.PERFORM);
        this.internalFluidStorage.insert(remainder, remainder.getAmount(), Action.PERFORM);
      }
      this.network.getCraftingManager().onTaskChanged();
      if (size != 0) continue;
      return 0;
    }
    return size;
  }

  @Override
  public List<ICraftingMonitorElement> getCraftingMonitorElements() {
    return this.craftingMonitorElementFactory.getElements(
        this.nodes.all(), this.internalStorage, this.internalFluidStorage);
  }

  @Override
  public ICraftingPattern getPattern() {
    return this.pattern;
  }

  @Override
  public long getStartTime() {
    return this.startTime;
  }

  @Override
  public UUID getId() {
    return this.id;
  }

  @Override
  public void onAllDone(Node node) {
    this.nodes.remove(node);
  }

  @Override
  public void onSingleDone(Node node) {
    ++this.currentStep;
    this.network.getCraftingManager().onTaskChanged();
  }
}
