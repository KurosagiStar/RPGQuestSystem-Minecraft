# Plantilla y Guía Maestra de Misiones (rpg_quests.json)

Esta guía documenta la estructura de misiones, los tipos de objetivos, el sistema de brújula dinámica, zonas de spawneo automático, sonidos temáticos por profesión y los presets de aldeano disponibles.

---

## 1. Estructura Completa de una Misión

```json
{
  "id": "identificador_unico",
  "category": "MAIN",               // MAIN | CONTRACT | SECONDARY | REPEATABLE
  "title": "Título de la Misión",
  "npc": "Nombre Visible del NPC",  // Nombre con el que se interactúa y entrega
  "npcId": "villager_guardia",      // ID del preset (ej. villager_guardia, villager_cazador)
  "description": "Texto descriptivo de la misión en el Diario (J).",
  "suggestedLevel": 5,              // Nivel sugerido visible en el Diario
  "prerequisiteQuestId": "",        // Opcional: ID de misión previa requerida
  "activeTag": "q_mision_activa",   // Tag que Easy NPC da al jugador al aceptar
  "doneTag": "q_mision_lista",      // Tag que el mod asigna al cumplir todas las etapas
  "completedTag": "q_mision_fin",   // Tag que Easy NPC da al cobrar la recompensa
  "rewards": [
    "5x Lingotes de Oro",
    "2 Niveles de Experiencia"
  ],
  "stages": [
    // Lista de etapas u objetivos (ver abajo)
  ]
}
```

---

## 2. Presets Oficiales de Aldeanos (`preset/villager/`)

Para importar en el juego usa `/easy_npc preset import <nombre>`:

| Preset | Nombre Visible | Profesión / Skin | Misión Asociada | Tipo |
| :--- | :--- | :--- | :--- | :--- |
| `villager_guardia` / `villager_matar` | Capitán de la Guardia | Armero / Nieve | `caza_zombies` | `KILL` |
| `villager_centinela` | Centinela Veterano | Armero / Nieve | `contrato_fell_knights` | `KILL` (Elite) |
| `villager_cazador` | Cazador de Bestias | Armero / Pantano | `cazador_pantano` | `SCORE` + Spawn |
| `villager_alquimista` | Alquimista Elena | Clérigo / Pantano | `elena_alquimia` | `ITEM` |
| `villager_craftear` | Maestro Armero | Armero / Sabana | `craftear_arcos` | `CRAFT` |
| `villager_minar` | Herrero Enano | Armero / Montaña | `minar_hierro` | `MINE` |
| `villager_hornear` | Panadero de la Aldea | Granjero / Llanura | `hornear_pan` | `SMELT` |
| `villager_pescar` | Pescador del Muelle | Pescador / Taiga | `pescar_rio` | `FISH` |
| `villager_explorador` | Explorador del Sendero | Cartógrafo / Sabana | `explorar_torre` | `SCORE` + Brújula |
| `villager_bioma` | Naturalista de la Arboleda | Cartógrafo / Jungla | `explorar_bioma` | `BIOME` |
| `villager_estructura` | Vigía de la Ciudadela | Armero / Desierto | `descubrir_aldea` | `STRUCTURE` |
| `villager_dimension` | Ocultista del Vacío | Clérigo / Pantano | `viaje_nether` | `DIMENSION` |

---

## 3. Sonidos Temáticos en la Interacción

Al hacer clic derecho en cualquier NPC de aldeano, el motor reproduce automáticamente un sonido de trabajo acorde a su rol:
- **Clérigos / Alquimistas / Ocultistas**: `VILLAGER_WORK_CLERIC`
- **Armeros / Guardias / Centinelas**: `VILLAGER_WORK_WEAPONSMITH`
- **Herreros / Mineros / Forja**: `VILLAGER_WORK_ARMORER`
- **Panaderos / Granjeros**: `VILLAGER_WORK_FARMER`
- **Pescadores**: `VILLAGER_WORK_FISHERMAN`
- **Exploradores / Naturalistas / Cartógrafos**: `VILLAGER_WORK_CARTOGRAPHER`
- **Otros / Genéricos**: `VILLAGER_AMBIENT`

---

## 4. Guía de Brújulas, Ubicaciones y Spawneo Automático

### Brújula de Regreso al NPC (100% Desacoplada)
No requiere coordenadas manuales:
1. Al interactuar con el NPC para aceptar la misión, el motor vincula automáticamente a **esa entidad física** por su UUID y posición (`rqe_pos_{questId}_{x}_{z}`).
2. **Si el NPC está en chunks cargados:** La brújula lo sigue en vivo aunque se mueva.
3. **Si te alejas a chunks descargados:** La brújula usa las coordenadas guardadas.

---

### Zona de Búsqueda con Spawneo de Mobs
```json
{
  "description": "Rastrea el sector del nido en la cueva",
  "type": "SCORE",
  "target": "dummy_none",
  "amount": 0,
  "hasLocation": true,
  "posX": -250,
  "posZ": 350,
  "radius": 25,
  "spawnMobsOnEntry": true,
  "mobToSpawn": "minecraft:cave_spider",
  "mobSpawnCount": 4
}
```

