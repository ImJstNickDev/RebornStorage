package net.gigabit101.rebornstorage;

import com.refinedmods.refinedstorage.api.storage.AccessType;
import com.refinedmods.refinedstorage.api.storage.StorageType;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskManager;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.API;
import java.util.List;
import net.gigabit101.rebornstorage.items.ItemRebornStorageCell;
import net.gigabit101.rebornstorage.storage.DiskTier;
import net.gigabit101.rebornstorage.storage.RebornFluidDiskFactory;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

final class StorageContentTest {
  private static Item rs(String path) {
    return ForgeRegistries.ITEMS.getValue(new ResourceLocation("refinedstorage", path));
  }

  static void recipes(ServerWorld world, FakePlayer player) {
    Item previousItem = rs("64k_storage_part"), previousFluid = rs("4096k_fluid_storage_part");
    Item processor = rs("advanced_processor"), iron = rs("quartz_enriched_iron");
    for (DiskTier tier : DiskTier.values()) {
      boolean fluid = tier.type == StorageType.FLUID;
      boolean original = tier.disk().getId().getPath().endsWith("_disk");
      Item part = tier.part().get(), disk = tier.disk().get();
      Item previous = fluid ? previousFluid : previousItem;
      Item center = fluid ? Items.BUCKET : Items.REDSTONE;
      match(
          world,
          player,
          part,
          processor,
          original ? previous : iron,
          processor,
          previous,
          center,
          previous,
          processor,
          original ? iron : previous,
          processor);
      if (original) {
        match(
            world,
            player,
            disk,
            iron,
            iron,
            iron,
            Items.REDSTONE,
            part,
            Items.REDSTONE,
            Items.GLASS,
            Items.REDSTONE,
            Items.GLASS);
      }
      if (!original || !ModList.get().isLoaded("extrastorage"))
        match(world, player, disk, part, rs("storage_housing"));
      String tag = fluid ? "fluids/" : "items/";
      verify(
          part.getTags()
                  .contains(
                      new ResourceLocation(
                          "refinedstorage:parts/" + tag + tier.capacity / 1000 + "k"))
              && disk.getTags()
                  .contains(
                      new ResourceLocation(
                          "refinedstorage:disks/" + tag + tier.capacity / 1000 + "k"))
              && disk.getTags().contains(new ResourceLocation("refinedstorage:disks")),
          "RS tags include " + tier);
      if (fluid) previousFluid = part;
      else previousItem = part;
    }
  }

  private static void match(
      ServerWorld world, FakePlayer player, Item result, Item... ingredients) {
    CraftingInventory table = new CraftingInventory(player.inventoryMenu, 3, 3);
    for (int i = 0; i < ingredients.length; i++) table.setItem(i, new ItemStack(ingredients[i]));
    List<ICraftingRecipe> matches =
        world.getRecipeManager().getRecipesFor(IRecipeType.CRAFTING, table, world);
    verify(
        matches.size() == 1 && matches.get(0).assemble(table).getItem() == result,
        "one crafting-table result for " + result.getRegistryName() + ", got " + matches);
  }

  static void fluid(ServerWorld world, FakePlayer player, DiskTier tier) {
    ItemRebornStorageCell item = (ItemRebornStorageCell) tier.disk().get();
    IStorageDiskManager manager = API.instance().getStorageDiskManager(world);
    ItemStack stack = new ItemStack(item);
    item.inventoryTick(stack, world, player, 0, false);
    IStorageDisk<FluidStack> disk = manager.getByStack(stack);
    disk.setSettings(() -> {}, () -> AccessType.INSERT_EXTRACT);
    FluidStack water = new FluidStack(Fluids.WATER, 1);
    FluidStack tagged = new FluidStack(Fluids.WATER, 1);
    tagged.getOrCreateTag().putString("variant", "test");
    int cap = tier.capacity;
    verify(
        disk.getCapacity() == cap && item.getType() == StorageType.FLUID,
        "fluid capacity and type " + tier);
    disk.insert(tagged, 7, Action.PERFORM);
    verify(
        disk.insert(water, Integer.MAX_VALUE, Action.SIMULATE).getAmount()
                == Integer.MAX_VALUE - cap + 7
            && disk.getStored() == 7,
        "simulated fluid insertion " + tier);
    verify(
        disk.insert(water, Integer.MAX_VALUE, Action.PERFORM).getAmount()
                == Integer.MAX_VALUE - cap + 7
            && disk.getStored() == cap
            && disk.insert(water, 1, Action.PERFORM).getAmount() == 1,
        "fluid overflow " + tier);
    IStorageDisk<FluidStack> restored =
        RebornFluidDiskFactory.INSTANCE.createFromNbt(world, disk.writeToNbt());
    ItemStack recovered =
        RebornFluidDiskFactory.INSTANCE.createDiskItem(restored, item.getId(stack));
    verify(
        restored.getStored() == cap
            && restored.getOwner().equals(player.getUUID())
            && recovered.getItem() == item
            && item.getId(recovered).equals(item.getId(stack))
            && restored.extract(tagged, 8, 1, Action.SIMULATE).getAmount() == 7,
        "fluid NBT and recovery " + tier);
    player.setShiftKeyDown(true);
    player.setItemInHand(Hand.MAIN_HAND, stack);
    verify(
        item.use(world, player, Hand.MAIN_HAND).getObject() == stack,
        "full fluid disk retained " + tier);
    disk.extract(tagged, 7, 1, Action.PERFORM);
    disk.extract(water, cap - 7, 1, Action.PERFORM);
    verify(
        disk.getStored() == 0
            && item.use(world, player, Hand.MAIN_HAND).getObject().getItem()
                == rs("storage_housing")
            && player.inventory.contains(new ItemStack(tier.part().get()))
            && manager.getByStack(stack) == null,
        "fluid disassembly " + tier);
    player.setShiftKeyDown(false);
  }

  private static void verify(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
    RebornStorage.logger.info("PORT CONTENT PASS: " + label);
  }
}
