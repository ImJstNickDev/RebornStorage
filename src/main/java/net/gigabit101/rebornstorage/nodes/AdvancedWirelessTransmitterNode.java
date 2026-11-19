package net.gigabit101.rebornstorage.nodes;

import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorage.api.network.INetworkNodeVisitor;
import com.refinedmods.refinedstorage.apiimpl.network.node.WirelessTransmitterNetworkNode;
import com.refinedmods.refinedstorage.inventory.item.UpgradeItemHandler;
import com.refinedmods.refinedstorage.item.UpgradeItem;
import net.gigabit101.rebornstorage.Constants;
import net.gigabit101.rebornstorage.RebornStorageConfig;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Reuse the native upgrade inventory and RSInfinityBooster's transmitter hooks. */
public class AdvancedWirelessTransmitterNode extends WirelessTransmitterNetworkNode {
  public static final ResourceLocation ID =
      new ResourceLocation(Constants.MOD_ID, "advanced_wireless_transmitter");

  public AdvancedWirelessTransmitterNode(World world, BlockPos pos) {
    super(world, pos);
  }

  @Override
  public ResourceLocation getId() {
    return ID;
  }

  @Override
  public int getRange() {
    if (super.getRange() == Integer.MAX_VALUE) return Integer.MAX_VALUE;
    int upgrades = ((UpgradeItemHandler) getUpgrades()).getUpgradeCount(UpgradeItem.Type.RANGE);
    long range =
        RebornStorageConfig.TRANSMITTER_RANGE.get()
            + (long) upgrades * RebornStorageConfig.TRANSMITTER_UPGRADE_RANGE.get();
    return (int) Math.min(Integer.MAX_VALUE, range);
  }

  @Override
  public int getEnergyUsage() {
    long upgradeCost =
        (long) super.getEnergyUsage() - RS.SERVER_CONFIG.getWirelessTransmitter().getUsage();
    return (int)
        Math.min(Integer.MAX_VALUE, RebornStorageConfig.TRANSMITTER_COST.get() + upgradeCost);
  }

  @Override
  public void visit(INetworkNodeVisitor.Operator operator) {
    Direction support = getDirection();
    if (support != null) operator.apply(world, pos.relative(support), support.getOpposite());
  }
}
