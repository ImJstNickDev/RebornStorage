package net.gigabit101.rebornstorage.init;

import com.refinedmods.refinedstorage.api.storage.StorageType;
import net.gigabit101.rebornstorage.client.CreativeTabRebornStorage;
import net.gigabit101.rebornstorage.items.ItemWirelessGrid;
import net.gigabit101.rebornstorage.items.ItemRebornStorageCell;
import net.gigabit101.rebornstorage.Constants;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;

public class ModItems
{
    public static final Item.Properties ITEM_GROUP = new Item.Properties().tab(CreativeTabRebornStorage.INSTANCE);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Constants.MOD_ID);
    public static final Item.Properties PROPERTIES = new Item.Properties().tab(CreativeTabRebornStorage.INSTANCE);


    private static Item.Properties properties() { return new Item.Properties().tab(CreativeTabRebornStorage.INSTANCE); }

    //ItemBlocks
    public static final RegistryObject<Item> BLOCK_MULTI_FRAME_ITEM = ITEMS.register("multiblock_frame", () -> new BlockItem(ModBlocks.BLOCK_MULTI_FRAME.get(), ITEM_GROUP));
    public static final RegistryObject<Item> BLOCK_MULTI_HEAT_ITEM = ITEMS.register("multiblock_heat", () -> new BlockItem(ModBlocks.BLOCK_MULTI_HEAT.get(), ITEM_GROUP));
    public static final RegistryObject<Item> BLOCK_MULTI_FRAME_CPU = ITEMS.register("multiblock_cpu", () -> new BlockItem(ModBlocks.BLOCK_MULTI_CPU.get(), ITEM_GROUP));
    public static final RegistryObject<Item> BLOCK_MULTI_FRAME_STORAGE = ITEMS.register("multiblock_storage", () -> new BlockItem(ModBlocks.BLOCK_MULTI_STORAGE.get(), ITEM_GROUP));





    public static final RegistryObject<Item> RAW_SUPER_ADVANCED_PROCESSOR = ITEMS.register("raw_super_advanced_processor", () -> new Item(properties()));
    public static final RegistryObject<Item> SUPER_ADVANCED_PROCESSOR = ITEMS.register("super_advanced_processor", () -> new Item(properties()));

    public static final RegistryObject<Item> WIRELESS_GRID = ITEMS.register("super_wireless_crafting_grid", () -> new ItemWirelessGrid(properties().stacksTo(1).rarity(Rarity.EPIC), ItemWirelessGrid.Type.NORMAL, () -> 100000));
    public static final RegistryObject<Item> CREATIVE_WIRELESS_GRID = ITEMS.register("creative_super_wireless_crafting_grid", () -> new ItemWirelessGrid(properties().stacksTo(1).rarity(Rarity.EPIC), ItemWirelessGrid.Type.CREATIVE, () -> 100000));

    public static final RegistryObject<Item> PART_32768K = ITEMS.register("storagepart_32768k", () -> new Item(properties()));
    public static final RegistryObject<Item> DISK_32768K = ITEMS.register("disk_32768k", () -> new ItemRebornStorageCell(32768000, StorageType.ITEM));
    public static final RegistryObject<Item> PART_65536K = ITEMS.register("storagepart_65536k", () -> new Item(properties()));
    public static final RegistryObject<Item> DISK_65536K = ITEMS.register("disk_65536k", () -> new ItemRebornStorageCell(65536000, StorageType.ITEM));
    public static final RegistryObject<Item> PART_131072K = ITEMS.register("storagepart_131072k", () -> new Item(properties()));
    public static final RegistryObject<Item> DISK_131072K = ITEMS.register("disk_131072k", () -> new ItemRebornStorageCell(131072000, StorageType.ITEM));
    public static final RegistryObject<Item> PART_262144K = ITEMS.register("storagepart_262144k", () -> new Item(properties()));
    public static final RegistryObject<Item> DISK_262144K = ITEMS.register("disk_262144k", () -> new ItemRebornStorageCell(262144000, StorageType.ITEM));
}
