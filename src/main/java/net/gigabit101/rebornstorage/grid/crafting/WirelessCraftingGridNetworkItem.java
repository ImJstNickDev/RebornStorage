package net.gigabit101.rebornstorage.grid.crafting;

import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.item.INetworkItem;
import com.refinedmods.refinedstorage.api.network.item.INetworkItemManager;
import com.refinedmods.refinedstorage.api.network.security.Permission;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.inventory.player.PlayerSlot;
import com.refinedmods.refinedstorage.util.WorldUtils;
import net.gigabit101.rebornstorage.items.ItemWirelessGrid;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public class WirelessCraftingGridNetworkItem implements INetworkItem
{
    private final INetworkItemManager handler;
    private final PlayerEntity player;
    private final ItemStack stack;
    private final PlayerSlot slot;


    public WirelessCraftingGridNetworkItem(INetworkItemManager handler, PlayerEntity player, ItemStack stack, PlayerSlot slot)
    {
        this.handler = handler;
        this.player = player;
        this.stack = stack;
        this.slot = slot;
    }

    @Override
    public PlayerEntity getPlayer()
    {
        return player;
    }

    @Override
    public boolean onOpen(INetwork network)
    {
        if (!network.canRun()) return false;
        IEnergyStorage energy = stack.getCapability(CapabilityEnergy.ENERGY, null).orElse(null);

        if (RS.SERVER_CONFIG.getWirelessGrid().getUseEnergy() && ((ItemWirelessGrid) stack.getItem()).getType() != ItemWirelessGrid.Type.CREATIVE &&
                energy != null && energy.getEnergyStored() <= RS.SERVER_CONFIG.getWirelessGrid().getOpenUsage())
        {
            sendOutOfEnergyMessage();
            return false;
        }

        if (!network.getSecurityManager().hasPermission(Permission.MODIFY, player))
        {
            WorldUtils.sendNoPermissionMessage(player);

            return false;
        }

        API.instance().getGridManager().openGrid(WirelessCraftingGridGridFactory.ID, (ServerPlayerEntity) player, stack, slot);

        this.drainEnergy(RS.SERVER_CONFIG.getWirelessGrid().getOpenUsage());
        return true;
    }

    private void sendOutOfEnergyMessage() {
        this.player.sendMessage(new TranslationTextComponent("misc.refinedstorage.network_item.out_of_energy", new Object[]{new TranslationTextComponent(this.stack.getItem().getDescriptionId())}), this.player.getUUID());
    }

    @Override
    public void drainEnergy(int energy)
    {
        if (RS.SERVER_CONFIG.getWirelessGrid().getUseEnergy() && ((ItemWirelessGrid)this.stack.getItem()).getType() != ItemWirelessGrid.Type.CREATIVE) {
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
}
