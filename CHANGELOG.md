# Registro de Cambios (Changelog) - RPG Quest Engine

## [1.3.1-update] - 2026-11-13
- **HUD de Supervivencia (`SURVIVE`):** El aviso de enemigos restantes al finalizar el tiempo (`⚔ Elimina enemigos restantes [N]`) se muestra como segundo objetivo en el HUD (`QuestHudOverlay`) y marca con `Objetivo` sobre sus cabezas a los rezagados (`QuestEntityMarkerRenderer`).
- **Corrección de Oleadas y Spawneo:**
  - Bloqueado el spawneo de nuevas oleadas cuando el temporizador `SURVIVE` ya alcanzó su objetivo (`current >= required`).
  - Conteo global de rezagados por `waveTag` (`level.getAllEntities()`) sin depender del radio de 80 bloques.
  - Búsqueda vertical de suelo limitada a `-1..+4` para evitar spawneos bajo tierra.
  - Equipamiento de armas por defecto (`CROSSBOW`, `IRON_AXE`, `IRON_SWORD`) para illagers que spawneen sin arma.
- **Alineación y Diálogos en Presets:** Corregidos los `activeTag`/`lista`/`entregada` en 6 presets y añadidos los 9 diálogos faltantes de bosses/dimensiones/gemas en `chain_muralla_2_centinela`, `villager_cazador_contracts`, `villager_dimension_main`, `villager_explorador_main`, `villager_bioma_secondary` y `villager_alquimista_secondary`.
- **Migración de Misiones Anteriores (`rpg_quests.json`):** Añadido `relativeToNpc: true` en `cazador_pantano` y `explorar_torre`, y configurados `rewardXp` y `rewardItems` en las 22 misiones anteriores para mostrar el `QuestToast` de recompensas.

## [1.3.0-update] - 2026-11-12

### 1. Nuevas Mecánicas de Misión y Combate
- **Recompensas Automáticas (`rewardXp` y `rewardItems`):** El motor entrega XP e ítems reales al completar la misión (`completedTag`), y muestra un **Toast de Recompensas** (`QuestToast`) con el icono del ítem obtenido.
- **Rutas Ramificadas (`blockedByTags`):** Permite bloquear misiones excluyentes si el jugador completó otra ruta.
- **Emboscadas, Oleadas y Supervivencia (`SURVIVE`, `ambushTitle`, `mobToSpawn` múltiple):**
  - `SURVIVE`: Temporizador por segundos dentro de la zona de misión; al terminar el tiempo exige eliminar a los enemigos restantes de la oleada (`rqe_wave_*`) antes de completar la misión.
  - Oleadas dinámicas: Spawnean enemigos armados (`finalizeSpawn`) y se reponen en bloque al bajar del 50% de vivos.
  - Eliminado el sonido molesto en cada tick de progreso.
- **Combate a Distancia (`KILL_BOW`):** Detecta kills con arcos vanilla y arcos modded/Cisco (`ProjectileWeaponItem` / flechas).
- **Zonas Portables (`relativeToNpc`):** Calcula coordenadas relativas al jugador/NPC con un máximo de 300 metros.
- **Comando `/rpgquest reload`:** Recarga `rpg_quests.json` en caliente sin reiniciar el juego.

### 2. Estandarización de Presets por Categoría
- Renombrados los presets individuales en `preset/villager/` con sufijos `_main`, `_secondary`, `_contracts`, `_repeatable` y sus `Tags` internos.
- Añadidos nuevos presets de contrato: `villager_tabernero_contracts`, `villager_gremio_contracts` y `villager_vigia_contracts`.

---

## [1.2.0-update] - 2026-11-12

### 1. Sincronización Estricta de Tags de Entidades (`SyncEntityTagsPacket`)
- **Resolución de Limitación Vanilla/Forge:** Minecraft Forge no sincroniza los scoreboard tags (`living.getTags()`) de entidades al cliente. Se implementó `SyncEntityTagsPacket` enviado automáticamente cuando el jugador comienza a rastrear la entidad (`PlayerEvent.StartTracking`).
- **Eliminación Total de Colisión por Nombres Visibles:** `QuestEntityMarkerRenderer` y `isMatchingNpc` priorizan de forma estricta los `npcId` internos registrados en las misiones. Si una misión define `npcId` (ej. `chain_muralla_1_comandante`), solo las entidades que contengan ese tag recibirán marcadores, sin colisionar jamás con NPCs de nombres idénticos o similares.

