package com.rpgquestengine;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = RPGQuestEngine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QuestEventListener {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player instanceof ServerPlayer sp && !player.isCreative()) {
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
            if (blockId != null) {
                incrementQuestTarget(sp, "MINE", blockId.toString());
            }
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer sp) {
            ItemStack stack = event.getCrafting();
            if (!stack.isEmpty()) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (itemId != null) {
                    incrementQuestTarget(sp, "CRAFT", itemId.toString(), stack.getCount());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer sp) {
            List<ItemStack> drops = event.getDrops();
            for (ItemStack stack : drops) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (itemId != null) {
                    incrementQuestTarget(sp, "FISH", itemId.toString(), stack.getCount());
                }
            }
            incrementQuestTarget(sp, "FISH", "any", 1);
        }
    }

    @SubscribeEvent
    public static void onEntityKilled(LivingDeathEvent event) {
        ServerPlayer sp = resolvePlayerKiller(event.getSource(), event.getEntity());
        if (sp != null) {
            LivingEntity victim = event.getEntity();
            ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
            if (entityId != null) {
                incrementQuestTarget(sp, "KILL", entityId.toString());
            }

            // Soporte para jefes multipart (ej. Ice and Fire, Cataclysm)
            if (victim.getVehicle() instanceof LivingEntity parentLiving) {
                ResourceLocation parentId = ForgeRegistries.ENTITY_TYPES.getKey(parentLiving.getType());
                if (parentId != null && !parentId.equals(entityId)) {
                    incrementQuestTarget(sp, "KILL", parentId.toString());
                }
            }
        }
    }

    private static ServerPlayer resolvePlayerKiller(net.minecraft.world.damagesource.DamageSource source, LivingEntity victim) {
        // 1. Causa directa o indirecta
        if (source.getEntity() instanceof ServerPlayer sp) {
            return sp;
        }
        if (source.getDirectEntity() instanceof ServerPlayer sp) {
            return sp;
        }

        // 2. Proyectil lanzado por jugador (flecha, tridente, magia de mods)
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj) {
            if (proj.getOwner() instanceof ServerPlayer sp) {
                return sp;
            }
        }

        // 3. Mascota o invocación domesticada (lobo, minion de magia)
        if (source.getEntity() instanceof net.minecraft.world.entity.TamableAnimal tamable) {
            if (tamable.getOwner() instanceof ServerPlayer sp) {
                return sp;
            }
        }

        // 4. Último jugador que le hizo daño reciente (dentro de los últimos segundos)
        if (victim.getLastHurtByMob() instanceof ServerPlayer sp) {
            return sp;
        }

        return null;
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer sp && event.getTarget() instanceof LivingEntity living) {
            Set<String> tags = living.getTags();
            if (tags != null && !tags.isEmpty()) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> sp),
                        new SyncEntityTagsPacket(living.getId(), new ArrayList<>(tags))
                );
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof LivingEntity living) {
            if (event.getLevel().isClientSide()) {
                String name = living.getName().getString();
                ClientQuestData.rememberNpcPosition(name, living.getX(), living.getZ());
                if (living.getCustomName() != null) {
                    ClientQuestData.rememberNpcPosition(living.getCustomName().getString(), living.getX(), living.getZ());
                }
                for (String tag : living.getTags()) {
                    ClientQuestData.rememberNpcPosition(tag, living.getX(), living.getZ());
                }
            } else if (event.getEntity() instanceof ServerPlayer sp) {
                // Reproducir sonido temático de aldeano según tipo/profesión (cooldown de 1.4s)
                playNpcInteractSound(sp, living);

                // LADO SERVIDOR: Recordar siempre la última entidad con la que interactuó el jugador
                String entityUuid = living.getStringUUID();
                String entityName = living.getName().getString();
                String customName = living.getCustomName() != null ? living.getCustomName().getString() : "";
                long posX = (long) living.getX();
                long posZ = (long) living.getZ();

                // 1. Guardar referencia temporal del último NPC interactuado
                for (String t : new java.util.ArrayList<>(sp.getTags())) {
                    if (t.startsWith("rqe_last_npc_")) {
                        sp.removeTag(t);
                    }
                }
                sp.addTag("rqe_last_npc_" + entityUuid + "_" + posX + "_" + posZ);

                // 2. Evaluar objetivos de hablar / entregar a este NPC (TALK / DELIVER)
                for (QuestDefinition q : QuestConfig.QUESTS) {
                    if (sp.getTags().contains(q.activeTag) && !sp.getTags().contains(q.completedTag)) {
                        for (int sIdx = 0; sIdx < q.stages.size(); sIdx++) {
                            QuestDefinition.QuestStage stage = q.stages.get(sIdx);
                            String stType = stage.type != null ? stage.type.toUpperCase() : "";
                            if ("TALK".equals(stType) || "DELIVER".equals(stType)) {
                                boolean targetMatch = false;
                                if (stage.target != null && !stage.target.isEmpty()) {
                                    if ("any".equalsIgnoreCase(stage.target)) targetMatch = true;
                                    for (String entTag : living.getTags()) {
                                        if (entTag.equalsIgnoreCase(stage.target)) { targetMatch = true; break; }
                                    }
                                    if (!targetMatch) {
                                        String tNorm = java.text.Normalizer.normalize(stage.target, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
                                        String eNorm = java.text.Normalizer.normalize(entityName, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
                                        String cNorm = java.text.Normalizer.normalize(customName, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
                                        if (eNorm.contains(tNorm) || cNorm.contains(tNorm) || tNorm.contains(eNorm)) targetMatch = true;
                                    }
                                }
                                if (targetMatch) {
                                    String internalKey = RPGQuestEngine.getInternalScoreKey(q.id, sIdx, stage.type);
                                    RPGQuestEngine.setInternalScore(sp, internalKey, 1);
                                }
                            }
                        }
                    }
                }

                // 3. Asociar a cualquier misión activa que el jugador tenga pendiente de entrega o aceptación
                for (QuestDefinition q : QuestConfig.QUESTS) {
                    boolean matches = false;

                    // A) Si la entidad tiene un tag directo que coincide con el ID de misión o npcId
                    for (String entTag : living.getTags()) {
                        if (entTag.equalsIgnoreCase(q.id) || entTag.equalsIgnoreCase(q.npcId) || entTag.equalsIgnoreCase(q.activeTag)) {
                            matches = true;
                            break;
                        }
                    }

                    // B) Si el nombre coincide
                    if (!matches && q.npc != null && !q.npc.isEmpty()) {
                        String qNorm = java.text.Normalizer.normalize(q.npc, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
                        String eNorm = java.text.Normalizer.normalize(entityName, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
                        String cNorm = java.text.Normalizer.normalize(customName, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
                        if (eNorm.contains(qNorm) || qNorm.contains(eNorm) || cNorm.contains(qNorm) || qNorm.contains(cNorm)) {
                            matches = true;
                        }
                    }

                    // C) Si el jugador tiene la misión activa y no tenía ningún NPC asignado aún
                    boolean alreadyHasNpc = false;
                    for (String t : sp.getTags()) {
                        if (t.startsWith("rqe_pos_" + q.id + "_")) {
                            alreadyHasNpc = true;
                            break;
                        }
                    }
                    if (!matches && sp.getTags().contains(q.activeTag) && !alreadyHasNpc) {
                        matches = true;
                    }

                    if (matches) {
                        for (String t : new java.util.ArrayList<>(sp.getTags())) {
                            if (t.startsWith("rqe_npc_" + q.id + "_") || t.startsWith("rqe_pos_" + q.id + "_")) {
                                sp.removeTag(t);
                            }
                        }
                        sp.addTag("rqe_npc_" + q.id + "_" + entityUuid);
                        sp.addTag("rqe_pos_" + q.id + "_" + posX + "_" + posZ);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer sp) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(event.getSmelting().getItem());
            if (itemId != null) {
                incrementQuestTarget(sp, "SMELT", itemId.toString(), event.getSmelting().getCount());
            }
        }
    }

    private static void incrementQuestTarget(ServerPlayer sp, String type, String target) {
        incrementQuestTarget(sp, type, target, 1);
    }

    private static void incrementQuestTarget(ServerPlayer sp, String type, String target, int amount) {
        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (sp.getTags().contains(q.activeTag) && !sp.getTags().contains(q.completedTag)) {
                for (int sIdx = 0; sIdx < q.stages.size(); sIdx++) {
                    QuestDefinition.QuestStage stage = q.stages.get(sIdx);
                    if (type.equalsIgnoreCase(stage.type)) {
                        if ("any".equalsIgnoreCase(stage.target) || target.equalsIgnoreCase(stage.target)) {
                            String internalScoreKey = RPGQuestEngine.getInternalScoreKey(q.id, sIdx, stage.type);
                            var sb = sp.getServer().getScoreboard();
                            var obj = sb.getObjective(internalScoreKey);
                            if (obj == null) {
                                obj = sb.addObjective(internalScoreKey, net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY, net.minecraft.network.chat.Component.literal(internalScoreKey), net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER);
                            }
                            var score = sb.getOrCreatePlayerScore(sp.getScoreboardName(), obj);
                            score.add(amount);
                        }
                    }
                }
            }
        }
    }

    private static final java.util.Map<java.util.UUID, Long> LAST_SOUND_TIME = new java.util.concurrent.ConcurrentHashMap<>();

    private static void playNpcInteractSound(ServerPlayer sp, LivingEntity living) {
        String entityType = ForgeRegistries.ENTITY_TYPES.getKey(living.getType()).toString().toLowerCase();
        boolean isVillagerNpc = entityType.contains("villager") || entityType.contains("npc");
        if (!isVillagerNpc) return;

        long now = System.currentTimeMillis();
        Long last = LAST_SOUND_TIME.get(sp.getUUID());
        if (last != null && (now - last) < 1400) return;
        LAST_SOUND_TIME.put(sp.getUUID(), now);

        String name = living.getName().getString().toLowerCase();
        String custom = living.getCustomName() != null ? living.getCustomName().getString().toLowerCase() : "";
        String combo = name + " " + custom;

        net.minecraft.sounds.SoundEvent sound = net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT;
        float pitch = 0.95F + (new java.util.Random().nextFloat() * 0.15F);

        if (combo.contains("alquimi") || combo.contains("cleric") || combo.contains("elena") || combo.contains("ocultista")) {
            sound = net.minecraft.sounds.SoundEvents.VILLAGER_WORK_CLERIC;
        } else if (combo.contains("armero") || combo.contains("weapon") || combo.contains("guardia") || combo.contains("centinela")) {
            sound = net.minecraft.sounds.SoundEvents.VILLAGER_WORK_WEAPONSMITH;
        } else if (combo.contains("herrero") || combo.contains("minero") || combo.contains("forja")) {
            sound = net.minecraft.sounds.SoundEvents.VILLAGER_WORK_ARMORER;
        } else if (combo.contains("panader") || combo.contains("horn") || combo.contains("farmer")) {
            sound = net.minecraft.sounds.SoundEvents.VILLAGER_WORK_FARMER;
        } else if (combo.contains("pesca")) {
            sound = net.minecraft.sounds.SoundEvents.VILLAGER_WORK_FISHERMAN;
        } else if (combo.contains("explora") || combo.contains("bioma") || combo.contains("cartograph")) {
            sound = net.minecraft.sounds.SoundEvents.VILLAGER_WORK_CARTOGRAPHER;
        }

        sp.serverLevel().playSound(null, living.getX(), living.getY(), living.getZ(), sound, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, pitch);
    }
}