- **Radios recomendados:** `20-30` para cuevas o interiores; `40-60` para campamentos y exteriores.
- **Control de cupo:** Solo genera las criaturas que falten para alcanzar `mobSpawnCount`.
- **Corte automático:** En cuanto se cumple el objetivo `KILL` de esa criatura, el generador **se apaga por completo**.
- **Aviso:** Sonido nítido en el cliente vía paquete UI (`RAID_HORN` + sonido de criatura).

---

## 5. Todos los Tipos de Etapas Disponibles (`type`)

### 1. Eliminar Criaturas (`KILL`)
```json
{
  "description": "Elimina 5 Zombies",
  "type": "KILL",
  "target": "minecraft:zombie",
  "amount": 5
}
```

### 2. Minar Bloques (`MINE`)
```json
{
  "description": "Pica 8 Minerales de Hierro",
  "type": "MINE",
  "target": "minecraft:iron_ore",
  "amount": 8
}
```

### 3. Craftear en Mesa (`CRAFT`)
```json
{
  "description": "Fabrica 2 Arcos",
  "type": "CRAFT",
  "target": "minecraft:bow",
  "amount": 2
}
```

### 4. Ítems en Inventario (`ITEM`)
```json
{
  "description": "Recolecta 4 Ojos de Araña",
  "type": "ITEM",
  "target": "minecraft:spider_eye",
  "amount": 4
}
```

### 5. Cocinar / Fundir en Horno (`SMELT`)
```json
{
  "description": "Hornea 4 Patatas en el Horno",
  "type": "SMELT",
  "target": "minecraft:baked_potato",
  "amount": 4
}
```

### 6. Pescar con Caña (`FISH`)
```json
{
  "description": "Pesca 3 capturas en el río",
  "type": "FISH",
  "target": "any",
  "amount": 3
}
```

### 7. Descubrir un Bioma (`BIOME`)
```json
{
  "description": "Explora un Bosque (Forest)",
  "type": "BIOME",
  "target": "minecraft:forest",
  "amount": 1
}
```

### 8. Descubrir una Estructura (`STRUCTURE`)
```json
{
  "description": "Entra a una Aldea",
  "type": "STRUCTURE",
  "target": "minecraft:village_plains",
  "amount": 1
}
```

### 9. Viajar a una Dimensión (`DIMENSION`)
```json
{
  "description": "Atraviesa el portal al Nether",
  "type": "DIMENSION",
  "target": "minecraft:the_nether",
  "amount": 1
}
```

### 10. Hablar con otro NPC / Relevo (`TALK`)
```json
{
  "description": "Habla con el Centinela Veterano en la torre",
  "type": "TALK",
  "target": "villager_centinela",
  "amount": 1
}
```

### 11. Entregar Informe u Objetos (`DELIVER`)
```json
{
  "description": "Entrega las órdenes selladas al Capitán",
  "type": "DELIVER",
  "target": "villager_guardia",
  "amount": 1
}
```

### 12. Ítems con NBT Específico (ej. Gemas de Apotheosis)
```json
{
  "description": "Consigue una Gema de Apotheosis",
  "type": "ITEM",
  "target": "apotheosis:gem",
  "amount": 1,
  "nbtKey": "affix_data.gem",
  "nbtValue": ""
}
```

### 13. Objetivo Opcional (`"optional": true`)
```json
{
  "description": "Prepara flechas extra (Opcional)",
  "type": "CRAFT",
  "target": "minecraft:arrow",
  "amount": 8,
  "optional": true
}
```


---

## Nuevas Capacidades del Motor (v1.3.0+)

### 1. Recompensas Reales Automáticas (`rewardXp` y `rewardItems`)
El motor entrega XP e ítems automáticamente al recibir `completedTag`:
```json
"rewardXp": 250,
"rewardItems": [
  { "id": "minecraft:emerald", "count": 8 },
  { "id": "minecraft:golden_apple", "count": 1 }
]
```

### 2. Rutas Ramificadas y Decisiones Excluyentes (`blockedByTags`)
Permite crear misiones con 2 finales posibles (ej. Ruta A bloquea Ruta B):
```json
"blockedByTags": ["q_ruta_b_entregada"]
```
Si el jugador tiene cualquiera de esos tags, la misión no mostrará marcador `!` ni se podrá activar.

### 3. Emboscadas / Oleadas con Alerta en Pantalla (`ambushTitle`)
Dentro de cualquier etapa con `spawnMobsOnEntry: true`:
```json
"spawnMobsOnEntry": true,
"mobToSpawn": "minecraft:pillager",
"mobSpawnCount": 5,
"ambushTitle": "§c⚔ ¡EMBOSCADA DE SAQUEADORES!"
```
Dispara título en pantalla y sonido de alerta al entrar al radio de la zona.

### 4. Supervivencia por Oleadas (`SURVIVE`) y Combate con Arco (`KILL_BOW`)
- `"type": "SURVIVE"` con `"amount": 45` (segundos) y `"spawnMobsOnEntry": true` mantiene oleadas activas (hasta `mobSpawnCount`, reponiendo en bloque al bajar del 50%) y exige limpiar los restantes al acabarse el tiempo.
- `"type": "KILL_BOW"` con `"target": "any"` cuenta kills realizados con cualquier arco vanilla o modded (Cisco).

### 5. Zonas Relativas Portables (`relativeToNpc`)
- `"relativeToNpc": true` en una etapa con `hasLocation: true` calcula la zona relativa a donde se aceptó la misión (máximo 300 metros).
