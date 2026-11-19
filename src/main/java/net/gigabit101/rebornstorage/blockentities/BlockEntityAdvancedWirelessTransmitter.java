package net.gigabit101.rebornstorage.blockentities;

import com.refinedmods.refinedstorage.tile.NetworkNodeTile;
import com.refinedmods.refinedstorage.tile.data.TileDataParameter;
import net.gigabit101.rebornstorage.init.ModBlocks;
import net.gigabit101.rebornstorage.nodes.AdvancedWirelessTransmitterNode;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockEntityAdvancedWirelessTransmitter
    extends NetworkNodeTile<AdvancedWirelessTransmitterNode> {
  public static final TileDataParameter<Integer, BlockEntityAdvancedWirelessTransmitter> RANGE =
      new TileDataParameter<>(DataSerializers.INT, 0, tile -> tile.getNode().getRange());

  public BlockEntityAdvancedWirelessTransmitter() {
    super(ModBlocks.TRANSMITTER_TILE.get());
    dataManager.addWatchedParameter(RANGE);
  }

  @Override
  public AdvancedWirelessTransmitterNode createNode(World world, BlockPos pos) {
    return new AdvancedWirelessTransmitterNode(world, pos);
  }
}
