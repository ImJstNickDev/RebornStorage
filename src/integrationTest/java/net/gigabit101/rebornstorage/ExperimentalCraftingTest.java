package net.gigabit101.rebornstorage;

import com.refinedmods.refinedstorage.RSItems;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.*;
import com.refinedmods.refinedstorage.apiimpl.storage.disk.FluidStorageDisk;
import com.refinedmods.refinedstorage.apiimpl.storage.disk.ItemStorageDisk;
import com.refinedmods.refinedstorage.item.PatternItem;
import java.util.*;
import net.gigabit101.rebornstorage.blockentities.BlockEntityMultiCrafter;
import net.gigabit101.rebornstorage.crafting.*;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.NonNullList;

/** Differential tests with the real RS executor/storage and actual vanilla recipes. */
final class ExperimentalCraftingTest {
  private static int checks;

  private static void check(boolean value, String description) {
    if (!value) throw new AssertionError("Experimental crafting: " + description);
    checks++;
    RebornStorage.logger.info("EXPERIMENT PASS: " + description);
  }

  static void run(BlockEntityMultiCrafter tile, INetwork network) throws Exception {
    checks = 0;
    ItemStack saved = tile.getStorageNode().patterns.getStackInSlot(20).copy();
    try {
      compare(tile, network, new Item[] {Items.OAK_LOG}, Items.OAK_PLANKS, 4, false, false);
      compare(
          tile,
          network,
          new Item[] {Items.OAK_PLANKS, null, null, Items.OAK_PLANKS},
          Items.STICK,
          4,
          false,
          false);
      compare(
          tile,
          network,
          new Item[] {
            Items.MILK_BUCKET,
            Items.MILK_BUCKET,
            Items.MILK_BUCKET,
            Items.SUGAR,
            Items.EGG,
            Items.SUGAR,
            Items.WHEAT,
            Items.WHEAT,
            Items.WHEAT
          },
          Items.CAKE,
          1,
          false,
          false);
      compare(tile, network, new Item[] {Items.OAK_LOG}, Items.OAK_PLANKS, 4, true, false);
      compare(tile, network, new Item[] {Items.OAK_LOG}, Items.OAK_PLANKS, 4, false, true);
    } finally {
      tile.getStorageNode().patterns.setStackInSlot(20, saved);
      tile.getNode().applyPatternChanges();
      network.getCraftingManager().invalidate();
    }
    RebornStorage.logger.info("EXPERIMENT CHECKS: " + checks);
  }

