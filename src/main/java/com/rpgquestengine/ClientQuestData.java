package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClientQuestData {
    private static final Set<String> PLAYER_TAGS = new HashSet<>();
    private static final Map<String, Integer> PLAYER_SCORES = new HashMap<>();
    private static boolean initialized = false;

    public static synchronized void updateData(Set<String> newTags, Map<String, Integer> newScores) {
        if (!initialized) {
            if (newTags != null) PLAYER_TAGS.addAll(newTags);
            if (newScores != null) PLAYER_SCORES.putAll(newScores);
            initialized = true;
            return;
        }

        if (newTags != null) {
            // Misión Aceptada -> Toast nativo estilo Logro
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if (newTags.contains(q.activeTag) && !PLAYER_TAGS.contains(q.activeTag)) {
                    playClientSound(1);
                    showQuestToast("Misión Aceptada", q.title, getCategoryIcon(q.category), 0xFFAA00);
                    break;
                }
            }

            // Objetivos Cumplidos -> Toast nativo verde
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if (newTags.contains(q.doneTag) && !PLAYER_TAGS.contains(q.doneTag)) {
                    playClientSound(3);
                    showQuestToast("Objetivos Cumplidos", q.title, new ItemStack(Items.WRITABLE_BOOK), 0x55FF55);
                    break;
                }
            }

            // Misión Entregada y Cobrada -> Toast de desafío superado
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if (newTags.contains(q.completedTag) && !PLAYER_TAGS.contains(q.completedTag)) {
                    playClientSound(4);
                    showQuestToast("Misión Completada", q.title, new ItemStack(Items.EMERALD), 0xFFAA00);
                    break;
                }
            }
        }

        if (newScores != null) {
            for (Map.Entry<String, Integer> entry : newScores.entrySet()) {
                int oldVal = PLAYER_SCORES.getOrDefault(entry.getKey(), 0);
                int newVal = entry.getValue();
                if (newVal > oldVal) {
                    playClientSound(2);
                    break;
                }
            }
        }

        PLAYER_TAGS.clear();
        if (newTags != null) PLAYER_TAGS.addAll(newTags);
        PLAYER_SCORES.clear();
        if (newScores != null) PLAYER_SCORES.putAll(newScores);
    }

    private static void showQuestToast(String header, String title, ItemStack icon, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getToasts() != null) {
            mc.getToasts().addToast(new QuestToast(header, title, icon, color));
        }
    }

    private static ItemStack getCategoryIcon(String category) {
        if (category == null) return new ItemStack(Items.COMPASS);
        switch (category.toUpperCase()) {
            case "MAIN": return new ItemStack(Items.GOLDEN_SWORD);
            case "CONTRACT": return new ItemStack(Items.IRON_SWORD);
            case "REPEATABLE": return new ItemStack(Items.FISHING_ROD);
            default: return new ItemStack(Items.MAP);
        }
    }

    private static void playClientSound(int type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        switch (type) {
            case 1:
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F, 0.85F));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 0.7F));
                break;
            case 2:
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 0.8F, 1.2F));
                break;
            case 3:
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F));
                break;
            case 4:
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 0.9F, 1.1F));
                break;
        }
    }

    public static synchronized boolean hasTag(String tag) {
        if (tag == null || tag.isEmpty()) return false;
        return PLAYER_TAGS.contains(tag);
    }

    public static synchronized int getScore(String objective) {
        if (objective == null || objective.isEmpty()) return 0;
        return PLAYER_SCORES.getOrDefault(objective, 0);
    }

    public static synchronized Set<String> getTags() {
        return new HashSet<>(PLAYER_TAGS);
    }
}
