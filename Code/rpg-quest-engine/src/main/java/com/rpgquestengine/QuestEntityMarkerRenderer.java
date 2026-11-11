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
        if (tracked == null) return;

        boolean isDone = ClientQuestData.hasTag(tracked.doneTag);
        String markerText = null;
        int markerColor = 0xFFFF55; // Amarillo por defecto

        if (isDone) {
            // Misión lista para entregar: Marcar al NPC de entrega
            if (isMatchingNpc(living, tracked.npc)) {
                markerText = "§6[ ! ] §e" + tracked.npc;
                markerColor = 0xFFAA00;
            }
        } else {
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
            if (markerText == null && isMatchingNpc(living, tracked.npc)) {
                markerText = "§e✦ §6" + tracked.npc;
                markerColor = 0xFFFF55;
            }
        }

        if (markerText != null) {
            renderFloatingMarker(event, mc.font, markerText, markerColor);
        }
    }

    private static boolean isMatchingNpc(LivingEntity living, String targetNpcName) {
        if (targetNpcName == null || targetNpcName.isEmpty()) return false;
        String entityName = living.getName().getString();
        if (entityName.equalsIgnoreCase(targetNpcName) || entityName.contains(targetNpcName)) return true;
        String customName = living.getCustomName() != null ? living.getCustomName().getString() : "";
        return customName.equalsIgnoreCase(targetNpcName) || customName.contains(targetNpcName);
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

        // Elevar por encima del nametag regular
        poseStack.translate(0.0D, event.getEntity().getBbHeight() + 0.65F, 0.0D);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();
        float halfW = font.width(text) / 2.0F;

        // Fondo oscuro para legibilidad óptima
        Minecraft.getInstance().renderBuffers().bufferSource();
        int bg = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;

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
                event.getPackedLight()
        );

        font.drawInBatch(
                text,
                -halfW,
                0,
                0xFFFFFF,
                false,
                matrix,
                event.getMultiBufferSource(),
                Font.DisplayMode.NORMAL,
                0,
                event.getPackedLight()
        );

        poseStack.popPose();
    }
}

