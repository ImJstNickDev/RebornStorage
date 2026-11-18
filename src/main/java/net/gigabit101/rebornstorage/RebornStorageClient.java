package net.gigabit101.rebornstorage;

import com.refinedmods.refinedstorage.screen.KeyInputListener;
import net.gigabit101.rebornstorage.client.KeyBindings;
import net.gigabit101.rebornstorage.core.multiblock.events.MultiblockClientTickHandler;
import net.gigabit101.rebornstorage.init.ModItems;
import net.gigabit101.rebornstorage.init.ModScreens;
import net.gigabit101.rebornstorage.packet.PacketChangeMode;
import net.gigabit101.rebornstorage.packet.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(
    modid = Constants.MOD_ID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.MOD)
public class RebornStorageClient {
  @SubscribeEvent
  public static void clientInit(FMLClientSetupEvent event) {
    event.enqueueWork(ModScreens::init);
    MinecraftForge.EVENT_BUS.addListener(RebornStorageClient::onKeyInput);
    MinecraftForge.EVENT_BUS.register(new MultiblockClientTickHandler());
    ClientRegistry.registerKeyBinding(KeyBindings.OPEN_WIRELESS_CRAFTING_GRID);
    ClientRegistry.registerKeyBinding(KeyBindings.MODE_SWITCH_WIRELESS_CRAFTING_GRID);
  }

  public static void onKeyInput(InputEvent.KeyInputEvent e) {
    if (Minecraft.getInstance().player != null) {
      if (KeyBindings.OPEN_WIRELESS_CRAFTING_GRID.consumeClick()) {
        KeyInputListener.findAndOpen(
            ModItems.WIRELESS_GRID.get(), ModItems.CREATIVE_WIRELESS_GRID.get());
      }
      if (KeyBindings.MODE_SWITCH_WIRELESS_CRAFTING_GRID.consumeClick() && e.getAction() == 1) {
        PacketHandler.sendToServer(new PacketChangeMode());
      }
    }
  }
}
