package net.gigabit101.rebornstorage.storage;

import com.google.common.collect.ListMultimap;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskListener;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskManager;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.apiimpl.storage.disk.ItemStorageDisk;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Uses RS's disk contents and NBT format. The live variant list avoids repeated multimap lookups.
 */
final class RebornItemDisk extends ItemStorageDisk {
  private final ServerWorld world;
  private IStorageDiskListener listener;
  private IStorageDiskManager manager;
  private Item lastItem;
  private List<ItemStack> lastVariants;
  private int stored;
  private long changes;

  RebornItemDisk(ServerWorld world, int capacity, UUID owner) {
    super(world, capacity, owner);
    this.world = world;
  }

  private List<ItemStack> variants(Item item) {
    if (lastItem != item) {
      lastVariants = ((ListMultimap<Item, ItemStack>) getRawStacks()).get(item);
      lastItem = item;
    }
    return lastVariants;
  }

  @Override
  public int getStored() {
    return stored;
  }

  @Override
  public void updateItemCount() {
    stored = getStacks().stream().mapToInt(ItemStack::getCount).sum();
    changes++;
  }

  @Override
  public ResourceLocation getFactoryId() {
    return RebornItemDiskFactory.ID;
  }

  @Override
  public void setSettings(
      @Nullable IStorageDiskListener listener, IStorageDiskContainerContext context) {
    super.setSettings(listener, context);
    this.listener = listener;
  }

  private void changed(int delta) {
    stored += delta;
    changes++;
    if (listener != null) listener.onChanged();
    if (world != null) {
      if (manager == null) manager = API.instance().getStorageDiskManager(world);
      manager.markForSaving();
    }
  }

  @Override
  public ItemStack insert(ItemStack input, int size, Action action) {
    if (action == Action.PERFORM
        && size > 0
        && !input.isEmpty()
        && lastItem == input.getItem()
        && lastVariants.size() == 1
        && size <= getCapacity() - stored) {
      List<ItemStack> entries = lastVariants;
      ItemStack candidate = entries.get(0);
      long start = changes;
      boolean matches = API.instance().getComparer().isEqualNoQuantity(candidate, input);
      if (start == changes) {
        if (matches) {
          candidate.grow(size);
          changed(size);
          return ItemStack.EMPTY;
        }
        ItemStack added = ItemHandlerHelper.copyStackWithSize(input, size);
        if (start == changes) {
          entries.add(added);
          changed(size);
          return ItemStack.EMPTY;
        }
      }
    }
    return insertOther(input, size, action);
  }

  private ItemStack insertOther(ItemStack input, int size, Action action) {
    // A capability comparison or stack copy can call back into this disk.
    // A persistent conflict leaves the input available to the next storage.
    for (int attempt = 0; attempt < 2; attempt++) {
      int accepted = getCapacity() < 0 ? size : Math.min(size, getCapacity() - stored);
      if (accepted <= 0 || input.isEmpty()) break;
      long start = changes;
      List<ItemStack> entries = variants(input.getItem());
      ItemStack match = ItemStack.EMPTY;
      for (int i = 0; i < entries.size() && start == changes; i++) {
        ItemStack candidate = entries.get(i);
        if (API.instance().getComparer().isEqualNoQuantity(candidate, input)) {
          match = candidate;
          break;
        }
      }
      if (start != changes) continue;
      ItemStack remainderSource = match.isEmpty() ? input : match;
      if (action == Action.PERFORM) {
        if (match.isEmpty()) {
          ItemStack added = ItemHandlerHelper.copyStackWithSize(input, accepted);
          if (start != changes) continue;
          entries.add(added);
        } else {
          match.grow(accepted);
        }
        changed(accepted);
      }
      // RS copies a partial remainder after the disk listener has observed the write.
      return accepted == size
          ? ItemStack.EMPTY
          : ItemHandlerHelper.copyStackWithSize(remainderSource, size - accepted);
    }
    return ItemHandlerHelper.copyStackWithSize(input, size);
  }

  @Override
  public ItemStack extract(ItemStack query, int size, int flags, Action action) {
    if (query.isEmpty() || size <= 0) return ItemStack.EMPTY;
    for (int attempt = 0; attempt < 2; attempt++) {
      long start = changes;
      List<ItemStack> entries = variants(query.getItem());
      for (int i = 0; i < entries.size() && start == changes; i++) {
        ItemStack candidate = entries.get(i);
        if (!API.instance().getComparer().isEqual(candidate, query, flags)) continue;
        if (start != changes) break;
        int taken = Math.min(size, candidate.getCount());

        if (action == Action.PERFORM) {
          if (taken == candidate.getCount()) entries.remove(i);
          else candidate.shrink(taken);
          changed(-taken);
        }
        return ItemHandlerHelper.copyStackWithSize(candidate, taken);
      }
      if (start == changes) break;
    }
    return ItemStack.EMPTY;
  }
}
