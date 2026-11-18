package net.gigabit101.rebornstorage.client;

import net.gigabit101.rebornstorage.init.ModItems;
import net.gigabit101.rebornstorage.Constants;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;

public class CreativeTabRebornStorage extends ItemGroup
{
    public static CreativeTabRebornStorage INSTANCE = new CreativeTabRebornStorage();

    public CreativeTabRebornStorage()
    {
        super(Constants.MOD_ID);
    }

    @Override
    public ItemStack makeIcon()
    {
        return new ItemStack(ModItems.DISK_262144K.get());
    }
}
