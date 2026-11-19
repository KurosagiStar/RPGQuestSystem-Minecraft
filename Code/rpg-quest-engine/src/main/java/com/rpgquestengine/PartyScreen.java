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
    private boolean confirmLeave = false;

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

        UiStyle.drawRoundedCard(g, boxX - 1, boxY - 1, boxW + 2, boxH + 2, 7, UiStyle.BG_DARK, UiStyle.BORDER_PANEL, true);
        UiStyle.drawRoundedRect(g, boxX + 2, boxY + 2, boxW - 4, 22, 5, UiStyle.BG_HEADER);
        g.drawString(this.font, "§6§lGRUPO DE AVENTURA §7(Tecla G)", boxX + 14, boxY + 9, 0xFFD4AF37);
        g.fill(boxX + 12, boxY + 25, boxX + boxW - 12, boxY + 26, 0x55E0B84C);

        int contentY = boxY + 32;

        // Invitación pendiente
        if (!ClientQuestData.PARTY_INVITE_FROM.isEmpty()) {
            int invH = 26;
            UiStyle.drawRoundedCard(g, boxX + 14, contentY, boxW - 28, invH, 5, 0xCC1E2C22, UiStyle.ACCENT_GREEN, false);
            g.drawString(this.font, "§aInvitación de: §e" + ClientQuestData.PARTY_INVITE_FROM, boxX + 22, contentY + 9, 0xFFFFFF);
            drawActionBtn(g, mouseX, mouseY, boxX + boxW - 170, contentY + 4, 70, 18, "§aAceptar", UiStyle.ACCENT_GREEN_DARK, 3, "");
            drawActionBtn(g, mouseX, mouseY, boxX + boxW - 94, contentY + 4, 70, 18, "§cRechazar", UiStyle.ACCENT_RED_DARK, 4, "");
            contentY += invH + 10;
        }

        int colW = (boxW - 42) / 2;
        int leftX = boxX + 14;
        int rightX = leftX + colW + 14;
        int colH = boxY + boxH - contentY - 14;

        // Columna izquierda: Tu Grupo
        UiStyle.drawRoundedCard(g, leftX, contentY, colW, colH, 6, UiStyle.BG_PANEL, UiStyle.BORDER_SUBTLE, false);
        g.drawString(this.font, "§eMIEMBROS DEL GRUPO", leftX + 10, contentY + 8, 0xFFD4AF37);

        if (!ClientQuestData.PARTY_IN_PARTY) {
            g.drawString(this.font, "§7No estás en ningún grupo.", leftX + 10, contentY + 28, 0xAAAAAA);
            g.drawString(this.font, "§8Sincroniza misiones MAIN, NPCs y", leftX + 10, contentY + 42, 0x888888);
            g.drawString(this.font, "§8comparte bajas/Wither en vivo.", leftX + 10, contentY + 53, 0x888888);
            drawActionBtn(g, mouseX, mouseY, leftX + 10, contentY + 72, 130, 20, "§aCrear Grupo", UiStyle.ACCENT_GREEN_DARK, 1, "");
        } else {
            int my = contentY + 26;
            for (SyncPartyPacket.MemberEntry m : ClientQuestData.PARTY_MEMBERS) {
                if (my + 20 > contentY + colH - 34) break;
                UiStyle.drawRoundedRect(g, leftX + 6, my, colW - 12, 18, 4, UiStyle.BG_CARD);
                String prefix = m.isLeader ? "§6★ " : "§7• ";
                String status = m.online ? "§a(Online)" : "§8(Offline)";
                g.drawString(this.font, prefix + "§f" + m.name + " " + status, leftX + 12, my + 5, 0xFFFFFF);
                if (ClientQuestData.PARTY_IS_LEADER && !m.isLeader) {
                    drawActionBtn(g, mouseX, mouseY, leftX + colW - 66, my + 1, 56, 16, "§cSacar", UiStyle.ACCENT_RED_DARK, 7, m.uuid);
                }
                my += 21;
            }
            int btnY = contentY + colH - 26;
            drawActionBtn(g, mouseX, mouseY, leftX + 8, btnY, colW - 16, 20, "§cAbandonar Grupo", UiStyle.ACCENT_RED_DARK, -2, "");
        }

        // Columna derecha: Arriba Jugadores Conectados, Abajo Recompensas Sin Reclamar
        int topRightH = Math.max(80, colH / 2 - 4);
        int botRightY = contentY + topRightH + 8;
        int botRightH = colH - topRightH - 8;

        UiStyle.drawRoundedCard(g, rightX, contentY, colW, topRightH, 6, UiStyle.BG_PANEL, UiStyle.BORDER_SUBTLE, false);
        g.drawString(this.font, "§eJUGADORES CONECTADOS", rightX + 10, contentY + 8, 0xFFD4AF37);

        if (ClientQuestData.PARTY_INVITABLE.isEmpty()) {
            g.drawString(this.font, "§7No hay otros jugadores disponibles.", rightX + 10, contentY + 26, 0x888888);
        } else {
            int py = contentY + 24;
            boolean canInvite = !ClientQuestData.PARTY_IN_PARTY || ClientQuestData.PARTY_IS_LEADER;
            for (String pName : ClientQuestData.PARTY_INVITABLE) {
                if (py + 20 > contentY + topRightH - 6) break;
                UiStyle.drawRoundedRect(g, rightX + 6, py, colW - 12, 18, 4, UiStyle.BG_CARD);
                g.drawString(this.font, "§f" + pName, rightX + 12, py + 5, 0xFFFFFF);
                if (canInvite) {
                    drawActionBtn(g, mouseX, mouseY, rightX + colW - 66, py + 1, 56, 16, "§aInvitar", UiStyle.ACCENT_GREEN_DARK, 2, pName);
                }
                py += 20;
            }
        }

        // Recompensas acumuladas sin reclamar
        int rewCnt = ClientQuestData.PARTY_PENDING_REWARDS.size();
        UiStyle.drawRoundedCard(g, rightX, botRightY, colW, botRightH, 6, UiStyle.BG_PANEL, UiStyle.BORDER_SUBTLE, false);
        g.drawString(this.font, "§6RECOMPENSAS SIN RECLAMAR §7(" + rewCnt + ")", rightX + 10, botRightY + 8, 0xFFD4AF37);

        if (rewCnt == 0) {
            g.drawString(this.font, "§8No tienes recompensas pendientes.", rightX + 10, botRightY + 28, 0x777777);
        } else {
            int ry = botRightY + 24;
            for (String qid : ClientQuestData.PARTY_PENDING_REWARDS) {
                if (ry + 20 > botRightY + botRightH - 26) break;
                QuestDefinition q = QuestConfig.getQuestById(qid);
                String qTitle = q != null ? q.title : qid;
                UiStyle.drawRoundedRect(g, rightX + 6, ry, colW - 12, 18, 4, UiStyle.BG_CARD);
                String trimmed = this.font.plainSubstrByWidth(qTitle, colW - 82);
                g.drawString(this.font, "§e" + trimmed, rightX + 12, ry + 5, 0xFFFFFF);
                drawActionBtn(g, mouseX, mouseY, rightX + colW - 66, ry + 1, 56, 16, "§aReclamar", UiStyle.ACCENT_GREEN_DARK, 8, qid);
                ry += 20;
            }
            drawActionBtn(g, mouseX, mouseY, rightX + 8, botRightY + botRightH - 24, colW - 16, 18,
                    "§a§lReclamar Todo (" + rewCnt + ")", UiStyle.ACCENT_GREEN_DARK, 9, "");
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (confirmLeave) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            g.fill(0, 0, this.width, this.height, 0xCC0A0A0D);
            int pw = 236;
            int ph = 88;
            int px = (this.width - pw) / 2;
            int py = (this.height - ph) / 2;
            UiStyle.drawRoundedCard(g, px, py, pw, ph, 7, UiStyle.BG_PANEL, UiStyle.BORDER_PANEL, true);
            g.drawCenteredString(this.font, "§e¿Seguro que deseas", px + pw / 2, py + 15, 0xFFFFFF);
            g.drawCenteredString(this.font, "§eabandonar el grupo?", px + pw / 2, py + 27, 0xFFFFFF);
            drawActionBtn(g, mouseX, mouseY, px + 14, py + ph - 28, 98, 18, "§cSí, abandonar", UiStyle.ACCENT_RED_DARK, 5, "");
            drawActionBtn(g, mouseX, mouseY, px + pw - 112, py + ph - 28, 98, 18, "§7Cancelar", 0xFF2B2E38, -3, "");
            g.pose().popPose();
        }
    }

    private void drawActionBtn(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, String label, int bg, int action, String arg) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        UiStyle.drawPillButton(g, this.font, x, y, w, h, label, bg, bg + 0x00151515, UiStyle.BORDER_SUBTLE, hovered);
        clickBtns.add(new ClickBtn(x, y, w, h, action, arg));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (confirmLeave) {
                for (int i = clickBtns.size() - 1; i >= 0; i--) {
                    ClickBtn b = clickBtns.get(i);
                    if (b.action() != 5 && b.action() != -3) continue;
                    if (mouseX >= b.x() && mouseX <= b.x() + b.w() && mouseY >= b.y() && mouseY <= b.y() + b.h()) {
                        confirmLeave = false;
                        if (b.action() == 5) {
                            NetworkHandler.CHANNEL.sendToServer(new PartyActionPacket(5, ""));
                        }
                        return true;
                    }
                }
                return true;
            }
            for (ClickBtn b : clickBtns) {
                if (mouseX >= b.x() && mouseX <= b.x() + b.w() && mouseY >= b.y() && mouseY <= b.y() + b.h()) {
                    if (b.action() == -2) {
                        confirmLeave = true;
                        return true;
                    }
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
