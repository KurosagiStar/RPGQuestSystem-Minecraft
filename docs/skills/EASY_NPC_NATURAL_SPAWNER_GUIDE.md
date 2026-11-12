---
name: easy-npc-natural-spawner
description: Natural and procedural spawning module for Easy NPC entities in Minecraft Forge 1.20.1. Handled by companion developer.
---

# Easy NPC Natural Spawner - Especificaciones de Integración

> **Para el Agente / Desarrollador Colaborador:**
> Esta especificación define las reglas para la generación natural y procedural de los NPCs con misiones encadenadas y presets individuales en el mundo.

---

## 1. Identificadores y Nombres de Presets Creados

### A. Cadena de la Muralla (Trilogía Encadenada)
Ubicación de archivos: `config/easy_npc/preset/chain/`

| Eslabón | Archivo Preset (`.snbt`) | Comando In-Game para Importar | Tag Técnico de Entidad | Bioma / Estructura Sugerida |
| :--- | :--- | :--- | :--- | :--- |
| **Paso 1** | `chain_muralla_1_comandante.npc.snbt` | `/easy_npc preset import chain/chain_muralla_1_comandante` | `chain_muralla_1_comandante` | Aldea (`#minecraft:village`), Murallas, Llanuras |
| **Paso 2** | `chain_muralla_2_centinela.npc.snbt` | `/easy_npc preset import chain/chain_muralla_2_centinela` | `chain_muralla_2_centinela` | Puesto Avanzado / Torres de Nieve / Colinas |
| **Paso 3** | `chain_muralla_3_cazador.npc.snbt` | `/easy_npc preset import chain/chain_muralla_3_cazador` | `chain_muralla_3_cazador` | Pantano (`#minecraft:is_swamp`), Entrada de Cavernas |

---

## 2. Lógica de Flujo de la Cadena

```
[Comandante de la Muralla]
      │
      ├─ Misión 1: Eliminar 5 Zombies (q_capitan_activa ➔ q_capitan_lista)
      ├─ Entrega y Cobro: Añade tag q_capitan_entregada
      └─ Da Misión de Relevo (q_relevo_activa): "Habla con el Centinela en la Torre"
            │
            ▼
[Centinela de la Torre]
      │
      ├─ Recibe Orden Sellada (q_relevo_activa ➔ q_relevo_entregada)
      ├─ Da Contrato Riscos: Eliminar 3 Caballeros Caídos (q_fell_knights_activa ➔ q_fell_knights_lista)
      ├─ Entrega y Cobro: Añade tag q_fell_knights_entregada
      └─ Envía al jugador al Campamento Oculto del Pantano
            │
            ▼
[Cazador del Pantano]
      │
      ├─ Desbloquea diálogo gracias a tag q_fell_knights_entregada
      ├─ Da Contrato Final: Rastrear nido y matar 4 Arañas de Cueva (q_cazador_activa ➔ q_cazador_lista)
      └─ Entrega y Cobro: Añade tag q_cazador_entregada + Recompensa legendaria (Arco de Poder)
```

---

## 3. Pautas para el Spawner Natural / Procedural

Al diseñar la lógica del spawner (sea datapack con estructuras, mod Java o función procedural):
1. **Spawn Único por Área:** Los NPCs de cadena no deben acumularse ni duplicarse en el mismo chunk.
2. **Inmunidad:** Configurar las entidades generadas con `Invulnerable: 1b` o `PersistenceRequired: 1b` para evitar que mueran por mobs hostiles antes de interactuar.
3. **Tags de Identificación:** Cada entidad debe poseer en su lista `Tags` el tag técnico correspondiente (`chain_muralla_1_comandante`, `chain_muralla_2_centinela`, etc.) para que el motor `rpgquestengine` pueda rastrearlo con exactitud en la brújula y eventos `TALK`/`DELIVER`.