  private static void compare(
      BlockEntityMultiCrafter tile,
      INetwork network,
      Item[] inputs,
      Item output,
      int outputCount,
      boolean tagged,
      boolean variants)
      throws Exception {
    ItemStack encoded = new ItemStack(RSItems.PATTERN.get());
    PatternItem.setToCurrentVersion(encoded);
    PatternItem.setExact(encoded, !variants);
    NonNullList<ItemStack> recipe = NonNullList.withSize(9, ItemStack.EMPTY);
    for (int i = 0; i < inputs.length; i++)
      if (inputs[i] != null) {
        ItemStack input = new ItemStack(inputs[i]);
        if (tagged) input.getOrCreateTag().putString("experiment", "preserve this exact NBT");
        recipe.set(i, input);
        PatternItem.setInputSlot(encoded, i, input);
      }
    PatternItem.setOutputSlot(encoded, 0, new ItemStack(output, outputCount));
    tile.getStorageNode().patterns.setStackInSlot(20, encoded);
    tile.getNode().applyPatternChanges();
    network.getCraftingManager().invalidate();
    ICraftingPattern pattern =
        tile.getNode().getPatterns().stream()
            .filter(p -> p.getStack() == encoded)
            .findFirst()
            .orElseThrow(() -> new AssertionError("test pattern"));
    if (!tagged && !variants && output == Items.OAK_PLANKS) dynamicRecipe(pattern);
    if (variants)
      recipe =
          new com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.CraftingPatternInputs(
                  pattern)
              .getRecipe();
    CraftingNode original = new CraftingNode(pattern, false, recipe);
    original.addQuantity(19);
    ItemStorageDisk a = new ItemStorageDisk(null, -1, null), b = TaskStorageFixture.empty();
    int ingredient = 0;
    for (ItemStack input : recipe)
      if (!input.isEmpty()) {
        if (variants) {
          original.getRequirements().addItemRequirement(ingredient, input, 9, 1);
          original
              .getRequirements()
              .addItemRequirement(ingredient, new ItemStack(Items.BIRCH_LOG), 10, 1);
          for (ItemStorageDisk storage : Arrays.asList(a, b)) {
            storage.insert(input, 9, Action.PERFORM);
            storage.insert(new ItemStack(Items.BIRCH_LOG), 10, Action.PERFORM);
          }
        } else {
          original.getRequirements().addItemRequirement(ingredient, input, 19, 1);
          for (ItemStorageDisk storage : Arrays.asList(a, b))
            storage.insert(input, 19, Action.PERFORM);
        }
        ingredient++;
      }
    original.onCalculationFinished();
    ExperimentalCraftingNode optimized =
        new ExperimentalCraftingNode(network, original.writeToNbt());
    check(
        optimized.isOptimized() != variants,
        "fixed variant specialization / mixed variant fallback");
    CountingListener la = new CountingListener(a), lb = new CountingListener(b);
    original.update(network, 1, new NodeList(), a, new FluidStorageDisk(null, -1, null), la);
    optimized.update(network, 1, new NodeList(), b, new FluidStorageDisk(null, -1, null), lb);
    check(la.singles == 6 && lb.singles == 6, "same CPU budget and per-craft callbacks");
    equal(a, b);
    check(
        original.writeToNbt().equals(optimized.writeToNbt()),
        "identical remaining node NBT after partial execution");
    if (output == Items.OAK_PLANKS && !variants && !tagged)
      backpressure(network, original.writeToNbt());
    // Use independent reconstruction paths: vanilla RS reads our node and our node reads vanilla
    // RS.
    CraftingNode vanillaResume = new CraftingNode(network, optimized.writeToNbt());
    optimized = new ExperimentalCraftingNode(network, original.writeToNbt());
    for (int tick = 2; vanillaResume.getQuantity() > 0 && tick < 20; tick++) {
      vanillaResume.update(
          network, tick, new NodeList(), a, new FluidStorageDisk(null, -1, null), la);
      optimized.update(network, tick, new NodeList(), b, new FluidStorageDisk(null, -1, null), lb);
      equal(a, b);
    }
    check(
        vanillaResume.getQuantity() == 0 && optimized.getQuantity() == 0, "both resume and finish");
    check(
        la.states.equals(lb.states),
        "same remaining work, requirements and inventory inside every craft notification");
    check(
        la.singles == 19 && lb.singles == 19 && la.done == 1 && lb.done == 1,
        "exact progress and one completion notification");
    check(
        b.extract(new ItemStack(output), Integer.MAX_VALUE, 1, Action.SIMULATE).getCount()
            == 19 * outputCount,
        "exact finished output quantity");
    if (output == Items.CAKE)
      check(
          b.extract(new ItemStack(Items.BUCKET), 999, 1, Action.SIMULATE).getCount() == 57,
          "57 empty buckets preserved from cake recipes");
    if (!variants) {
      ExperimentalCraftingNode stalled =
          new ExperimentalCraftingNode(network, original.writeToNbt());
      ItemStorageDisk empty = new ItemStorageDisk(null, -1, null);
      CountingListener listener = new CountingListener();
      stalled.update(
          network, 1, new NodeList(), empty, new FluidStorageDisk(null, -1, null), listener);
      check(
          listener.singles == 0 && empty.getStored() == 0,
          "missing inputs stall without producing or consuming");
    }
  }

  private static void equal(ItemStorageDisk a, ItemStorageDisk b) {
    check(
        a.getStored() == b.getStored() && a.getStacks().size() == b.getStacks().size(),
        "same internal inventory totals");
    for (ItemStack stack : a.getStacks())
      check(
          b.extract(stack, Integer.MAX_VALUE, 1, Action.SIMULATE).getCount() == stack.getCount(),
          "same item/NBT counts including intermediate items and remainders");
  }

  private static final class CountingListener implements NodeListener {
    int singles, done;
    final ItemStorageDisk storage;
    final List<CompoundNBT> states = new ArrayList<>();

