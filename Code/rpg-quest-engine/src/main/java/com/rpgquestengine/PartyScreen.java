package com.rpgquestengine;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class PartyScreen extends Screen {
    private record ClickBtn(int x, int y, int w, int h, int action, String arg) {}
    private final List<ClickBtn> clickBtns = new ArrayList<>();

    public PartyScreen() {
        super(Component.literal("Grupo de Aventura"));
    }

    @Override
    protected void init() {
        super.init();
        NetworkHandler.CHANNEL.sendToServer(new PartyActionPacket(0, ""));
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.width - 106, this.height - 28, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xEE0A0A0D);
        clickBtns.clear();

        int boxX = 24;
        int boxY = 20;
        int boxW = this.width - 48;
        int boxH = this.height - 56;

        g.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFFD4AF37);
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE141311);

        g.drawString(this.font, "§6§lGRUPO DE AVENTURA §7(Tecla G)", boxX + 14, boxY + 10, 0xFFD4AF37);
        g.fill(boxX + 12, boxY + 24, boxX + boxW - 12, boxY + 25, 0x66D4AF37);

        int contentY = boxY + 32;

        // Invitación pendiente
        if (!ClientQuestData.PARTY_INVITE_FROM.isEmpty()) {
            int invH = 26;
            g.fill(boxX + 14, contentY, boxX + boxW - 14, contentY + invH, 0xCC222B1E);
            g.drawString(this.font, "§aInvitación de: §e" + ClientQuestData.PARTY_INVITE_FROM, boxX + 22, contentY + 9, 0xFFFFFF);
            drawActionBtn(g, boxX + boxW - 170, contentY + 4, 70, 18, "§aAceptar", 0xFF2E4E2E, 3, "");
            drawActionBtn(g, boxX + boxW - 94, contentY + 4, 70, 18, "§cRechazar", 0xFF4E2E2E, 4, "");
            contentY += invH + 10;
        }

        int colW = (boxW - 42) / 2;
        int leftX = boxX + 14;
        int rightX = leftX + colW + 14;
        int colH = boxY + boxH - contentY - 14;

        // Columna izquierda: Tu Grupo
        g.fill(leftX, contentY, leftX + colW, contentY + colH, 0xAA191714);
        g.drawString(this.font, "§eMIEMBROS DEL GRUPO", leftX + 8, contentY + 8, 0xFFD4AF37);

        if (!ClientQuestData.PARTY_IN_PARTY) {
            g.drawString(this.font, "§7No estás en ningún grupo.", leftX + 8, contentY + 28, 0xAAAAAA);
            g.drawString(this.font, "§8Sincroniza misiones MAIN, NPCs y", leftX + 8, contentY + 42, 0x888888);
            g.drawString(this.font, "§8comparte bajas/Wither en vivo.", leftX + 8, contentY + 53, 0x888888);
            drawActionBtn(g, leftX + 8, contentY + 72, 130, 20, "§aCrear Grupo", 0xFF2E4E2E, 1, "");
        } else {
            int my = contentY + 26;
            for (SyncPartyPacket.MemberEntry m : ClientQuestData.PARTY_MEMBERS) {
                if (my + 20 > contentY + colH - 34) break;
                g.fill(leftX + 6, my, leftX + colW - 6, my + 18, 0x8822201C);
                String prefix = m.isLeader ? "§6★ " : "§7• ";
                String status = m.online ? "§a(Online)" : "§8(Offline)";
                g.drawString(this.font, prefix + "§f" + m.name + " " + status, leftX + 10, my + 5, 0xFFFFFF);
                if (ClientQuestData.PARTY_IS_LEADER && !m.isLeader) {
                    drawActionBtn(g, leftX + colW - 66, my + 1, 56, 16, "§cSacar", 0xFF4E2E2E, 7, m.uuid);
                }
                my += 21;
            }
            int btnY = contentY + colH - 26;
            drawActionBtn(g, leftX + 8, btnY, Math.min(130, colW / 2), 20, "§bSincronizar MAIN", 0xFF233A4E, 6, "");
            drawActionBtn(g, leftX + colW - 88, btnY, 80, 20, "§cSalir", 0xFF4E2E2E, 5, "");
        }

        // Columna derecha: Jugadores conectados
        g.fill(rightX, contentY, rightX + colW, contentY + colH, 0xAA191714);
        g.drawString(this.font, "§eJUGADORES CONECTADOS", rightX + 8, contentY + 8, 0xFFD4AF37);

        if (ClientQuestData.PARTY_INVITABLE.isEmpty()) {
            g.drawString(this.font, "§7No hay otros jugadores disponibles.", rightX + 8, contentY + 28, 0x888888);
        } else {
            int py = contentY + 26;
            boolean canInvite = !ClientQuestData.PARTY_IN_PARTY || ClientQuestData.PARTY_IS_LEADER;
            for (String pName : ClientQuestData.PARTY_INVITABLE) {
                if (py + 20 > contentY + colH - 10) break;
                g.fill(rightX + 6, py, rightX + colW - 6, py + 18, 0x8822201C);
                g.drawString(this.font, "§f" + pName, rightX + 10, py + 5, 0xFFFFFF);
                if (canInvite) {
                    drawActionBtn(g, rightX + colW - 66, py + 1, 56, 16, "§aInvitar", 0xFF2E4E2E, 2, pName);
                }
                py += 21;
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawActionBtn(GuiGraphics g, int x, int y, int w, int h, String label, int bg, int action, String arg) {
        g.fill(x, y, x + w, y + h, 0xFFD4AF37);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
        clickBtns.add(new ClickBtn(x, y, w, h, action, arg));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickBtn b : clickBtns) {
                if (mouseX >= b.x() && mouseX <= b.x() + b.w() && mouseY >= b.y() && mouseY <= b.y() + b.h()) {
                    NetworkHandler.CHANNEL.sendToServer(new PartyActionPacket(b.action(), b.arg()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
