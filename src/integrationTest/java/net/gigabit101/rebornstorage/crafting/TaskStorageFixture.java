package net.gigabit101.rebornstorage.crafting;

import com.refinedmods.refinedstorage.apiimpl.storage.disk.ItemStorageDisk;

public final class TaskStorageFixture {
  public static ItemStorageDisk empty() {
    return (TaskItemStorage)
        TaskItemStorage.restore(new ItemStorageDisk(null, -1, null).writeToNbt());
  }

  private TaskStorageFixture() {}
}
