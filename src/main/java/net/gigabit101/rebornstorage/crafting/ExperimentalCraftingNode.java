package net.gigabit101.rebornstorage.crafting;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.refinedmods.refinedstorage.api.autocrafting.task.CraftingTaskReadException;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.CraftingNode;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeList;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeListener;
import com.refinedmods.refinedstorage.apiimpl.storage.disk.ItemStorageDisk;
import com.refinedmods.refinedstorage.util.StackUtils;
import java.util.ArrayList;
import java.util.List;
import net.gigabit101.rebornstorage.nodes.PatternStorageNode;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

/**
 * RS-compatible single-craft executor. Compiles fixed-variant requirements once; mixed-variant
 * plans keep RS's original node. No batching, output caching, thread hopping or budget changes.
 */
public final class ExperimentalCraftingNode extends CraftingNode {
  private final ICraftingPattern pattern;
  private final NonNullList<ItemStack> recipe;
  private final List<ItemStack> ingredients = new ArrayList<>();
  private final List<Integer> amounts = new ArrayList<>();
  private final CompoundNBT initialRequirements;
  private final boolean eligible;
  private int completed;

  public ExperimentalCraftingNode(INetwork network, CompoundNBT tag)
      throws CraftingTaskReadException {
    this(
        com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.SerializationUtil
            .readPatternFromNbt(tag.getCompound("Pattern"), network.getWorld()),
        tag);
  }

  public ExperimentalCraftingNode(ICraftingPattern original, CompoundNBT tag)
      throws CraftingTaskReadException {
    this(original, tag, readRecipe(tag));
  }

  private ExperimentalCraftingNode(
      ICraftingPattern original, CompoundNBT tag, NonNullList<ItemStack> recipe)
      throws CraftingTaskReadException {
    super(original, tag.getBoolean("Root"), recipe);
    this.recipe = recipe;
    quantity = tag.getInt("Quantity");
    totalQuantity = tag.getInt("TotalQuantity");
    requirements.readFromNbt(tag);
    boolean ours =
        super.getPattern().getContainer() instanceof PatternStorageNode
            || super.getPattern().getContainer()
                instanceof net.gigabit101.rebornstorage.nodes.CraftingNode;
    pattern = ours ? ExperimentalPattern.wrap(super.getPattern(), true) : super.getPattern();
    initialRequirements = tag.copy();
    eligible = ours && compile(tag);
  }

  private static NonNullList<ItemStack> readRecipe(CompoundNBT tag) {
    NonNullList<ItemStack> recipe = NonNullList.create();
    ListNBT encoded = tag.getList("Recipe", 10);
    for (int i = 0; i < encoded.size(); i++)
      recipe.add(StackUtils.deserializeStackFromNbt(encoded.getCompound(i)));
    return recipe;
  }

  private boolean compile(CompoundNBT tag) {
    ListNBT groups = tag.getList("ItemsToUse", 9);
    int[] perCraft = tag.getIntArray("ItemsNeededPerCraft");
    if (groups.size() != perCraft.length || !tag.getList("FluidsToUse", 9).isEmpty()) return false;
    for (int i = 0; i < groups.size(); i++) {
      ListNBT variants = groups.getList(i);
      if (variants.size() != 1 || perCraft[i] <= 0) return false;
      ItemStack stack = StackUtils.deserializeStackFromNbt(variants.getCompound(0));
      if (stack.isEmpty() || (long) quantity * perCraft[i] != stack.getCount()) return false;
      int match = -1;
      for (int j = 0; j < ingredients.size(); j++)
        if (API.instance().getComparer().isEqual(ingredients.get(j), stack, 1)) {
          match = j;
          break;
        }
      if (match < 0) {
        stack.setCount(1);
        ingredients.add(stack);
        amounts.add(perCraft[i]);
      } else {
        long count = (long) amounts.get(match) + perCraft[i];
        if (count > Integer.MAX_VALUE) return false;
        amounts.set(match, (int) count);
      }
    }
    return true;
  }

  @Override
  public ICraftingPattern getPattern() {
    return pattern == null ? super.getPattern() : pattern;
  }

  public boolean isOptimized() {
    return eligible;
  }

  @Override
  public com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeRequirements
      getRequirements() {
    if (!eligible || completed == 0) return super.getRequirements();
    com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeRequirements current =
        new com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeRequirements();
    try {
      current.readFromNbt(writeToNbt());
    } catch (CraftingTaskReadException e) {
      throw new IllegalStateException(e);
    }
    return current;
  }

  private boolean available(ItemStorageDisk storage) {
    for (int i = 0; i < ingredients.size(); i++) {
      ItemStack query = ingredients.get(i);
      boolean found = false;
      for (ItemStack stored : storage.getRawStacks().get(query.getItem())) {
        if (!API.instance().getComparer().isEqual(stored, query, 1)) continue;
        found = stored.getCount() >= amounts.get(i);
        break;
      }
      if (!found) return false;
    }
    return true;
  }

  @Override
  public void update(
      INetwork network,
      int ticks,
      NodeList nodes,
      IStorageDisk<ItemStack> storage,
      IStorageDisk<FluidStack> fluids,
      NodeListener listener) {
    if (!eligible || !(storage instanceof ItemStorageDisk)) {
      super.update(network, ticks, nodes, storage, fluids, listener);
      return;
    }
    for (ICraftingPatternContainer container :
        network.getCraftingManager().getAllContainers(getPattern())) {
      int interval = container.getUpdateInterval();
      if (interval < 0) throw new IllegalStateException("Negative crafting interval");
      if (interval != 0 && ticks % interval != 0) continue;
      for (int i = 0;
          i < container.getMaximumSuccessfulCraftingUpdates()
              && available((ItemStorageDisk) storage);
          i++) {
        for (int j = 0; j < ingredients.size(); j++) {
          ItemStack extracted =
              storage.extract(ingredients.get(j), amounts.get(j), 1, Action.PERFORM);
          if (extracted.getCount() != amounts.get(j))
            throw new IllegalStateException("Crafting input changed during consumption");
        }
        ItemStack output = getPattern().getOutput(recipe);
        if (!isRoot()) storage.insert(output, output.getCount(), Action.PERFORM);
        else {
          ItemStack remainder = network.insertItem(output, output.getCount(), Action.PERFORM);
          storage.insert(remainder, remainder.getCount(), Action.PERFORM);
        }
        for (ItemStack byproduct : getPattern().getByproducts(recipe))
          storage.insert(byproduct, byproduct.getCount(), Action.PERFORM);
        completed++;
        next();
        listener.onSingleDone(this);
        if (getQuantity() <= 0) {
          listener.onAllDone(this);
          return;
        }
      }
    }
  }

  @Override
  public CompoundNBT writeToNbt() {
    CompoundNBT tag = super.writeToNbt();
    if (eligible && completed != 0) {
      ListNBT groups = initialRequirements.getList("ItemsToUse", 9).copy();
      int[] perCraft = initialRequirements.getIntArray("ItemsNeededPerCraft");
      for (int i = 0; i < groups.size(); i++) {
        ListNBT variants = groups.getList(i);
        ItemStack stack = StackUtils.deserializeStackFromNbt(variants.getCompound(0));
        int remaining = stack.getCount() - completed * perCraft[i];
        variants.clear();
        if (remaining > 0) {
          stack.setCount(remaining);
          variants.add(StackUtils.serializeStackToNbt(stack));
        }
      }
      tag.put("ItemsToUse", groups);
    }
    return tag;
  }
}
