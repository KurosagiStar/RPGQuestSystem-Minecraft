# Plantilla y Guía Maestra de Misiones (rpg_quests.json)

Esta guía explica en detalle cómo configurar misiones, los tipos de objetivos, el sistema de brújula dinámica, y el nuevo sistema de zonas con spawneo automático de criaturas.

---

## 1. Estructura Completa de una Misión

```json
{
  "id": "identificador_unico",
  "category": "MAIN",               // MAIN | CONTRACT | SECONDARY | REPEATABLE
  "title": "Título de la Misión",
  "npc": "Nombre Visible del NPC",  // Nombre con el que se interactúa y entrega
  "npcId": "id_preset_opcional",    // Opcional: ID del preset de Easy NPC (ej. npc_matar)
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

## 2. Guía de Brújulas, Ubicaciones y Spawneo Automático

### ¿Cómo funciona la Brújula de Regreso al NPC?
No necesitas poner coordenadas del NPC manualmente:
1. Al interactuar con el NPC (clic derecho) para aceptar la misión, el motor guarda en tu jugador el UUID del NPC y su posición exacta (`rqe_pos_{questId}_{x}_{z}`).
2. **Si el NPC está en chunks cargados:** La brújula lo sigue en tiempo real incluso si camina.
3. **Si te alejas a miles de bloques (chunks descargados):** La brújula sigue funcionando usando las coordenadas fijas recordadas.
4. Si quieres forzar coordenadas fijas del NPC desde el JSON:
   ```json
   "hasNpcLocation": true,
   "npcX": 100,
   "npcZ": -250
   ```

---

### ¿Cómo configurar una Zona de Búsqueda con Spawneo de Mobs?

Para misiones donde el jugador debe ir a una cueva, campamento o ruina y eliminar criaturas:

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

#### Parámetros clave de zona y spawneo:
| Campo | Tipo | Explicación |
| :--- | :--- | :--- |
| `hasLocation` | `boolean` | Activa la brújula HUD con flecha dinámica (`▸ 150m`) hacia el punto. |
| `posX`, `posZ` | `int` | Coordenadas centrales del sector. |
| `radius` | `int` | **Radio de activación**. **Recomendado:** `20` a `30` para cuevas o casas; `40` a `60` para campamentos o aldeas. (Evitar valores de 100+ porque activa el evento demasiado lejos). |
| `spawnMobsOnEntry` | `boolean` | Si es `true`, al pisar el radio genera los mobs automáticamente. |
| `mobToSpawn` | `string` | ID de la entidad de Minecraft o mod (ej. `minecraft:cave_spider`, `minecraft:skeleton`). |
| `mobSpawnCount` | `int` | Cantidad **mínima requerida** de mobs en la zona. |

#### Reglas del motor para el spawneo:
- **Control de cupo:** El motor escanea el área; solo genera las criaturas que falten para alcanzar `mobSpawnCount`.
- **Corte automático:** En cuanto el jugador mata la cantidad requerida en la etapa de `KILL` de esa criatura, o la misión está completada, el generador **se apaga por completo** y no vuelve a generar más mobs ni mandar alertas.
- **Sonido:** Dispara un aviso sonoro nítido en el cliente con sonido de cuerno de asalto (`RAID_HORN`) y alerta visual en pantalla.

---

## 3. Patrón Recomendado: Misión de Contrato / Cueva

Combina una etapa de localización con una de eliminación:

```json
{
  "id": "contrato_cueva_aranias",
  "category": "CONTRACT",
  "title": "Contrato: La Bestia de la Cueva",
  "npc": "Cazador de Bestias",
  "npcId": "npc_cazador",
  "description": "Un peligroso arácnido acecha en las profundidades. Limpia la zona.",
  "suggestedLevel": 6,
  "activeTag": "q_cazador_activa",
  "doneTag": "q_cazador_lista",
  "completedTag": "q_cazador_entregada",
  "rewards": [
    "12x Monedas de Campeón",
    "1x Arco Mejorado"
  ],
  "stages": [
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
    },
    {
      "description": "Elimina 3 Arañas de Cueva",
      "type": "KILL",
      "target": "minecraft:cave_spider",
      "amount": 3
    },
    {
      "description": "Prepara 8 Flechas para la caza (Opcional)",
      "type": "CRAFT",
      "target": "minecraft:arrow",
      "amount": 8,
      "optional": true
    }
  ]
}
```

---

## 4. Todos los Tipos de Etapas Disponibles (`type`)

### A. Eliminar Criaturas (`KILL`)
```json
{
  "description": "Elimina 5 Zombies",
  "type": "KILL",
  "target": "minecraft:zombie",
  "amount": 5
}
```

### B. Minar / Romper Bloques (`MINE`)
```json
{
  "description": "Pica 8 Minerales de Hierro",
  "type": "MINE",
  "target": "minecraft:iron_ore",
  "amount": 8
}
```

### C. Craftear Ítems (`CRAFT`)
```json
{
  "description": "Fabrica 1 Escudo reforzado",
  "type": "CRAFT",
  "target": "minecraft:shield",
  "amount": 1
}
```

### D. Tener Ítems en Inventario (`ITEM`)
Verifica posesión física en el inventario del jugador:
```json
{
  "description": "Consigue 10 Plumas de Pollo",
  "type": "ITEM",
  "target": "minecraft:feather",
  "amount": 10
}
```

### E. Cocinar / Fundir en Horno (`SMELT`)
```json
{
  "description": "Funde 5 Lingotes de Cobre",
  "type": "SMELT",
  "target": "minecraft:copper_ingot",
  "amount": 5
}
```

### F. Pescar Ítems (`FISH`)
```json
{
  "description": "Pesca 3 Peces tropicales",
  "type": "FISH",
  "target": "minecraft:tropical_fish",
  "amount": 3
}
```

### G. Descubrir un Bioma (`BIOME`)
```json
{
  "description": "Explora el Pantano",
  "type": "BIOME",
  "target": "minecraft:swamp",
  "amount": 1
}
```

### H. Descubrir una Estructura (`STRUCTURE`)
```json
{
  "description": "Encuentra una Fortaleza del Nether",
  "type": "STRUCTURE",
  "target": "minecraft:fortress",
  "amount": 1
}
```

### I. Viajar a una Dimensión (`DIMENSION`)
```json
{
  "description": "Atraviesa el portal al Nether",
  "type": "DIMENSION",
  "target": "minecraft:the_nether",
  "amount": 1
}
```

### J. Objetivo Opcional (`"optional": true`)
No bloquea la entrega de la misión si no se cumple:
```json
{
  "description": "Recolecta pociones (Opcional)",
  "type": "ITEM",
  "target": "minecraft:potion",
  "amount": 2,
  "optional": true
}
```
