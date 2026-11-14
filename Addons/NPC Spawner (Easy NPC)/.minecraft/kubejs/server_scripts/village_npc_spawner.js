// Sistema de Conversion Activa y Natural de Aldeanos a NPCs de Misiones (KubeJS 6 / 1.20.1 Forge)

var GLOBAL_CONVERSION_CHANCE = 0.50 // 50% de probabilidad de conversion
var SCAN_INTERVAL_TICKS = 40        // Escaneo cada 2 segundos (40 ticks)
var VILLAGE_RADIUS = 500            // Radio de unicidad de presets (500 bloques: no se repite ningun preset en 500 bloques)

// Reservas temporales para evitar duplicados cuando varios aldeanos se convierten en el mismo tick
var PENDING_PRESETS = []

function enableNpcCombat(npc) {
  if (!npc) return
  try {
    npc.setInvulnerable(false)
    if (npc.getEntityAttributes) {
      var attrs = npc.getEntityAttributes()
      if (attrs && attrs.getCombatAttributes && attrs.setCombatAttributes) {
        var combat = attrs.getCombatAttributes()
        if (combat) {
          var updated = combat
            .withIsAttackableByPlayers(true)
            .withIsAttackableByMonsters(true)
            .withIsInvulnerable(false)
          attrs.setCombatAttributes(updated)
          if (npc.setEntityAttributes) {
            npc.setEntityAttributes(attrs)
          }
        }
      }
    }
  } catch (err) {
    console.warn('[NPC Spawner] No se pudo actualizar CombatAttributes via API: ' + err)
  }
}

// Tabla de presets configurados con su nombre visible y peso de aparicion
var NPC_SELECTION_TABLE = [
  { id: 'npc_sec_alquimista', name: 'Alquimista', rl: 'easy_npc:preset/villager/npc_sec_alquimista.npc.snbt', weight: 15 },
  { id: 'npc_sec_armero', name: 'Armero', rl: 'easy_npc:preset/villager/npc_sec_armero.npc.snbt', weight: 15 },
  { id: 'npc_sec_minero', name: 'Minero', rl: 'easy_npc:preset/villager/npc_sec_minero.npc.snbt', weight: 15 },
  { id: 'npc_contract_cazador', name: 'Cazador', rl: 'easy_npc:preset/villager/npc_contract_cazador.npc.snbt', weight: 12 },
  { id: 'npc_contract_tabernero', name: 'Tabernero', rl: 'easy_npc:preset/villager/npc_contract_tabernero.npc.snbt', weight: 12 },
  { id: 'npc_contract_vigia', name: 'Vigia', rl: 'easy_npc:preset/villager/npc_contract_vigia.npc.snbt', weight: 12 },
  { id: 'npc_rep_panadero', name: 'Panadero', rl: 'easy_npc:preset/villager/npc_rep_panadero.npc.snbt', weight: 18 },
  { id: 'npc_rep_pescador', name: 'Pescador', rl: 'easy_npc:preset/villager/npc_rep_pescador.npc.snbt', weight: 18 }
]

