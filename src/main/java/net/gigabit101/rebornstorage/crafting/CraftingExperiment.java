package net.gigabit101.rebornstorage.crafting;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import java.util.Locale;
import net.gigabit101.rebornstorage.RebornStorageConfig;

/** Explicit opt-in on the experimental branch; no global RS factory is replaced. */
public final class CraftingExperiment {
  public enum Mode {
    OFF,
    PATTERN,
    TASK
  }

  private CraftingExperiment() {}

  public static Mode mode() {
    String override = System.getProperty("reborn.craftingExperiment");
    return override == null
        ? RebornStorageConfig.CRAFTING_EXPERIMENT.get()
        : Mode.valueOf(override.toUpperCase(Locale.ROOT));
  }

  public static ICraftingPattern wrap(ICraftingPattern pattern) {
    return mode() == Mode.OFF || pattern instanceof ExperimentalPattern
        ? pattern
        : ExperimentalPattern.wrap(pattern, mode() == Mode.TASK);
  }
}
