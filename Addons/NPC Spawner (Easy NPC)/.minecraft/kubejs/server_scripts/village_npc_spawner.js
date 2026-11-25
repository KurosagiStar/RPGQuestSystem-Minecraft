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
  { id: 'npc_sec_alquimista', name: 'Alquimista', rl: 'easy_npc:preset/villager/npc_sec_alquimista.npc.snbt', weight: 15, blocks: ['minecraft:brewing_stand', 'minecraft:bookshelf', 'minecraft:cauldron'] },
  { id: 'npc_sec_armero', name: 'Armero', rl: 'easy_npc:preset/villager/npc_sec_armero.npc.snbt', weight: 15, blocks: ['minecraft:anvil', 'minecraft:smithing_table', 'minecraft:grindstone', 'minecraft:fletching_table'] },
  { id: 'npc_sec_minero', name: 'Minero', rl: 'easy_npc:preset/villager/npc_sec_minero.npc.snbt', weight: 15, blocks: ['minecraft:blast_furnace', 'minecraft:stonecutter'] },
  { id: 'npc_contract_cazador', name: 'Cazador', rl: 'easy_npc:preset/villager/npc_contract_cazador.npc.snbt', weight: 12, blocks: ['minecraft:campfire', 'minecraft:target', 'minecraft:fletching_table'] },
  { id: 'npc_contract_tabernero', name: 'Tabernero', rl: 'easy_npc:preset/villager/npc_contract_tabernero.npc.snbt', weight: 12, blocks: ['minecraft:barrel', 'minecraft:smoker', 'minecraft:lantern'] },
  { id: 'npc_contract_vigia', name: 'Vigia', rl: 'easy_npc:preset/villager/npc_contract_vigia.npc.snbt', weight: 12, blocks: ['minecraft:bell', 'minecraft:lantern', 'minecraft:stone_brick_wall'] },
  { id: 'npc_rep_panadero', name: 'Panadero', rl: 'easy_npc:preset/villager/npc_rep_panadero.npc.snbt', weight: 18, blocks: ['minecraft:smoker', 'minecraft:hay_block', 'minecraft:composter'] },
  { id: 'npc_rep_pescador', name: 'Pescador', rl: 'easy_npc:preset/villager/npc_rep_pescador.npc.snbt', weight: 18, blocks: ['minecraft:barrel', 'minecraft:water'] },
  { id: 'npc_sec_bibliotecario', name: 'Bibliotecario', rl: 'easy_npc:preset/villager/npc_sec_bibliotecario.npc.snbt', weight: 15, blocks: ['minecraft:lectern', 'minecraft:bookshelf'] },
  { id: 'npc_sec_cartografo', name: 'Cartografo', rl: 'easy_npc:preset/villager/npc_sec_cartografo.npc.snbt', weight: 15, blocks: ['minecraft:cartography_table'] },
  { id: 'npc_contract_mercenario', name: 'Mercenario', rl: 'easy_npc:preset/villager/npc_contract_mercenario.npc.snbt', weight: 12, blocks: ['minecraft:grindstone', 'minecraft:bell', 'minecraft:iron_bars'] },
  { id: 'npc_rep_cazador_nocturno', name: 'Cazador Nocturno', rl: 'easy_npc:preset/villager/npc_rep_cazador_nocturno.npc.snbt', weight: 14, blocks: ['minecraft:campfire', 'minecraft:lantern'] },
  { id: 'npc_sec_herbolario', name: 'Herbolario', rl: 'easy_npc:preset/villager/npc_sec_herbolario.npc.snbt', weight: 14, blocks: ['minecraft:composter', 'minecraft:flower_pot', 'minecraft:azalea_leaves'] },
  { id: 'npc_sec_ingeniero', name: 'Ingeniero', rl: 'easy_npc:preset/villager/npc_sec_ingeniero.npc.snbt', weight: 14, blocks: ['minecraft:observer', 'minecraft:piston', 'minecraft:redstone_lamp'] },
  { id: 'npc_contract_inquisidor', name: 'Inquisidor', rl: 'easy_npc:preset/villager/npc_contract_inquisidor.npc.snbt', weight: 12, blocks: ['minecraft:enchanting_table', 'minecraft:lectern', 'minecraft:soul_lantern'] },
  { id: 'npc_contract_explorador_marino', name: 'Capitan Marino', rl: 'easy_npc:preset/villager/npc_contract_explorador_marino.npc.snbt', weight: 12, blocks: ['minecraft:barrel', 'minecraft:prismarine', 'minecraft:lantern'] },
  { id: 'npc_rep_granjero', name: 'Granjero', rl: 'easy_npc:preset/villager/npc_rep_granjero.npc.snbt', weight: 16, blocks: ['minecraft:hay_block', 'minecraft:composter', 'minecraft:barrel'] },
  { id: 'npc_rep_lenador', name: 'Lenador', rl: 'easy_npc:preset/villager/npc_rep_lenador.npc.snbt', weight: 16, blocks: ['minecraft:stonecutter', 'minecraft:campfire', 'minecraft:barrel'] }
