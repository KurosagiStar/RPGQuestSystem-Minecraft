package com.rpgquestengine;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HudPositionScreen extends Screen {
    private final Screen parentScreen;
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public HudPositionScreen(Screen parent) {
        super(Component.literal("Ajustar Posición del HUD"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("Guardar y Salir"), b -> {
            QuestConfig.saveSettings();
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
        }).bounds(this.width / 2 - 60, this.height - 35, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, "§e§lArrastra la caja del HUD con el ratón a la posición deseada", this.width / 2, 15, 0xFFFFFF);

        int boxX = QuestConfig.hudPosX;
        int boxY = QuestConfig.hudPosY;
        int boxW = 205;
        int boxH = 55;

        if (dragging) {
            QuestConfig.hudPosX = Math.max(0, Math.min(this.width - boxW, mouseX - dragOffsetX));
            QuestConfig.hudPosY = Math.max(0, Math.min(this.height - boxH, mouseY - dragOffsetY));
        }

        graphics.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, 0xCC111111);
        graphics.fill(boxX - 3, boxY - 2, boxX - 2, boxY + boxH + 2, 0xFFFFAA00);

        graphics.drawString(this.font, "§6[Principal] §fMisión de Ejemplo", boxX + 4, boxY + 4, 0xFFFFFF);
        graphics.drawString(this.font, "§7Descripción del objetivo activo", boxX + 4, boxY + 16, 0xAAAAAA);
        graphics.drawString(this.font, "§eProgreso: §f3 / 5", boxX + 4, boxY + 28, 0xFFFFFF);

        graphics.fill(boxX + 4, boxY + 40, boxX + boxW - 8, boxY + 44, 0xFF333333);
        graphics.fill(boxX + 4, boxY + 40, boxX + (boxW - 8) * 3 / 5, boxY + 44, 0xFFFFAA00);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int boxX = QuestConfig.hudPosX;
        int boxY = QuestConfig.hudPosY;
        int boxW = 205;
        int boxH = 55;

        if (button == 0 && mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH) {
            this.dragging = true;
            this.dragOffsetX = (int) mouseX - boxX;
            this.dragOffsetY = (int) mouseY - boxY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}

