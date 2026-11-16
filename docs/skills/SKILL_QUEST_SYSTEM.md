---
name: minecraft-quest-system
description: Complete MMORPG Quest & NPC System for Minecraft 1.20.1 Forge using custom Witcher 3 HUD/Journal engine (NPC RPG Quest System) and Easy NPC 7.18.0 entities with custom RPG Dialog UI.
---

# Minecraft MMORPG Quest & NPC System Guide (1.20.1 Forge)

> [!IMPORTANT]
> **REGLAS CRÍTICAS PARA CUALQUIER AGENTE:**
> 1. **UI de Diálogo Propia (`NpcDialogScreen`):** Easy NPC 7.18.0 **ignora** `Conditions` en botones/diálogos y rompe su UI con >4 botones. Por eso `RPGQuestEngine.onPlayerEntityInteract` intercepta el clic derecho normal (sin Shift) sobre cualquier entidad con tag `npc_*` y abre `NpcDialogScreen` directamente. `Shift + clic derecho` deja pasar la interacción nativa de Easy NPC para edición admin.
> 2. **No escribir árboles de diálogo complejos en `.npc.snbt`:** Easy NPC solo provee entidad, modelo/skin y posición. Toda la lógica de misiones, prerrequisitos, lore, objetivos y recompensas vive en `rpg_quests.json` + Java (`rpgquestengine`).
> 3. **Despliegue obligatorio tras cada cambio:** Copiar `.jar`, `rpg_quests.json` y `.npc.snbt` tanto a `Minecraft-Ready/` como a `D:\Games\Minecraft\Instances\Cisco's Fantasy Medieval RPG [Dragonfyre]\` usando **`Copy-Item -LiteralPath`** en PowerShell (por los corchetes `[Dragonfyre]`), y hacer `git push origin main`.

---

## 1. Arquitectura y Ubicación de Archivos

### A. Repositorio y Paquete
- **GitHub:** `https://github.com/KurosagiStar/RPGQuestSystem-Minecraft.git` (rama `main`)
- **Código fuente:** `C:\Users\Kuromi\dev\NPC-RPG-Quest-System\Code\rpg-quest-engine\`
- **Config y presets listos:** `C:\Users\Kuromi\dev\NPC-RPG-Quest-System\Minecraft-Ready\`
- **ModID:** `rpgquestengine` (`com.rpgquestengine`)

### B. Clases Clave del Mod
- `RPGQuestEngine.java`: Inicializador `@Mod`, scheduler cada 10 ticks, validación (`ITEM`, `KILL`, `DIMENSION`, `BIOME`, `STRUCTURE`, `SURVIVE`, `TAG`, `SCORE`), interceptación de clic derecho en NPCs (`onPlayerEntityInteract`), comandos (`/rpgquest reload`, `reset`, `talk`, `ui`, `sync_npcs`, `respawn_saved`, `spawn_npc`, `set_npc_pos`).
- `NpcDialogScreen.java` + `OpenNpcDialogPacket.java` + `NpcDialogActionPacket.java`: Pantalla RPG propia con botones dinámicos (`Aceptar Misión`, `Completar Misión`, `Cerrar`), lore completo, objetivo, separador y cuadritos con iconos reales de recompensas (`rewardXp`, `rewardXpLevels`, `rewardItems`, `randomRewards` con tooltip de posibilidades, `choiceRewards` clicable para elegir 1). Al completar, sincroniza también `ftbquests change_progress <player> complete <ftbQuestId>` si existe `ftbQuestId`.
- `AdminNpcManagerScreen.java` + `AdminNpcActionPacket.java` + `OpenAdminUiPacket.java`: Gestor visual (`/rpgquest ui`) por campañas y NPCs, con `collectChain` hasta profundidad 250.
- `QuestHudOverlay.java`: HUD flotante con progreso, brújula hacia zonas y nombre legible (`q.npc`) al regresar a entregar.
- `QuestJournalScreen.java`: Diario completo en tecla `J`.
- `QuestEntityMarkerRenderer.java`: Marcadores 3D sobre entidades (`quest_available`, `quest_in_progress`, `quest_completed`, `quest_objective`) usando `RenderType.textSeeThrough` para compatibilidad con `F3+T` y shaders.
- `assets/xaerominimap/`: Icono azul de exclamación (`quest_exclamation.png`) y definiciones para `easy_npc/*` embebidos directamente en el `.jar` para el radar de Xaero's Minimap (sin requerir resource pack manual).
- `QuestConfig.java`: Lectura/escritura UTF-8 de `config/rpg_quests.json` y `config/rpg_npc_locations.json`.

### C. Flujo de Compilación y Despliegue (PowerShell)
```powershell
cd C:\Users\Kuromi\dev\NPC-RPG-Quest-System\Code\rpg-quest-engine
.\gradlew.bat build --no-daemon

Copy-Item -LiteralPath "C:\Users\Kuromi\dev\NPC-RPG-Quest-System\Code\rpg-quest-engine\build\libs\rpgquestengine-1.0.0.jar" -Destination "C:\Users\Kuromi\dev\NPC-RPG-Quest-System\Minecraft-Ready\mods\rpgquestengine-1.0.0.jar" -Force
Copy-Item -LiteralPath "C:\Users\Kuromi\dev\NPC-RPG-Quest-System\Code\rpg-quest-engine\build\libs\rpgquestengine-1.0.0.jar" -Destination "D:\Games\Minecraft\Instances\Cisco's Fantasy Medieval RPG [Dragonfyre]\mods\rpgquestengine-1.0.0.jar" -Force
Copy-Item -LiteralPath "C:\Users\Kuromi\dev\NPC-RPG-Quest-System\Minecraft-Ready\config\rpg_quests.json" -Destination "D:\Games\Minecraft\Instances\Cisco's Fantasy Medieval RPG [Dragonfyre]\config\rpg_quests.json" -Force
```

---

## 2. Campañas Principales y NPCs (`MAIN` — 7 Cadenas, 25 NPCs, 236 Misiones)

Todas las cadenas usan **progresión secuencial por bloque de NPC** (completas todas las misiones del NPC 1 antes de pasar al NPC 2, sin idas y vueltas) y texto en español neutro con instrucciones de dónde ir o cómo invocar:

1. **The Champion's Trials (`cisco_trials_`, 67 misiones):** `npc_main_comandante`, `npc_main_trials_2..5`
2. **Traveller's Journey (`cisco_traveller_`, 55 misiones):** `npc_main_explorador`, `npc_main_traveller_2..5`
3. **Age of Dragons (`cisco_dragons_`, 30 misiones):** `npc_main_ocultista`, `npc_main_dragons_2..5`
4. **Adventum Per Tempus (`cisco_adventum_`, 44 misiones):** `npc_main_adventum_1..4` (`Cronomante I..IV`)
5. **Deus Interfectorem (`cisco_deus_`, 25 misiones):** `npc_main_deus_1..3` (`Verdugo de Dioses I..III`)
6. **Beasts of Legend (`cisco_beasts_`, 11 misiones):** `npc_main_beasts_1..2` (`Cazador de Bestias I..II`)
7. **Champions of the Risen (`cisco_champions_`, 4 misiones):** `npc_main_champions_1` (`Campeón Alzado`)

### NPCs Naturales en Aldeas (`village_npc_spawner.js` — 18 NPCs, 61 Misiones)
- **Secundarios (`SECONDARY`):** `npc_sec_alquimista`, `npc_sec_armero`, `npc_sec_minero`, `npc_sec_bibliotecario`, `npc_sec_cartografo`, `npc_sec_herbolario`, `npc_sec_ingeniero` (4 variantes c/u en los 5 primeros, 3 en los 2 nuevos).
- **Contratos (`CONTRACT`):** `npc_contract_cazador` (6), `npc_contract_tabernero` (4), `npc_contract_vigia` (4), `npc_contract_mercenario` (4), `npc_contract_inquisidor` (3), `npc_contract_explorador_marino` (3).
- **Repetibles (`REPEATABLE`):** `npc_rep_panadero` (4), `npc_rep_pescador` (4), `npc_rep_cazador_nocturno` (4), `npc_rep_granjero` (3), `npc_rep_lenador` (3). Las misiones `REPEATABLE` no añaden `completedTag` al entregarse.

---

## 3. Reglas de Formato en `rpg_quests.json`

- **Campo `amount`:** `QuestHudOverlay` y el motor leen `stage.amount` (NO `required`).
- **Campo `npc` vs `npcId`:** `npcId` es la tag técnica de la entidad (ej. `npc_main_comandante`); `npc` es el nombre legible mostrado en HUD/radar/mensajes (ej. `Comandante`).
- **Recompensas soportadas:** `rewardXp`, `rewardXpLevels`, `rewardItems` (`[{id, count}]`), `randomRewards` (`[{id, count}]`), `choiceRewards` (`[{id, count}]`).
- **Prerrequisitos bloqueados:** si un NPC está bloqueado por otro NPC, `RPGQuestEngine.openNpcDialog` envía el nombre del NPC requerido en `questLore` para que `NpcDialogScreen` muestre `Completa primero las misiones de <NPC> (<Título>)`.

---

## 4. Comandos In-Game Útiles

- `/rpgquest reload`: Recarga `rpg_quests.json` en caliente sin reiniciar Minecraft.
- `/rpgquest reset`: Limpia tags y progreso del jugador para probar desde cero.
- `/rpgquest ui`: Abre `AdminNpcManagerScreen` para spawnear, mover o re-spawnear NPCs por campaña.
- `/rpgquest spawn_npc <npc_id>`: Spawnea un preset `.npc.snbt` en la posición actual y guarda sus coordenadas.
- `/rpgquest respawn_saved`: Teletransporta o re-spawnea todos los NPCs guardados en `rpg_npc_locations.json` en sus coordenadas exactas.
- `/rpgquest sync_npcs`: Re-importa presets de NPCs cargados preservando coordenadas (no borra la entidad vieja si el spawn falla).
