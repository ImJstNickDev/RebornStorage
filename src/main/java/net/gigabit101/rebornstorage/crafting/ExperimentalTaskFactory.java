package net.gigabit101.rebornstorage.crafting;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.autocrafting.task.*;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.CraftingTask;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.calculator.CalculationResult;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.calculator.CraftingCalculator;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;

/**
 * Retains RS calculation/preview semantics and converts its completed plan through its NBT schema.
 */
public final class ExperimentalTaskFactory implements ICraftingTaskFactory {
  private static final java.lang.reflect.Field NODES = nodesField();

  private static java.lang.reflect.Field nodesField() {
    try {
      java.lang.reflect.Field field = CraftingTask.class.getDeclaredField("nodes");
      field.setAccessible(true);
      return field;
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  public static final ResourceLocation ID =
      new ResourceLocation("rebornstorage", "experimental_v6");

  @Override
  public ICalculationResult create(
      INetwork network, ICraftingRequestInfo requested, int quantity, ICraftingPattern pattern) {
    ICalculationResult result =
        new CraftingCalculator(network, requested, quantity, pattern).calculate();
    if (!result.isOk()
        || result.getTask() == null
        || NODES == null
        || result.getTask().getClass() != CraftingTask.class) return result;
    try {
      ICraftingTask task =
          new ExperimentalTask(
              network,
              result.getTask().writeToNbt(new CompoundNBT()),
              result.getTask().getPattern(),
              (com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeList)
                  NODES.get(result.getTask()));
      return new CalculationResult(result.getType(), result.getPreviewElements(), task);
    } catch (CraftingTaskReadException | IllegalAccessException e) {
      // A changed/unloaded container during planning must retain the already valid RS plan.
      return result;
    }
  }

  @Override
  public ICraftingTask createFromNbt(INetwork network, CompoundNBT tag)
      throws CraftingTaskReadException {
    return CraftingExperiment.mode() == CraftingExperiment.Mode.TASK
        ? new ExperimentalTask(network, tag)
        : new CraftingTask(network, tag);
  }
}
