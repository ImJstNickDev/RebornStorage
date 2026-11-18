package net.gigabit101.rebornstorage.nodes;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.refinedmods.refinedstorage.apiimpl.network.node.NetworkNode;
import com.refinedmods.refinedstorage.inventory.item.BaseItemHandler;
import com.refinedmods.refinedstorage.util.StackUtils;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.gigabit101.rebornstorage.Constants;
import net.gigabit101.rebornstorage.init.ModBlocks;
import net.gigabit101.rebornstorage.multiblocks.MultiBlockCrafter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
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

/**
 * Persistent per-storage inventory, not a network graph entry or pattern provider. Uses the
 * original node ID/NBT format so existing inventories remain readable.
 */
public final class PatternStorageNode extends NetworkNode implements ICraftingPatternContainer {
  private boolean reading;
  private MultiBlockCrafter crafter;
  private UUID providerId = UUID.randomUUID();
  public final BaseItemHandler patterns =
      new BaseItemHandler(78) {
        @Override
        protected void onContentsChanged(int slot) {
          super.onContentsChanged(slot);
          if (!reading) {
            markDirty();
            if (crafter != null) crafter.markPatternsDirty();
          }
        }

        @Override
        public int getSlotLimit(int slot) {
          return 1;
        }
      };

  public PatternStorageNode(World world, BlockPos pos) {
    super(world, pos);
  }

  public void attach(MultiBlockCrafter crafter) {
    this.crafter = crafter;
  }

  public UUID getProviderId() {
    return providerId;
  }

  public boolean isEmpty() {
    for (int i = 0; i < patterns.getSlots(); i++)
      if (!patterns.getStackInSlot(i).isEmpty()) return false;
    return true;
  }

  @Override
  public void update() {} // Inventory edits notify the aggregate; no per-tick world lookups.

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public int getEnergyUsage() {
    return 0;
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
  public CompoundNBT write(CompoundNBT tag) {
    StackUtils.writeItems(patterns, 0, tag);
    tag.putUUID("ProviderId", providerId);
    return super.write(tag);
  }

  @Override
  public void read(CompoundNBT tag) {
    reading = true;
    try {
      StackUtils.readItems(patterns, 0, tag);
      super.read(tag);
      if (tag.hasUUID("ProviderId")) providerId = tag.getUUID("ProviderId");
    } finally {
      reading = false;
    }
  }

  // RS 1.9.17 deserializes in-flight tasks via the persisted node at ContainerPos.
  // This inert adapter is never exposed in the graph and never supplies extra execution budget.
  @Override
  public int getMaximumSuccessfulCraftingUpdates() {
    return 0;
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
  public List<ICraftingPattern> getPatterns() {
    return Collections.emptyList();
  }

  @Override
  public IItemHandlerModifiable getPatternInventory() {
    return patterns;
  }

  @Override
  public ITextComponent getName() {
    return new StringTextComponent("Multiblock pattern storage");
  }

  @Override
  public BlockPos getPosition() {
    return pos;
  }

  @Override
  public ICraftingPatternContainer getRootContainer() {
    return crafter == null ? null : crafter.getNode();
  }

  @Override
  public UUID getUuid() {
    return providerId;
  }

  @Override
  public void unlock() {}
}
