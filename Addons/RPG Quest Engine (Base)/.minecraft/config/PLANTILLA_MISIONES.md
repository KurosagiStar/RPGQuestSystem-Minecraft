# Plantilla y Guía de Misiones (rpg_quests.json)

Esta plantilla muestra la estructura completa y ejemplos listos para copiar y pegar de **todos los tipos de objetivos** soportados por el motor `rpgquestengine`.

---

## 1. Estructura General de una Misión

```json
{
  "id": "identificador_unico",
  "category": "MAIN",               // MAIN | CONTRACT | SECONDARY | REPEATABLE
  "title": "Título de la Misión",
  "npc": "Nombre Visible del NPC",  // Nombre con el que se interactúa y entrega
  "npcId": "id_preset_opcional",    // Opcional: ID del preset de Easy NPC (ej. npc_matar)
  "description": "Texto descriptivo de la misión en el Diario (J).",
  "suggestedLevel": 5,              // Nivel sugerido que se muestra en el Journal
  "prerequisiteQuestId": "",        // Opcional: ID de misión previa requerida
  "activeTag": "q_mision_activa",   // Tag que Easy NPC da al jugador al aceptar
  "doneTag": "q_mision_lista",      // Tag que el mod le da al jugador al cumplir etapas
  "completedTag": "q_mision_fin",   // Tag que Easy NPC da al cobrar la recompensa
  "rewards": [
    "5x Lingotes de Oro",
    "2 Niveles de Experiencia"
  ],
  "stages": [
    // Una o más etapas (ver tipos abajo)
  ]
}
```

---

## 2. Tipos de Etapas Disponibles (`type`)

### A. Eliminar Criaturas (`KILL`)
El motor cuenta automáticamente las criaturas que mates de ese ID:
```json
{
  "description": "Elimina 5 Zombies",
  "type": "KILL",
  "target": "minecraft:zombie",
  "amount": 5
}
```

### B. Minar / Romper Bloques (`MINE`)
Cuenta cada bloque picado que coincida con el ID:
```json
{
  "description": "Pica 8 Minerales de Hierro",
  "type": "MINE",
  "target": "minecraft:iron_ore",
  "amount": 8
}
```

### C. Craftear Ítems (`CRAFT`)
Cuenta ítems creados en la mesa de crafteo o inventario:
```json
{
  "description": "Fabrica 1 Escudo reforzado",
  "type": "CRAFT",
  "target": "minecraft:shield",
  "amount": 1,
  "optional": false
}
```

### D. Tener Ítems en Inventario (`ITEM`)
Verifica la cantidad física de ítems que el jugador lleva encima:
```json
{
  "description": "Consigue 10 Plumas de Pollo",
  "type": "ITEM",
  "target": "minecraft:feather",
  "amount": 10
}
```

### E. Cocinar / Fundir en Horno (`SMELT`)
Cuenta los ítems extraídos del horno tras fundir:
```json
{
  "description": "Funde 5 Lingotes de Cobre",
  "type": "SMELT",
  "target": "minecraft:copper_ingot",
  "amount": 5
}
```

### F. Pescar Ítems (`FISH`)
Cuenta cualquier ítem obtenido mediante caña de pescar:
```json
{
  "description": "Pesca 3 Peces tropicales",
  "type": "FISH",
  "target": "minecraft:tropical_fish",
  "amount": 3
}
```

### G. Explorar una Zona con Brújula y Spawneo Automático (`SCORE` o `KILL` + Coordenadas)
Guía al jugador con la brújula HUD hacia el punto exacto, y genera los mobs si faltan en el área:
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

### H. Descubrir un Bioma (`BIOME`)
Se completa al pisar el bioma especificado:
```json
{
  "description": "Explora el Pantano",
  "type": "BIOME",
  "target": "minecraft:swamp",
  "amount": 1
}
```

### I. Descubrir una Estructura (`STRUCTURE`)
Se completa al entrar a una estructura generada:
```json
{
  "description": "Encuentra una Fortaleza del Nether",
  "type": "STRUCTURE",
  "target": "minecraft:fortress",
  "amount": 1
}
```

### J. Viajar a una Dimensión (`DIMENSION`)
Se completa al pisar esa dimensión:
```json
{
  "description": "Atraviesa el portal al Nether",
  "type": "DIMENSION",
  "target": "minecraft:the_nether",
  "amount": 1
}
```

### K. Objetivo Opcional (`"optional": true`)
No bloquea la entrega de la misión si el jugador decide no hacerlo:
```json
{
  "description": "Prepara pociones de curación (Opcional)",
  "type": "ITEM",
  "target": "minecraft:potion",
  "amount": 2,
  "optional": true
}
```

---

## 3. Plantilla JSON Completa Lista para Rellenar

```json
[
  {
    "id": "ejemplo_contrato_cueva",
    "category": "CONTRACT",
    "title": "La Amenaza del Subterráneo",
    "npc": "Cazador de Bestias",
    "npcId": "npc_cazador",
    "description": "Elimina a las criaturas que habitan las cavernas del este.",
    "suggestedLevel": 4,
    "activeTag": "q_subterraneo_activa",
    "doneTag": "q_subterraneo_lista",
    "completedTag": "q_subterraneo_entregada",
    "rewards": [
      "10x Monedas de Campeón",
      "500 Puntos de Experiencia"
    ],
    "stages": [
      {
        "description": "Llega a la entrada de la cueva",
        "type": "SCORE",
        "target": "dummy_none",
        "amount": 0,
        "hasLocation": true,
        "posX": 120,
        "posZ": -450,
        "radius": 20,
        "spawnMobsOnEntry": true,
        "mobToSpawn": "minecraft:spider",
        "mobSpawnCount": 4
      },
      {
        "description": "Elimina 4 Arañas",
        "type": "KILL",
        "target": "minecraft:spider",
        "amount": 4
      },
      {
        "description": "Recolecta 2 Ojos de Araña (Opcional)",
        "type": "ITEM",
        "target": "minecraft:spider_eye",
        "amount": 2,
        "optional": true
      }
    ]
  }
]
```
