package com.rpgquestengine;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class QuestToast implements Toast {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/toasts.png");

    private final String header;
    private final String title;
    private final ItemStack icon;
    private final int headerColor;

    public QuestToast(String header, String title, ItemStack icon, int headerColor) {
        this.header = header;
        this.title = title;
        this.icon = icon;
        this.headerColor = headerColor;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        // Tarjeta estilo Darkomizer con borde negro y acento lateral
        UiStyle.drawRoundedCard(graphics, 0, 0, this.width(), this.height(), 3, UiStyle.BG_DARK, UiStyle.BORDER_PANEL, false);
        graphics.fill(2, 3, 4, this.height() - 3, 0xFF000000 | (this.headerColor & 0x00FFFFFF));

        Font font = toastComponent.getMinecraft().font;

        // Renderizar ícono del logro (espada, pergamino, etc.)
        if (!this.icon.isEmpty()) {
            graphics.renderFakeItem(this.icon, 8, 8);
        }

        // Título del logro / misión
        graphics.drawString(font, this.header, 30, 7, this.headerColor, false);

        List<FormattedCharSequence> titleLines = font.split(Component.literal(this.title), 120);
        if (!titleLines.isEmpty()) {
            graphics.drawString(font, titleLines.get(0), 30, 18, 0xFFFFFF, false);
        }

        // Duración: 5 segundos visible
        return timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}