    CountingListener() {
      this(null);
    }

    CountingListener(ItemStorageDisk storage) {
      this.storage = storage;
    }

    public void onSingleDone(Node node) {
      singles++;
      if (storage != null) {
        CompoundNBT state = node.writeToNbt();
        state.put("Inventory", storage.writeToNbt());
        state.putInt("Stored", storage.getStored());
        states.add(state);
      }
    }

    public void onAllDone(Node node) {
      done++;
    }
  }

  private static void dynamicRecipe(ICraftingPattern original) {
    final int[] calls = new int[2];
    net.minecraft.item.crafting.ShapelessRecipe dynamic =
        new net.minecraft.item.crafting.ShapelessRecipe(
            new net.minecraft.util.ResourceLocation("rebornstorage:dynamic_probe"),
            "",
            new ItemStack(Items.DIAMOND),
            NonNullList.create()) {
          @Override
          public ItemStack assemble(net.minecraft.inventory.CraftingInventory inv) {
            ItemStack out = new ItemStack(Items.DIAMOND);
            out.getOrCreateTag().putInt("sequence", ++calls[0]);
            return out;
          }

          @Override
          public NonNullList<ItemStack> getRemainingItems(
              net.minecraft.inventory.CraftingInventory inv) {
            ItemStack out = new ItemStack(Items.BUCKET);
            out.getOrCreateTag().putInt("sequence", ++calls[1]);
            return NonNullList.of(ItemStack.EMPTY, out);
          }
        };
    com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingPattern base =
        new com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingPattern(
            new com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingPatternContext(
                original.getContainer(), original.getStack()),
            false,
            true,
            dynamic,
            new com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingPatternInputs(
                original.getInputs(), original.getFluidInputs()),
            new com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingPatternOutputs(
                original.getOutputs(), original.getByproducts(), original.getFluidOutputs()),
            null);
    ICraftingPattern wrapped = CraftingExperiment.wrap(base);
    NonNullList<ItemStack> input =
        new com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.CraftingPatternInputs(base)
            .getRecipe();
    for (int n = 1; n <= 3; n++) {
      check(
          wrapped.getOutput(input).getTag().getInt("sequence") == n,
          "dynamic recipe result evaluated on every craft");
      check(
          wrapped.getByproducts(input).get(0).getTag().getInt("sequence") == n,
          "dynamic remainder hooks preserved on every craft");
    }
  }

  private static void backpressure(INetwork actual, CompoundNBT partial) throws Exception {
    partial = partial.copy();
    partial.putBoolean("Root", true);
    for (int acceptedPerInsert : new int[] {0, 2}) {
      int[] inserted = {0};
      INetwork limited =
          (INetwork)
              java.lang.reflect.Proxy.newProxyInstance(
                  INetwork.class.getClassLoader(),
                  new Class<?>[] {INetwork.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("insertItem")) {
                      ItemStack stack = (ItemStack) args[0];
                      int amount = (Integer) args[1];
                      int accepted = Math.min(amount, acceptedPerInsert);
                      if (args[2] == Action.PERFORM) inserted[0] += accepted;
                      ItemStack remainder = stack.copy();
                      remainder.setCount(amount - accepted);
                      return remainder;
                    }
                    return method.invoke(actual, args);
                  });
      ExperimentalCraftingNode node = new ExperimentalCraftingNode(limited, partial);
      int crafts = node.getQuantity();
      ItemStorageDisk storage = TaskStorageFixture.empty();
      storage.insert(new ItemStack(Items.OAK_LOG), crafts, Action.PERFORM);
      CountingListener listener = new CountingListener();
      for (int tick = 1; node.getQuantity() > 0 && tick < 20; tick++)
        node.update(
            limited, tick, new NodeList(), storage, new FluidStorageDisk(null, -1, null), listener);
      int retained =
          storage
              .extract(new ItemStack(Items.OAK_PLANKS), Integer.MAX_VALUE, 1, Action.SIMULATE)
              .getCount();
      check(
          node.getQuantity() == 0 && inserted[0] + retained == crafts * 4,
          "full/partial external storage retains every unaccepted output");
      check(
          storage.getStored() == retained, "full/partial output insertion consumes inputs exactly");
    }
  }
}
