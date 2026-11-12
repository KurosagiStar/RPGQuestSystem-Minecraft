package com.rpgquestengine;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Npc;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = RPGQuestEngine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class QuestEntityMarkerRenderer {

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || entity == player) return;

        double distSq = player.distanceToSqr(entity);
        if (distSq > 1024.0) return; // Máximo 32 bloques de visión

        QuestDefinition tracked = getTrackedQuest();
        boolean isDone = tracked != null && ClientQuestData.hasTag(tracked.doneTag);
        String markerText = null;
        int markerColor = 0xFFFF55; // Amarillo por defecto

        if (tracked != null && isDone) {
            // Misión lista para entregar: Signo de exclamación clásico RPG sobre la cabeza
            if (isMatchingNpc(living, tracked)) {
                markerText = "§6§l[ ! ]";
                markerColor = 0xFFAA00;
            }
        } else if (tracked != null) {
            // Misión en progreso: Verificar si es objetivo de cacería (KILL)
            for (int sIdx = 0; sIdx < tracked.stages.size(); sIdx++) {
                QuestDefinition.QuestStage stage = tracked.stages.get(sIdx);
                if ("KILL".equalsIgnoreCase(stage.type)) {
                    int cur = ClientQuestData.getScore(RPGQuestEngine.getInternalScoreKey(tracked.id, sIdx, stage.type));
                    if (cur < stage.amount) {
                        ResourceLocation entityType = ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
                        if (entityType != null && ("any".equalsIgnoreCase(stage.target) || entityType.toString().equalsIgnoreCase(stage.target))) {
                            markerText = "§c⚔ §f[Objetivo] §c⚔";
                            markerColor = 0xFF5555;
                            break;
                        }
                    }
                }
            }

            // También marcar NPC si requiere hablar/interactuar o si es quien dio la misión
            if (markerText == null && isMatchingNpc(living, tracked)) {
                markerText = "§e§l✦";
                markerColor = 0xFFFF55;
            }
        }

        // Si no tiene marcador por misión activa o de combate, verificar si el NPC tiene una misión DISPONIBLE para aceptar
        if (markerText == null) {
            for (QuestDefinition q : QuestConfig.QUESTS) {
                // Disponible = no activa y no entregada
                if (!ClientQuestData.hasTag(q.activeTag) && !ClientQuestData.hasTag(q.completedTag)) {
                    // Si tiene prerrequisito, verificar que la anterior esté completada
                    if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                        QuestDefinition prereq = QuestConfig.getQuestById(q.prerequisiteQuestId);
                        if (prereq != null && !ClientQuestData.hasTag(prereq.completedTag)) {
                            continue; // Aún bloqueada
                        }
                    }
                    if (isMatchingNpc(living, q)) {
                        markerText = "§e§l✦"; // Estrella amarilla brillante sobre la cabeza
                        markerColor = 0xFFFF55;
                        break;
                    }
                }
            }
        }

        if (markerText != null) {
            renderFloatingMarker(event, mc.font, markerText, markerColor);
        }
    }

    private static boolean isMatchingNpc(LivingEntity living, QuestDefinition quest) {
        if (quest == null) return false;

        // 1. Coincidencia por npcId explícito (ej: "npc_matar", "cisco_capitan_guardia")
        if (quest.npcId != null && !quest.npcId.isEmpty()) {
            for (String tag : living.getTags()) {
                if (tag.equalsIgnoreCase(quest.npcId)) {
                    ClientQuestData.rememberNpcPosition(quest.npcId, living.getX(), living.getZ());
                    ClientQuestData.rememberNpcPosition(quest.npc, living.getX(), living.getZ());
                    return true;
                }
            }
        }

        // 2. Coincidencia por nombre normalizado
        String targetNpcName = quest.npc;
        if (targetNpcName == null || targetNpcName.isEmpty()) return false;
        String entityName = normalize(living.getName().getString());
        String targetNorm = normalize(targetNpcName);

        boolean matches = entityName.contains(targetNorm) || targetNorm.contains(entityName);
        if (!matches && living.getCustomName() != null) {
            String customName = normalize(living.getCustomName().getString());
            matches = customName.contains(targetNorm) || targetNorm.contains(customName);
        }

        if (matches) {
            ClientQuestData.rememberNpcPosition(targetNpcName, living.getX(), living.getZ());
            if (quest.npcId != null && !quest.npcId.isEmpty()) {
                ClientQuestData.rememberNpcPosition(quest.npcId, living.getX(), living.getZ());
            }
        }
        return matches;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
    }

    private static QuestDefinition getTrackedQuest() {
        String trackedId = QuestConfig.trackedQuestId;
        if ("none".equals(trackedId)) return null;

        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (q.id.equals(trackedId) && ClientQuestData.hasTag(q.activeTag) && !ClientQuestData.hasTag(q.completedTag)) {
                return q;
            }
        }

        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (ClientQuestData.hasTag(q.activeTag) && !ClientQuestData.hasTag(q.completedTag)) {
                return q;
            }
        }
        return null;
    }

    private static void renderFloatingMarker(RenderNameTagEvent event, Font font, String text, int color) {
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        // Elevar significativamente por encima del nametag y barra de vida regular (+0.95F)
        poseStack.translate(0.0D, event.getEntity().getBbHeight() + 0.95F, 0.0D);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();
        float halfW = font.width(text) / 2.0F;

        // Luz completa 0xF000F0 (brillo / emisión total en oscuridad y sombras)
        int fullBright = 0xF000F0;
        int bg = 0x90000000;

        // Texto visible a través de paredes y obstáculos (See-Through)
        font.drawInBatch(
                text,
                -halfW,
                0,
                color,
                false,
                matrix,
                event.getMultiBufferSource(),
                Font.DisplayMode.SEE_THROUGH,
                bg,
                fullBright
        );

        // Texto normal brillante con color vívido
        font.drawInBatch(
                text,
                -halfW,
                0,
                color,
                false,
                matrix,
                event.getMultiBufferSource(),
                Font.DisplayMode.NORMAL,
                0,
                fullBright
        );

        poseStack.popPose();
    }
}

