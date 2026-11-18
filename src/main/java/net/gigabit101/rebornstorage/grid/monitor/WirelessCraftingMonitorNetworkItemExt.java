package net.gigabit101.rebornstorage.grid.monitor;

import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorage.RSContainers;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.item.INetworkItemManager;
import com.refinedmods.refinedstorage.api.network.security.Permission;
import com.refinedmods.refinedstorage.apiimpl.network.item.WirelessCraftingMonitorNetworkItem;
import com.refinedmods.refinedstorage.tile.craftingmonitor.CraftingMonitorTile;
import com.refinedmods.refinedstorage.tile.craftingmonitor.WirelessCraftingMonitor;
import com.refinedmods.refinedstorage.container.factory.CraftingMonitorContainerProvider;
import com.refinedmods.refinedstorage.inventory.player.PlayerSlot;
import com.refinedmods.refinedstorage.util.WorldUtils;
import net.gigabit101.rebornstorage.items.ItemWirelessGrid;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.Objects;

public class WirelessCraftingMonitorNetworkItemExt extends WirelessCraftingMonitorNetworkItem
{
    private final INetworkItemManager handler;
    private final PlayerEntity player;
    private final ItemStack stack;
    private final PlayerSlot slot;

    public WirelessCraftingMonitorNetworkItemExt(INetworkItemManager handler, PlayerEntity player, ItemStack stack, PlayerSlot slot)
    {
        super(handler, player, stack, slot);
        this.handler = handler;
        this.player = player;
        this.stack = stack;
        this.slot = slot;
    }

    @Override
    public boolean onOpen(INetwork network)
    {
        if (!network.canRun()) return false;
        IEnergyStorage energy = stack.getCapability(CapabilityEnergy.ENERGY, null).orElse(null);
        if (RS.SERVER_CONFIG.getWirelessCraftingMonitor().getUseEnergy() && ((ItemWirelessGrid)this.stack.getItem()).getType() != ItemWirelessGrid.Type.CREATIVE && energy != null && energy.getEnergyStored() <= RS.SERVER_CONFIG.getWirelessCraftingMonitor().getOpenUsage()) {
            this.sendOutOfEnergyMessage();
            return false;
        } else if (network.getSecurityManager().hasPermission(Permission.MODIFY, this.player) && network.getSecurityManager().hasPermission(Permission.AUTOCRAFTING, this.player)) {
            WirelessCraftingMonitor wirelessCraftingMonitor = new WirelessCraftingMonitor(this.stack, this.player.getServer(), this.slot);
            ServerPlayerEntity var10000 = (ServerPlayerEntity)this.player;
            CraftingMonitorContainerProvider var10001 = new CraftingMonitorContainerProvider(RSContainers.WIRELESS_CRAFTING_MONITOR, wirelessCraftingMonitor, (CraftingMonitorTile)null);
            PlayerSlot var10002 = this.slot;
            Objects.requireNonNull(var10002);
            NetworkHooks.openGui(var10000, var10001, var10002::writePlayerSlot);
            this.drainEnergy(RS.SERVER_CONFIG.getWirelessCraftingMonitor().getOpenUsage());
            return true;
        } else {
            WorldUtils.sendNoPermissionMessage(this.player);
            return false;
        }
    }

    @Override
    public void drainEnergy(int energy)
    {
        if (RS.SERVER_CONFIG.getWirelessCraftingMonitor().getUseEnergy() && ((ItemWirelessGrid)this.stack.getItem()).getType() != ItemWirelessGrid.Type.CREATIVE) {
            this.stack.getCapability(CapabilityEnergy.ENERGY).ifPresent((energyStorage) -> {
                energyStorage.extractEnergy(energy, false);
                if (energyStorage.getEnergyStored() <= 0) {
                    this.handler.close(this.player);
                    this.player.closeContainer();
                    this.sendOutOfEnergyMessage();
                }
            });
        }
    }

    public void sendOutOfEnergyMessage()
    {
        this.player.sendMessage(new TranslationTextComponent("misc.refinedstorage.network_item.out_of_energy", new Object[]{new TranslationTextComponent(this.stack.getItem().getDescriptionId())}), this.player.getUUID());
    }
}
