package net.gigabit101.rebornstorage.crafting;

import com.google.common.collect.ListMultimap;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.apiimpl.storage.disk.ItemStorageDisk;
import com.refinedmods.refinedstorage.apiimpl.storage.disk.factory.ItemStorageDiskFactory;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;

/**
 * The unlimited, listener-free disk owned exclusively by a v6 task. Stack counts are live even
 * inside callbacks. Only RS's derived total needs reconciliation before ordinary disk operations.
 */
final class TaskItemStorage extends ItemStorageDisk {
  private final Map<Item, List<ItemStack>> buckets = new IdentityHashMap<>();
  private int countDelta;
  private long revision;

  long revision() {
    return revision;
  }

  boolean singleton(ItemStack stack) {
    return buckets.get(stack.getItem()).size() == 1;
  }

  static boolean fixedIdentity(ItemStack stack) {
    // Null dispatcher compatibility means Forge has no serializable capability writers here.
    return stack.getTag() == null
        && stack.areCapsCompatible(
            (net.minecraftforge.common.capabilities.CapabilityDispatcher) null);
  }

  private TaskItemStorage(ItemStorageDisk restored) {
    super(null, -1, restored.getOwner());
    getRawStacks().putAll(restored.getRawStacks());
    updateItemCount();
  }

  static IStorageDisk<ItemStack> restore(CompoundNBT tag) {
    IStorageDisk<ItemStack> disk = new ItemStorageDiskFactory().createFromNbt(null, tag);
    return disk.getCapacity() == -1
            && disk.getClass() == ItemStorageDisk.class
            && ((ItemStorageDisk) disk).getRawStacks() instanceof ListMultimap
        ? new TaskItemStorage((ItemStorageDisk) disk)
        : disk;
  }

  ItemStack find(ItemStack query) {
    List<ItemStack> variants = buckets.get(query.getItem());
    if (variants == null) {
      variants = ((ListMultimap<Item, ItemStack>) getRawStacks()).get(query.getItem());
      buckets.put(query.getItem(), variants);
    }
    for (int i = 0; i < variants.size(); i++) {
      ItemStack candidate = variants.get(i);
      if (API.instance().getComparer().isEqual(candidate, query, 1)) return candidate;
    }
    return ItemStack.EMPTY;
  }

  void consume(ItemStack matched, int amount) {
    if (matched.getCount() == amount) {
      getRawStacks().remove(matched.getItem(), matched);
      revision++;
    } else matched.shrink(amount);
    countDelta -= amount;
  }

  boolean growPlain(long expectedRevision, ItemStack destination, int count) {
    if (expectedRevision != revision || getStored() == getCapacity()) return false;
    destination.grow(count);
    countDelta += count;
    return true;
  }

  boolean growOutput(long expectedRevision, ItemStack destination, ItemStack output) {
    if (expectedRevision != revision
        || getStored() == getCapacity()
        || !API.instance().getComparer().isEqualNoQuantity(destination, output)
        || expectedRevision != revision) return false;
    int amount = output.getCount();
    destination.grow(amount);
    countDelta += amount;
    return true;
  }

  ItemStack addOutput(ItemStack output) {
    if (output.isEmpty() || getStored() == getCapacity()) return ItemStack.EMPTY;
    int amount = output.getCount();
    ItemStack existing = find(output);
    if (existing.isEmpty()) {
      existing = output.copy();
      getRawStacks().put(output.getItem(), existing);
      revision++;
    } else existing.grow(amount);
    countDelta += amount;
    return existing;
  }

  private void synchronizeCount() {
    if (countDelta != 0) {
      updateItemCount();
      countDelta = 0;
    }
  }

  @Override
  public int getStored() {
    return super.getStored() + countDelta;
  }

  @Override
  public ItemStack insert(ItemStack stack, int size, Action action) {
    if (action == Action.PERFORM) revision++;
    synchronizeCount();
    return super.insert(stack, size, action);
  }

  @Override
  public ItemStack extract(ItemStack stack, int size, int flags, Action action) {
    if (action == Action.PERFORM) revision++;
    synchronizeCount();
    return super.extract(stack, size, flags, action);
  }
}
