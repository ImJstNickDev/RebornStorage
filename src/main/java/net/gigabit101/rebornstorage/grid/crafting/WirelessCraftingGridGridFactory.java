package net.gigabit101.rebornstorage.grid.crafting;

import com.refinedmods.refinedstorage.api.network.grid.GridFactoryType;
import com.refinedmods.refinedstorage.api.network.grid.IGrid;
import com.refinedmods.refinedstorage.api.network.grid.IGridFactory;
import com.refinedmods.refinedstorage.inventory.player.PlayerSlot;
import net.gigabit101.rebornstorage.Constants;
import net.gigabit101.rebornstorage.grid.crafting.WirelessCraftingGrid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;

public class WirelessCraftingGridGridFactory implements IGridFactory
{
    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "wireless_crafting_grid");

    @Nullable
    @Override
    public IGrid createFromStack(PlayerEntity player, ItemStack stack, PlayerSlot slot)
    {
        return new WirelessCraftingGrid(stack, player.level, player.getServer(), slot);
    }

    @Nullable
    @Override
    public IGrid createFromBlock(PlayerEntity player, BlockPos pos)
    {
        return null;
    }

    @Nullable
    @Override
    public TileEntity getRelevantTile(World level, BlockPos pos)
    {
        return null;
    }

    @Override
    public GridFactoryType getType()
    {
        return GridFactoryType.STACK;
    }

}