### 2. Modernización Visual de Marcadores Flotantes 3D e Iconos por Categoría
- **Iconos Modernos 64x64 RGBA (`textures/gui/icons/`):** Renderizados en 3D encima del texto a altura elevada (`+1.35F`) para evitar solapamientos con niveles y barras de vida:
  - **Principal (`MAIN`):** `quest_available.png` (Amarillo `!`) + `§6§lMisión`.
  - **Secundaria (`SECONDARY`):** `quest_available_secondary.png` (Azul `!`) + `§9§lSecundaria`.
  - **Contrato (`CONTRACT`):** `quest_available_contract.png` (Rojo `!`) + `§c§lContrato`.
  - **Repetible (`REPEATABLE`):** `quest_available_repeatable.png` (Verde `!`) + `§a§lRepetible`.
  - **En Progreso / Hablar:** `quest_in_progress.png` (Celeste `✦`).
  - **Completada:** `quest_completed.png` (Dorado `?`, también visible junto al badge en el HUD).
  - **Objetivo (`KILL`):** `quest_objective.png` (Rojo `⚔`).
- **Radar en Tiempo Real:** `QuestHudOverlay` prioriza coordenadas vivas en el cliente por `npcId` frame a frame y `RPGQuestEngine` sincroniza tags de entidades cercanas (radio 48 bloques) cada 10 ticks.
- **Presets `villager_*.npc.snbt`:** Todos los presets individuales ahora incluyen su campo `Tags: ["villager_<nombre>"]` para coincidencia estricta por `npcId`.

### 3. Presets Oficiales de Cadena de Misiones (Easy NPC v7.18.0)
- Presets reubicados de forma estándar en `config/easy_npc/preset/villager/` con importación nativa:
  - `/easy_npc preset custom import villager/chain_muralla_1_comandante`
  - `/easy_npc preset custom import villager/chain_muralla_2_centinela`
  - `/easy_npc preset custom import villager/chain_muralla_3_cazador`
- Metadatos completos y atributos `id: "easy_npc:villager"` y `EasyNPCVersion: 3` incluidos para compatibilidad 100% inmediata.

### 4. Limpieza Profunda en Reset de Misiones
- El comando `/rpgquest reset` ahora purga en tiempo real:
  - Todos los tags de estado `q_*` y de seguimiento `rqe_*`.
  - Tags de asignación y coordenadas fijas (`rqe_pos_*` y `rqe_npc_*`).
  - Scoreboards internos y sincronización inmediata a cliente de UI y marcadores.

---

### 1. Cadenas de Misiones y Relevos entre NPCs (Handoff)
- **Soporte de Prerrequisitos (`prerequisiteQuestId`):** Permite encadenar misiones (ej. `Misión B` solo es aceptable si `Misión A` ya tiene su `completedTag` entregada). Si un jugador intenta forzar la activación sin cumplir el prerrequisito, el motor revoca el tag y emite advertencia sonora/visual.
- **Nuevas Etapas de Misión:**
  - `TALK`: Completada al interactuar con un NPC objetivo específico (`target: "villager_centinela"` o tag de NPC).
  - `DELIVER`: Entrega de objetos o informes al interactuar con el NPC de destino.