// Obtiene los identificadores de presets que ya existen en el radio para no repetirlos
function getExistingPresetNamesNearby(level, pos, radius) {
  var existing = {}
  var now = level.getTime()

  // Limpiar reservas expiradas y contar las activas dentro del radio
  var activePending = []
  for (var p = 0; p < PENDING_PRESETS.length; p++) {
    var pend = PENDING_PRESETS[p]
    if (now < pend.expireTime) {
      activePending.push(pend)
      if (Math.abs(pend.x - pos.x) <= radius && Math.abs(pend.z - pos.z) <= radius) {
        existing[pend.id] = true
      }
    }
  }
  PENDING_PRESETS = activePending

  var entitiesAround = level.getEntitiesWithin(AABB.of(
    pos.x - radius, pos.y - 32, pos.z - radius,
    pos.x + radius, pos.y + 32, pos.z + radius
  ))

  for (var i = 0; i < entitiesAround.size(); i++) {
    var e = entitiesAround.get(i)
    if (e && e.type && String(e.type).indexOf('easy_npc') !== -1) {
      // 1. Deteccion por Tags de entidad
      if (e.tags) {
        for (var k = 0; k < NPC_SELECTION_TABLE.length; k++) {
          var idTag = 'cisco_npc_' + NPC_SELECTION_TABLE[k].id
          if (e.tags.contains(idTag)) {
            existing[NPC_SELECTION_TABLE[k].id] = true
          }
        }
      }

      // 2. Deteccion por CustomName / DisplayName
      var dispName = e.customName ? e.customName.string : (e.displayName ? e.displayName.string : '')
      if (dispName) {
        for (var j = 0; j < NPC_SELECTION_TABLE.length; j++) {
          if (dispName.indexOf(NPC_SELECTION_TABLE[j].name) !== -1) {
            existing[NPC_SELECTION_TABLE[j].id] = true
          }
        }
      }
    }
  }
  return existing
}

// Selecciona un preset aleatorio garantizando que no este ya en el diametro de la aldea
function chooseUniquePreset(level, pos, radius) {
  var existing = getExistingPresetNamesNearby(level, pos, radius)
  var available = []
  var totalWeight = 0

  for (var i = 0; i < NPC_SELECTION_TABLE.length; i++) {
    var item = NPC_SELECTION_TABLE[i]
    if (!existing[item.id]) {
      available.push(item)
      totalWeight += item.weight
    }
  }

  // Si todos los presets ya estan presentes en la aldea, no spawneamos mas para no duplicar
  if (available.length === 0) {
    return null
  }

  var randomVal = Math.random() * totalWeight
  var running = 0
  for (var j = 0; j < available.length; j++) {
    running += available[j].weight
    if (randomVal <= running) {
      return available[j]
    }
  }
  return available[0]
}

function findSolidGroundY(level, bx, startY, bz) {
  for (var dy = 0; dy <= 6; dy++) {
    var checkY = startY - dy
    var block = level.getBlock(bx, checkY, bz)
    if (block && !block.id.equals('minecraft:air') && !block.id.equals('minecraft:void_air') && !block.id.equals('minecraft:cave_air')) {
      return checkY + 1
    }
  }
  return startY
}

