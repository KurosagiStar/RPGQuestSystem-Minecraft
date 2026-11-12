package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class QuestTitleBannerOverlay {
    private static String bannerHeader = "";
    private static String bannerTitle = "";
    private static int bannerColor = 0xFFFFAA00;
    private static String promptQuestId = "";
    private static ItemStack bannerIcon = ItemStack.EMPTY;
    private static long showUntilTime = 0;
    private static final long DISPLAY_DURATION = 5000; // 5 segundos

    private static final ResourceLocation TOAST_TEXTURE = new ResourceLocation("textures/gui/toasts.png");

    public static void showBanner(String header, String title, ItemStack icon, int color, String questIdForPrompt) {
        bannerHeader = header;
        bannerTitle = title;
        bannerIcon = icon != null ? icon : ItemStack.EMPTY;
        bannerColor = color;
        promptQuestId = questIdForPrompt != null ? questIdForPrompt : "";
        showUntilTime = System.currentTimeMillis() + DISPLAY_DURATION;
    }

    public static boolean hasActivePrompt() {
        return System.currentTimeMillis() <= showUntilTime && !promptQuestId.isEmpty();
    }

    public static String getActivePromptQuestId() {
        return promptQuestId;
    }

    public static void clearPrompt() {
        promptQuestId = "";
        showUntilTime = 0;
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
        int startY = 25; // Centro arriba

        boolean hasPrompt = !promptQuestId.isEmpty();
        int boxW = 280;
        int boxH = hasPrompt ? 46 : 38;
        int boxX = centerX - (boxW / 2);

        // Fondo oscuro RPG con desvanecimiento
        int bgColor = (alphaInt << 24) | 0x121215;
        graphics.fill(boxX, startY, boxX + boxW, startY + boxH, bgColor);

        // Bordes dorados temáticos con desvanecimiento
        int borderColor = (alphaInt << 24) | (bannerColor & 0x00FFFFFF);
        graphics.fill(boxX, startY, boxX + boxW, startY + 1, borderColor);
        graphics.fill(boxX, startY + boxH - 1, boxX + boxW, startY + boxH, borderColor);
        graphics.fill(boxX, startY, boxX + 1, startY + boxH, borderColor);
        graphics.fill(boxX + boxW - 1, startY, boxX + boxW, startY + boxH, borderColor);

        // Icono a la izquierda
        int textStartX = boxX + 12;
        if (!bannerIcon.isEmpty()) {
            graphics.renderFakeItem(bannerIcon, boxX + 10, startY + 10);
            textStartX = boxX + 34;
        }

        int headerColor = (alphaInt << 24) | (bannerColor & 0x00FFFFFF);
        int titleColor = (alphaInt << 24) | 0x00FFFFFF;

        graphics.drawString(font, bannerHeader, textStartX, startY + 8, headerColor, false);
        graphics.drawString(font, "§l" + bannerTitle, textStartX, startY + 20, titleColor, false);

        if (hasPrompt) {
            int promptColor = (alphaInt << 24) | 0x00AAAAAA;
            graphics.drawString(font, "§e[Presiona V para seguir]", textStartX, startY + 32, promptColor, false);
        }
    }
}


