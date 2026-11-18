package net.gigabit101.rebornstorage;

import com.refinedmods.refinedstorage.api.IRSAPI;
import com.refinedmods.refinedstorage.api.RSAPIInject;
import com.refinedmods.refinedstorage.apiimpl.API;
import net.gigabit101.rebornstorage.core.multiblock.events.MultiblockEventHandler;
import net.gigabit101.rebornstorage.grid.crafting.WirelessCraftingGridGridFactory;
import net.gigabit101.rebornstorage.init.ModBlocks;
import net.gigabit101.rebornstorage.init.ModContainers;
import net.gigabit101.rebornstorage.init.ModItems;
import net.gigabit101.rebornstorage.multiblocks.MultiBlockCrafter;
import net.gigabit101.rebornstorage.nodes.PatternStorageNode;
import net.gigabit101.rebornstorage.packet.PacketHandler;
import net.gigabit101.rebornstorage.blockentities.BlockEntityMultiCrafter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.SlotTypeMessage;


@Mod(Constants.MOD_ID)
public class RebornStorage
{
    @RSAPIInject
    public static IRSAPI RSAPI;

    public static Logger logger = LogManager.getLogger();

    public static RebornStorage INSTANCE;

    public RebornStorage()
    {
        INSTANCE = this;
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, RebornStorageConfig.COMMON_CONFIG);
        ModItems.ITEMS.register(eventBus);
        ModBlocks.BLOCKS.register(eventBus);
        ModBlocks.TILES_ENTITIES.register(eventBus);
        ModContainers.CONTAINERS.register(eventBus);
        eventBus.addListener(this::preInit);

        if (net.minecraftforge.fml.ModList.get().isLoaded("curios"))
            InterModComms.sendTo("curios", SlotTypeMessage.REGISTER_TYPE, () -> new SlotTypeMessage.Builder("grid").icon(new ResourceLocation(Constants.MOD_ID, "items/grid")).size(1).build());
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new MultiblockEventHandler());


    }

    @SubscribeEvent
    public void preInit(FMLCommonSetupEvent event)
    {
        PacketHandler.register();
        API.instance().getStorageDiskRegistry().add(net.gigabit101.rebornstorage.storage.RebornItemDiskFactory.ID, net.gigabit101.rebornstorage.storage.RebornItemDiskFactory.INSTANCE);
        API.instance().getNetworkNodeRegistry().add(Constants.MULTI_BLOCK_ID, (tag, world, pos) ->
        {
            PatternStorageNode node = new PatternStorageNode(world, pos);
            node.read(tag);
            return node;
        });
        API.instance().getGridManager().add(WirelessCraftingGridGridFactory.ID, new WirelessCraftingGridGridFactory());
    }

    public static MultiBlockCrafter getMultiBlock(World world, BlockPos pos)
    {
        TileEntity tileEntity = world.getBlockEntity(pos);
        if (tileEntity instanceof BlockEntityMultiCrafter)
        {
            return (MultiBlockCrafter) ((BlockEntityMultiCrafter) tileEntity).getMultiblockController();
        }
        return null;
    }

}
