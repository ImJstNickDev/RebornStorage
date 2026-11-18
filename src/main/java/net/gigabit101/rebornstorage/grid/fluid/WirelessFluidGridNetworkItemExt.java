package net.gigabit101.rebornstorage.grid.fluid;

import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.item.INetworkItemManager;
import com.refinedmods.refinedstorage.api.network.security.Permission;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.apiimpl.network.grid.factory.WirelessFluidGridGridFactory;
import com.refinedmods.refinedstorage.apiimpl.network.item.WirelessFluidGridNetworkItem;
import com.refinedmods.refinedstorage.inventory.player.PlayerSlot;
import com.refinedmods.refinedstorage.util.WorldUtils;
import net.gigabit101.rebornstorage.items.ItemWirelessGrid;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public class WirelessFluidGridNetworkItemExt extends WirelessFluidGridNetworkItem
{
    private final INetworkItemManager handler;
    private final PlayerEntity player;
    private final ItemStack stack;
    private final PlayerSlot slot;

    public WirelessFluidGridNetworkItemExt(INetworkItemManager handler, PlayerEntity player, ItemStack stack, PlayerSlot slot)
    {
        super(handler, player, stack, slot);
        this.handler = handler;
        this.player = player;
        this.stack = stack;
        this.slot = slot;
    }

    public boolean onOpen(INetwork network) {
        if (!network.canRun()) return false;
        IEnergyStorage energy = stack.getCapability(CapabilityEnergy.ENERGY, null).orElse(null);
        if (RS.SERVER_CONFIG.getWirelessFluidGrid().getUseEnergy() && ((ItemWirelessGrid)this.stack.getItem()).getType() != ItemWirelessGrid.Type.CREATIVE && energy != null && energy.getEnergyStored() <= RS.SERVER_CONFIG.getWirelessFluidGrid().getOpenUsage()) {
            this.sendOutOfEnergyMessage();
            return false;
        } else if (!network.getSecurityManager().hasPermission(Permission.MODIFY, this.player)) {
            WorldUtils.sendNoPermissionMessage(this.player);
            return false;
        } else {
            API.instance().getGridManager().openGrid(WirelessFluidGridGridFactory.ID, (ServerPlayerEntity)this.player, this.stack, this.slot);
            this.drainEnergy(RS.SERVER_CONFIG.getWirelessFluidGrid().getOpenUsage());
            return true;
        }
    }

    @Override
    public void drainEnergy(int energy)
    {
        if (RS.SERVER_CONFIG.getWirelessFluidGrid().getUseEnergy() && ((ItemWirelessGrid)this.stack.getItem()).getType() != ItemWirelessGrid.Type.CREATIVE) {
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

    private void sendOutOfEnergyMessage() {
        this.player.sendMessage(new TranslationTextComponent("misc.refinedstorage.network_item.out_of_energy", new Object[]{new TranslationTextComponent(this.stack.getItem().getDescriptionId())}), this.player.getUUID());
    }
}
