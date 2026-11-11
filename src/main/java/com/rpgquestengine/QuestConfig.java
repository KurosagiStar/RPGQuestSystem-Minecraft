package com.rpgquestengine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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

    public static int hudPosX = 10;
    public static int hudPosY = 15;
    public static boolean hudEnabled = true;
    public static String trackedQuestId = "";

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
                Type listType = new TypeToken<List<QuestDefinition>>() {}.getType();
                List<QuestDefinition> loaded = GSON.fromJson(reader, listType);
                if (loaded != null) QUESTS = loaded;
                LOGGER.info("[RPGQuestEngine] Loaded {} quests from rpg_quests.json", QUESTS.size());
            } catch (Exception e) {
                LOGGER.error("[RPGQuestEngine] Error loading rpg_quests.json", e);
            }
        }

        if (settingsFile.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(settingsFile), StandardCharsets.UTF_8)) {
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                if (obj != null) {
                    if (obj.has("hudPosX")) hudPosX = obj.get("hudPosX").getAsInt();
                    if (obj.has("hudPosY")) hudPosY = obj.get("hudPosY").getAsInt();
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
            obj.addProperty("hudEnabled", hudEnabled);
            obj.addProperty("trackedQuestId", trackedQuestId);
            GSON.toJson(obj, writer);
        } catch (Exception e) {
            LOGGER.error("[RPGQuestEngine] Error saving settings", e);
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

