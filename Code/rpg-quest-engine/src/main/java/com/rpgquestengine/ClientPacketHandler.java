package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientPacketHandler {

    public static void initClient(IEventBus modEventBus) {
        modEventBus.addListener(KeyBindings::registerKeys);
        modEventBus.addListener(RPGQuestEngine::addBuiltInPacks);
        modEventBus.addListener(ClientPacketHandler::onClientSetup);
        MinecraftForge.EVENT_BUS.register(new QuestHudOverlay());
        MinecraftForge.EVENT_BUS.register(new QuestTitleBannerOverlay());
        MinecraftForge.EVENT_BUS.register(new ClientKeyEvents());
        MinecraftForge.EVENT_BUS.register(new ReforgingRerollClientHandler());
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            QuestConfig.loadConfig();
            RPGQuestEngine.ensureXaeroEasyNpcCategory();
        });
    }

    public static void openAdminUi(OpenAdminUiPacket msg) {
        QuestConfig.NPC_LOCATIONS.clear();
        QuestConfig.NPC_LOCATIONS.putAll(msg.locations);
        if (msg.openScreen) {
            Minecraft.getInstance().setScreen(new AdminNpcManagerScreen());
        }
    }

    public static void openNpcDialog(OpenNpcDialogPacket msg) {
        Minecraft.getInstance().setScreen(new NpcDialogScreen(msg));
    }

    public static class ClientKeyEvents {
        @SubscribeEvent
        public void onKeyInput(InputEvent.Key event) {
            if (KeyBindings.OPEN_JOURNAL_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    if (mc.isLocalServer()) {
                        QuestConfig.loadConfig();
                    }
                    mc.setScreen(new QuestJournalScreen());
                }
            }

            if (KeyBindings.OPEN_PARTY_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    mc.setScreen(new PartyScreen());
                }
            }

            if (KeyBindings.OPEN_ROADMAP_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                mc.options.hideGui = false;
                if (mc.screen == null) {
                    mc.setScreen(new GearRoadmapScreen());
                }
            }

            if (KeyBindings.TRACK_PROMPT_KEY.consumeClick()) {
                if (QuestTitleBannerOverlay.hasActivePrompt()) {
                    String qId = QuestTitleBannerOverlay.getActivePromptQuestId();
                    QuestConfig.trackedQuestId = qId;
                    QuestConfig.saveSettings();
                    QuestTitleBannerOverlay.clearPrompt();
                    ClientQuestData.playClientSound(1);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("§a[RPGQuestEngine] Siguiendo misión actual."), true);
                    }
                }
            }
        }
    }
}
