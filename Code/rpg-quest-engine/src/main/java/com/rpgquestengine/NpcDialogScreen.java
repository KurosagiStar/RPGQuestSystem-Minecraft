package com.rpgquestengine;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class NpcDialogScreen extends Screen {
    private final OpenNpcDialogPacket data;

    private static final int BOX_W = 320;
    private static final int BOX_H = 165;

    public NpcDialogScreen(OpenNpcDialogPacket data) {
        super(Component.literal(data.npcName));
        this.data = data;
    }

    @Override
    protected void init() {
        int left = (this.width - BOX_W) / 2;
        int top = (this.height - BOX_H) / 2;
        int btnY = top + BOX_H - 30;

        if ("AVAILABLE".equals(data.state)) {
            this.addRenderableWidget(Button.builder(Component.literal("§aAceptar Misión"), b -> {
                NetworkHandler.CHANNEL.sendToServer(new NpcDialogActionPacket("ACCEPT", data.questId));
                this.onClose();
            }).bounds(left + 20, btnY, 130, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                    .bounds(left + BOX_W - 110, btnY, 90, 20).build());
        } else if ("TURN_IN".equals(data.state)) {
            this.addRenderableWidget(Button.builder(Component.literal("§6Completar Misión"), b -> {
                NetworkHandler.CHANNEL.sendToServer(new NpcDialogActionPacket("COMPLETE", data.questId));
                this.onClose();
            }).bounds(left + 20, btnY, 140, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                    .bounds(left + BOX_W - 110, btnY, 90, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                    .bounds(left + (BOX_W - 100) / 2, btnY, 100, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int left = (this.width - BOX_W) / 2;
        int top = (this.height - BOX_H) / 2;

        // Fondo oscuro RPG + borde dorado
        g.fill(left - 2, top - 2, left + BOX_W + 2, top + BOX_H + 2, 0xFFD4AF37);
        g.fill(left, top, left + BOX_W, top + BOX_H, 0xEE141418);
        g.fill(left, top + 24, left + BOX_W, top + 25, 0x88D4AF37);

        // Nombre del NPC
        g.drawString(this.font, "§6" + data.npcName, left + 14, top + 9, 0xFFFFFF);

        int textY = top + 34;
        int maxTextW = BOX_W - 28;

        String body;
        String sub = "";
        switch (data.state) {
            case "AVAILABLE" -> {
                body = "Tengo un encargo para ti: §e" + data.questTitle;
                sub = "§7Objetivo: §f" + data.questDesc + (data.rewardXp > 0 ? "  §a(+" + data.rewardXp + " XP)" : "");
            }
            case "TURN_IN" -> {
                body = "¡Has completado §e" + data.questTitle + "§f! Excelente trabajo.";
                sub = data.rewardXp > 0 ? "§aRecompensa: +" + data.rewardXp + " XP" : "§aLista para entregar.";
            }
            case "IN_PROGRESS" -> {
                body = "¿Cómo vas con §e" + data.questTitle + "§f?";
                sub = "§7Objetivo pendiente: §f" + data.questDesc;
            }
            case "LOCKED" -> {
                body = "Aún no estás listo para mis encargos.";
                sub = "§cRequiere completar: §e" + data.questTitle;
            }
            default -> {
                body = "Ya completaste todos mis encargos por ahora, aventurero.";
                sub = "§7Vuelve más adelante si surgen nuevos peligros.";
            }
        }

        List<FormattedCharSequence> lines = this.font.split(Component.literal(body), maxTextW);
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, left + 14, textY, 0xFFFFFF);
            textY += 12;
        }

        if (!sub.isEmpty()) {
            textY += 8;
            List<FormattedCharSequence> subLines = this.font.split(Component.literal(sub), maxTextW);
            for (FormattedCharSequence line : subLines) {
                g.drawString(this.font, line, left + 14, textY, 0xDDDDDD);
                textY += 12;
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
