---
name: easy-npc-natural-spawner
description: Integration, rules, and ecosystem architecture for natural/procedural spawning of Easy NPC entities in Minecraft Forge 1.20.1. Complements the NPC RPG Quest System with automated placement, biome tagging, structure attachment, and density management.
---

# Easy NPC Natural Spawner Skill & Architectural Guide (1.20.1 Forge)

Este documento define la arquitectura y las reglas para implementar el addon de spawn natural para **Easy NPC (v7.18.0+)**, diseñado para complementarse de manera modular con el **NPC RPG Quest System**.

---

## 1. Misión y Filosofía del Addon

El motor principal (`rpgquestengine`) gestiona las misiones, el HUD y el seguimiento de los objetivos.
El addon `easy-npc-natural-spawner` resuelve la distribución procedural de los NPCs en el mundo:
1. **Colocación Natural**: Spawneo orgánico de NPCs en aldeas, estructuras, campamentos o biomas designados según reglas configurables.
2. **Asignación de Presets**: Al generar un NPC en el mundo, se le inyecta automáticamente el archivo `.npc.snbt` correspondiente (ej. cazador en pantanos, guardia en aldeas, explorador en montañas).
3. **Persistencia y Control de Población**: Evitar duplicación excesiva, controlar densidad por chunk o radio de bloques y asegurar que los NPCs de misión no desaparezcan (despawn falso).

---

## 2. Estructura de Repositorio (Monorepo)

Dentro de `https://github.com/KurosagiStar/RPGQuestSystem-Minecraft.git`:

```text
RPGQuestSystem-Minecraft/
├── mods/                             <-- Mods compilados (.jar)
├── config/
│   ├── rpg_quests.json               <-- Definición de misiones
│   ├── easy_npc/preset/villager/     <-- Presets SNBT de NPCs
│   └── easy_npc_spawner/             <-- Reglas de spawn de este addon
│       ├── spawn_rules.json          <-- Reglas de biomas, estructuras y presets
│       └── density_limits.json       <-- Capacidad máxima por área
├── easy-npc-spawner/                 <-- Código fuente del addon de spawn
└── src/                              <-- Código fuente del motor de quests
```

---

## 3. Especificación de Configuración (`spawn_rules.json`)

El archivo de configuración debe vincular **Reglas de Entorno** con **Presets de Easy NPC**:

```json
[
  {
    "ruleId": "spawn_cazador_pantano",
    "preset": "easy_npc:villager/npc_cazador",
    "name": "Cazador de Bestias",
    "trigger": {
      "biomes": [
        "minecraft:swamp",
        "minecraft:mangrove_swamp"
      ],
      "structures": [
        "minecraft:swamp_hut"
      ],
      "chance": 0.35,
      "minDistanceBetweenSame": 250
    },
    "spawnCondition": {
      "minY": 62,
      "maxY": 80,
      "requiresSolidGround": true
    },
    "persistence": {
      "invulnerable": true,
      "persistent": true,
      "noDespawn": true
    }
  },
  {
    "ruleId": "spawn_capitan_guardia_aldea",
    "preset": "easy_npc:villager/cisco_capitan_guardia",
    "name": "Capitán de la Guardia",
    "trigger": {
      "structures": [
        "minecraft:village_plains",
        "minecraft:village_taiga",
        "minecraft:village_snowy"
      ],
      "chance": 1.0,
      "maxPerStructure": 1
    },
    "persistence": {
      "invulnerable": true,
      "persistent": true
    }
  }
]
```

---

## 4. Puntos Críticos de Integración Técnica

### A. Carga de Presets de Easy NPC por Código
En Forge 1.20.1, Easy NPC almacena sus entidades bajo `de.markusbordihn.easynpc.entity.EasyNPC`:
- Para aplicar un preset a una entidad recién creada:
  - Leer el SNBT de `config/easy_npc/preset/...`
  - Utilizar el deserializador NBT de Minecraft / Easy NPC (`TagParser.parseTag(snbtString)`).
  - Aplicar el tag NBT resultante con `entity.load(nbt)`.

### B. Inyección de Tags de Misión
Al generar el NPC, sus datos de diálogo ya deben tener los tags vinculados con `rpg_quests.json` (ejemplo: si la misión es `cazador_pantano`, el preset debe contener en prioridad 0 la acción `/tag @p add q_cazador_activa`).

### C. Prevención de Duplicación (UUIDs y Chunk Tracking)
- Cuando un NPC es generado naturalmente por estructura o chunk, guardar su posición o registrar un tag de chunk (`has_quest_npc`) en la Capability del LevelChunk para evitar re-spawns en recargas de chunk.

---

## 5. Flujo de Trabajo Compartido (Colaboración Git)

1. **Tu compañero**: Desarrolla en la subcarpeta `easy-npc-spawner/` o en un mod companion.
2. **Tú**: Mantienes el HUD, el diario y la validación en `src/`.
3. Ambos comparten `config/` (`rpg_quests.json`, presets y `spawn_rules.json`).
4. Al hacer commits, ninguno toca la carpeta del otro, evitando conflictos de merge.
