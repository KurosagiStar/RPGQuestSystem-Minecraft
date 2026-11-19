package com.rpgquestengine;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public class UiStyle {
    public static final int BG_DARK = 0xF4141519;
    public static final int BG_PANEL = 0xEC1B1D23;
    public static final int BG_CARD = 0xE823262E;
    public static final int BG_HEADER = 0xF5282C35;
    public static final int BORDER_PANEL = 0xFF30343E;
    public static final int BORDER_GOLD = 0xFFE5BE53;
    public static final int BORDER_SUBTLE = 0x38FFFFFF;
    public static final int ACCENT_GREEN = 0xFF2ECC71;
    public static final int ACCENT_GREEN_DARK = 0xFF229954;
    public static final int ACCENT_RED_DARK = 0xFF8B2635;
    public static final int ACCENT_BLUE_DARK = 0xFF2563EB;
    public static final int ACCENT_SLATE = 0xFF2E323C;

    private static final int ARC_SEGMENTS = 14;

    private static float getPhysicalPixel() {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        return scale > 0 ? (float) (1.0 / scale) : 0.5f;
    }

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

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int lerpColor(int top, int bottom, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((top >>> 24) & 0xFF) * (1 - t) + ((bottom >>> 24) & 0xFF) * t);
        int r = (int) (((top >>> 16) & 0xFF) * (1 - t) + ((bottom >>> 16) & 0xFF) * t);
        int g = (int) (((top >>> 8) & 0xFF) * (1 - t) + ((bottom >>> 8) & 0xFF) * t);
        int b = (int) ((top & 0xFF) * (1 - t) + (bottom & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Construye el perímetro suave de un rectángulo redondeado en coordenadas float.
     */
    private static void buildPerimeter(float x, float y, float w, float h, float r, float[] outX, float[] outY) {
        float cr = Math.max(0.1f, Math.min(r, Math.min(w * 0.5f, h * 0.5f)));
        float[] cx = { x + w - cr, x + w - cr, x + cr, x + cr };
        float[] cy = { y + cr, y + h - cr, y + h - cr, y + cr };
        double[] startAngles = { -Math.PI / 2, 0, Math.PI / 2, Math.PI };

        int idx = 0;
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= ARC_SEGMENTS; i++) {
                double ang = startAngles[c] + (Math.PI / 2) * ((double) i / ARC_SEGMENTS);
                outX[idx] = cx[c] + (float) Math.cos(ang) * cr;
                outY[idx] = cy[c] + (float) Math.sin(ang) * cr;
                idx++;
            }
        }
    }

    private static void emitTri(BufferBuilder buf, Matrix4f mat,
                                float x1, float y1, int c1,
                                float x2, float y2, int c2,
                                float x3, float y3, int c3) {
        buf.vertex(mat, x1, y1, 0f).color((c1 >> 16) & 0xFF, (c1 >> 8) & 0xFF, c1 & 0xFF, (c1 >>> 24) & 0xFF).endVertex();
        buf.vertex(mat, x3, y3, 0f).color((c3 >> 16) & 0xFF, (c3 >> 8) & 0xFF, c3 & 0xFF, (c3 >>> 24) & 0xFF).endVertex();
        buf.vertex(mat, x2, y2, 0f).color((c2 >> 16) & 0xFF, (c2 >> 8) & 0xFF, c2 & 0xFF, (c2 >>> 24) & 0xFF).endVertex();
    }

    /**
     * Dibuja un rectángulo redondeado vectorial suavizado con anti-aliasing nativo de pantalla y degradado vertical.
     */
    public static void drawSmoothRoundedRect(GuiGraphics g, float x, float y, float w, float h, float radius,
                                             int topColor, int bottomColor) {
        if (w <= 0 || h <= 0) return;
        g.flush();

        float px = getPhysicalPixel();
        float aa = px * 0.9f;
        int totalPts = 4 * (ARC_SEGMENTS + 1);
        float[] ix = new float[totalPts];
        float[] iy = new float[totalPts];
        float[] ox = new float[totalPts];
        float[] oy = new float[totalPts];

        buildPerimeter(x, y, w, h, radius, ix, iy);
        buildPerimeter(x - aa, y - aa, w + aa * 2, h + aa * 2, radius + aa, ox, oy);

        float midX = x + w * 0.5f;
        float midY = y + h * 0.5f;
        int midColor = lerpColor(topColor, bottomColor, 0.5f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f mat = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < totalPts; i++) {
            int next = (i + 1) % totalPts;
            int c1 = lerpColor(topColor, bottomColor, (iy[i] - y) / h);
            int c2 = lerpColor(topColor, bottomColor, (iy[next] - y) / h);
            int o1 = withAlpha(c1, 0);
            int o2 = withAlpha(c2, 0);

            // Relleno interior
            emitTri(buf, mat, midX, midY, midColor, ix[i], iy[i], c1, ix[next], iy[next], c2);
            // Franja anti-aliasing exterior (1px físico)
            emitTri(buf, mat, ix[i], iy[i], c1, ox[i], oy[i], o1, ix[next], iy[next], c2);
            emitTri(buf, mat, ix[next], iy[next], c2, ox[i], oy[i], o1, ox[next], oy[next], o2);
        }

        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Dibuja un contorno vectorial fino (1.2px físicos) con anti-aliasing.
     */
    public static void drawSmoothOutline(GuiGraphics g, float x, float y, float w, float h, float radius,
                                         float thicknessPx, int color) {
        if (w <= 0 || h <= 0) return;
        g.flush();

        float px = getPhysicalPixel();
        float t = Math.max(px, thicknessPx * px);
        float aa = px * 0.75f;
        int totalPts = 4 * (ARC_SEGMENTS + 1);

        float[] innerX = new float[totalPts];
        float[] innerY = new float[totalPts];
        float[] outerX = new float[totalPts];
        float[] outerY = new float[totalPts];
        float[] aaInX = new float[totalPts];
        float[] aaInY = new float[totalPts];
        float[] aaOutX = new float[totalPts];
        float[] aaOutY = new float[totalPts];

        buildPerimeter(x + t * 0.5f, y + t * 0.5f, w - t, h - t, Math.max(0.5f, radius - t * 0.5f), innerX, innerY);
        buildPerimeter(x - t * 0.5f, y - t * 0.5f, w + t, h + t, radius + t * 0.5f, outerX, outerY);
        buildPerimeter(x + t * 0.5f + aa, y + t * 0.5f + aa, w - t - aa * 2, h - t - aa * 2, Math.max(0.2f, radius - t * 0.5f - aa), aaInX, aaInY);
        buildPerimeter(x - t * 0.5f - aa, y - t * 0.5f - aa, w + t + aa * 2, h + t + aa * 2, radius + t * 0.5f + aa, aaOutX, aaOutY);

        int clear = withAlpha(color, 0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f mat = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < totalPts; i++) {
            int n = (i + 1) % totalPts;
            // Núcleo del borde
            emitTri(buf, mat, innerX[i], innerY[i], color, outerX[i], outerY[i], color, innerX[n], innerY[n], color);
            emitTri(buf, mat, innerX[n], innerY[n], color, outerX[i], outerY[i], color, outerX[n], outerY[n], color);
            // AA exterior
            emitTri(buf, mat, outerX[i], outerY[i], color, aaOutX[i], aaOutY[i], clear, outerX[n], outerY[n], color);
            emitTri(buf, mat, outerX[n], outerY[n], color, aaOutX[i], aaOutY[i], clear, aaOutX[n], aaOutY[n], clear);
            // AA interior
            emitTri(buf, mat, aaInX[i], aaInY[i], clear, innerX[i], innerY[i], color, aaInX[n], aaInY[n], clear);
            emitTri(buf, mat, aaInX[n], aaInY[n], clear, innerX[i], innerY[i], color, innerX[n], innerY[n], color);
        }

        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Sombra suave multicapa estilo moderno.
     */
    public static void drawSoftShadow(GuiGraphics g, float x, float y, float w, float h, float radius) {
        float[] spreads = { 6f, 4f, 2.2f, 1f };
        int[] alphas = { 0x10, 0x18, 0x24, 0x30 };
        for (int i = 0; i < spreads.length; i++) {
            float s = spreads[i];
            int c = alphas[i] << 24;
            drawSmoothRoundedRect(g, x - s * 0.5f, y + s * 0.45f, w + s, h + s, radius + s * 0.5f, c, c);
        }
    }

    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        drawSmoothRoundedRect(g, x, y, w, h, radius, color, color);
    }

    /**
     * Dibuja una tarjeta moderna con sombra difuminada, degradado sutil y borde fino vectorial.
     */
    public static void drawRoundedCard(GuiGraphics g, int x, int y, int w, int h, int radius,
                                       int bgColor, int borderColor, boolean withShadow) {
        if (withShadow) {
            drawSoftShadow(g, x, y, w, h, radius);
        }
        int topBg = lighten(bgColor, 0.03f);
        int botBg = darken(bgColor, 0.08f);
        drawSmoothRoundedRect(g, x, y, w, h, radius, topBg, botBg);
        drawSmoothOutline(g, x, y, w, h, radius, 1.35f, borderColor);
    }

    /**
     * Dibuja un botón moderno tipo píldora con degradado, sombra sutil y estado hover suave.
     */
    public static void drawPillButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                      String label, int bg, int hoverBg, int border, boolean hovered) {
        float r = Math.min(6f, h * 0.45f);
        int base = hovered ? hoverBg : bg;
        int topBg = lighten(base, hovered ? 0.08f : 0.04f);
        int botBg = darken(base, 0.12f);
        if (hovered) {
            drawSoftShadow(g, x, y, w, h, r);
        }
        drawSmoothRoundedRect(g, x, y, w, h, r, topBg, botBg);
        drawSmoothOutline(g, x, y, w, h, r, hovered ? 1.5f : 1.15f, hovered ? lighten(border, 0.25f) : border);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
    }

    /**
     * Barra de progreso vectorial redondeada.
     */
    public static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, float ratio, int trackColor, int fillColor) {
        float r = Math.max(2f, h * 0.5f);
        drawSmoothRoundedRect(g, x, y, w, h, r, darken(trackColor, 0.15f), trackColor);
        float clamped = Math.max(0.0f, Math.min(1.0f, ratio));
        float fillW = w * clamped;
        if (fillW > 1.5f) {
            float fr = Math.min(r, fillW * 0.5f);
            drawSmoothRoundedRect(g, x, y, fillW, h, fr, lighten(fillColor, 0.12f), darken(fillColor, 0.10f));
        }
    }
}
