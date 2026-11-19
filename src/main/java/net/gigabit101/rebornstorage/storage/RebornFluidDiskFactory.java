package net.gigabit101.rebornstorage.storage;

import com.refinedmods.refinedstorage.api.storage.StorageType;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskFactory;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.storage.disk.FluidStorageDisk;
import java.util.UUID;
import javax.annotation.Nullable;
import net.gigabit101.rebornstorage.Constants;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;

public class RebornFluidDiskFactory implements IStorageDiskFactory<FluidStack> {
  public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "fluid");
  public static final RebornFluidDiskFactory INSTANCE = new RebornFluidDiskFactory();

  @Override
  public FluidStorageDisk create(ServerWorld world, int capacity, @Nullable UUID owner) {
    return new FluidStorageDisk(world, capacity, owner) {
      @Override
      public ResourceLocation getFactoryId() {
        return ID;
      }

      @Override
      public FluidStack insert(FluidStack input, int size, Action action) {
        // RS 1.9.17 adds two signed ints when testing whether an insertion fits.
        if (size > Integer.MAX_VALUE - capacity
            && size > Integer.MAX_VALUE - getStored()
            && !input.isEmpty()) {
          int safeRequest = capacity - getStored() + 1;
          FluidStack remainder = super.insert(input, safeRequest, action);
          remainder.grow(size - safeRequest);
          return remainder;
        }
        return super.insert(input, size, action);
      }
    };
  }

  @Override
  public IStorageDisk<FluidStack> createFromNbt(ServerWorld world, CompoundNBT tag) {
    FluidStorageDisk disk =
        create(world, tag.getInt("Capacity"), tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
    ListNBT fluids = tag.getList("Fluids", 10);
    for (int i = 0; i < fluids.size(); i++) {
      FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluids.getCompound(i));
      if (!fluid.isEmpty()) disk.getRawStacks().put(fluid.getRawFluid(), fluid);
    }
    return disk;
  }

  @Override
  public ItemStack createDiskItem(IStorageDisk<FluidStack> disk, UUID id) {
    return DiskTier.recover(StorageType.FLUID, disk.getCapacity(), id);
  }
}
