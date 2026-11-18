package net.gigabit101.rebornstorage.core.multiblock.events;

import net.gigabit101.rebornstorage.core.multiblock.MultiblockRegistry;
import net.minecraft.world.World;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MultiblockEventHandler
{
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onChunkLoad(ChunkEvent.Load event)
    {
        Chunk chunk = (Chunk) event.getChunk();
        IWorld level = event.getChunk().getWorldForge();
        MultiblockRegistry.onChunkLoaded((World) level, chunk);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWorldUnload(WorldEvent.Unload event)
    {
        MultiblockRegistry.onWorldUnloaded((World) event.getWorld());
    }
}
