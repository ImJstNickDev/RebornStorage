package net.gigabit101.rebornstorage.multiblocks;

import com.refinedmods.refinedstorage.api.network.INetworkNodeVisitor;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.util.NetworkUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.gigabit101.rebornstorage.RebornStorageConfig;
import net.gigabit101.rebornstorage.RebornStorageEventHandler;
import net.gigabit101.rebornstorage.blockentities.BlockEntityMultiCrafter;
import net.gigabit101.rebornstorage.core.multiblock.IMultiblockPart;
import net.gigabit101.rebornstorage.core.multiblock.MultiblockControllerBase;
import net.gigabit101.rebornstorage.core.multiblock.rectangular.RectangularMultiblockControllerBase;
import net.gigabit101.rebornstorage.init.ModBlocks;
import net.gigabit101.rebornstorage.nodes.CraftingNode;
import net.gigabit101.rebornstorage.nodes.PatternStorageNode;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

public class MultiBlockCrafter extends RectangularMultiblockControllerBase {
  public Map<Integer, ItemStackHandler> invs = new TreeMap<>();
  public int speed = 0;
  public int pages = 0;
  public World level;
  public int currentPage = 1;
  private CraftingNode node;
  private final List<PatternStorageNode> storages = new ArrayList<>();
  private final List<Port> ports = new ArrayList<>();
  private boolean valid;
  private int frames, heats;
  private int lastCpuCost, lastStorageCost, lastFrameCost, lastHeatCost;

  private static class Port {
    final BlockPos pos;
    final Direction side;

    Port(BlockPos pos, Direction side) {
      this.pos = pos;
      this.side = side;
    }
  }

  public CraftingNode getNode() {
    return node;
  }

  public boolean hasNetworkNode() {
    return valid && isAssembled() && node != null;
  }

  public void markPatternsDirty() {
    if (node != null) RebornStorageEventHandler.patterns(node);
  }

  private void invalidateStructure() {
    if (!valid) return;
    valid = false;
    if (node != null) node.invalidate();
    for (PatternStorageNode storage : storages) storage.attach(null);
  }

  public void visitPorts(INetworkNodeVisitor.Operator operator) {
    if (!hasNetworkNode()) return;
    for (Port port : ports) {
      if (level.hasChunkAt(port.pos)) operator.apply(level, port.pos, port.side);
      if (!valid) break; // A conflicting network may remove a part during traversal.
    }
  }

  private void discoverNetworks() {
    if (node != null) RebornStorageEventHandler.graph(node.getNetwork());
    for (Port port : ports) {
      if (!level.hasChunkAt(port.pos)) continue;
      INetworkNode adjacent = NetworkUtils.getNodeFromTile(level.getBlockEntity(port.pos));
      if (adjacent != null) RebornStorageEventHandler.graph(adjacent.getNetwork());
    }
  }

  private int energyCost() {
    lastCpuCost = RebornStorageConfig.CPU_COST.get();
    lastStorageCost = RebornStorageConfig.STORAGE_COST.get();
    lastFrameCost = RebornStorageConfig.FRAME_COST.get();
    lastHeatCost = RebornStorageConfig.HEAT_COST.get();
    return Math.toIntExact(
        (long) speed * lastCpuCost
            + (long) pages * lastStorageCost
            + (long) frames * lastFrameCost
            + (long) heats * lastHeatCost);
  }

  public MultiBlockCrafter(World world) {
    super(world);
    this.level = world;
  }

  @Override
  public void onAttachedPartWithMultiblockData(
      IMultiblockPart iMultiblockPart, CompoundNBT nbtTagCompound) {
    readFromNBT(nbtTagCompound);
  }

  @Override
  public void onBlockAdded(IMultiblockPart iMultiblockPart) {
    invalidateStructure();
  }

  @Override
  public void onBlockRemoved(IMultiblockPart iMultiblockPart) {
    invalidateStructure();
  }

  @Override
  public void onMachineAssembled() {
    updateInfo("machineAssembled");
  }

