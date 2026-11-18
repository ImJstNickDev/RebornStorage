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
    check(
        optimized.hasIntermediatePlan() == (!tagged && !variants && output != Items.CAKE),
        "only fixed plain intermediate recipes skip remainder evaluation");
    if (output == Items.OAK_PLANKS && !variants && !tagged) {
      externalStorage(network, original.writeToNbt());
      lateOutputCapabilities(network, original.getPattern(), original.writeToNbt());
      reentrantInputs(network, original.getPattern(), false);
      CapabilityProbe caps = new CapabilityProbe();
      net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(caps);
      try {
        reentrantInputs(network, original.getPattern(), true);
      } finally {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(caps);
      }
    }
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

  private static void externalStorage(INetwork network, CompoundNBT tag) throws Exception {
    tag.putBoolean("Root", true);
    net.minecraftforge.items.ItemStackHandler handler =
        new net.minecraftforge.items.ItemStackHandler(1) {
          @Override
          public int getSlotLimit(int slot) {
            return 2;
          }
        };
    com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageContext context =
        new com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageContext() {
          public int getPriority() {
            return 0;
          }

          public com.refinedmods.refinedstorage.api.storage.AccessType getAccessType() {
            return com.refinedmods.refinedstorage.api.storage.AccessType.INSERT;
          }

          public boolean acceptsItem(ItemStack stack) {
            return true;
          }

          public boolean acceptsFluid(net.minecraftforge.fluids.FluidStack stack) {
            return false;
          }
        };
    List<com.refinedmods.refinedstorage.api.storage.IStorage<ItemStack>> targets =
        network.getItemStorageCache().getStorages();
    List<com.refinedmods.refinedstorage.api.storage.IStorage<ItemStack>> saved =
        new ArrayList<>(targets);
    try {
      targets.clear();
      targets.add(
          new com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.ItemExternalStorage(
              context, () -> handler, false));
      ExperimentalCraftingNode node = new ExperimentalCraftingNode(network, tag);
      ItemStorageDisk disk = TaskStorageFixture.empty();
      disk.insert(new ItemStack(Items.OAK_LOG), 19, Action.PERFORM);
      CountingListener listener = new CountingListener();
      node.update(network, 1, new NodeList(), disk, new FluidStorageDisk(null, -1, null), listener);
      check(
          listener.singles == 6
              && handler.getStackInSlot(0).getCount() == 2
              && disk.extract(new ItemStack(Items.OAK_PLANKS), 100, 1, Action.SIMULATE).getCount()
                  == 22,
          "real RS external storage accepts two items and all subsequent remainders stay in the"
              + " task");
    } finally {
      targets.clear();
      targets.addAll(saved);
    }
  }

  // One server-side listener invalidates an input after the first committed craft.
  // The next iteration must see the replacement or capability change synchronously.
  private static void reentrantInputs(
      INetwork network, ICraftingPattern indexedPattern, boolean capability) throws Exception {
    for (boolean fast : new boolean[] {false, true}) {
      ItemStack input = new ItemStack(Items.OAK_LOG);
      NonNullList<ItemStack> inputs = NonNullList.withSize(9, ItemStack.EMPTY);
      inputs.set(0, input);
      CraftingNode reference = new CraftingNode(indexedPattern, false, inputs);
      reference.addQuantity(19);
      reference.getRequirements().addItemRequirement(0, input, 19, 1);
      reference.onCalculationFinished();
      CraftingNode node =
          fast ? new ExperimentalCraftingNode(indexedPattern, reference.writeToNbt()) : reference;
      ItemStorageDisk disk =
          fast ? TaskStorageFixture.empty() : new ItemStorageDisk(null, -1, null);
      disk.insert(input, 19, Action.PERFORM);
      CountingListener listener =
          new CountingListener() {
            @Override
            public void onSingleDone(Node n) {
              super.onSingleDone(n);
              if (singles != 1) return;
              ItemStack stored = disk.getRawStacks().get(Items.OAK_LOG).iterator().next();
              if (capability) {
                // Initialize the Forge dispatcher, then change only its serialized state.
                stored.areCapsCompatible(input);
                CapabilityProbe.data.get(stored).putInt("phase", 1);
                check(stored.getTag() == null, "variant differs by capability only");
              } else {
                int count = disk.extract(input, 19, 1, Action.PERFORM).getCount();
                ItemStack replacement = input.copy();
                replacement.getOrCreateTag().putString("variant", "listener");
                disk.insert(replacement, count, Action.PERFORM);
              }
            }
          };
      node.update(network, 1, new NodeList(), disk, new FluidStorageDisk(null, -1, null), listener);
      check(
          listener.singles == 1 && node.getQuantity() == 18 && disk.getStored() == 22,
          "listener mutation stalls next craft; caps="
              + capability
              + ", optimized="
              + fast
              + ", callbacks="
              + listener.singles
              + ", remaining="
              + node.getQuantity()
              + ", stored="
              + disk.getStored());
    }
    CapabilityProbe.data.clear();
  }

  private static void lateOutputCapabilities(
      INetwork network, ICraftingPattern pattern, CompoundNBT tag) throws Exception {
    Map<Integer, Integer> reference = null;
    for (boolean fast : new boolean[] {false, true}) {
      CraftingNode node =
          fast ? new ExperimentalCraftingNode(pattern, tag) : new CraftingNode(network, tag);
      ItemStorageDisk disk =
          fast ? TaskStorageFixture.empty() : new ItemStorageDisk(null, -1, null);
      disk.insert(new ItemStack(Items.OAK_LOG), 19, Action.PERFORM);
      CapabilityProbe probe = new CapabilityProbe();
      probe.item = Items.OAK_PLANKS;
      CountingListener listener =
          new CountingListener() {
            @Override
            public void onSingleDone(Node n) {
              super.onSingleDone(n);
              probe.phase = singles;
              if (singles == 3) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(probe);
                check(
                    !TaskStorageFixture.plainHooks(),
                    "new output capability listener recognized inside the tick");
              }
            }
          };
      try {
        check(
            TaskStorageFixture.plainHooks(),
            "audited Forge and Curios listeners recognized on the test server");
        node.update(
            network, 1, new NodeList(), disk, new FluidStorageDisk(null, -1, null), listener);
        Map<Integer, Integer> amounts = new TreeMap<>();
        for (ItemStack stack : disk.getRawStacks().get(Items.OAK_PLANKS)) {
          stack.areCapsCompatible(
              (net.minecraftforge.common.capabilities.CapabilityDispatcher) null);
          CompoundNBT state = CapabilityProbe.data.get(stack);
          amounts.merge(state == null ? -1 : state.getInt("phase"), stack.getCount(), Integer::sum);
        }
        check(
            listener.singles == 6 && amounts.get(-1) == 12 && amounts.size() == 4,
            "three distinct capability-bearing outputs after mid-order listener registration");
        if (reference == null) reference = amounts;
        else
          check(
              reference.equals(amounts),
              "private intermediate variants match native RS after listener registration");
      } finally {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(probe);
        CapabilityProbe.data.clear();
      }
    }
  }

  public static final class CapabilityProbe {
    Item item = Items.OAK_LOG;
    int phase;
    static final IdentityHashMap<ItemStack, CompoundNBT> data = new IdentityHashMap<>();

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void attach(net.minecraftforge.event.AttachCapabilitiesEvent<ItemStack> event) {
      ItemStack stack = event.getObject();
      if (stack.getItem() != item) return;
      CompoundNBT state = new CompoundNBT();
      state.putInt("phase", phase);
      data.put(stack, state);
      event.addCapability(
          new net.minecraft.util.ResourceLocation("rebornstorage", "comparison_probe"),
          new net.minecraftforge.common.capabilities.ICapabilitySerializable<CompoundNBT>() {
            public CompoundNBT serializeNBT() {
              return state.copy();
            }

            public void deserializeNBT(CompoundNBT tag) {
              state.merge(tag);
            }

            public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                net.minecraftforge.common.capabilities.Capability<T> cap,
                net.minecraft.util.Direction side) {
              return net.minecraftforge.common.util.LazyOptional.empty();
            }
          });
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

  private static class CountingListener implements NodeListener {
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
      CraftingNode[] active = new CraftingNode[1];
      List<CompoundNBT> observed = new ArrayList<>();
      INetwork limited =
          (INetwork)
              java.lang.reflect.Proxy.newProxyInstance(
                  INetwork.class.getClassLoader(),
                  new Class<?>[] {INetwork.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("insertItem")) {
                      observed.add(active[0].getRequirements().writeToNbt(new CompoundNBT()));
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
      CraftingNode reference = new CraftingNode(limited, partial);
      int crafts = node.getQuantity();
      ItemStorageDisk baseline = new ItemStorageDisk(null, -1, null);
      baseline.insert(new ItemStack(Items.OAK_LOG), crafts, Action.PERFORM);
      active[0] = reference;
      for (int tick = 1; reference.getQuantity() > 0 && tick < 20; tick++)
        reference.update(
            limited,
            tick,
            new NodeList(),
            baseline,
            new FluidStorageDisk(null, -1, null),
            new CountingListener());
      List<CompoundNBT> expected = new ArrayList<>(observed);
      observed.clear();
      inserted[0] = 0;
      active[0] = node;
      ItemStorageDisk storage = TaskStorageFixture.empty();
      storage.insert(new ItemStack(Items.OAK_LOG), crafts, Action.PERFORM);
      CountingListener listener = new CountingListener();
      for (int tick = 1; node.getQuantity() > 0 && tick < 20; tick++)
        node.update(
            limited, tick, new NodeList(), storage, new FluidStorageDisk(null, -1, null), listener);
      check(
          expected.size() == crafts && expected.equals(observed),
          "network insertion observes the same consumed requirements for each craft as RS");
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