### 2. Detección Robusta de Kills Indirectos y Jefes
- **Resolución Avanzada de Asesino (`resolvePlayerKiller`):**
  - **Proyectiles:** Detección de daño causado por flechas, tridentes y proyectiles mágicos (`Projectile.getOwner()`).
  - **Mascotas e Invocaciones:** Crédito de kill para el dueño de perros, lobos y summons domesticados (`TamableAnimal.getOwner()`).
  - **Magia y Daño de Área:** Daño de hechizos (ej. *Iron's Spells and Spellbooks*) registrado mediante el último jugador atacante registrado en la entidad (`victim.getLastHurtByMob()`).
  - **Jefes Multipart:** Detección del piloto o vehículo si la parte destruida es un sub-componente (`victim.getVehicle()`).

### 3. Soporte de NBT para Gemas de Apotheosis e Ítems Modded
- **Filtrado NBT en Etapas `ITEM`:** Las etapas `ITEM` ahora admiten `nbtKey` (con soporte para claves anidadas separadas por punto como `affix_data.gem`) y `nbtValue` opcional.
- Permite crear misiones de recolección de gemas de Apotheosis (`apotheosis:gem`) distinguiendo gemas específicas o cualquier gema engarzable sin falsos positivos con ítems base vacíos.

### 4. Separación Estricta de Bases de Datos de Misiones
- **Base Vanilla (`Addons/RPG Quest Engine (Base)/.minecraft/config/rpg_quests.json`):** 100% libre de dependencias de mods externos. Compatible con cualquier servidor o cliente vanilla/forge estándar.
- **Addon Cisco Implementation (`Addons/Cisco Implementation/.minecraft/config/rpg_quests.json`):** Misiones y contratos exclusivos para los jefes y dimensiones del modpack:
  - Jefes: *Fell King Boss* (`cisco_mod:fellkingboss`), *Ignis* (`cataclysm:ignis`), *Netherite Monstrosity* (`cataclysm:netherite_monstrosity`), *The Harbinger* (`cataclysm:the_harbinger`), *Night Lich* (`bosses_of_mass_destruction:lich`), *Frostmaw* (`mowziesmobs:frostmaw`).
  - Dimensiones: *The Aether* (`aether:the_aether`), *Twilight Forest* (`twilightforest:twilight_forest`).
  - Gemas de *Apotheosis*.

---

## [1.0.0-update] - 2026-11-12

### 1. Sistema de Misiones y Tracking
- **Desacoplamiento total de nombres de NPC:** El motor ya no exige coincidencia de nombres con `q.npc`. Al interactuar con cualquier entidad y recibir un tag `q_<id>_activa`, el motor registra automáticamente el UUID y la posición física exacta de esa entidad mediante `rqe_last_npc_*`.
- **Brújula persistente y tracking en vivo:**
  - Si el NPC está en chunks cargados: seguimiento en tiempo real de sus coordenadas vía `SyncPlayerQuestsPacket`.
  - Si el NPC está en chunks descargados: la brújula de retorno usa coordenadas fijas cacheadas en `rqe_pos_{questId}_{x}_{z}` sin perderse.
- **Corte automático de spawn:** Al completar la cantidad de muertes requeridas en la etapa de tipo `KILL` o al quedar la misión en estado lista (`doneTag`), el generador de mobs apaga de inmediato el spawneo y las alertas.
- **Control de cupo de mobs en zonas:** El motor evalúa la cantidad de entidades vivas en el área antes de spawnear y solo genera la diferencia hasta `mobSpawnCount`.
- **Carga de configuración unificada:** `QuestConfig.loadConfig()` ahora se ejecuta tanto en cliente como en servidor (`commonSetup`), asegurando que servidores dedicados y partidas LAN carguen las misiones correctamente.

---

### 2. Audio y Efectos
- **Sonidos temáticos por profesión de NPC:** Al hacer clic derecho sobre cualquier NPC de tipo aldeano, se reproduce el sonido correspondiente a su oficio con variación de pitch y cooldown de 1.4s:
  - Clérigo / Alquimista / Ocultista: `VILLAGER_WORK_CLERIC`
  - Armero / Guardia / Centinela: `VILLAGER_WORK_WEAPONSMITH`
  - Herrero / Minero: `VILLAGER_WORK_ARMORER`
  - Panadero / Granjero: `VILLAGER_WORK_FARMER`
  - Pescador: `VILLAGER_WORK_FISHERMAN`
  - Cartógrafo / Explorador / Naturalista: `VILLAGER_WORK_CARTOGRAPHER`
  - Genérico: `VILLAGER_AMBIENT`
- **Sonido de alerta UI por paquete:** Implementado `PlayQuestSoundPacket` para reproducir sonidos de alerta de zona (`RAID_HORN` + sonido de criatura) directo en el cliente sin pérdida por atenuación de distancia en el servidor.

---

### 3. Renombrado de Presets, IDs y Comandos de Importación

Se normalizaron todos los presets bajo la subcarpeta `preset/villager/` adoptando el prefijo explícito de entidad `villager_*`:

| Archivo Anterior | Archivo Nuevo (`.npc.snbt`) | Comando In-Game para Importar | `npcId` en JSON | Tag Activo | Tag Entrega |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `npc_matar` / `cisco_capitan_guardia` | `villager_guardia` / `villager_matar` | `/easy_npc preset import villager_guardia` | `villager_guardia` | `q_capitan_activa` | `q_capitan_lista` |
| `cisco_centinela_veterano` | `villager_centinela` | `/easy_npc preset import villager_centinela` | `villager_centinela` | `q_fell_knights_activa` | `q_fell_knights_lista` |
| `npc_cazador` | `villager_cazador` | `/easy_npc preset import villager_cazador` | `villager_cazador` | `q_cazador_activa` | `q_cazador_lista` |
| `alquimista_elena` *(legacy FTB)* | `villager_alquimista` | `/easy_npc preset import villager_alquimista` | `villager_alquimista` | `q_elena_activa` | `q_elena_lista` |
| `npc_craftear` | `villager_craftear` | `/easy_npc preset import villager_craftear` | `villager_craftear` | `q_armero_activa` | `q_armero_lista` |
| `npc_minar` | `villager_minar` | `/easy_npc preset import villager_minar` | `villager_minar` | `q_minero_activa` | `q_minero_lista` |
| `npc_hornear` | `villager_hornear` | `/easy_npc preset import villager_hornear` | `villager_hornear` | `q_panadero_activa` | `q_panadero_lista` |
| `npc_pescar` | `villager_pescar` | `/easy_npc preset import villager_pescar` | `villager_pescar` | `q_pescador_activa` | `q_pescador_lista` |
| `npc_explorador` | `villager_explorador` | `/easy_npc preset import villager_explorador` | `villager_explorador` | `q_explorador_activa` | `q_explorador_lista` |
| *(Nuevo)* | `villager_bioma` | `/easy_npc preset import villager_bioma` | `villager_bioma` | `q_bioma_activa` | `q_bioma_lista` |
| *(Nuevo)* | `villager_estructura` | `/easy_npc preset import villager_estructura` | `villager_estructura` | `q_estructura_activa` | `q_estructura_lista` |
| *(Nuevo)* | `villager_dimension` | `/easy_npc preset import villager_dimension` | `villager_dimension` | `q_nether_activa` | `q_nether_lista` |

#### Comandos Estándar en los Diálogos de los Presets:
- **Aceptar Misión:**
  `/tag @initiator add q_<id>_activa`
- **Condición de Entrega (Priority 20):**
  `PLAYER_TAG: q_<id>_lista`
- **Cobrar Recompensa:**
  ```text
  /tag @initiator remove q_<id>_activa
  /tag @initiator remove q_<id>_lista
  /tag @initiator add q_<id>_entregada
  ```

---

### 4. Cobertura de los 11 Tipos de Misiones en `rpg_quests.json`

| ID Misión | Categoría | Tipo de Etapa | Objetivo | NPC Asignado |
| :--- | :--- | :--- | :--- | :--- |
| `caza_zombies` | `MAIN` | `KILL` | 5 Zombies | Capitán de la Guardia |
| `contrato_fell_knights` | `CONTRACT` | `KILL` | 3 Wither Skeletons | Centinela Veterano |
| `cazador_pantano` | `CONTRACT` | `SCORE` + Zona + `KILL` | Rastrear cueva (r:25) y matar 3 arañas de cueva | Cazador de Bestias |
| `elena_alquimia` | `SECONDARY` | `ITEM` | 4 Ojos de Araña | Alquimista Elena |
| `craftear_arcos` | `SECONDARY` | `CRAFT` | 2 Arcos | Maestro Armero |
| `minar_hierro` | `SECONDARY` | `MINE` | 8 Mena de Hierro | Herrero Enano |
| `hornear_pan` | `SECONDARY` | `SMELT` | 4 Patatas en Horno | Panadero de la Aldea |
| `pescar_rio` | `REPEATABLE` | `FISH` | 3 Peces | Pescador del Muelle |
| `explorar_torre` | `SECONDARY` | `SCORE` + Coords | Sendero Alto (180, -220, r:30) | Explorador del Sendero |
| `explorar_bioma` | `SECONDARY` | `BIOME` | Bioma Bosque (`minecraft:forest`) | Naturalista de la Arboleda |
| `descubrir_aldea` | `SECONDARY` | `STRUCTURE` | Aldea de Llanura (`village_plains`) | Vigía de la Ciudadela |
| `viaje_nether` | `MAIN` | `DIMENSION` | Entrar al Nether (`the_nether`) | Ocultista del Vacío |

---

### 5. Documentación
- Actualizado `docs/skills/PLANTILLA_MISIONES.md` con la tabla completa de presets, sonidos por profesión y ejemplos JSON de cada tipo de misión listos para copiar y pegar.
- Sincronizadas las skills locales y del repositorio (`SKILL_QUEST_SYSTEM.md`).