  public void updateInfo(String reason) {
    speed = 0;
    pages = 0;
    frames = 0;
    heats = 0;
    invs.clear();
    storages.clear();
    ports.clear();
    List<BlockEntityMultiCrafter> collector = new ArrayList<>();
    BlockPos min = getMinimumCoord(), max = getMaximumCoord();
    for (IMultiblockPart part : connectedParts) {
      BlockEntityMultiCrafter tile = (BlockEntityMultiCrafter) part;
      if (part.getBlockState().getBlock() == ModBlocks.BLOCK_MULTI_STORAGE.get())
        collector.add(tile);
      else if (part.getBlockState().getBlock() == ModBlocks.BLOCK_MULTI_CPU.get()) speed++;
      else if (part.getBlockState().getBlock() == ModBlocks.BLOCK_MULTI_FRAME.get()) frames++;
      else if (part.getBlockState().getBlock() == ModBlocks.BLOCK_MULTI_HEAT.get()) heats++;
      BlockPos p = part.getBlockPos();
      if (p.getX() == min.getX()) ports.add(new Port(p.west(), Direction.EAST));
      if (p.getX() == max.getX()) ports.add(new Port(p.east(), Direction.WEST));
      if (p.getY() == min.getY()) ports.add(new Port(p.below(), Direction.UP));
      if (p.getY() == max.getY()) ports.add(new Port(p.above(), Direction.DOWN));
      if (p.getZ() == min.getZ()) ports.add(new Port(p.north(), Direction.SOUTH));
      if (p.getZ() == max.getZ()) ports.add(new Port(p.south(), Direction.NORTH));
    }
    collector.sort(
        java.util.Comparator.comparingInt(
                (BlockEntityMultiCrafter t) -> t.page.orElse(Integer.MAX_VALUE))
            .thenComparing(t -> t.getBlockPos()));
    for (BlockEntityMultiCrafter tile : collector) {
      pages++;
      if (!tile.page.equals(Optional.of(pages))) {
        tile.page = Optional.of(pages);
        tile.setChanged();
      }
      if (!level.isClientSide) {
        PatternStorageNode storage = tile.getStorageNode();
        storage.attach(this);
        storages.add(storage);
        invs.put(pages, storage.patterns);
      }
    }
    if (level.isClientSide) return;
    BlockPos anchor = collector.isEmpty() ? min : collector.get(0).getBlockPos();
    if (node == null || !node.getPos().equals(anchor)) {
      if (node != null) node.invalidate();
      UUID id = storages.isEmpty() ? UUID.randomUUID() : storages.get(0).getProviderId();
      node = new CraftingNode(level, anchor, this, id);
    }
    valid = true;
    node.configure(speed, energyCost(), storages);
    discoverNetworks();
  }

  public ItemStackHandler getInvForPage(int page) {
    return invs.get(page);
  }

  @Override
  protected void onMachineRestored() {
    updateInfo("machine rebuilt");
  }

  @Override
  protected void onMachinePaused() {
    invalidateStructure();
  }

  @Override
  protected void onMachineDisassembled() {
    invalidateStructure();
  }

  @Override
  protected int getMinimumNumberOfBlocksForAssembledMachine() {
    return (9 * 3);
  }

  @Override
  protected int getMaximumXSize() {
    return RebornStorageConfig.MULTIBLOCK_MAX_XSIZE.get();
  }

  @Override
  protected int getMaximumZSize() {
    return RebornStorageConfig.MULTIBLOCK_MAX_ZSIZE.get();
  }

  @Override
  protected int getMaximumYSize() {
    return RebornStorageConfig.MULTIBLOCK_MAX_YSIZE.get();
  }

  @Override
  protected int getMinimumXSize() {
    return RebornStorageConfig.MULTIBLOCK_MIN_XSIZE.get();
  }

  @Override
  protected int getMinimumYSize() {
    return RebornStorageConfig.MULTIBLOCK_MIN_YSIZE.get();
  }

  @Override
  protected int getMinimumZSize() {
    return RebornStorageConfig.MULTIBLOCK_MIN_ZSIZE.get();
  }

  @Override
  protected void onAssimilate(MultiblockControllerBase multiblockControllerBase) {
    invalidateStructure();
  }

  @Override
  protected void onAssimilated(MultiblockControllerBase multiblockControllerBase) {
    invalidateStructure();
  }

  @Override
  protected boolean updateServer() {
    if (node != null && valid) {
      if (lastCpuCost != RebornStorageConfig.CPU_COST.get()
          || lastStorageCost != RebornStorageConfig.STORAGE_COST.get()
          || lastFrameCost != RebornStorageConfig.FRAME_COST.get()
          || lastHeatCost != RebornStorageConfig.HEAT_COST.get())
        node.configure(speed, energyCost(), storages);
      node.update();
    }
    return false; // Static structure: inventory/page changes explicitly mark their saved data.
  }

  @Override
  public void updateClient() {}

  @Override
  public void writeToNBT(CompoundNBT nbtTagCompound) {
    nbtTagCompound.putInt("currentpage", currentPage);
  }

  @Override
  public void readFromNBT(CompoundNBT nbtTagCompound) {
    currentPage = nbtTagCompound.getInt("currentpage");
  }

  @Override
  public void formatDescriptionPacket(CompoundNBT nbtTagCompound) {
    writeToNBT(nbtTagCompound);
  }

  @Override
  public void decodeDescriptionPacket(CompoundNBT nbtTagCompound) {
    readFromNBT(nbtTagCompound);
  }
}
