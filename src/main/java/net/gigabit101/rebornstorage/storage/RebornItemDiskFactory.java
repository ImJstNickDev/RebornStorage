package net.gigabit101.rebornstorage.storage;

import com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskFactory;
import com.refinedmods.refinedstorage.apiimpl.storage.disk.ItemStorageDisk;
import com.refinedmods.refinedstorage.util.StackUtils;
import java.util.UUID;
import javax.annotation.Nullable;
import net.gigabit101.rebornstorage.items.ItemRebornStorageCell;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;

/** RS storage implementation with a distinct factory ID for correct disk recovery. */
public class RebornItemDiskFactory implements IStorageDiskFactory<ItemStack> {
  public static final ResourceLocation ID = new ResourceLocation("rebornstorage", "item");
  public static final RebornItemDiskFactory INSTANCE = new RebornItemDiskFactory();

  @Override
  public IStorageDisk<ItemStack> create(ServerWorld world, int capacity, @Nullable UUID owner) {
    if (net.gigabit101.rebornstorage.crafting.CraftingExperiment.mode()
        == net.gigabit101.rebornstorage.crafting.CraftingExperiment.Mode.TASK)
      return new RebornItemDisk(world, capacity, owner);
    return new ItemStorageDisk(world, capacity, owner) {
      @Override
      public ResourceLocation getFactoryId() {
        return ID;
      }
    };
  }

  @Override
  public IStorageDisk<ItemStack> createFromNbt(ServerWorld world, CompoundNBT tag) {
    ItemStorageDisk disk =
        (ItemStorageDisk)
            create(
                world, tag.getInt("Capacity"), tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
    ListNBT items = tag.getList("Items", 10);
    for (int i = 0; i < items.size(); i++) {
      ItemStack stack = StackUtils.deserializeStackFromNbt(items.getCompound(i));
      if (!stack.isEmpty()) disk.getRawStacks().put(stack.getItem(), stack);
    }
    disk.updateItemCount();
    return disk;
  }

  @Override
  public ItemStack createDiskItem(IStorageDisk<ItemStack> disk, UUID id) {
    Item item =
        ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("rebornstorage", "disk_" + disk.getCapacity() / 1000 + "k"));
    if (!(item instanceof ItemRebornStorageCell))
      throw new IllegalArgumentException("Unknown Reborn disk capacity: " + disk.getCapacity());
    ItemStack stack = new ItemStack(item);
    ((ItemRebornStorageCell) item).setId(stack, id);
    return stack;
  }
}
