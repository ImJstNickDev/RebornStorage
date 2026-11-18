package net.gigabit101.rebornstorage;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.API;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.gigabit101.rebornstorage.core.multiblock.MultiblockRegistry;
import net.gigabit101.rebornstorage.nodes.CraftingNode;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;

/**
 * Coalesce after every world's network/node updates; never recursively rebuild a graph in a
 * callback.
 */
@Mod.EventBusSubscriber(modid = Constants.MOD_ID)
public class RebornStorageEventHandler {
  private static final Set<INetwork> graphs = identitySet();
  private static final Set<INetwork> managers = identitySet();
  private static final Set<CraftingNode> providers = identitySet();

  private static <T> Set<T> identitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
  }

  private static <T> Set<T> drain(Set<T> set) {
    if (set.isEmpty()) return Collections.emptySet();
    Set<T> copy = identitySet();
    copy.addAll(set);
    set.clear();
    return copy;
  }

  public static void graph(INetwork network) {
    if (network != null) graphs.add(network);
  }

  public static void queue(INetwork network) {
    if (network != null) managers.add(network);
  }

  public static void patterns(CraftingNode node) {
    if (!node.getWorld().isClientSide) {
      node.deferPatterns();
      providers.add(node);
    }
  }

  @SubscribeEvent
  public static void worldTick(TickEvent.WorldTickEvent event) {
    if (event.phase == TickEvent.Phase.START) MultiblockRegistry.tickStart(event.world);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void serverTick(TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    for (INetwork network : drain(graphs)) {
      if (network.getWorld().isLoaded(network.getPosition())
          && API.instance()
              .getNetworkManager((ServerWorld) network.getWorld())
              .all()
              .contains(network)) {
        network
            .getNodeGraph()
            .invalidate(Action.PERFORM, network.getWorld(), network.getPosition());
        // RS compares graph entries by position, not object identity. A recreated aggregate at
        // the same anchor can replace a stale entry without receiving onConnected from RS.
        network
            .getNodeGraph()
            .all()
            .forEach(
                entry -> {
                  if (entry.getNode() instanceof CraftingNode
                      && entry.getNode().getNetwork() == null) entry.getNode().onConnected(network);
                });
      }
    }
    for (CraftingNode node : drain(providers)) node.rebuildPatterns("coalesced update");
    for (INetwork network : drain(managers)) {
      if (API.instance()
          .getNetworkManager((ServerWorld) network.getWorld())
          .all()
          .contains(network)) network.getCraftingManager().invalidate();
    }
  }

  @SubscribeEvent
  public static void unload(WorldEvent.Unload event) {
    graphs.removeIf(n -> n.getWorld() == event.getWorld());
    managers.removeIf(n -> n.getWorld() == event.getWorld());
    providers.removeIf(n -> n.getWorld() == event.getWorld());
  }

  @SubscribeEvent
  public static void stopped(FMLServerStoppedEvent event) {
    graphs.clear();
    managers.clear();
    providers.clear();
  }
}
