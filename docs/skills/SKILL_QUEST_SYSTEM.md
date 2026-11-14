---
name: minecraft-quest-system
description: Complete MMORPG Quest & NPC System for Minecraft 1.20.1 Forge using custom Witcher 3 HUD/Journal engine (NPC RPG Quest System) and Easy NPC 7.18.0. Zero FTB Quests dependencies. Fully portable and extensible.
---

# Minecraft MMORPG Quest & NPC System Guide (1.20.1 Forge)

> [!IMPORTANT]
> **REGLA DE ORO PARA CUALQUIER AGENTE:**
> **NUNCA** reintroducir dependencias con `FTB Quests` o `UIQuest`. Ambos mods fueron descartados por problemas de sincronización de paquetes, pérdida de auto-tracking y rigidez en comandos.
> Este sistema es **100% autónomo** y funciona sobre dos pilares desacoplados:
> 1. **`rpgquestengine` (Mod Forge custom en Java)**: Diario (`J`), HUD flotante Witcher 3, QuestToast estilo logros vanilla, marcadores flotantes sobre entidades y NPCs (`QuestEntityMarkerRenderer`), brújula con distancia hacia zonas amplias o NPCs de regreso, validación de eventos, estructuras, dimensiones, biomas, soporte NBT, resolución de kills indirectos, y sincronización de red Servidor <-> Cliente.
> 2. **`easy_npc` (v7.18.0+)**: Gestiona modelos de NPCs y árboles de diálogo mediante condiciones `PLAYER_TAG` y jerarquía de `Priority`.

---

## 1. Arquitectura y Ubicación de Archivos

