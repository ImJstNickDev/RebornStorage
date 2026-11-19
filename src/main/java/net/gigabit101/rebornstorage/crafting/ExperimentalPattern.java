package net.gigabit101.rebornstorage.crafting;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingPattern;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingPatternContext;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingPatternInputs;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.CraftingPatternOutputs;
import com.refinedmods.refinedstorage.item.PatternItem;
import java.lang.reflect.Field;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.item.crafting.ShapelessRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

/**
 * Reuses only vanilla recipe workspaces. Output and remainder calls still occur for every craft.
 */
public final class ExperimentalPattern extends CraftingPattern {
  private static final Field RECIPE = recipeField();
  private final CraftingPattern delegate;
  private final ICraftingRecipe recipe;
  private final boolean task, reusable;
  private final ThreadLocal<Workspace> workspace = ThreadLocal.withInitial(Workspace::new);

  private static Field recipeField() {
    try {
      Field field = CraftingPattern.class.getDeclaredField("recipe");
      field.setAccessible(true);
      return field;
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null; // Unsupported RS internals: preserve the ordinary pattern.
    }
  }

  static ICraftingPattern wrap(ICraftingPattern pattern, boolean task) {
    if (pattern.getClass() != CraftingPattern.class || pattern.isProcessing() || RECIPE == null)
      return pattern;
    try {
      return new ExperimentalPattern(
          (CraftingPattern) pattern, (ICraftingRecipe) RECIPE.get(pattern), task);
    } catch (IllegalAccessException e) {
      return pattern;
    }
  }

  private ExperimentalPattern(CraftingPattern original, ICraftingRecipe recipe, boolean task) {
    super(
        new CraftingPatternContext(original.getContainer(), original.getStack()),
        false,
        PatternItem.isExact(original.getStack()),
        recipe,
        new CraftingPatternInputs(original.getInputs(), original.getFluidInputs()),
        new CraftingPatternOutputs(
            original.getOutputs(), original.getByproducts(), original.getFluidOutputs()),
        original.getAllowedTagList());
    this.delegate = original;
    this.recipe = recipe;
    this.task = task;
    this.reusable =
        recipe != null
            && (recipe.getClass() == ShapedRecipe.class
                || recipe.getClass() == ShapelessRecipe.class);
  }

  /**
   * Exact vanilla shaped/shapeless assemble copies this stack. Base Item, BlockItem and
   * MinecartItem use Forge's default container check, backed by Item's final craftingRemainingItem.
   * Subclasses (including modded items), tagged inputs and container items stay on RS's path.
   */
  ItemStack fixedResult(NonNullList<ItemStack> inputs) {
    if (!reusable || inputs.size() != getInputs().size()) return null;
    for (ItemStack input : inputs) {
      if (input.isEmpty()) continue;
      Class<?> type = input.getItem().getClass();
      if ((type != net.minecraft.item.Item.class
              && type != net.minecraft.item.BlockItem.class
              && type != net.minecraft.item.MinecartItem.class)
          || input.getTag() != null
          || input.hasContainerItem()) return null;
    }
    ItemStack template = recipe.getResultItem();
    // Read the live result object. The caller copies it for every craft, including Forge caps.
    return template.isEmpty() ? null : template;
  }

  @Override
  public ResourceLocation getCraftingTaskFactoryId() {
    return task ? ExperimentalTaskFactory.ID : delegate.getCraftingTaskFactoryId();
  }

  @Override
  public ItemStack getOutput(NonNullList<ItemStack> took) {
    Workspace work = workspace.get();
    if (!reusable || work.busy) return delegate.getOutput(took);
    work.load(took, getInputs().size());
    try {
      ItemStack result = recipe.assemble(work.inventory);
      if (result.isEmpty()) throw new IllegalStateException("Cannot have empty result");
      return result;
    } finally {
      work.clear();
    }
  }

  @Override
  public NonNullList<ItemStack> getByproducts(NonNullList<ItemStack> took) {
    Workspace work = workspace.get();
    if (!reusable || work.busy) return delegate.getByproducts(took);
    work.load(took, getInputs().size());
    try {
      NonNullList<ItemStack> result = NonNullList.create();
      // Preserve Forge's per-item remainder hooks, including capabilities and container items.
      for (ItemStack stack : recipe.getRemainingItems(work.inventory))
        if (!stack.isEmpty()) result.add(stack);
      return result;
    } finally {
      work.clear();
    }
  }

  /** Consume the actual Forge remainder list in slot order without building a filtered copy. */
  void storeByproducts(NonNullList<ItemStack> took, TaskItemStorage storage) {
    Workspace work = workspace.get();
    if (!reusable || work.busy) {
      for (ItemStack stack : delegate.getByproducts(took)) storage.addOutput(stack);
      return;
    }
    work.load(took, getInputs().size());
    try {
      NonNullList<ItemStack> remaining = recipe.getRemainingItems(work.inventory);
      for (int i = 0; i < remaining.size(); i++) storage.addOutput(remaining.get(i));
    } finally {
      work.clear();
    }
  }

  private static final class Workspace {
    final RecipeInventory inventory = new RecipeInventory();
    boolean busy;

    void load(NonNullList<ItemStack> took, int expected) {
      if (took.size() != expected)
        throw new IllegalArgumentException("Pattern input size mismatch");
      busy = true;
      inventory.inputs = took;
    }

    void clear() {
      inventory.inputs = null;
      busy = false;
    }
  }

  /** The exact vanilla implementations only read this view; custom recipe classes are delegated. */
  private static final class RecipeInventory extends DummyCraftingInventory {
    NonNullList<ItemStack> inputs;

    @Override
    public ItemStack getItem(int slot) {
      return inputs != null && slot >= 0 && slot < inputs.size()
          ? inputs.get(slot)
          : ItemStack.EMPTY;
    }
  }
}
