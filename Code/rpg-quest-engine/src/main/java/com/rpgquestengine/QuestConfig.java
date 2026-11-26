package com.rpgquestengine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QuestConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static List<QuestDefinition> QUESTS = new ArrayList<>();
    public static final java.util.Map<String, QuestDefinition> QUESTS_BY_ID = new java.util.HashMap<>();
    public static final java.util.Map<String, QuestDefinition> QUESTS_BY_ACTIVE_TAG = new java.util.HashMap<>();
    public static final java.util.Map<String, List<QuestDefinition>> QUESTS_BY_NPC = new java.util.HashMap<>();
    public static java.util.Map<String, double[]> NPC_LOCATIONS = new java.util.HashMap<>();

    // Boss challenges loaded from "challenges" key in rpg_quests.json
    public static List<ChallengeDefinition> CHALLENGES = new ArrayList<>();
    public static final java.util.Map<String, ChallengeDefinition> CHALLENGES_BY_ID = new java.util.HashMap<>();

    public static ChallengeDefinition getChallengeById(String id) {
        return CHALLENGES_BY_ID.get(id);
    }

    public static int hudPosX = 10;
    public static int hudPosY = 15;
    public static double hudPosXRatio = -1.0;
    public static double hudPosYRatio = -1.0;
    public static boolean hudEnabled = true;
    public static String trackedQuestId = "";

    public static void rebuildIndices() {
        QUESTS_BY_ID.clear();
        QUESTS_BY_ACTIVE_TAG.clear();
        QUESTS_BY_NPC.clear();
        for (QuestDefinition q : QUESTS) {
            if (q.id != null && !q.id.isEmpty()) {
                QUESTS_BY_ID.put(q.id, q);
            }
            if (q.activeTag != null && !q.activeTag.isEmpty()) {
                QUESTS_BY_ACTIVE_TAG.put(q.activeTag, q);
            }
            if (q.npcId != null && !q.npcId.isEmpty()) {
                QUESTS_BY_NPC.computeIfAbsent(q.npcId, k -> new ArrayList<>()).add(q);
            }
        }
    }

    public static QuestDefinition getQuestById(String id) {
        if (id == null || id.isEmpty()) return null;
        QuestDefinition q = QUESTS_BY_ID.get(id);
        if (q != null) return q;
        for (QuestDefinition cand : QUESTS) {
            if (id.equals(cand.id)) return cand;
        }
        return null;
    }

    public static List<QuestDefinition> getQuestsForNpc(String npcId) {
        if (npcId == null || npcId.isEmpty()) return java.util.Collections.emptyList();
        return QUESTS_BY_NPC.getOrDefault(npcId, java.util.Collections.emptyList());
    }

    public static void setNpcLocation(String npcId, double x, double y, double z) {
        if (npcId == null || npcId.isEmpty()) return;
        NPC_LOCATIONS.put(npcId, new double[]{x, y, z});
        File npcLocFile = FMLPaths.CONFIGDIR.get().resolve("rpg_npc_locations.json").toFile();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(npcLocFile), StandardCharsets.UTF_8)) {
            GSON.toJson(NPC_LOCATIONS, writer);
        } catch (Exception e) {
            LOGGER.error("[RPGQuestEngine] Error saving rpg_npc_locations.json", e);
        }
    }

    // ── Internal helpers ────────────────────────────────────────────────────────

    /** Parse a quests JSON that may be a bare list or {"quests":[...],"challenges":[...]}. */
    private static void applyQuestsJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            Type questListType = new TypeToken<List<QuestDefinition>>() {}.getType();
            Type challengeListType = new TypeToken<List<ChallengeDefinition>>() {}.getType();

            if (root.isJsonArray()) {
                // Legacy bare list – challenges stay as configured
                List<QuestDefinition> loaded = GSON.fromJson(root, questListType);
                if (loaded != null) QUESTS = loaded;
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("quests")) {
                    List<QuestDefinition> loaded = GSON.fromJson(obj.get("quests"), questListType);
                    if (loaded != null) QUESTS = loaded;
                }
                if (obj.has("challenges")) {
                    List<ChallengeDefinition> loaded = GSON.fromJson(obj.get("challenges"), challengeListType);
                    if (loaded != null) {
                        CHALLENGES = loaded;
                        CHALLENGES_BY_ID.clear();
                        for (ChallengeDefinition c : CHALLENGES) {
                            if (c.id != null && !c.id.isEmpty()) CHALLENGES_BY_ID.put(c.id, c);
                        }
                    }
                }
            }
            rebuildIndices();
        } catch (Exception e) {
            LOGGER.error("[RPGQuestEngine] Error parsing quests/challenges JSON", e);
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    public static void loadConfig() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        File questFile = configDir.resolve("rpg_quests.json").toFile();
        File settingsFile = configDir.resolve("rpg_quests_settings.json").toFile();

        // Si existe un archivo anterior cisco_quests.json, migrarlo limpiamente
        File oldQuestFile = configDir.resolve("cisco_quests.json").toFile();
        if (!questFile.exists() && oldQuestFile.exists()) {
            oldQuestFile.renameTo(questFile);
        }

        if (!questFile.exists()) {
            createDefaultQuests(questFile);
        } else {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(questFile), StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
                applyQuestsJson(sb.toString());
                LOGGER.info("[RPGQuestEngine] Loaded {} quests, {} challenges from rpg_quests.json",
                        QUESTS.size(), CHALLENGES.size());
            } catch (Exception e) {
                LOGGER.error("[RPGQuestEngine] Error loading rpg_quests.json", e);
            }
        }

        File npcLocFile = configDir.resolve("rpg_npc_locations.json").toFile();
        if (npcLocFile.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(npcLocFile), StandardCharsets.UTF_8)) {
                Type mapType = new TypeToken<java.util.Map<String, double[]>>() {}.getType();
                java.util.Map<String, double[]> loadedLocs = GSON.fromJson(reader, mapType);
                if (loadedLocs != null) NPC_LOCATIONS = loadedLocs;
            } catch (Exception e) {
                LOGGER.error("[RPGQuestEngine] Error loading rpg_npc_locations.json", e);
            }
        }

        if (settingsFile.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(settingsFile), StandardCharsets.UTF_8)) {
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                if (obj != null) {
                    if (obj.has("hudPosX")) hudPosX = obj.get("hudPosX").getAsInt();
                    if (obj.has("hudPosY")) hudPosY = obj.get("hudPosY").getAsInt();
                    if (obj.has("hudPosXRatio")) hudPosXRatio = obj.get("hudPosXRatio").getAsDouble();
                    if (obj.has("hudPosYRatio")) hudPosYRatio = obj.get("hudPosYRatio").getAsDouble();
                    if (obj.has("hudEnabled")) hudEnabled = obj.get("hudEnabled").getAsBoolean();
                    if (obj.has("trackedQuestId")) trackedQuestId = obj.get("trackedQuestId").getAsString();
                }
            } catch (Exception e) {
                LOGGER.error("[RPGQuestEngine] Error loading settings", e);
            }
        }
    }

    public static void saveSettings() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        File settingsFile = configDir.resolve("rpg_quests_settings.json").toFile();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(settingsFile), StandardCharsets.UTF_8)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("hudPosX", hudPosX);
            obj.addProperty("hudPosY", hudPosY);
            obj.addProperty("hudPosXRatio", hudPosXRatio);
            obj.addProperty("hudPosYRatio", hudPosYRatio);
            obj.addProperty("hudEnabled", hudEnabled);
            obj.addProperty("trackedQuestId", trackedQuestId);
            GSON.toJson(obj, writer);
        } catch (Exception e) {
            LOGGER.error("[RPGQuestEngine] Error saving settings", e);
        }
    }

    public static String getRawQuestsJson() {
        // Emit object wrapper so challenges are synced to clients
        JsonObject root = new JsonObject();
        root.add("quests", JsonParser.parseString(GSON.toJson(QUESTS)));
        root.add("challenges", JsonParser.parseString(GSON.toJson(CHALLENGES)));
        return GSON.toJson(root);
    }

    public static void applyServerQuestsJson(String json) {
        try {
            applyQuestsJson(json);
            File questFile = FMLPaths.CONFIGDIR.get().resolve("rpg_quests.json").toFile();
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(questFile), StandardCharsets.UTF_8)) {
                writer.write(json);
            } catch (Exception ignored) {}
            LOGGER.info("[RPGQuestEngine] Synced {} quests, {} challenges from server.",
                    QUESTS.size(), CHALLENGES.size());
        } catch (Exception e) {
            LOGGER.error("[RPGQuestEngine] Error applying server quests JSON", e);
        }
    }

    private static void createDefaultQuests(File file) {
        List<QuestDefinition> defaults = new ArrayList<>();

        QuestDefinition capitan = new QuestDefinition();
        capitan.id = "capitan_zombies";
        capitan.category = "MAIN";
        capitan.title = "La Caída de la Noche";
        capitan.npc = "Capitán de la Guardia";
        capitan.description = "El Capitán solicita ayuda inmediata para proteger las murallas contra la invasión de no-muertos.";
        capitan.suggestedLevel = 5;
        capitan.activeTag = "q_capitan_activa";
        capitan.doneTag = "q_capitan_lista";
        capitan.completedTag = "q_capitan_entregada";
        capitan.rewards.add("5x Monedas de Campeón");
        capitan.rewards.add("2 Niveles de Experiencia");
        capitan.stages.add(new QuestDefinition.QuestStage("Elimina 5 zombies en los alrededores", "KILL", "minecraft:zombie", 5));
        defaults.add(capitan);

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(defaults, writer);
            QUESTS = defaults;
        } catch (Exception e) {
            LOGGER.error("[RPGQuestEngine] Error creating default rpg_quests.json", e);
        }
    }
}

