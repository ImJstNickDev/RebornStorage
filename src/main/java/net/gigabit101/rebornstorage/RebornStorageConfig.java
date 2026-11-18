package net.gigabit101.rebornstorage;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber
public class RebornStorageConfig
{
    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_MULTIBLOCK = "multiblock";

    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();

    public static ForgeConfigSpec COMMON_CONFIG;

    public static ForgeConfigSpec.IntValue FRAME_COST;
    public static ForgeConfigSpec.IntValue HEAT_COST;
    public static ForgeConfigSpec.IntValue CPU_COST;
    public static ForgeConfigSpec.IntValue STORAGE_COST;
    public static ForgeConfigSpec.EnumValue<net.gigabit101.rebornstorage.crafting.CraftingExperiment.Mode> CRAFTING_EXPERIMENT;

    public static ForgeConfigSpec.IntValue MULTIBLOCK_MAX_XSIZE;
    public static ForgeConfigSpec.IntValue MULTIBLOCK_MAX_YSIZE;
    public static ForgeConfigSpec.IntValue MULTIBLOCK_MAX_ZSIZE;

    public static ForgeConfigSpec.IntValue MULTIBLOCK_MIN_XSIZE;
    public static ForgeConfigSpec.IntValue MULTIBLOCK_MIN_YSIZE;
    public static ForgeConfigSpec.IntValue MULTIBLOCK_MIN_ZSIZE;




    static
    {
        COMMON_BUILDER.comment("General Settings").push(CATEGORY_GENERAL);
        CRAFTING_EXPERIMENT = COMMON_BUILDER.comment("Experimental branch only. OFF, PATTERN, TASK. Restart required.")
            .defineEnum("experimental_crafting", net.gigabit101.rebornstorage.crafting.CraftingExperiment.Mode.OFF);

        FRAME_COST = COMMON_BUILDER.comment("Power requirement for Frame blocks").defineInRange("multiblock_crafter_frame_cost", 0, 0, 1000);
        HEAT_COST = COMMON_BUILDER.comment("Power requirement for Heat exchanger blocks").defineInRange("multiblock_crafter_heat_cost", 0, 0, 1000);
        CPU_COST = COMMON_BUILDER.comment("Power requirement for Crafting Cpu blocks").defineInRange("multiblock_crafter_cpu_cost", 5, 0, 1000);
        STORAGE_COST = COMMON_BUILDER.comment("Power requirement for Storage blocks").defineInRange("multiblock_crafter_storage_cost", 10, 0, 1000);

        COMMON_BUILDER.pop();

        COMMON_BUILDER.comment("Multiblock Settings").push(CATEGORY_MULTIBLOCK);

        MULTIBLOCK_MAX_XSIZE = COMMON_BUILDER.comment("Maximum X size").defineInRange("multiblock_crafter_max_x", 16, 3, 64);
        MULTIBLOCK_MAX_YSIZE = COMMON_BUILDER.comment("Maximum Y size").defineInRange("multiblock_crafter_max_y", 16, 3, 64);
        MULTIBLOCK_MAX_ZSIZE = COMMON_BUILDER.comment("Maximum Z size").defineInRange("multiblock_crafter_max_z", 16, 3, 64);

        MULTIBLOCK_MIN_XSIZE = COMMON_BUILDER.comment("Maximum X size").defineInRange("multiblock_crafter_min_x", 3, 3, 64);
        MULTIBLOCK_MIN_YSIZE = COMMON_BUILDER.comment("Maximum Y size").defineInRange("multiblock_crafter_min_y", 3, 3, 64);
        MULTIBLOCK_MIN_ZSIZE = COMMON_BUILDER.comment("Maximum Z size").defineInRange("multiblock_crafter_min_z", 3, 3, 64);


        COMMON_BUILDER.pop();

        COMMON_CONFIG = COMMON_BUILDER.build();
    }

}
