package net.gigabit101.rebornstorage.client.screens;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.gigabit101.rebornstorage.containers.ContainerMultiCrafter;
import net.gigabit101.rebornstorage.packet.PacketGui;
import net.gigabit101.rebornstorage.packet.PacketHandler;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.entity.player.PlayerInventory;

public class ScreenMultiCrafter extends ContainerScreen<ContainerMultiCrafter>
{
    ScreenBuilder builder = new ScreenBuilder();

    ContainerMultiCrafter containerMultiCrafter;
    public static int maxSlotsPerPage = 78;
    Button buttonNext;
    Button buttonBack;

    public ScreenMultiCrafter(ContainerMultiCrafter containerMultiCrafter, PlayerInventory playerInventory, ITextComponent title)
    {
        super(containerMultiCrafter, playerInventory, title);
        this.imageWidth = 250;
        this.imageHeight = 240;
        this.inventoryLabelY = 130;
        this.containerMultiCrafter = containerMultiCrafter;

    }

    @Override
    public void init()
    {
        super.init();
        buttonNext = new Button(this.leftPos + 209, this.topPos + 172, 20, 20, new StringTextComponent(">"), p_onPress_1_ ->
        {
            boolean shift = Screen.hasShiftDown();
            int next = containerMultiCrafter.page + 1;
            if(shift)
            {
                int test = containerMultiCrafter.page + 10;
                if(containerMultiCrafter.pageCount >= test)
                {
                    next = test;
                }
            }

            if(containerMultiCrafter != null && containerMultiCrafter.blockPos != null)
                PacketHandler.sendToServer(new PacketGui(next, containerMultiCrafter.blockPos));
        });
        buttonBack = new Button(this.leftPos + 13, this.topPos + 172, 20, 20, new StringTextComponent("<"), p_onPress_1_ ->
        {
            boolean shift = Screen.hasShiftDown();
            int next = containerMultiCrafter.page - 1;
            if(shift)
            {
                int test = containerMultiCrafter.page - 10;
                if(test > 0)
                {
                    next = test;
                }
            }

            if(containerMultiCrafter != null && containerMultiCrafter.blockPos != null)
                PacketHandler.sendToServer(new PacketGui(next, containerMultiCrafter.blockPos));
        });
        addButton(buttonBack);
        addButton(buttonNext);

        if (containerMultiCrafter != null)
        {
            if (containerMultiCrafter.pageCount != 0)
            {
                if (containerMultiCrafter.pageCount == containerMultiCrafter.page)
                {
                    buttonNext.active = false;
                }
                if (containerMultiCrafter.page == 1)
                {
                    buttonBack.active = false;
                }
            }
        }
    }

    @Override
    protected void renderLabels(MatrixStack poseStack, int mouseX, int mouseY)
    {
        this.font.draw(poseStack, this.title, (float) this.titleLabelX, (float) this.titleLabelY, 4210752);
        this.font.draw(poseStack, this.inventory.getDisplayName(), (float) this.inventoryLabelX, (float) this.inventoryLabelY, 4210752);
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks)
    {
        this.renderBackground(matrixStack);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
        this.renderTooltip(matrixStack, mouseX, mouseY);

        this.font.draw(matrixStack, new StringTextComponent("Page " + containerMultiCrafter.page + " of " + containerMultiCrafter.pageCount), leftPos + 10, topPos + 224, 4210752);
        if(buttonNext.isMouseOver(mouseX, mouseY) || buttonBack.isMouseOver(mouseX, mouseY))
        {
            renderTooltip(matrixStack, new StringTextComponent("Hold <Left-Shift> to increment by 10"), mouseX, mouseY);
        }
    }

    @Override
    public void renderBg(MatrixStack matrixStack, float partialTicks, int x, int y)
    {
        builder.drawDefaultBackground(this, matrixStack, leftPos, topPos, getXSize(), getYSize(), 256, 256);
        builder.drawPlayerSlots(this, matrixStack, leftPos + getXSize() / 2, topPos + 140, true, 256, 256);
        if (containerMultiCrafter != null)
        {
            if (containerMultiCrafter.pageCount > 0 && containerMultiCrafter.page <= containerMultiCrafter.pageCount)
            {
                drawSlots(13, 6, maxSlotsPerPage, matrixStack);
            }
        }
    }

    public void drawSlots(int col, int rows, int max, MatrixStack matrixStack)
    {
        int i = 0;
        for (int y = 0; y < rows; y++)
        {
            for (int x = 0; x < col; x++)
            {
                i++;
                if (i <= max) builder.drawSlot(this, matrixStack, leftPos + 8 + x * 18, topPos + 20 + y * 18, 256, 256);
            }
        }
    }
}
