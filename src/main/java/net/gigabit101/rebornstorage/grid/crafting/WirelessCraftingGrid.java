package net.gigabit101.rebornstorage.grid.crafting;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.grid.GridType;
import com.refinedmods.refinedstorage.api.network.grid.ICraftingGridListener;
import com.refinedmods.refinedstorage.api.network.security.Permission;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IStackList;
import com.refinedmods.refinedstorage.tile.grid.WirelessGrid;
import com.refinedmods.refinedstorage.inventory.player.PlayerSlot;
import com.refinedmods.refinedstorage.util.StackUtils;
import net.gigabit101.rebornstorage.RebornStorage;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.inventory.IInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.CraftResultInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.world.World;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class WirelessCraftingGrid extends WirelessGrid
{
    @Nullable
    private final MinecraftServer server;
    private final World level;
    private final Set<ICraftingGridListener> listeners = new HashSet<>();
    private boolean queuedSave;

    private final Container craftingMenu = new Container(null, 0)
    {
        @Override
        public boolean stillValid(@Nonnull PlayerEntity player)
        {
            return false;
        }

        @Override
        public void slotsChanged(@Nonnull IInventory container)
        {
            if (server != null)
            {
                onCraftingMatrixChanged();
            }
        }
    };

    private ICraftingRecipe currentRecipe;
    private final CraftingInventory craftingContainer = new CraftingInventory(craftingMenu, 3, 3)
    {
        @Override
        public void setChanged()
        {
            super.setChanged();
            if (!queuedSave && server != null)
            {
                queuedSave = true;
                server.execute(() ->
                {
                    if (!getStack().hasTag())
                    {
                        getStack().setTag(new CompoundNBT());
                    }

                    StackUtils.writeItems(craftingContainer, 1, Objects.requireNonNull(getStack().getTag()));
                    queuedSave = false;
                });
            }
        }
    };
    private final CraftResultInventory craftingResultContainer = new CraftResultInventory();

    public WirelessCraftingGrid(ItemStack stack, World level, @Nullable MinecraftServer server, PlayerSlot slot)
    {
        super(stack, server, slot);
        this.server = server;
        this.level = level;
        if (stack.hasTag())
        {
            StackUtils.readItems(craftingContainer, 1, stack.getTag());
        }
    }

    @Override
    public ITextComponent getTitle()
    {
        return new TranslationTextComponent("gui.refinedstorage.crafting_grid");
    }

    @Override
    public GridType getGridType()
    {
        return GridType.CRAFTING;
    }

    @Override
    public CraftingInventory getCraftingMatrix()
    {
        return craftingContainer;
    }

    @Override
    public CraftResultInventory getCraftingResult()
    {
        return craftingResultContainer;
    }

    @Override
    public void onCraftingMatrixChanged()
    {
        if (currentRecipe == null || !currentRecipe.matches(craftingContainer, level))
        {
            currentRecipe = level.getRecipeManager().getRecipeFor(IRecipeType.CRAFTING, craftingContainer, level).orElse(null);
        }
        if (currentRecipe == null)
        {
            craftingResultContainer.setItem(0, ItemStack.EMPTY);
        } else
        {
            craftingResultContainer.setItem(0, currentRecipe.assemble(craftingContainer));
        }

        listeners.forEach(ICraftingGridListener::onCraftingMatrixChanged);

        if (!getStack().hasTag())
        {
            getStack().setTag(new CompoundNBT());
        }

        StackUtils.writeItems(craftingContainer, 1, getStack().getTag());
    }

    @Override
    public void onCrafted(PlayerEntity player, @Nullable IStackList<ItemStack> availableItems, @Nullable IStackList<ItemStack> usedItems)
    {
        RebornStorage.RSAPI.getCraftingGridBehavior().onCrafted(this, currentRecipe, player, availableItems, usedItems);
    }

    @Override
    public void onClear(PlayerEntity player)
    {
        INetwork network = getNetwork();

        if (network != null && network.getSecurityManager().hasPermission(Permission.INSERT, player))
        {
            for (int i = 0; i < craftingContainer.getContainerSize(); ++i)
            {
                ItemStack slot = craftingContainer.getItem(i);

                if (!slot.isEmpty())
                {
                    craftingContainer.setItem(i, network.insertItem(slot, slot.getCount(), Action.PERFORM));

                    network.getItemStorageTracker().changed(player, slot.copy());
                }
            }
        }
    }

    @Override
    public void onCraftedShift(PlayerEntity player)
    {
        RebornStorage.RSAPI.getCraftingGridBehavior().onCraftedShift(this, player);
    }

    @Override
    public void onRecipeTransfer(PlayerEntity player, ItemStack[][] recipe)
    {
        RebornStorage.RSAPI.getCraftingGridBehavior().onRecipeTransfer(this, player, recipe);
    }

    @Override
    public void addCraftingListener(ICraftingGridListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void removeCraftingListener(ICraftingGridListener listener)
    {
        listeners.remove(listener);
    }
}