### A. Repositorio Oficial en GitHub y Local
- **GitHub:** `https://github.com/KurosagiStar/RPGQuestSystem-Minecraft.git` (rama `main`)
- **Directorio de desarrollo local:** `C:\Users\Kuromi\dev\NPC-RPG-Quest-System\Code\rpg-quest-engine\`
- **ModID:** `rpgquestengine`
- **Nombre en Forge:** `NPC RPG Quest System`
- **Paquete Java:** `com.rpgquestengine`

### B. Clases Críticas del Mod
- `RPGQuestEngine.java`: Inicializador `@Mod`, scheduler de validación en servidor cada 10 ticks (0.5s), verificación de metas (`DIMENSION`, `STRUCTURE`, `BIOME`, `ITEM`, `TAG`, `SCORE`), soporte NBT para ítems/gemas (`nbtKey`, `nbtValue`), aislamiento por índice (`getInternalScoreKey(questId, stageIndex, type)`) y sincronización de red.
- `QuestToast.java`: Notificaciones animadas nativas de Minecraft usando la UI de logros / avances (`ToastComponent`, `textures/gui/toasts.png`).
- `QuestEntityMarkerRenderer.java`: Renderiza iconos modernos 3D (`textures/gui/icons/*.png`) y texto a altura `+1.35F` sobre entidades en un radio de 32 bloques, diferenciados por categoría (`MAIN` amarillo, `SECONDARY` azul, `CONTRACT` rojo, `REPEATABLE` verde), `quest_in_progress.png` (celeste), `quest_completed.png` (dorado) y `quest_objective.png` (rojo).
- `QuestHudOverlay.java`: HUD flotante moderno con badge de categoría, títulos limpios en negrita temática, divisores, progreso por objetivos, y brújula cardinal hacia objetivos y hacia el NPC de entrega (`§e▲ Capitán (45m)`).
- `QuestJournalScreen.java`: Menú completo en tecla `J` con pestaña inicial `Activas`, agrupación por categoría, word-wrap de 2 líneas y botón dinámico `Seguir` / `Dejar de seguir`.
- `QuestEventListener.java`: Listener Forge nativo para `KILL`, `MINE`, `CRAFT`, `FISH`, `SMELT`, `TALK`, `DELIVER`. Incluye resolución avanzada de kills de bosses/magia/proyectiles/mascotas (`resolvePlayerKiller`), replicación en `StartTracking` de tags y sonidos de oficio para aldeanos con cooldown de 1.4s.
- `QuestConfig.java`: Carga y guardado UTF-8 de `config/rpg_quests.json` tanto en cliente como en servidor (`commonSetup`).
- `NetworkHandler.java`, `SyncPlayerQuestsPacket.java` & `SyncEntityTagsPacket.java`: Replicación bidireccional cliente-servidor de progreso y sincronización de scoreboard tags de entidades hacia el cliente para coincidencia estricta por `npcId`.

### C. Rutas de Despliegue en el Sistema
Al compilar con `cd Code/rpg-quest-engine; .\gradlew.bat build --no-daemon`, el archivo `build/libs/rpgquestengine-1.0.0.jar` se sincroniza en:
1. Repositorio: `C:\Users\Kuromi\dev\NPC-RPG-Quest-System\Mods\rpgquestengine-1.0.0.jar`
2. Instancia jugable: `D:\Games\Minecraft\Instances\Cisco's Fantasy Medieval RPG [Dragonfyre]\mods\`
3. Cliente de respaldo: `C:\Users\Kuromi\Desktop\cisco\Client\Cisco's Fantasy Medieval RPG [Dragonfyre]\mods\`
4. Servidor dedicado: `C:\Users\Kuromi\Desktop\cisco\Server\mods\`

---

## 2. Bases de Datos de Misiones Separadas

1. **Base Vanilla (`Addons/RPG Quest Engine (Base)/.minecraft/config/rpg_quests.json`):**
   - 100% compatible con Minecraft Vanilla y mods genéricos.
   - Libre de dependencias externas.
2. **Implementación Cisco (`Addons/Cisco Implementation/.minecraft/config/rpg_quests.json`):**
   - Incluye contratos exclusivos: *Fell King Boss* (`cisco_mod:fellkingboss`), *Ignis* (`cataclysm:ignis`), *Netherite Monstrosity* (`cataclysm:netherite_monstrosity`), *The Harbinger* (`cataclysm:the_harbinger`), *Night Lich* (`bosses_of_mass_destruction:lich`), *Frostmaw* (`mowziesmobs:frostmaw`).
   - Dimensiones: *The Aether* (`aether:the_aether`), *Twilight Forest* (`twilightforest:twilight_forest`).
   - Gemas engarzables de *Apotheosis* (`apotheosis:gem` con `affix_data.gem`).

---

## 3. Presets Oficiales de Easy NPC

> **Archivo histórico:** Los 19 presets de prueba anteriores están archivados en `preset/ejemplos/`.

### A. NPCs Principales (`MAIN` — colocación manual en historia/spawn):
- `npc_main_comandante`: Comandante (*El Camino del Guerrero* `trial_warrior`)
- `npc_main_explorador`: Explorador (*La Reliquia de los Ancestros* `trial_relic`, *El Puesto de Observación* `explorar_torre`, *El Aether* `dimension_aether_expedition`)
- `npc_main_ocultista`: Ocultista (*El Portal al Inframundo* `viaje_nether`)

### B. NPCs de Spawn Natural (`preset/villager/` — 12 en `village_npc_spawner.js`):
- **Secundarios (5):** `npc_sec_alquimista`, `npc_sec_armero`, `npc_sec_minero`, `npc_sec_bibliotecario`, `npc_sec_cartografo`
- **Contratos (4):** `npc_contract_cazador`, `npc_contract_tabernero`, `npc_contract_vigia`, `npc_contract_mercenario`
- **Repetibles (3):** `npc_rep_panadero`, `npc_rep_pescador`, `npc_rep_cazador_nocturno`

### C. Presets de Misiones Encadenadas (archivados en `preset/ejemplos/`):
Comando: `/easy_npc preset custom import villager/<nombre>`
Cada preset incluye su scoreboard tag único (`Tags: ["<nombre>"]`) sincronizado automáticamente con el cliente (`SyncEntityTagsPacket`) para emparejamiento estricto por `npcId` sin colisiones por nombre visible:
- `chain_muralla_1_comandante`: Paso 1 - Defiende muralla y entrega la orden sellada de relevo (`q_relevo_activa`).
- `chain_muralla_2_centinela`: Paso 2 - Recibe la orden de relevo (`TALK` con `target: "chain_muralla_2_centinela"`) y encarga purgar los riscos de caballeros caídos (`q_fell_knights_activa`).
- `chain_muralla_3_cazador`: Paso 3 - Desbloqueado tras limpiar los riscos; encarga destruir el nido de arañas (`q_cazador_activa`) y otorga recompensa final.

---

## 4. Tipos de Etapa (`type`) Soportados

- `KILL`: IDs de entidades (soporta proyectiles, magia, summons domesticados y multipartes).
- `KILL_BOW`: Bajas con cualquier arco o ballesta (incluye arcos de Cisco/modded).
- `SURVIVE`: Supervivencia cronometrada con oleadas dinámicas de enemigos (`mobToSpawn` soporta lista separada por comas, `ambushTitle` para alerta) y limpieza final de rezagados.
- `ITEM`: Conteo en inventario. Admite `nbtKey` anidado (ej. `affix_data.gem`) y `nbtValue`.
- `TALK`: Interactuar con un NPC específico (`target: "villager_centinela"` o tag de entidad).
- `DELIVER`: Entregar órdenes o reportes interactuando con el NPC de destino.
- `MINE`: Minería de bloques por id.
- `CRAFT`: Crafteo en mesa de trabajo o inventario.
- `FISH`: Pesca de ítems.
- `SMELT`: Fundición en horno.
- `BIOME`: Localización en bioma.
- `STRUCTURE`: Localización en mazmorra o estructura.
- `DIMENSION`: Cruce a otra dimensión.
- `SCORE`: Scoreboards dummy de vanilla.
- `TAG`: Tags vanilla de entidad.

---

## 5. Documentación Completa de Misiones
Consulta `PLANTILLA_MISIONES.md` en esta misma carpeta para ejemplos JSON completos de configuración.
