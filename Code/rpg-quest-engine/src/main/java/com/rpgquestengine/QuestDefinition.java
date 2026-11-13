package com.rpgquestengine;

import java.util.ArrayList;
import java.util.List;

public class QuestDefinition {
    public String id;
    public String category = "SECONDARY"; // MAIN, SECONDARY, REPEATABLE, CONTRACT
    public String title = "Misión";
    public String npc = "Desconocido";
    public String npcId = ""; // ID técnico del NPC (ej: "npc_matar", "capitan_guardia") para rastreo robusto
    public String description = "";
    public int suggestedLevel = 1;
    public List<String> rewards = new ArrayList<>();

    // Prerrequisito para encadenar misiones (Punto 5)
    public String prerequisiteQuestId = "";
    // Tags que bloquean esta misión (para rutas ramificadas / decisiones excluyentes)
    public List<String> blockedByTags = new ArrayList<>();

    // Recompensas automáticas otorgadas por el motor al completarse
    public int rewardXp = 0;
    public List<RewardItem> rewardItems = new ArrayList<>();

    public static class RewardItem {
        public String id = "minecraft:emerald";
        public int count = 1;
    }

    public String activeTag;
    public String doneTag;
    public String completedTag;

    // Coordenadas del NPC para regreso (opcional)
    public boolean hasNpcLocation = false;
    public int npcX = 0;
    public int npcZ = 0;

    public List<QuestStage> stages = new ArrayList<>();

    public static class QuestStage {
        public String description;
        // KILL, ITEM, MINE, CRAFT, FISH, SMELT, DIMENSION, STRUCTURE, BIOME, TAG, SCORE
        public String type = "KILL";
        public String target;
        public int amount = 1;

        // Objetivo opcional (Punto 2)
        public boolean optional = false;

        // Zona de búsqueda amplia / brújula (Punto 1)
        public boolean hasLocation = false;
        public boolean relativeToNpc = false;
        public int posX = 0;
        public int posZ = 0;
        public int radius = 150; // Radio amplio de búsqueda (ej. 150 o 200 bloques)

        // Spawneo dinámico garantizado en la zona (Punto 4 / Cacerías / Emboscadas)
        public boolean spawnMobsOnEntry = false;
        public String mobToSpawn = "minecraft:cave_spider";
        public int mobSpawnCount = 4;
        public String ambushTitle = "";

        // Soporte para filtrado por NBT (ej. Gemas de Apotheosis: nbtKey="affix_data.gem", nbtValue="apotheosis:core/warlord")
        public String nbtKey = "";
        public String nbtValue = "";

        public QuestStage() {}

        public QuestStage(String desc, String type, String target, int amount) {
            this.description = desc;
            this.type = type;
            this.target = target;
            this.amount = amount;
        }
    }
}
