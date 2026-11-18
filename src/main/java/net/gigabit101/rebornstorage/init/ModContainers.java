package net.gigabit101.rebornstorage.init;

import net.gigabit101.rebornstorage.containers.ContainerMultiCrafter;
import net.gigabit101.rebornstorage.Constants;
import net.minecraft.inventory.container.ContainerType;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;

public class ModContainers
{
    public static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, Constants.MOD_ID);
    public static final RegistryObject<ContainerType<ContainerMultiCrafter>> MULTI_CRAFTER_CONTAINER = CONTAINERS.register("container_multiblock_crafter", () -> IForgeContainerType.create(ContainerMultiCrafter::new));
}
