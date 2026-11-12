# NPC RPG Quest System (Minecraft Forge 1.20.1)

Sistema universal e independiente de misiones estilo MMORPG / The Witcher 3 con integración nativa para **Easy NPC (v7.18.0+)**. No depende de FTB Quests ni de mods externos de interfaz.

---

## 📁 Estructura del Repositorio

```text
RPGQuestSystem-Minecraft/
├── Mods/                             <-- Archivos instalables en el juego (.jar)
│   └── rpgquestengine-1.0.0.jar      <-- Mod compilado listo para .minecraft/mods/
├── Code/                             <-- Todo el código fuente de los sistemas
│   ├── rpg-quest-engine/             <-- Código fuente del mod Java Forge
│   │   ├── src/
│   │   ├── build.gradle
│   │   └── gradlew.bat
│   └── easy-npc-spawner/             <-- Código / scripts del addon de spawn
├── config/                           <-- Configuraciones compartidas
│   ├── rpg_quests.json               <-- Definición de misiones
│   └── easy_npc/preset/villager/     <-- Presets SNBT de Easy NPC
├── docs/                             <-- Guías técnicas y skills
│   └── skills/
└── README.md
```

---

## 🚀 Instalación Rápida (Jugador / Servidor)

1. Copia el archivo de la carpeta `Mods/`:
   - `Mods/rpgquestengine-1.0.0.jar` ➔ en la carpeta `.minecraft/mods/` (cliente y servidor).
2. Copia la carpeta `config/`:
   - `config/rpg_quests.json` ➔ en `.minecraft/config/rpg_quests.json`.
   - `config/easy_npc/preset/` ➔ en `.minecraft/config/easy_npc/preset/`.
3. Inicia o reinicia Minecraft.

---

## 🎮 Controles y Teclas

- **`J`**: Abre el **Diario de Misiones** (pestañas: *Activas*, *Principales*, *Secundarias*, *Contratos*, *Completadas*).
- **`H`**: Abre la pantalla de **reposicionamiento del HUD flotante** (arrastra el cuadro con el ratón).
- **Botón Seguir / Dejar de seguir**: Fija o quita la misión seleccionada del HUD flotante.

---

## 🌟 Características Principales

1. **HUD Flotante Witcher 3**:
   - Muestra badge por categoría (`PRINCIPAL`, `SECUNDARIA`, `CONTRATO`, `REPETIBLE`).
   - Título estilizado, objetivos dinámicos, barras de progreso y tachado de metas cumplidas.
   - Brújula cardinal (`▲ ↗ ▶ ↘ ▼ ↙ ◀ ↖`) y distancia en metros hacia zonas amplias de exploración o de regreso al NPC.
2. **Notificaciones tipo Logro Vanilla (QuestToast)**:
   - Notificación animada en la esquina superior derecha al iniciar, progresar o completar misiones.
3. **Marcadores sobre Mobs y NPCs**:
   - `§c⚔ §fObjetivo` sobre monstruos que debes cazar para la misión activa.
   - `§e§l! §6§lMisión` sobre el NPC con misión disponible para aceptar.
   - `§b§l✦ §fEn progreso` sobre el NPC que otorgó la misión activa.
   - `§b§l✦ §fHablar` sobre el NPC de destino de informes o entregas (`TALK` / `DELIVER`).
   - `§6§l? §e§lCompletada` sobre el NPC cuando la misión está lista para reclamar recompensas.
4. **Sincronización Cliente-Servidor Nativa**:
   - Paquete `SyncPlayerQuestsPacket` cada 10 ticks (0.5s) para scores, misiones activas y posiciones en vivo.
   - Paquete `SyncEntityTagsPacket` enviado en `StartTracking` para replicar tags internos de entidades (`npcId`) al cliente, garantizando detección precisa sin falsos positivos por nombres repetidos.

---

## 📜 Estructura de Misión (`config/rpg_quests.json`)

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
    "npcX": 100,
    "npcZ": 200,
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
        "description": "Fabrica 2 Flechas adicionales",
        "type": "CRAFT",
        "target": "minecraft:arrow",
        "amount": 8,
        "optional": true
      }
    ]
  }
]
```

### Tipos de Etapas Soportadas (`type`):
- `KILL`: Eliminar entidades por ResourceLocation (ej. `minecraft:zombie`).
- `ITEM`: Mantener ítems en el inventario (ej. `minecraft:iron_ingot`).
- `MINE`: Picar bloques (ej. `minecraft:diamond_ore`).
- `CRAFT`: Craftear ítems (ej. `minecraft:torch`).
- `FISH`: Pescar ítems (`target: "any"` o ID específico).
- `SMELT`: Fundir ítems en hornos.
- `BIOME`: Explorar un bioma específico (ej. `minecraft:dripstone_caves`).
- `STRUCTURE`: Entrar a una estructura/mazmorra (ej. `minecraft:mineshaft`).
- `SCORE`: Evaluar scoreboards de Minecraft.
- `TAG`: Evaluar tags de jugador (`/tag`).

---

## 👥 Integración con Easy NPC (Diálogos de 4 Estados)

Importar un preset en el juego mirando al NPC:
```text
/easy_npc preset import [nombre_preset]
```

### Prioridades de Diálogo:
- **Prioridad 20 (Entregado)**: Condición: `PLAYER_TAG: q_[id]_entregada`. Mensaje de agradecimiento post-misión.
- **Prioridad 15 (Completado para entregar)**: Condición: `PLAYER_TAG: q_[id]_lista`. Otorga recompensas y ejecuta:
  ```text
  /tag @p add q_[id]_entregada
  /tag @p remove q_[id]_lista
  /tag @p remove q_[id]_activa
  ```
- **Prioridad 10 (En Progreso)**: Condición: `PLAYER_TAG: q_[id]_activa`. Mensaje de recordatorio ("¿Cómo va la misión?").
- **Prioridad 0 (Disponible)**: Sin condiciones. Botón de diálogo que ejecuta:
  ```text
  /tag @p add q_[id]_activa
  ```

---

## 🛠 Compilación desde el Código Fuente

Requisitos: JDK 17.

```bash
# Entrar a la carpeta del motor:
cd Code/rpg-quest-engine

# Compilar:
./gradlew build
# El jar resultante se genera en Code/rpg-quest-engine/build/libs/rpgquestengine-1.0.0.jar
```
