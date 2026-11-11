package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class QuestTitleBannerOverlay {
    private static String bannerHeader = "";
    private static String bannerTitle = "";
    private static int bannerColor = 0xFFFFAA00;
    private static long showUntilTime = 0;
    private static final long DISPLAY_DURATION = 3500; // 3.5 segundos

    public static void showBanner(String header, String title, int color) {
        bannerHeader = header;
        bannerTitle = title;
        bannerColor = color;
        showUntilTime = System.currentTimeMillis() + DISPLAY_DURATION;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now > showUntilTime) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        long timeLeft = showUntilTime - now;
        float alpha = 1.0f;
        if (timeLeft < 500) {
            alpha = (float) timeLeft / 500.0f;
        } else if ((DISPLAY_DURATION - timeLeft) < 500) {
            alpha = (float) (DISPLAY_DURATION - timeLeft) / 500.0f;
        }

        int alphaInt = (int) (alpha * 255.0f);
        if (alphaInt < 10) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int centerX = screenWidth / 2;
        int startY = 40;

        int bgWidth = 260;
        int bgHeight = 44;
        int bgX = centerX - (bgWidth / 2);

        // Fondo oscuro estilizado con fade
        int bgColor = (alphaInt << 24) | 0x000F0F0F;
        graphics.fill(bgX, startY, bgX + bgWidth, startY + bgHeight, bgColor);

        // Líneas doradas superior e inferior
        int lineBorderColor = (alphaInt << 24) | (bannerColor & 0x00FFFFFF);
        graphics.fill(bgX + 15, startY, bgX + bgWidth - 15, startY + 1, lineBorderColor);
        graphics.fill(bgX + 15, startY + bgHeight - 1, bgX + bgWidth - 15, startY + bgHeight, lineBorderColor);

        // Textos centrados
        int headerColor = (alphaInt << 24) | (bannerColor & 0x00FFFFFF);
        int titleColor = (alphaInt << 24) | 0x00FFFFFF;

        graphics.drawCenteredString(font, bannerHeader, centerX, startY + 10, headerColor);
        graphics.drawCenteredString(font, "§l" + bannerTitle, centerX, startY + 24, titleColor);
    }
}

