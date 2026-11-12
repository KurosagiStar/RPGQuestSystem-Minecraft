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
- `QuestEntityMarkerRenderer.java`: Renderiza marcadores flotantes 3D sobre la cabeza de entidades en un radio de 32 bloques:
  - `§c⚔ [Objetivo] ⚔` sobre los monstruos objetivo (`KILL`) de la misión seguida.
  - `§e✦ [Nombre NPC]` sobre el NPC emisor de la misión activa.
  - `§6[ ! ] [Nombre NPC]` sobre el NPC cuando la misión está lista para entregar.
- `QuestHudOverlay.java`: HUD flotante moderno con badge de categoría, títulos limpios en negrita temática, divisores, progreso por objetivos, y brújula cardinal hacia objetivos y hacia el NPC de entrega (`§e▲ Capitán (45m)`).
- `QuestJournalScreen.java`: Menú completo en tecla `J` con pestaña inicial `Activas`, agrupación por categoría, word-wrap de 2 líneas y botón dinámico `Seguir` / `Dejar de seguir`.
- `QuestEventListener.java`: Listener Forge nativo para `KILL`, `MINE`, `CRAFT`, `FISH`, `SMELT`, `TALK`, `DELIVER`. Incluye resolución avanzada de kills de bosses/magia/proyectiles/mascotas (`resolvePlayerKiller`) y sonidos de oficio para aldeanos con cooldown de 1.4s.
- `QuestConfig.java`: Carga y guardado UTF-8 de `config/rpg_quests.json` tanto en cliente como en servidor (`commonSetup`).
- `NetworkHandler.java` & `SyncPlayerQuestsPacket.java`: Replicación bidireccional cliente-servidor.

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

### A. Presets de Aldeano Individuales (`preset/villager/`):
Comando: `/easy_npc preset import villager/<nombre>`
- `villager_guardia`: Capitán de la Guardia (Misión: `caza_zombies`)
- `villager_centinela`: Centinela Veterano (Misión: `contrato_fell_knights`)
- `villager_cazador`: Cazador de Bestias (Misión: `cazador_pantano`)
- `villager_alquimista`: Alquimista Elena (Misión: `elena_alquimia`)
- `villager_craftear`: Maestro Armero (Misión: `craftear_arcos`)
- `villager_minar`: Herrero Enano (Misión: `minar_hierro`)
- `villager_hornear`: Panadero de la Aldea (Misión: `hornear_pan`)
- `villager_pescar`: Pescador del Muelle (Misión: `pescar_rio`)
- `villager_explorador`: Explorador del Sendero (Misión: `explorar_torre`)
- `villager_bioma`: Naturalista de la Arboleda (Misión: `explorar_bioma`)
- `villager_estructura`: Vigía de la Ciudadela (Misión: `descubrir_aldea`)
- `villager_dimension`: Ocultista del Vacío (Misión: `viaje_nether`)

### B. Presets de Misiones Encadenadas (`preset/villager/`):
Comando: `/easy_npc preset import villager/<nombre>`
- `chain_muralla_1_comandante`: Paso 1 - Defiende muralla y entrega la orden sellada de relevo (`q_relevo_activa`).
- `chain_muralla_2_centinela`: Paso 2 - Recibe la orden de relevo (`TALK`) y encarga purgar los riscos de caballeros caídos (`q_fell_knights_activa`).
- `chain_muralla_3_cazador`: Paso 3 - Desbloqueado tras limpiar los riscos; encarga destruir el nido de arañas (`q_cazador_activa`) y otorga recompensa final.

---

## 4. Tipos de Etapa (`type`) Soportados

- `KILL`: IDs de entidades (soporta proyectiles, magia, summons domesticados y multipartes).
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