function tryConvertVillager(villager, level) {
  if (!villager || !villager.isAlive()) return

  // Marcar inmediatamente para que nunca sea evaluado dos veces
  villager.tags.add('cisco_evaluated_villager')

  // Tirada de probabilidad (50%)
  if (Math.random() > GLOBAL_CONVERSION_CHANCE) return

  var pos = villager.blockPosition()
  var server = level.getServer()

  // Control de densidad local: maximo 1 NPC cada 20 bloques inmediatos para no aglomerar
  var tooClose = false
  var entitiesImmediate = level.getEntitiesWithin(AABB.of(
    pos.x - 20, pos.y - 8, pos.z - 20,
    pos.x + 20, pos.y + 8, pos.z + 20
  ))
  for (var i = 0; i < entitiesImmediate.size(); i++) {
    var e = entitiesImmediate.get(i)
    if (e && e.type && String(e.type).indexOf('easy_npc') !== -1) {
      tooClose = true
      break
    }
  }
  if (tooClose) return

  // Seleccionar preset unico en el radio configurado (500 bloques)
  var selected = chooseUniquePreset(level, pos, VILLAGE_RADIUS)
  if (!selected) {
    return
  }

  // Reservar inmediatamente este preset durante 20 ticks para evitar duplicados en el mismo tick
  PENDING_PRESETS.push({
    id: selected.id,
    x: pos.x,
    y: pos.y,
    z: pos.z,
    expireTime: level.getTime() + 20
  })

  // Garantizar posicion en suelo solido para evitar que quede flotando en el aire
  var bx = Math.floor(villager.x)
  var by = Math.floor(villager.y)
  var bz = Math.floor(villager.z)
  var targetY = findSolidGroundY(level, bx, by, bz)

  var posX = villager.x.toFixed(2)
  var posY = Number(targetY).toFixed(2)
  var posZ = villager.z.toFixed(2)

  // 1. Eliminacion limpia del aldeano vanilla para que el cliente reciba el paquete de destruccion
  // Enviar comando kill por UUID garantiza que el paquete de despawn se envie a todos los clientes cercanos
  var villagerUuid = String(villager.uuid || villager.stringUuid || villager.getUuid())
  server.runCommandSilent('kill ' + villagerUuid)
  villager.discard()

  // 2. Programar la importacion del Easy NPC con 2 ticks de retraso
  // Esto previene colisiones fisicas de hitboxes y garantiza que la entidad previa ya no exista en el cliente
  server.scheduleInTicks(2, function() {
    server.runCommandSilent('easy_npc preset import_new custom ' + selected.rl + ' ' + posX + ' ' + posY + ' ' + posZ)
    server.runCommandSilent('tag @e[type=easy_npc:villager,x=' + posX + ',y=' + posY + ',z=' + posZ + ',distance=..3,limit=1] add cisco_npc_' + selected.id)
    
    // Configurar atributos de combate y movilidad de Easy NPC:
    // IsAttackableByPlayers: 1b (los jugadores pueden golpearlo y hacerle dano)
    // IsAttackableByMonsters: 1b (los monstruos pueden atacarlo)
    // IsInvulnerable: 0b (no es invulnerable)
    // NoGravity: 0b (tiene gravedad y movilidad natural de entidad)
    // CanBeHitByProjectile: 1b (flechas y proyectiles pueden impactarlo)
    server.runCommandSilent('execute as @e[type=easy_npc:villager,x=' + posX + ',y=' + posY + ',z=' + posZ + ',distance=..3,limit=1] run data merge entity @s {Invulnerable:0b,NoGravity:0b,EntityAttribute:{IsAttackableByPlayers:1b,IsAttackableByMonsters:1b,IsInvulnerable:0b,NoGravity:0b,CanBeHitByProjectile:1b}}')

    // Aplicar tambien directamente sobre la instancia Java de CombatAttributes
    var nearbyNpcs = level.getEntitiesWithin(AABB.of(posX - 3, posY - 3, posZ - 3, posX + 3, posY + 3, posZ + 3))
    for (var i = 0; i < nearbyNpcs.size(); i++) {
      var n = nearbyNpcs.get(i)
      if (String(n.type) === 'easy_npc:villager') {
        enableNpcCombat(n)
      }
    }
    
    console.log('[NPC Spawner] Aldeano natural convertido con exito en: ' + selected.name + ' (' + selected.id + ') en ' + posX + ', ' + posY + ', ' + posZ)
  })
}

// 1. Escaner Activo de Aldeanos cargados en el mundo (detecta aldeas generadas naturalmente)
LevelEvents.tick(function(event) {
  var level = event.level
  if (level.isClientSide()) return

  if (level.getTime() % SCAN_INTERVAL_TICKS !== 0) return

  var players = level.getPlayers()
  for (var p = 0; p < players.size(); p++) {
    var player = players.get(p)
    if (!player) continue

    var pPos = player.blockPosition()
    // Buscar aldeanos en un radio de 64 bloques alrededor de cada jugador
    var nearbyEntities = level.getEntitiesWithin(AABB.of(
      pPos.x - 64, pPos.y - 32, pPos.z - 64,
      pPos.x + 64, pPos.y + 32, pPos.z + 64
    ))
    for (var i = 0; i < nearbyEntities.size(); i++) {
      var ent = nearbyEntities.get(i)
      if (ent && ent.type === 'minecraft:villager' && ent.tags && !ent.tags.contains('cisco_evaluated_villager')) {
        tryConvertVillager(ent, level)
      }
    }
  }
})

