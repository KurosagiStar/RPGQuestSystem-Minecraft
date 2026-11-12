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
    private static final Map<String, double[]> KNOWN_NPC_POSITIONS = new HashMap<>();
    private static final Map<String, double[]> SERVER_NPC_POSITIONS = new HashMap<>(); // questId -> [x, z] en vivo
    private static boolean initialized = false;

    public static void setServerNpcPosition(String questId, double x, double z) {
        if (questId != null && !questId.isEmpty()) {
            SERVER_NPC_POSITIONS.put(questId, new double[]{x, z});
        }
    }

    public static double[] getServerNpcPosition(String questId) {
        if (questId == null) return null;
        return SERVER_NPC_POSITIONS.get(questId);
    }

    public static void rememberNpcPosition(String npcName, double x, double z) {
        if (npcName != null && !npcName.isEmpty()) {
            String norm = normalizeName(npcName);
            KNOWN_NPC_POSITIONS.put(norm, new double[]{x, z});
        }
    }

    public static double[] getKnownNpcPosition(String npcName) {
        if (npcName == null || npcName.isEmpty()) return null;
        String norm = normalizeName(npcName);

        // 1. Coincidencia exacta normalizada
        double[] pos = KNOWN_NPC_POSITIONS.get(norm);
        if (pos != null) return pos;

        // 2. Coincidencia parcial (ej. "guardia" en "capitan de la guardia")
        for (Map.Entry<String, double[]> entry : KNOWN_NPC_POSITIONS.entrySet()) {
            if (entry.getKey().contains(norm) || norm.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalizeName(String str) {
        return java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
    }

    public static synchronized void updateData(Set<String> newTags, Map<String, Integer> newScores) {
        updateData(newTags, newScores, null);
    }

    public static synchronized void updateData(Set<String> newTags, Map<String, Integer> newScores, Map<String, double[]> liveNpcPositions) {
        if (liveNpcPositions != null) {
            SERVER_NPC_POSITIONS.putAll(liveNpcPositions);
        }

        if (!initialized) {
            if (newTags != null) PLAYER_TAGS.addAll(newTags);
            if (newScores != null) PLAYER_SCORES.putAll(newScores);
            initialized = true;
            return;
        }

        if (newTags != null) {
            // Misión Aceptada
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if (newTags.contains(q.activeTag) && !PLAYER_TAGS.contains(q.activeTag)) {
                    playClientSound(1);

                    // Si ya hay una misión en seguimiento activa y no es "none", mostrar prompt [Presiona V para seguir]
                    boolean hasCurrentTracking = isTrackingAnotherActiveQuest(q.id);
                    if (hasCurrentTracking) {
                        QuestTitleBannerOverlay.showBanner("NUEVA MISIÓN", q.title, getCategoryIcon(q.category), 0xFFFFAA00, q.id);
                    } else {
                        // Si no había ninguna misión seguida, fijar automáticamente
                        QuestConfig.trackedQuestId = q.id;
                        QuestConfig.saveSettings();
                        QuestTitleBannerOverlay.showBanner("MISIÓN ACEPTADA", q.title, getCategoryIcon(q.category), 0xFFFFAA00, "");
                    }
                    break;
                }
            }

            // Objetivos Cumplidos -> Banner verde en el centro superior
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if (newTags.contains(q.doneTag) && !PLAYER_TAGS.contains(q.doneTag)) {
                    playClientSound(3);
                    QuestTitleBannerOverlay.showBanner("OBJETIVOS CUMPLIDOS", q.title, new ItemStack(Items.WRITABLE_BOOK), 0xFF55FF55, "");
                    break;
                }
            }

            // Misión Entregada y Cobrada -> Banner dorado
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if (newTags.contains(q.completedTag) && !PLAYER_TAGS.contains(q.completedTag)) {
                    playClientSound(4);
                    QuestTitleBannerOverlay.showBanner("MISIÓN COMPLETADA", q.title, new ItemStack(Items.EMERALD), 0xFFFFCC00, "");
                    if (q.id.equals(QuestConfig.trackedQuestId)) {
                        // Pasar automáticamente a la siguiente misión pendiente
                        QuestConfig.trackedQuestId = findNextPendingQuestId(q.id, newTags);
                        QuestConfig.saveSettings();
                    }
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

    private static boolean isTrackingAnotherActiveQuest(String exceptQuestId) {
        String current = QuestConfig.trackedQuestId;
        if (current == null || current.isEmpty() || "none".equals(current)) return false;
        if (current.equals(exceptQuestId)) return false;

        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (q.id.equals(current) && hasTag(q.activeTag) && !hasTag(q.completedTag)) {
                return true;
            }
        }
        return false;
    }

    private static String findNextPendingQuestId(String completedQuestId, Set<String> currentTags) {
        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (!q.id.equals(completedQuestId) && currentTags.contains(q.activeTag) && !currentTags.contains(q.completedTag)) {
                return q.id;
            }
        }
        return "";
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

    public static void playClientSound(int type) {
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
            case 5:
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.RAID_HORN.value(), 1.2F, 1.1F));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SPIDER_HURT, 1.0F, 0.9F));
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
