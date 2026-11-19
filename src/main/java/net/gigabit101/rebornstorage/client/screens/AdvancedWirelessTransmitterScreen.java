package net.gigabit101.rebornstorage.client.screens;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.refinedmods.refinedstorage.screen.BaseScreen;
import com.refinedmods.refinedstorage.screen.widget.sidebutton.RedstoneModeSideButton;
import com.refinedmods.refinedstorage.tile.NetworkNodeTile;
import net.gigabit101.rebornstorage.blockentities.BlockEntityAdvancedWirelessTransmitter;
import net.gigabit101.rebornstorage.containers.AdvancedWirelessTransmitterContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.text.ITextComponent;

public class AdvancedWirelessTransmitterScreen
    extends BaseScreen<AdvancedWirelessTransmitterContainer> {
  public AdvancedWirelessTransmitterScreen(
      AdvancedWirelessTransmitterContainer container,
      PlayerInventory inventory,
      ITextComponent title) {
    super(container, 211, 137, inventory, title);
  }

  @Override
  public void onPostInit(int x, int y) {
    this.addSideButton(new RedstoneModeSideButton(this, NetworkNodeTile.REDSTONE_MODE));
  }

  @Override
  public void tick(int x, int y) {}

  @Override
  public void renderBackground(MatrixStack matrixStack, int x, int y, int mouseX, int mouseY) {
    this.bindTexture("refinedstorage", "gui/wireless_transmitter.png");
    this.blit(matrixStack, x, y, 0, 0, this.imageWidth, this.imageHeight);
  }

  @Override
  public void renderForeground(MatrixStack matrixStack, int mouseX, int mouseY) {
    this.renderString(matrixStack, 7, 7, this.title.getString());
    this.renderString(
        matrixStack,
        28,
        25,
        I18n.get(
            "gui.refinedstorage.wireless_transmitter.distance",
            BlockEntityAdvancedWirelessTransmitter.RANGE.getValue()));
    this.renderString(matrixStack, 7, 43, I18n.get("container.inventory"));
  }
}