// Mapa completo de presets para deteccion y recarga por comando
var ALL_PRESET_MAP = {
  'nuevo': { name: 'Nuevo', rl: 'easy_npc:preset/villager/nuevo.npc.snbt' },
  'cisco_capitan_guardia': { name: 'Capitan de la Guardia', rl: 'easy_npc:preset/villager/cisco_capitan_guardia.npc.snbt' },
  'capitan_de_la_guardia': { name: 'Capitan de la Guardia', rl: 'easy_npc:preset/villager/capitan_de_la_guardia.npc.snbt' },
  'alquimista_maeve': { name: 'Alquimista Maeve', rl: 'easy_npc:preset/villager/alquimista_maeve.npc.snbt' },
  'kaelen_el_cazador': { name: 'Kaelen el Cazador', rl: 'easy_npc:preset/villager/kaelen_el_cazador.npc.snbt' },
  'maestro_arcano_thorne': { name: 'Maestro Arcano Thorne', rl: 'easy_npc:preset/villager/maestro_arcano_thorne.npc.snbt' },
  'cisco_centinela_veterano': { name: 'Centinela Veterano', rl: 'easy_npc:preset/villager/cisco_centinela_veterano.npc.snbt' },
  'herrero_de_la_fragua': { name: 'Herrero de la Fragua', rl: 'easy_npc:preset/villager/herrero_de_la_fragua.npc.snbt' },
  'npc_cazador': { name: 'Cazador', rl: 'easy_npc:preset/villager/npc_cazador.npc.snbt' },
  'npc_craftear': { name: 'Artesano', rl: 'easy_npc:preset/villager/npc_craftear.npc.snbt' },
  'npc_explorador': { name: 'Explorador', rl: 'easy_npc:preset/villager/npc_explorador.npc.snbt' },
  'npc_hornear': { name: 'Panadero', rl: 'easy_npc:preset/villager/npc_hornear.npc.snbt' },
  'npc_matar': { name: 'Combatiente', rl: 'easy_npc:preset/villager/npc_matar.npc.snbt' },
  'npc_minar': { name: 'Minero', rl: 'easy_npc:preset/villager/npc_minar.npc.snbt' },
  'npc_pescar': { name: 'Pescador', rl: 'easy_npc:preset/villager/npc_pescar.npc.snbt' }
}

