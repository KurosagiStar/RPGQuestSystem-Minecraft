package com.rpgquestengine;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class UiStyle {
    public static final int BG_DARK = 0xEE131419;
    public static final int BG_PANEL = 0xDD1A1C23;
    public static final int BG_CARD = 0xCC21242D;
    public static final int BG_HEADER = 0xEE262A35;
    public static final int BORDER_GOLD = 0xFFE0B84C;
    public static final int BORDER_SUBTLE = 0xFF343946;
    public static final int ACCENT_GREEN = 0xFF39B54A;
    public static final int ACCENT_GREEN_DARK = 0xFF234D2A;
    public static final int ACCENT_RED_DARK = 0xFF542529;
    public static final int ACCENT_BLUE_DARK = 0xFF223B54;

    /**
     * Dibuja un rectángulo con esquinas redondeadas reales (radius 2..6 px).
     */
    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        // Cuerpo central
        g.fill(x, y + r, x + w, y + h - r, color);
        // Franjas superior e inferior redondeadas fila por fila
        for (int dy = 0; dy < r; dy++) {
            int dist = r - dy;
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dist * dist)));
            g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
            g.fill(x + inset, y + h - 1 - dy, x + w - inset, y + h - dy, color);
        }
    }

    /**
     * Dibuja una tarjeta moderna con sombra suave, borde redondeado y relleno interior.
     */
    public static void drawRoundedCard(GuiGraphics g, int x, int y, int w, int h, int radius,
                                       int bgColor, int borderColor, boolean withShadow) {
        if (withShadow) {
            drawRoundedRect(g, x + 2, y + 3, w, h, radius, 0x55000000);
        }
        drawRoundedRect(g, x, y, w, h, radius, borderColor);
        drawRoundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), bgColor);
    }

    /**
     * Dibuja un botón moderno redondeado con estado hover.
     */
    public static void drawPillButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      String label, int bg, int hoverBg, int border, boolean hovered) {
        int r = Math.min(4, h / 2);
        drawRoundedCard(g, x, y, w, h, r, hovered ? hoverBg : bg, hovered ? 0xFFFFE082 : border, false);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
    }

    /**
     * Barra de progreso redondeada moderna.
     */
    public static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, float ratio, int trackColor, int fillColor) {
        int r = Math.max(2, h / 2);
        drawRoundedRect(g, x, y, w, h, r, trackColor);
        int fillW = (int) (w * Math.max(0.0f, Math.min(1.0f, ratio)));
        if (fillW >= r * 2) {
            drawRoundedRect(g, x, y, fillW, h, r, fillColor);
        } else if (fillW > 0) {
            g.fill(x + 1, y + 1, x + fillW, y + h - 1, fillColor);
        }
    }
}
