package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UiStyle {
    public static final int BG_DARK = 0xFF2B2B2E;
    public static final int BG_PANEL = 0xFF222225;
    public static final int BG_CARD = 0xFF1C1C1F;
    public static final int BG_HEADER = 0xFF333337;
    public static final int BORDER_PANEL = 0xFF0B0B0D;
    public static final int BORDER_GOLD = 0xFFE5BE53;
    public static final int BORDER_SUBTLE = 0xFF3E3E42;
    public static final int ACCENT_GREEN = 0xFF4BB83D;
    public static final int ACCENT_GREEN_DARK = 0xFF3C9D30;
    public static final int ACCENT_RED_DARK = 0xFF9E2B36;
    public static final int ACCENT_BLUE_DARK = 0xFF2563EB;
    public static final int ACCENT_SLATE = 0xFF38383D;

    private static int lighten(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, (int) (((argb >>> 16) & 0xFF) + 255 * amount));
        int g = Math.min(255, (int) (((argb >>> 8) & 0xFF) + 255 * amount));
        int b = Math.min(255, (int) ((argb & 0xFF) + 255 * amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.max(0, (int) (((argb >>> 16) & 0xFF) * (1.0f - amount)));
        int g = Math.max(0, (int) (((argb >>> 8) & 0xFF) * (1.0f - amount)));
        int b = Math.max(0, (int) ((argb & 0xFF) * (1.0f - amount)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Rectángulo pixel-art con esquinas biseladas de 1px estilo Minecraft Darkomizer.
     */
    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        if (radius <= 0 || w < 4 || h < 4) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * Dibuja un panel o caja estilo Minecraft Darkomizer:
     * - Si borderColor != 0 (marco exterior / activo): outline negro, bisel 3D elevado y relleno gris carbón.
     * - Si borderColor == 0 (paneles internos / slots): caja hundida con sombra arriba/izq y brillo abajo/der.
     */
    public static void drawRoundedCard(GuiGraphics g, int x, int y, int w, int h, int radius,
                                       int bgColor, int borderColor, boolean withShadow) {
        if (w <= 2 || h <= 2) return;
        if (withShadow) {
            drawRoundedRect(g, x + 2, y + 2, w, h, radius, 0x55000000);
        }

        if (borderColor == 0) {
            // Caja hundida estilo slot/panel interno Darkomizer
            g.fill(x, y, x + w, y + h, bgColor);
            // Sombra arriba e izquierda
            g.fill(x, y, x + w - 1, y + 1, 0xFF131315);
            g.fill(x, y, x + 1, y + h - 1, 0xFF131315);
            // Brillo sutil abajo y derecha
            g.fill(x + 1, y + h - 1, x + w, y + h, 0xFF2F2F33);
            g.fill(x + w - 1, y + 1, x + w, y + h, 0xFF2F2F33);
        } else {
            // Marco principal elevado con outline negro/oscuro y bisel 3D
            int outer = (borderColor == BORDER_PANEL) ? 0xFF0B0B0D : borderColor;
            drawRoundedRect(g, x, y, w, h, radius, outer);
            drawRoundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), bgColor);

            // Bisel 3D interior (solo en marcos oscuros para mantener look Darkomizer)
            int hi = (borderColor == BORDER_PANEL) ? 0xFF3D3D42 : lighten(bgColor, 0.08f);
            int lo = (borderColor == BORDER_PANEL) ? 0xFF171719 : darken(bgColor, 0.25f);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, hi);
            g.fill(x + 1, y + 2, x + 2, y + h - 2, hi);
            g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, lo);
            g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, lo);
        }
    }

    /**
     * Botón estilo Minecraft Bedrock Ore UI: outline negro, labio inferior 3D, cara elevada y brillo superior.
     */
    public static void drawPillButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      String label, int bg, int hoverBg, int border, boolean hovered) {
        if (w <= 4 || h <= 4) return;
        int face = hovered ? hoverBg : bg;
        int lipColor = darken(face, 0.45f);
        int hi = lighten(face, 0.14f);
        int lip = (h >= 18) ? 2 : 1;

        // Outline negro exterior
        g.fill(x, y, x + w, y + h, 0xFF0B0B0D);
        // Base / labio inferior 3D
        g.fill(x + 1, y + h - 1 - lip, x + w - 1, y + h - 1, lipColor);
        // Cara superior elevada
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1 - lip, face);
        // Brillo interior superior/izquierdo estilo Ore UI
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
        g.fill(x + 1, y + 1, x + 2, y + h - 1 - lip, hi);

        g.drawCenteredString(font, label, x + w / 2, y + (h - lip - 8) / 2, 0xFFFFFF);
    }

    /**
     * Barra de progreso estilo Darkomizer + Ore UI.
     */
    public static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, float ratio, int trackColor, int fillColor) {
        g.fill(x, y, x + w, y + h, 0xFF0B0B0D);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF171719);
        int innerW = w - 2;
        int fillW = (int) (innerW * Math.max(0.0f, Math.min(1.0f, ratio)));
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, fillColor);
            if (h >= 5) {
                g.fill(x + 1, y + 1, x + 1 + fillW, y + 2, lighten(fillColor, 0.15f));
            }
        }
    }

    /**
     * Botón interactivo de Minecraft Ore UI compatible con Screen.addRenderableWidget().
     */
    public static class OreButton extends Button {
        private final int bg;
        private final int hoverBg;
        private final Supplier<Boolean> activeTab;

        public OreButton(int x, int y, int w, int h, Component message, OnPress onPress, int bg, int hoverBg) {
            this(x, y, w, h, message, onPress, bg, hoverBg, null);
        }

        public OreButton(int x, int y, int w, int h, Component message, OnPress onPress,
                         int bg, int hoverBg, Supplier<Boolean> activeTab) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
            this.bg = bg;
            this.hoverBg = hoverBg;
            this.activeTab = activeTab;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            boolean isTabSelected = activeTab != null && activeTab.get();
            int base = !this.active ? 0xFF202023 : (isTabSelected ? ACCENT_GREEN_DARK : bg);
            int hov = !this.active ? 0xFF202023 : (isTabSelected ? ACCENT_GREEN : hoverBg);
            boolean showHover = this.active && this.isHoveredOrFocused();
            drawPillButton(g, font, getX(), getY(), getWidth(), getHeight(),
                    getMessage().getString(), base, hov, 0, showHover);
            if (isTabSelected) {
                int barW = Math.min(26, getWidth() / 3);
                int bx = getX() + (getWidth() - barW) / 2;
                int by = getY() + getHeight() - 3;
                g.fill(bx, by, bx + barW, by + 1, 0xFFEAEAEA);
            }
        }
    }

    /**
     * Barra horizontal superior estilo Zenless con secciones RPG y su tecla de acceso.
     * activeIdx: 0 = Roadmap [F1], 1 = Misiones [J], 2 = Grupo [G], 3 = Habilidades [O]
     */
    public static void addZenlessNavBar(int screenWidth, int y, int activeIdx, Consumer<OreButton> adder) {
        String[] labels = {
                "§e[F1] §fRoadmap",
                "§e[J] §fMisiones",
                "§e[G] §fGrupo",
                "§e[O] §fHabilidades"
        };
        int[] widths = { 104, 102, 86, 118 };
        int gap = 6;
        int totalW = 0;
        for (int w : widths) totalW += w;
        totalW += gap * (labels.length - 1);

        int x = Math.max(12, (screenWidth - totalW) / 2);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            adder.accept(new OreButton(x, y, widths[i], 20, Component.literal(labels[i]), b -> {
                Minecraft mc = Minecraft.getInstance();
                if (idx == 0 && activeIdx != 0) {
                    mc.setScreen(new GearRoadmapScreen());
                } else if (idx == 1 && activeIdx != 1) {
                    QuestConfig.loadConfig();
                    mc.setScreen(new QuestJournalScreen());
                } else if (idx == 2 && activeIdx != 2) {
                    mc.setScreen(new PartyScreen());
                } else if (idx == 3) {
                    mc.setScreen(null);
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("§a[RPG] Pulsa §e[O] §apara abrir el Árbol de Habilidades."), true);
                    }
                }
            }, ACCENT_SLATE, 0xFF47474D, () -> activeIdx == idx));
            x += widths[i] + gap;
        }
    }
}