// 2. Comando Personalizado para Resetear / Recargar Presets de NPCs en Tiempo Real
ServerEvents.commandRegistry(function(event) {
  var commands = event.commands
  var argTypes = event.arguments

  function executeReset(source, radius) {
    var player = source.entity
    if (!player || !player.isPlayer()) {
      source.sendFailure(Component.literal('§cEste comando debe ser ejecutado por un jugador.'))
      return 0
    }

    var r = Number(radius) || 32
    var pBlock = player.block
    var px = pBlock.x
    var py = pBlock.y
    var pz = pBlock.z
    var level = player.level
    var server = level.getServer()

    var nearby = level.getEntitiesWithin(AABB.of(
      px - r, py - r, pz - r,
      px + r, py + r, pz + r
    ))

    var reloadedCount = 0

    for (var i = 0; i < nearby.size(); i++) {
      var ent = nearby.get(i)
      if (!ent || !ent.isAlive()) continue

      var entType = String(ent.type)
      if (entType.indexOf('easy_npc') === -1) continue

      var matchedPreset = null

      // A. Deteccion por Tags de la entidad
      if (ent.tags) {
        var keys = Object.keys(ALL_PRESET_MAP)
        for (var k = 0; k < keys.length; k++) {
          var pId = keys[k]
          if (ent.tags.contains('cisco_npc_' + pId)) {
            matchedPreset = ALL_PRESET_MAP[pId]
            break
          }
        }
      }

      // B. Deteccion por CustomName / DisplayName
      if (!matchedPreset) {
        var dName = ent.customName ? ent.customName.string : (ent.displayName ? ent.displayName.string : '')
        if (dName) {
          var allKeys = Object.keys(ALL_PRESET_MAP)
          for (var j = 0; j < allKeys.length; j++) {
            var presetObj = ALL_PRESET_MAP[allKeys[j]]
            if (dName.indexOf(presetObj.name) !== -1) {
              matchedPreset = presetObj
              break
            }
          }
        }
      }

      // C. Si encontramos el preset correspondiente, recargarlo en caliente sobre su UUID
      if (matchedPreset) {
        var uuidStr = String(ent.uuid || ent.stringUuid || ent.getUuid())
        var eBlock = ent.block
        var posX = eBlock.x.toFixed(2)
        var posY = eBlock.y.toFixed(2)
        var posZ = eBlock.z.toFixed(2)

        // easy_npc preset import custom <preset> <pos> <uuid>
        server.runCommandSilent('easy_npc preset import custom ' + matchedPreset.rl + ' ' + posX + ' ' + posY + ' ' + posZ + ' ' + uuidStr)
        // Reasegurar flags vanilla y de Easy NPC de combate y movilidad
        server.runCommandSilent('execute as ' + uuidStr + ' run data merge entity @s {Invulnerable:0b,NoGravity:0b,EntityAttribute:{IsAttackableByPlayers:1b,IsAttackableByMonsters:1b,IsInvulnerable:0b,NoGravity:0b,CanBeHitByProjectile:1b}}')
        enableNpcCombat(ent)

        reloadedCount++
      }
    }

    if (reloadedCount > 0) {
      source.sendSuccess(Component.literal('§a[NPC Reset] Se recargaron ' + reloadedCount + ' NPC(s) con sus presets actualizados en un radio de ' + r + ' bloques.'), true)
    } else {
      source.sendSuccess(Component.literal('§e[NPC Reset] No se encontraron NPCs reconocibles de Easy NPC en un radio de ' + r + ' bloques.'), false)
    }

    return reloadedCount
  }

  // Registro: /cisco_npc_reset [radio] y /npc_reset [radio]
  var resetCmd = commands.literal('cisco_npc_reset')
    .requires(function(src) { return src.hasPermission(2) })
    .executes(function(ctx) {
      return executeReset(ctx.source, 32)
    })
    .then(commands.argument('radio', argTypes.INTEGER.create(event))
      .executes(function(ctx) {
        var r = argTypes.INTEGER.getResult(ctx, 'radio')
        return executeReset(ctx.source, r)
      })
    )

  var aliasCmd = commands.literal('npc_reset')
    .requires(function(src) { return src.hasPermission(2) })
    .executes(function(ctx) {
      return executeReset(ctx.source, 32)
    })
    .then(commands.argument('radio', argTypes.INTEGER.create(event))
      .executes(function(ctx) {
        var r = argTypes.INTEGER.getResult(ctx, 'radio')
        return executeReset(ctx.source, r)
      })
    )

  event.register(resetCmd)
  event.register(aliasCmd)
})

// 3. Asegurar que cualquier Easy NPC spawneado o cargado sea vulnerable a jugadores y monstruos
EntityEvents.spawned('easy_npc:villager', function(event) {
  if (event.level.isClientSide()) return
  var ent = event.entity
  enableNpcCombat(ent)
  event.server.scheduleInTicks(10, function() {
    enableNpcCombat(ent)
  })
})

// 4. Bloquear que Carry On (Shift + Click Derecho) levante a los Easy NPC
ItemEvents.entityInteracted(function(event) {
  if (!event.target || String(event.target.type) !== 'easy_npc:villager') return
  if (event.player && event.player.isCrouching()) {
    event.cancel()
  }
})
