package net.gigabit101.rebornstorage.containers;

import com.refinedmods.refinedstorage.container.BaseContainer;
import net.gigabit101.rebornstorage.blockentities.BlockEntityAdvancedWirelessTransmitter;
import net.gigabit101.rebornstorage.init.ModContainers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class AdvancedWirelessTransmitterContainer extends BaseContainer {
  public AdvancedWirelessTransmitterContainer(
      BlockEntityAdvancedWirelessTransmitter tile, PlayerEntity player, int id) {
    super(ModContainers.ADVANCED_TRANSMITTER.get(), tile, player, id);
    IItemHandler upgrades = tile.getNode().getUpgrades();
    for (int slot = 0; slot < upgrades.getSlots(); slot++) {
      addSlot(new SlotItemHandler(upgrades, slot, 187, 6 + slot * 18));
    }
    addPlayerInventory(8, 55);
    transferManager.addBiTransfer(player.inventory, upgrades);
  }
}
