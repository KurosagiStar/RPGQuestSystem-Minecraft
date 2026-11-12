---
name: minecraft-quest-system
description: Complete MMORPG Quest & NPC System for Minecraft 1.20.1 Forge using custom Witcher 3 HUD/Journal engine (NPC RPG Quest System) and Easy NPC 7.18.0. Zero FTB Quests dependencies. Fully portable and extensible.
---

# Minecraft MMORPG Quest & NPC System Guide (1.20.1 Forge)

> [!IMPORTANT]
> **REGLA DE ORO PARA CUALQUIER AGENTE:**
> **NUNCA** reintroducir dependencias con `FTB Quests` o `UIQuest`. Ambos mods fueron descartados por problemas de sincronización de paquetes, pérdida de auto-tracking y rigidez en comandos.
> Este sistema es **100% autónomo** y funciona sobre dos pilares desacoplados:
> 1. **`rpgquestengine` (Mod Forge custom en Java)**: Diario (`J`), HUD flotante Witcher 3, QuestToast estilo logros vanilla, marcadores flotantes sobre entidades y NPCs (`QuestEntityMarkerRenderer`), brújula con distancia hacia zonas amplias o NPCs de regreso, validación de eventos, estructuras, dimensiones, biomas, y sincronización de red Servidor <-> Cliente.
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
- `RPGQuestEngine.java`: Inicializador `@Mod`, scheduler de validación en servidor cada 10 ticks (0.5s), verificación de metas (`DIMENSION`, `STRUCTURE`, `BIOME`, `ITEM`, `TAG`, `SCORE`), aislamiento por índice (`getInternalScoreKey(questId, stageIndex, type)`) y sincronización de red.
- `QuestToast.java`: Notificaciones animadas nativas de Minecraft usando la UI de logros / avances (`ToastComponent`, `textures/gui/toasts.png`).
- `QuestEntityMarkerRenderer.java`: Renderiza marcadores flotantes 3D sobre la cabeza de entidades en un radio de 32 bloques:
  - `§c⚔ [Objetivo] ⚔` sobre los monstruos objetivo (`KILL`) de la misión seguida.
  - `§e✦ [Nombre NPC]` sobre el NPC emisor de la misión activa.
  - `§6[ ! ] [Nombre NPC]` sobre el NPC cuando la misión está lista para entregar.
- `QuestHudOverlay.java`: HUD flotante moderno con badge de categoría, títulos limpios en negrita temática, divisores, progreso por objetivos, y brújula cardinal hacia objetivos y hacia el NPC de entrega (`§e▲ Capitán (45m)`).
- `QuestJournalScreen.java`: Menú completo en tecla `J` con pestaña inicial `Activas`, agrupación por categoría, word-wrap de 2 líneas y botón dinámico `Seguir` / `Dejar de seguir`.
- `QuestEventListener.java`: Listener Forge nativo para `KILL`, `MINE`, `CRAFT`, `FISH`, `SMELT`.
- `QuestConfig.java`: Carga y guardado UTF-8 de `config/rpg_quests.json`.
- `NetworkHandler.java` & `SyncPlayerQuestsPacket.java`: Replicación bidireccional cliente-servidor.

