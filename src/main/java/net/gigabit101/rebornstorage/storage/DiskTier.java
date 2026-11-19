package net.gigabit101.rebornstorage.storage;

import com.refinedmods.refinedstorage.api.storage.StorageType;
import java.util.UUID;
import net.gigabit101.rebornstorage.items.ItemRebornStorageCell;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/** The original registries and the four extended tiers share one capacity and recovery table. */
public enum DiskTier {
  ITEM_256(StorageType.ITEM, 256, "small_item_disk"),
  ITEM_1024(StorageType.ITEM, 1024, "medium_item_disk"),
  ITEM_4096(StorageType.ITEM, 4096, "large_item_disk"),
  ITEM_16384(StorageType.ITEM, 16384, "larger_item_disk"),
  ITEM_32768(32768),
  ITEM_65536(65536),
  ITEM_131072(131072),
  ITEM_262144(262144),
  FLUID_16384(StorageType.FLUID, 16384, "small_fluid_disk"),
  FLUID_65536(StorageType.FLUID, 65536, "medium_fluid_disk"),
  FLUID_262144(StorageType.FLUID, 262144, "large_fluid_disk"),
  FLUID_1048576(StorageType.FLUID, 1048576, "larger_fluid_disk");

  public final StorageType type;
  public final int capacity;
  private final String diskId, partId;
  private RegistryObject<Item> disk, part;

  DiskTier(int thousands) {
    this(StorageType.ITEM, thousands, "disk_" + thousands + "k", "storagepart_" + thousands + "k");
  }

  DiskTier(StorageType type, int thousands, String diskId) {
    this(type, thousands, diskId, diskId + "_part");
  }

  DiskTier(StorageType type, int thousands, String diskId, String partId) {
    this.type = type;
    this.capacity = Math.multiplyExact(thousands, 1000);
    this.diskId = diskId;
    this.partId = partId;
  }

  public void register(DeferredRegister<Item> registry, Item.Properties partProperties) {
    part = registry.register(partId, () -> new Item(partProperties));
    disk = registry.register(diskId, () -> new ItemRebornStorageCell(this));
  }

  public RegistryObject<Item> disk() {
    return disk;
  }

  public RegistryObject<Item> part() {
    return part;
  }

  public static ItemStack recover(StorageType type, int capacity, UUID id) {
    for (DiskTier tier : values()) {
      if (tier.type == type && tier.capacity == capacity) {
        ItemRebornStorageCell item = (ItemRebornStorageCell) tier.disk.get();
        ItemStack stack = new ItemStack(item);
        item.setId(stack, id);
        return stack;
      }
    }
    throw new IllegalArgumentException("Unknown Reborn " + type + " disk capacity " + capacity);
  }
}