,
  // --- EXTENDED_NPCS_START ---
  { id: 'npc_sec_ext_01', name: 'Alquimista del Valle #1', rl: 'easy_npc:preset/villager/npc_sec_ext_01.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_02', name: 'Armero de la Costa #2', rl: 'easy_npc:preset/villager/npc_sec_ext_02.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_03', name: 'Minero del Norte #3', rl: 'easy_npc:preset/villager/npc_sec_ext_03.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_04', name: 'Bibliotecario del Bosque #4', rl: 'easy_npc:preset/villager/npc_sec_ext_04.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_05', name: 'Cartografo de la Colina #5', rl: 'easy_npc:preset/villager/npc_sec_ext_05.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_06', name: 'Herbolario del Rio #6', rl: 'easy_npc:preset/villager/npc_sec_ext_06.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_07', name: 'Ingeniero de la Frontera #7', rl: 'easy_npc:preset/villager/npc_sec_ext_07.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_08', name: 'Escriba del Paso #8', rl: 'easy_npc:preset/villager/npc_sec_ext_08.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_09', name: 'Carpintero de la Pradera #9', rl: 'easy_npc:preset/villager/npc_sec_ext_09.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_10', name: 'Joyero del Roble #10', rl: 'easy_npc:preset/villager/npc_sec_ext_10.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_11', name: 'Curtidor de la Mina #11', rl: 'easy_npc:preset/villager/npc_sec_ext_11.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_12', name: 'Botanico del Puerto #12', rl: 'easy_npc:preset/villager/npc_sec_ext_12.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_13', name: 'Geologo Errante #13', rl: 'easy_npc:preset/villager/npc_sec_ext_13.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_14', name: 'Relojero Veterano #14', rl: 'easy_npc:preset/villager/npc_sec_ext_14.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_15', name: 'Fundidor Aprendiz #15', rl: 'easy_npc:preset/villager/npc_sec_ext_15.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_16', name: 'Vidriero Maestro #16', rl: 'easy_npc:preset/villager/npc_sec_ext_16.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_17', name: 'Alfarero Solitario #17', rl: 'easy_npc:preset/villager/npc_sec_ext_17.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_18', name: 'Tejedor del Oeste #18', rl: 'easy_npc:preset/villager/npc_sec_ext_18.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_19', name: 'Sastre del Este #19', rl: 'easy_npc:preset/villager/npc_sec_ext_19.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_20', name: 'Trovador del Sur #20', rl: 'easy_npc:preset/villager/npc_sec_ext_20.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_21', name: 'Astronomo del Valle #21', rl: 'easy_npc:preset/villager/npc_sec_ext_21.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_22', name: 'Archivista de la Costa #22', rl: 'easy_npc:preset/villager/npc_sec_ext_22.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_23', name: 'Boticario del Norte #23', rl: 'easy_npc:preset/villager/npc_sec_ext_23.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_24', name: 'Alquimista del Bosque #24', rl: 'easy_npc:preset/villager/npc_sec_ext_24.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_25', name: 'Armero de la Colina #25', rl: 'easy_npc:preset/villager/npc_sec_ext_25.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_26', name: 'Minero del Rio #26', rl: 'easy_npc:preset/villager/npc_sec_ext_26.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_27', name: 'Bibliotecario de la Frontera #27', rl: 'easy_npc:preset/villager/npc_sec_ext_27.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_28', name: 'Cartografo del Paso #28', rl: 'easy_npc:preset/villager/npc_sec_ext_28.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_29', name: 'Herbolario de la Pradera #29', rl: 'easy_npc:preset/villager/npc_sec_ext_29.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_30', name: 'Ingeniero del Roble #30', rl: 'easy_npc:preset/villager/npc_sec_ext_30.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_31', name: 'Escriba de la Mina #31', rl: 'easy_npc:preset/villager/npc_sec_ext_31.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_32', name: 'Carpintero del Puerto #32', rl: 'easy_npc:preset/villager/npc_sec_ext_32.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_33', name: 'Joyero Errante #33', rl: 'easy_npc:preset/villager/npc_sec_ext_33.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_34', name: 'Curtidor Veterano #34', rl: 'easy_npc:preset/villager/npc_sec_ext_34.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_35', name: 'Botanico Aprendiz #35', rl: 'easy_npc:preset/villager/npc_sec_ext_35.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_36', name: 'Geologo Maestro #36', rl: 'easy_npc:preset/villager/npc_sec_ext_36.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_37', name: 'Relojero Solitario #37', rl: 'easy_npc:preset/villager/npc_sec_ext_37.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_38', name: 'Fundidor del Oeste #38', rl: 'easy_npc:preset/villager/npc_sec_ext_38.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_39', name: 'Vidriero del Este #39', rl: 'easy_npc:preset/villager/npc_sec_ext_39.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_40', name: 'Alfarero del Sur #40', rl: 'easy_npc:preset/villager/npc_sec_ext_40.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_41', name: 'Tejedor del Valle #41', rl: 'easy_npc:preset/villager/npc_sec_ext_41.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_42', name: 'Sastre de la Costa #42', rl: 'easy_npc:preset/villager/npc_sec_ext_42.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_43', name: 'Trovador del Norte #43', rl: 'easy_npc:preset/villager/npc_sec_ext_43.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_44', name: 'Astronomo del Bosque #44', rl: 'easy_npc:preset/villager/npc_sec_ext_44.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_45', name: 'Archivista de la Colina #45', rl: 'easy_npc:preset/villager/npc_sec_ext_45.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_46', name: 'Boticario del Rio #46', rl: 'easy_npc:preset/villager/npc_sec_ext_46.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_47', name: 'Alquimista de la Frontera #47', rl: 'easy_npc:preset/villager/npc_sec_ext_47.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_48', name: 'Armero del Paso #48', rl: 'easy_npc:preset/villager/npc_sec_ext_48.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_49', name: 'Minero de la Pradera #49', rl: 'easy_npc:preset/villager/npc_sec_ext_49.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_50', name: 'Bibliotecario del Roble #50', rl: 'easy_npc:preset/villager/npc_sec_ext_50.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_51', name: 'Cartografo de la Mina #51', rl: 'easy_npc:preset/villager/npc_sec_ext_51.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_52', name: 'Herbolario del Puerto #52', rl: 'easy_npc:preset/villager/npc_sec_ext_52.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_53', name: 'Ingeniero Errante #53', rl: 'easy_npc:preset/villager/npc_sec_ext_53.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_54', name: 'Escriba Veterano #54', rl: 'easy_npc:preset/villager/npc_sec_ext_54.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_55', name: 'Carpintero Aprendiz #55', rl: 'easy_npc:preset/villager/npc_sec_ext_55.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_56', name: 'Joyero Maestro #56', rl: 'easy_npc:preset/villager/npc_sec_ext_56.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_57', name: 'Curtidor Solitario #57', rl: 'easy_npc:preset/villager/npc_sec_ext_57.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_58', name: 'Botanico del Oeste #58', rl: 'easy_npc:preset/villager/npc_sec_ext_58.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_59', name: 'Geologo del Este #59', rl: 'easy_npc:preset/villager/npc_sec_ext_59.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_60', name: 'Relojero del Sur #60', rl: 'easy_npc:preset/villager/npc_sec_ext_60.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_61', name: 'Fundidor del Valle #61', rl: 'easy_npc:preset/villager/npc_sec_ext_61.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_62', name: 'Vidriero de la Costa #62', rl: 'easy_npc:preset/villager/npc_sec_ext_62.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_63', name: 'Alfarero del Norte #63', rl: 'easy_npc:preset/villager/npc_sec_ext_63.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_64', name: 'Tejedor del Bosque #64', rl: 'easy_npc:preset/villager/npc_sec_ext_64.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_65', name: 'Sastre de la Colina #65', rl: 'easy_npc:preset/villager/npc_sec_ext_65.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_66', name: 'Trovador del Rio #66', rl: 'easy_npc:preset/villager/npc_sec_ext_66.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_67', name: 'Astronomo de la Frontera #67', rl: 'easy_npc:preset/villager/npc_sec_ext_67.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_68', name: 'Archivista del Paso #68', rl: 'easy_npc:preset/villager/npc_sec_ext_68.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_69', name: 'Boticario de la Pradera #69', rl: 'easy_npc:preset/villager/npc_sec_ext_69.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_70', name: 'Alquimista del Roble #70', rl: 'easy_npc:preset/villager/npc_sec_ext_70.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_71', name: 'Armero de la Mina #71', rl: 'easy_npc:preset/villager/npc_sec_ext_71.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_72', name: 'Minero del Puerto #72', rl: 'easy_npc:preset/villager/npc_sec_ext_72.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_73', name: 'Bibliotecario Errante #73', rl: 'easy_npc:preset/villager/npc_sec_ext_73.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_74', name: 'Cartografo Veterano #74', rl: 'easy_npc:preset/villager/npc_sec_ext_74.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_75', name: 'Herbolario Aprendiz #75', rl: 'easy_npc:preset/villager/npc_sec_ext_75.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_76', name: 'Ingeniero Maestro #76', rl: 'easy_npc:preset/villager/npc_sec_ext_76.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_77', name: 'Escriba Solitario #77', rl: 'easy_npc:preset/villager/npc_sec_ext_77.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_78', name: 'Carpintero del Oeste #78', rl: 'easy_npc:preset/villager/npc_sec_ext_78.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_79', name: 'Joyero del Este #79', rl: 'easy_npc:preset/villager/npc_sec_ext_79.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_80', name: 'Curtidor del Sur #80', rl: 'easy_npc:preset/villager/npc_sec_ext_80.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_81', name: 'Botanico del Valle #81', rl: 'easy_npc:preset/villager/npc_sec_ext_81.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_82', name: 'Geologo de la Costa #82', rl: 'easy_npc:preset/villager/npc_sec_ext_82.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_83', name: 'Relojero del Norte #83', rl: 'easy_npc:preset/villager/npc_sec_ext_83.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_84', name: 'Fundidor del Bosque #84', rl: 'easy_npc:preset/villager/npc_sec_ext_84.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_85', name: 'Vidriero de la Colina #85', rl: 'easy_npc:preset/villager/npc_sec_ext_85.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_86', name: 'Alfarero del Rio #86', rl: 'easy_npc:preset/villager/npc_sec_ext_86.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_87', name: 'Tejedor de la Frontera #87', rl: 'easy_npc:preset/villager/npc_sec_ext_87.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_88', name: 'Sastre del Paso #88', rl: 'easy_npc:preset/villager/npc_sec_ext_88.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_89', name: 'Trovador de la Pradera #89', rl: 'easy_npc:preset/villager/npc_sec_ext_89.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_90', name: 'Astronomo del Roble #90', rl: 'easy_npc:preset/villager/npc_sec_ext_90.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_91', name: 'Archivista de la Mina #91', rl: 'easy_npc:preset/villager/npc_sec_ext_91.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_92', name: 'Boticario del Puerto #92', rl: 'easy_npc:preset/villager/npc_sec_ext_92.npc.snbt', weight: 10 },
  { id: 'npc_sec_ext_93', name: 'Alquimista Errante #93', rl: 'easy_npc:preset/villager/npc_sec_ext_93.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_01', name: 'Cazador del Valle #1', rl: 'easy_npc:preset/villager/npc_contract_ext_01.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_02', name: 'Tabernero de la Costa #2', rl: 'easy_npc:preset/villager/npc_contract_ext_02.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_03', name: 'Vigia del Norte #3', rl: 'easy_npc:preset/villager/npc_contract_ext_03.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_04', name: 'Mercenario del Bosque #4', rl: 'easy_npc:preset/villager/npc_contract_ext_04.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_05', name: 'Inquisidor de la Colina #5', rl: 'easy_npc:preset/villager/npc_contract_ext_05.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_06', name: 'Capitan Marino del Rio #6', rl: 'easy_npc:preset/villager/npc_contract_ext_06.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_07', name: 'Cazarrecompensas de la Frontera #7', rl: 'easy_npc:preset/villager/npc_contract_ext_07.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_08', name: 'Alguacil del Paso #8', rl: 'easy_npc:preset/villager/npc_contract_ext_08.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_09', name: 'Guardabosques de la Pradera #9', rl: 'easy_npc:preset/villager/npc_contract_ext_09.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_10', name: 'Templario del Roble #10', rl: 'easy_npc:preset/villager/npc_contract_ext_10.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_11', name: 'Exorcista de la Mina #11', rl: 'easy_npc:preset/villager/npc_contract_ext_11.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_12', name: 'Ballestero del Puerto #12', rl: 'easy_npc:preset/villager/npc_contract_ext_12.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_13', name: 'Cazador Errante #13', rl: 'easy_npc:preset/villager/npc_contract_ext_13.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_14', name: 'Tabernero Veterano #14', rl: 'easy_npc:preset/villager/npc_contract_ext_14.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_15', name: 'Vigia Aprendiz #15', rl: 'easy_npc:preset/villager/npc_contract_ext_15.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_16', name: 'Mercenario Maestro #16', rl: 'easy_npc:preset/villager/npc_contract_ext_16.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_17', name: 'Inquisidor Solitario #17', rl: 'easy_npc:preset/villager/npc_contract_ext_17.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_18', name: 'Capitan Marino del Oeste #18', rl: 'easy_npc:preset/villager/npc_contract_ext_18.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_19', name: 'Cazarrecompensas del Este #19', rl: 'easy_npc:preset/villager/npc_contract_ext_19.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_20', name: 'Alguacil del Sur #20', rl: 'easy_npc:preset/villager/npc_contract_ext_20.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_21', name: 'Guardabosques del Valle #21', rl: 'easy_npc:preset/villager/npc_contract_ext_21.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_22', name: 'Templario de la Costa #22', rl: 'easy_npc:preset/villager/npc_contract_ext_22.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_23', name: 'Exorcista del Norte #23', rl: 'easy_npc:preset/villager/npc_contract_ext_23.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_24', name: 'Ballestero del Bosque #24', rl: 'easy_npc:preset/villager/npc_contract_ext_24.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_25', name: 'Cazador de la Colina #25', rl: 'easy_npc:preset/villager/npc_contract_ext_25.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_26', name: 'Tabernero del Rio #26', rl: 'easy_npc:preset/villager/npc_contract_ext_26.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_27', name: 'Vigia de la Frontera #27', rl: 'easy_npc:preset/villager/npc_contract_ext_27.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_28', name: 'Mercenario del Paso #28', rl: 'easy_npc:preset/villager/npc_contract_ext_28.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_29', name: 'Inquisidor de la Pradera #29', rl: 'easy_npc:preset/villager/npc_contract_ext_29.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_30', name: 'Capitan Marino del Roble #30', rl: 'easy_npc:preset/villager/npc_contract_ext_30.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_31', name: 'Cazarrecompensas de la Mina #31', rl: 'easy_npc:preset/villager/npc_contract_ext_31.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_32', name: 'Alguacil del Puerto #32', rl: 'easy_npc:preset/villager/npc_contract_ext_32.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_33', name: 'Guardabosques Errante #33', rl: 'easy_npc:preset/villager/npc_contract_ext_33.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_34', name: 'Templario Veterano #34', rl: 'easy_npc:preset/villager/npc_contract_ext_34.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_35', name: 'Exorcista Aprendiz #35', rl: 'easy_npc:preset/villager/npc_contract_ext_35.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_36', name: 'Ballestero Maestro #36', rl: 'easy_npc:preset/villager/npc_contract_ext_36.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_37', name: 'Cazador Solitario #37', rl: 'easy_npc:preset/villager/npc_contract_ext_37.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_38', name: 'Tabernero del Oeste #38', rl: 'easy_npc:preset/villager/npc_contract_ext_38.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_39', name: 'Vigia del Este #39', rl: 'easy_npc:preset/villager/npc_contract_ext_39.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_40', name: 'Mercenario del Sur #40', rl: 'easy_npc:preset/villager/npc_contract_ext_40.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_41', name: 'Inquisidor del Valle #41', rl: 'easy_npc:preset/villager/npc_contract_ext_41.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_42', name: 'Capitan Marino de la Costa #42', rl: 'easy_npc:preset/villager/npc_contract_ext_42.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_43', name: 'Cazarrecompensas del Norte #43', rl: 'easy_npc:preset/villager/npc_contract_ext_43.npc.snbt', weight: 10 },
  { id: 'npc_contract_ext_44', name: 'Alguacil del Bosque #44', rl: 'easy_npc:preset/villager/npc_contract_ext_44.npc.snbt', weight: 10 },
  { id: 'npc_rep_ext_01', name: 'Panadero del Valle #1', rl: 'easy_npc:preset/villager/npc_rep_ext_01.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_02', name: 'Pescador de la Costa #2', rl: 'easy_npc:preset/villager/npc_rep_ext_02.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_03', name: 'Cazador Nocturno del Norte #3', rl: 'easy_npc:preset/villager/npc_rep_ext_03.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_04', name: 'Granjero del Bosque #4', rl: 'easy_npc:preset/villager/npc_rep_ext_04.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_05', name: 'Lenador de la Colina #5', rl: 'easy_npc:preset/villager/npc_rep_ext_05.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_06', name: 'Cocinero del Rio #6', rl: 'easy_npc:preset/villager/npc_rep_ext_06.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_07', name: 'Apicultor de la Frontera #7', rl: 'easy_npc:preset/villager/npc_rep_ext_07.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_08', name: 'Pastor del Paso #8', rl: 'easy_npc:preset/villager/npc_rep_ext_08.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_09', name: 'Recolector de la Pradera #9', rl: 'easy_npc:preset/villager/npc_rep_ext_09.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_10', name: 'Carbonero del Roble #10', rl: 'easy_npc:preset/villager/npc_rep_ext_10.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_11', name: 'Cantero de la Mina #11', rl: 'easy_npc:preset/villager/npc_rep_ext_11.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_12', name: 'Panadero del Puerto #12', rl: 'easy_npc:preset/villager/npc_rep_ext_12.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_13', name: 'Pescador Errante #13', rl: 'easy_npc:preset/villager/npc_rep_ext_13.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_14', name: 'Cazador Nocturno Veterano #14', rl: 'easy_npc:preset/villager/npc_rep_ext_14.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_15', name: 'Granjero Aprendiz #15', rl: 'easy_npc:preset/villager/npc_rep_ext_15.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_16', name: 'Lenador Maestro #16', rl: 'easy_npc:preset/villager/npc_rep_ext_16.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_17', name: 'Cocinero Solitario #17', rl: 'easy_npc:preset/villager/npc_rep_ext_17.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_18', name: 'Apicultor del Oeste #18', rl: 'easy_npc:preset/villager/npc_rep_ext_18.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_19', name: 'Pastor del Este #19', rl: 'easy_npc:preset/villager/npc_rep_ext_19.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_20', name: 'Recolector del Sur #20', rl: 'easy_npc:preset/villager/npc_rep_ext_20.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_21', name: 'Carbonero del Valle #21', rl: 'easy_npc:preset/villager/npc_rep_ext_21.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_22', name: 'Cantero de la Costa #22', rl: 'easy_npc:preset/villager/npc_rep_ext_22.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_23', name: 'Panadero del Norte #23', rl: 'easy_npc:preset/villager/npc_rep_ext_23.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_24', name: 'Pescador del Bosque #24', rl: 'easy_npc:preset/villager/npc_rep_ext_24.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_25', name: 'Cazador Nocturno de la Colina #25', rl: 'easy_npc:preset/villager/npc_rep_ext_25.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_26', name: 'Granjero del Rio #26', rl: 'easy_npc:preset/villager/npc_rep_ext_26.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_27', name: 'Lenador de la Frontera #27', rl: 'easy_npc:preset/villager/npc_rep_ext_27.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_28', name: 'Cocinero del Paso #28', rl: 'easy_npc:preset/villager/npc_rep_ext_28.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_29', name: 'Apicultor de la Pradera #29', rl: 'easy_npc:preset/villager/npc_rep_ext_29.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_30', name: 'Pastor del Roble #30', rl: 'easy_npc:preset/villager/npc_rep_ext_30.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_31', name: 'Recolector de la Mina #31', rl: 'easy_npc:preset/villager/npc_rep_ext_31.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_32', name: 'Carbonero del Puerto #32', rl: 'easy_npc:preset/villager/npc_rep_ext_32.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_33', name: 'Cantero Errante #33', rl: 'easy_npc:preset/villager/npc_rep_ext_33.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_34', name: 'Panadero Veterano #34', rl: 'easy_npc:preset/villager/npc_rep_ext_34.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_35', name: 'Pescador Aprendiz #35', rl: 'easy_npc:preset/villager/npc_rep_ext_35.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_36', name: 'Cazador Nocturno Maestro #36', rl: 'easy_npc:preset/villager/npc_rep_ext_36.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_37', name: 'Granjero Solitario #37', rl: 'easy_npc:preset/villager/npc_rep_ext_37.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_38', name: 'Lenador del Oeste #38', rl: 'easy_npc:preset/villager/npc_rep_ext_38.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_39', name: 'Cocinero del Este #39', rl: 'easy_npc:preset/villager/npc_rep_ext_39.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_40', name: 'Apicultor del Sur #40', rl: 'easy_npc:preset/villager/npc_rep_ext_40.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_41', name: 'Pastor del Valle #41', rl: 'easy_npc:preset/villager/npc_rep_ext_41.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_42', name: 'Recolector de la Costa #42', rl: 'easy_npc:preset/villager/npc_rep_ext_42.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_43', name: 'Carbonero del Norte #43', rl: 'easy_npc:preset/villager/npc_rep_ext_43.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_44', name: 'Cantero del Bosque #44', rl: 'easy_npc:preset/villager/npc_rep_ext_44.npc.snbt', weight: 12 },
  { id: 'npc_rep_ext_45', name: 'Panadero de la Colina #45', rl: 'easy_npc:preset/villager/npc_rep_ext_45.npc.snbt', weight: 12 }
  // --- EXTENDED_NPCS_END ---
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
  var nearbyBlocks = {}
  for (var dx = -5; dx <= 5; dx++) {
    for (var dy = -2; dy <= 3; dy++) {
      for (var dz = -5; dz <= 5; dz++) {
        var bid = level.getBlockState(pos.offset(dx, dy, dz)).block.id
        nearbyBlocks[bid] = true
      }
    }
  }

  var available = []
  var matched = []

  for (var i = 0; i < NPC_SELECTION_TABLE.length; i++) {
    var item = NPC_SELECTION_TABLE[i]
    if (!existing[item.id]) {
      available.push(item)
      if (item.blocks) {
        for (var b = 0; b < item.blocks.length; b++) {
          if (nearbyBlocks[item.blocks[b]]) {
            matched.push(item)
            break
          }
        }
      }
    }
  }

  var pool = matched.length > 0 ? matched : available
  if (pool.length === 0) {
    return null
  }

  var totalWeight = 0
  for (var k = 0; k < pool.length; k++) {
    totalWeight += pool[k].weight
  }

  var randomVal = Math.random() * totalWeight
  var running = 0
  for (var j = 0; j < pool.length; j++) {
    running += pool[j].weight
    if (randomVal <= running) {
      return pool[j]
    }
  }
  return pool[0]
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
  if (villager.tags && villager.tags.contains('no_quest_npc')) return

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
