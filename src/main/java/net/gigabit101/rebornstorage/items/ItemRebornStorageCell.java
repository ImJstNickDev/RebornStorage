package net.gigabit101.rebornstorage.items;

import com.refinedmods.refinedstorage.api.storage.StorageType;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskProvider;
import com.refinedmods.refinedstorage.api.storage.disk.StorageDiskSyncData;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.render.Styles;
import net.gigabit101.rebornstorage.client.CreativeTabRebornStorage;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskFactory;
import net.gigabit101.rebornstorage.storage.DiskTier;
import net.gigabit101.rebornstorage.storage.RebornItemDiskFactory;
import net.gigabit101.rebornstorage.storage.RebornFluidDiskFactory;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public class ItemRebornStorageCell extends Item implements IStorageDiskProvider
{
    private static final String NBT_ID = "id";

    private final DiskTier tier;

    public ItemRebornStorageCell(DiskTier tier)
    {
        super(new Item.Properties().tab(CreativeTabRebornStorage.INSTANCE).stacksTo(1));
        this.tier = tier;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected)
    {
        super.inventoryTick(stack, world, entity, itemSlot, isSelected);
        if (!world.isClientSide && !isValid(stack) && entity instanceof PlayerEntity)
        {
            UUID id = UUID.randomUUID();
            IStorageDiskFactory<?> factory = tier.type == StorageType.FLUID ? RebornFluidDiskFactory.INSTANCE : RebornItemDiskFactory.INSTANCE;
            API.instance().getStorageDiskManager((ServerWorld) world).set(id, factory.create((ServerWorld) world, tier.capacity, entity.getUUID()));
            API.instance().getStorageDiskManager((ServerWorld) world).markForSaving();
            setId(stack, id);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable World level, List<net.minecraft.util.text.ITextComponent> tooltip, ITooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        if (isValid(stack))
        {
            UUID id = getId(stack);

            API.instance().getStorageDiskSync().sendRequest(id);

            StorageDiskSyncData data = API.instance().getStorageDiskSync().getData(id);

            if (data != null)
            {
                if (data.getCapacity() == -1)
                {
                    tooltip.add(new TranslationTextComponent("misc.refinedstorage.storage.stored", API.instance().getQuantityFormatter().format(data.getStored())).setStyle(Styles.GRAY));
                } else
                {
                    tooltip.add(new TranslationTextComponent("misc.refinedstorage.storage.stored_capacity", API.instance().getQuantityFormatter().format(data.getStored()), API.instance().getQuantityFormatter().format(data.getCapacity())).setStyle(Styles.GRAY));
                }
            }
            if (flag.isAdvanced())
            {
                tooltip.add(new StringTextComponent(id.toString()));
            }
        }
    }


    /** Match RS: only an empty disk can be separated into its component and housing. */
    @Override
    public net.minecraft.util.ActionResult<ItemStack> use(World world, PlayerEntity player, net.minecraft.util.Hand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide && player.isShiftKeyDown() && isValid(stack))
        {
            com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskManager manager = API.instance().getStorageDiskManager((ServerWorld) world);
            com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk<?> disk = manager.getByStack(stack);
            if (disk != null && disk.getStored() == 0)
            {
                ItemStack component = new ItemStack(tier.part().get());
                if (!player.inventory.add(component)) player.drop(component, false);
                manager.remove(getId(stack));
                manager.markForSaving();
                return net.minecraft.util.ActionResult.success(new ItemStack(com.refinedmods.refinedstorage.RSItems.STORAGE_HOUSING.get()));
            }
        }
        return net.minecraft.util.ActionResult.pass(stack);
    }

    @Override
    public int getEntityLifespan(ItemStack stack, World world)
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public UUID getId(ItemStack disk)
    {
        return disk.getTag().getUUID(NBT_ID);
    }

    @Override
    public void setId(ItemStack disk, UUID id)
    {
        disk.getOrCreateTag().putUUID(NBT_ID, id);
    }

    @Override
    public boolean isValid(ItemStack disk)
    {
        return disk.hasTag() && disk.getTag().hasUUID(NBT_ID);
    }

    @Override
    public int getCapacity(ItemStack disk)
    {
        return tier.capacity;
    }

    @Override
    public StorageType getType()
    {
        return tier.type;
    }
}
