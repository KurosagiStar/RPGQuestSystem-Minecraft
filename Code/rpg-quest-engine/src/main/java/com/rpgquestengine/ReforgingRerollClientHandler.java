package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;

public class ReforgingRerollClientHandler {
    private static final String APOTH_SCREEN = "dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingScreen";
    private static final String ESOTERIC_SCREEN = "com.mira.esotericreforging.screen.EsotericReforgingScreen";

    private Button activeButton;

    public static boolean isReforgingScreen(Screen screen) {
        if (screen == null) return false;
        String cls = screen.getClass().getName();
        return APOTH_SCREEN.equals(cls) || ESOTERIC_SCREEN.equals(cls);
    }

    @SubscribeEvent
    public void onScreenInitPost(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isReforgingScreen(screen) || !(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            activeButton = null;
            return;
        }

        int btnX = containerScreen.getGuiLeft() + 123;
        int btnY = containerScreen.getGuiTop() + 39;

        activeButton = new RerollButton(btnX, btnY, 38, 18, containerScreen, b -> {
            NetworkHandler.CHANNEL.sendToServer(new RerollReforgePacket());
        });

        activeButton.setTooltip(Tooltip.create(Component.literal(
                "§6✦ Reroll de Opciones\n" +
                "§7Costo: §a1 Nivel de XP\n" +
                "§8No gasta materiales ni sigilos"
        )));

        event.addListener(activeButton);
    }

    @SubscribeEvent
    public void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (activeButton == null || !isReforgingScreen(event.getScreen())) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;

        activeButton.setX(containerScreen.getGuiLeft() + 123);
        activeButton.setY(containerScreen.getGuiTop() + 39);

        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu menu = containerScreen.getMenu();
        boolean hasInputs = menu != null && menu.slots.size() >= 6
                && menu.getSlot(0).hasItem()
                && menu.getSlot(1).hasItem()
                && (menu.getSlot(3).hasItem() || menu.getSlot(4).hasItem() || menu.getSlot(5).hasItem());
        boolean hasXp = mc.player != null && (mc.player.getAbilities().instabuild || mc.player.experienceLevel >= 1);

        activeButton.active = hasInputs && hasXp;
    }

    public static void onServerSyncSeed(int syncedSeed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getPersistentData().putInt("apoth_reforge_seed", syncedSeed);
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu != null) {
                try {
                    Field seedField = RerollReforgePacket.findField(menu.getClass(), "seed");
                    if (seedField != null) {
                        seedField.setInt(menu, syncedSeed);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (mc.screen != null && isReforgingScreen(mc.screen)) {
            try {
                Field animField = RerollReforgePacket.findField(mc.screen.getClass(), "animationTick");
                if (animField != null) {
                    animField.setInt(mc.screen, 6);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static class RerollButton extends Button {
        private final AbstractContainerScreen<?> parent;

        protected RerollButton(int x, int y, int width, int height, AbstractContainerScreen<?> parent, OnPress onPress) {
            super(x, y, width, height, Component.literal("↻ 1L"), onPress, DEFAULT_NARRATION);
            this.parent = parent;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int w = this.width;
            int h = this.height;

            int bg = !this.active ? 0xCC16131C : (this.isHoveredOrFocused() ? 0xEE35224A : 0xDD221730);
            int border = !this.active ? 0xFF443B4F : (this.isHoveredOrFocused() ? 0xFFD99BFF : 0xFF8E58B8);

            g.fill(x, y, x + w, y + h, bg);
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, y, x + 1, y + h, border);
            g.fill(x + w - 1, y, x + w, y + h, border);

            int textColor = !this.active ? 0xFF776E82 : (this.isHoveredOrFocused() ? 0xFFFFEA88 : 0xFFE6C8FF);
            g.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), x + w / 2, y + (h - 8) / 2, textColor);
        }
    }
}