### C. Rutas de Despliegue en el Sistema
Al compilar con `cd Code/rpg-quest-engine; .\gradlew.bat build --no-daemon`, el archivo `build/libs/rpgquestengine-1.0.0.jar` se sincroniza en:
1. Repositorio: `C:\Users\Kuromi\dev\NPC-RPG-Quest-System\Mods\rpgquestengine-1.0.0.jar`
2. Instancia jugable: `D:\Games\Minecraft\Instances\Cisco's Fantasy Medieval RPG [Dragonfyre]\mods\`
3. Cliente de respaldo: `C:\Users\Kuromi\Desktop\cisco\Client\Cisco's Fantasy Medieval RPG [Dragonfyre]\mods\`
4. Servidor dedicado: `C:\Users\Kuromi\Desktop\cisco\Server\mods\`
5. Paquete de distribución: `C:\Users\Kuromi\Desktop\RPGQuestEngine_Distribution\mods\`

---

## 2. Configuración de Misiones (`config/rpg_quests.json`)

```json
[
  {
    "id": "cazador_pantano",
    "category": "CONTRACT",
    "title": "La Bestia de la Cueva",
    "npc": "Cazador de Bestias",
    "description": "Neutraliza el nido de arañas del pantano.",
    "suggestedLevel": 6,
    "activeTag": "q_cazador_activa",
    "doneTag": "q_cazador_lista",
    "completedTag": "q_cazador_entregada",
    "hasNpcLocation": true,
    "npcX": -120,
    "npcZ": 240,
    "rewards": ["12x Monedas de Campeón", "1x Arco Mejorado"],
    "stages": [
      {
        "description": "Explora el sector del pantano",
        "type": "SCORE",
        "target": "dummy_none",
        "amount": 0,
        "hasLocation": true,
        "posX": -250,
        "posZ": 350,
        "radius": 160
      },
      {
        "description": "Elimina 3 Arañas de Cueva",
        "type": "KILL",
        "target": "minecraft:cave_spider",
        "amount": 3
      },
      {
        "description": "Prepara flechas de reserva",
        "type": "CRAFT",
        "target": "minecraft:arrow",
        "amount": 8,
        "optional": true
      }
    ]
  }
]
```

### Tipos de Etapa (`type`):
- `KILL`: IDs de entidades registradas (ej. `minecraft:zombie`, `minecraft:cave_spider`).
- `ITEM`: Conteo de ítems en inventario (ej. `minecraft:iron_ingot`).
- `MINE`: Minería de bloques por id (ej. `minecraft:diamond_ore`).
- `CRAFT`: Crafteo en mesa de trabajo o inventario.
- `FISH`: Pesca de ítems (`target: "any"` o ID específico).
- `SMELT`: Fundición en horno.
- `BIOME`: Localización en bioma (ej. `minecraft:dripstone_caves`).
- `STRUCTURE`: Localización en mazmorra o estructura (ej. `minecraft:mineshaft`).
- `SCORE`: Scoreboards dummy de vanilla.
- `TAG`: Tags vanilla de entidad (`/tag`).

---

## 3. Integración con Easy NPC (Diálogos de 4 Estados)

Comando para importar presets mirando al NPC:
```text
/easy_npc preset import [nombre_preset]
```

### Jerarquía de Prioridades en SNBT:
1. **Prioridad 20 (Misión Entregada / Finalizada)**:
   - Condición: `PLAYER_TAG: q_[id]_entregada`
   - Diálogo: "Gracias por tu ayuda anterior, aventurero."
2. **Prioridad 15 (Misión Lista para Cobrar)**:
   - Condición: `PLAYER_TAG: q_[id]_lista`
   - Acciones de Diálogo: Otorga recompensas y ejecuta:
     ```text
     /tag @p add q_[id]_entregada
     /tag @p remove q_[id]_lista
     /tag @p remove q_[id]_activa
     ```
3. **Prioridad 10 (Misión en Progreso)**:
   - Condición: `PLAYER_TAG: q_[id]_activa`
   - Diálogo: "¿Aún no has terminado? Vuelve cuando tengas los objetivos listos."
4. **Prioridad 0 (Misión Disponible para Aceptar)**:
   - Sin condiciones.
   - Botón de aceptación:
     ```text
     /tag @p add q_[id]_activa
     ```

---

## 8. Plantilla y Gu�a de Tipos de Misiones
Consulta PLANTILLA_MISIONES.md ubicado en este mismo directorio para ejemplos completos listos para copiar de:
- Br�jula y seguimiento en vivo por UUID / coordenadas fijas de fallback.
- Zonas de b�squeda con radio recomendado (20-30 m para cuevas, 40-60 m para exteriores).
- Spawneo autom�tico controlado con cupo (mobSpawnCount), sonido por paquete UI y corte autom�tico al cumplir objetivo KILL.
- Tipos de etapas: KILL, MINE, CRAFT, ITEM, SMELT, FISH, BIOME, STRUCTURE, DIMENSION.
