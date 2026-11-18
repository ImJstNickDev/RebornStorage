package net.gigabit101.rebornstorage.containers;

import com.refinedmods.refinedstorage.RSContainers;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingManager;
import com.refinedmods.refinedstorage.container.CraftingMonitorContainer;
import com.refinedmods.refinedstorage.tile.craftingmonitor.ICraftingMonitor;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;

/** Same RS screen, packets and controls; only redundant intermediate snapshots are coalesced. */
public class CoalescingCraftingMonitorContainer extends CraftingMonitorContainer {
  private static final Set<CoalescingCraftingMonitorContainer> pending =
      Collections.newSetFromMap(new IdentityHashMap<CoalescingCraftingMonitorContainer, Boolean>());
  private ICraftingManager attached;
  private boolean dirty, closed;

  public CoalescingCraftingMonitorContainer(ICraftingMonitor monitor, PlayerEntity player, int id) {
    super(RSContainers.WIRELESS_CRAFTING_MONITOR, monitor, null, player, id);
  }

  @Override
  public void broadcastChanges() {
    if (!getPlayer().level.isClientSide && !closed) {
      ICraftingManager current = getCraftingMonitor().getCraftingManager();
      if (attached != current) {
        if (attached != null) attached.removeListener(this);
        attached = current;
        if (attached != null) attached.addListener(this);
        onChanged();
      }
    }
    // Keep the original slot synchronization and RS container behavior. Its initial listener
    // registration is idempotent (a Set); onAttached is coalesced along with other notifications.
    super.broadcastChanges();
  }

  @Override
  public void onChanged() {
    if (!closed && !getPlayer().level.isClientSide && !dirty) {
      dirty = true;
      pending.add(this);
    }
  }

  protected void sendSnapshot() {
    super.onChanged();
  }

  public static void flushPending() {
    if (pending.isEmpty()) return;
    CoalescingCraftingMonitorContainer[] batch =
        pending.toArray(new CoalescingCraftingMonitorContainer[0]);
    pending.clear();
    for (CoalescingCraftingMonitorContainer menu : batch) {
      menu.dirty = false;
      if (!menu.closed && menu.getPlayer().containerMenu == menu) menu.sendSnapshot();
    }
  }

  public static void clearPending() {
    for (CoalescingCraftingMonitorContainer menu : pending) menu.dirty = false;
    pending.clear();
  }

  @Override
  public void removed(PlayerEntity player) {
    closed = true;
    dirty = false;
    pending.remove(this);
    if (attached != null) attached.removeListener(this);
    attached = null;
    super.removed(player);
  }
}
