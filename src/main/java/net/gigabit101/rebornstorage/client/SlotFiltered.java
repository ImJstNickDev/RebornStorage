package net.gigabit101.rebornstorage.client;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPatternProvider;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import javax.annotation.Nonnull;

public class SlotFiltered extends SlotItemHandler
{
    public SlotFiltered(IItemHandler inventoryIn, int index, int xPosition, int yPosition)
    {
        super(inventoryIn, index, xPosition, yPosition);
    }

    @Override
    public boolean mayPlace(@Nonnull ItemStack stack)
    {
        return stack.getItem() instanceof ICraftingPatternProvider;
    }
}
