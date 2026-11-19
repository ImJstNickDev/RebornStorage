package net.gigabit101.rebornstorage.init;

import net.gigabit101.rebornstorage.client.screens.ScreenMultiCrafter;
import net.gigabit101.rebornstorage.client.screens.AdvancedWirelessTransmitterScreen;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;

public class ModScreens
{
    public static void init()
    {
        RenderTypeLookup.setRenderLayer(ModBlocks.ADVANCED_TRANSMITTER.get(), RenderType.cutout());
        ScreenManager.register(ModContainers.ADVANCED_TRANSMITTER.get(), AdvancedWirelessTransmitterScreen::new);
        ScreenManager.register(ModContainers.MULTI_CRAFTER_CONTAINER.get(), ScreenMultiCrafter::new);
    }
}
