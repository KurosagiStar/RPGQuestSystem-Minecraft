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
        String markerIcon = null;
        int markerColor = 0xFFFF55; // Amarillo por defecto

        if (tracked != null && isDone) {
            // Misión lista para cobrar: Icono de interrogación dorado RPG con estilo limpio
            if (isMatchingNpc(living, tracked)) {
                markerText = "§e§lCompletada";
                markerIcon = "quest_completed.png";
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
                            markerText = "§fObjetivo";
                            markerIcon = "quest_objective.png";
                            markerColor = 0xFF5555;
                            break;
                        }
                    }
                }
            }

            // 1. Verificar si este NPC es el destino de una etapa TALK o DELIVER
            if (markerText == null) {
                for (QuestDefinition.QuestStage stage : tracked.stages) {
                    if ("TALK".equalsIgnoreCase(stage.type) || "DELIVER".equalsIgnoreCase(stage.type)) {
                        if (stage.target != null && !stage.target.isEmpty()) {
                            if (ClientQuestData.entityHasTag(living.getId(), stage.target)) {
                                markerText = "§b§l✦ §fHablar";
                                markerColor = 0x55FFFF;
                                break;
                            }
                            for (String tag : living.getTags()) {
                                if (tag.equalsIgnoreCase(stage.target)) {
                                    markerText = "§fHablar";
                                    markerIcon = "quest_in_progress.png";
                                    markerColor = 0x55FFFF;
                                    break;
                                }
                            }
                            if (markerText == null) {
                                String entityName = normalize(living.getName().getString());
                                String customName = living.getCustomName() != null ? normalize(living.getCustomName().getString()) : "";
                                String targetNorm = normalize(stage.target);
                                if (entityName.contains(targetNorm) || targetNorm.contains(entityName) || customName.contains(targetNorm) || targetNorm.contains(customName)) {
                                    markerText = "§fHablar";
                                    markerIcon = "quest_in_progress.png";
                                    markerColor = 0x55FFFF;
                                }
                            }
                        }
                    }
                }
            }

            // 2. NPC emisor de la misión activa
            if (markerText == null && isMatchingNpc(living, tracked)) {
                markerText = "§fEn progreso";
                markerIcon = "quest_in_progress.png";
                markerColor = 0x55FFFF;
            }
        }

        // Si no tiene marcador activo, verificar si el NPC tiene una misión DISPONIBLE para aceptar
        if (markerText == null) {
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if (!ClientQuestData.hasTag(q.activeTag) && !ClientQuestData.hasTag(q.completedTag)) {
                    if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                        QuestDefinition prereq = QuestConfig.getQuestById(q.prerequisiteQuestId);
                        if (prereq != null && !ClientQuestData.hasTag(prereq.completedTag)) {
                            continue;
                        }
                    }
                    if (isMatchingNpc(living, q)) {
                        String cat = q.category != null ? q.category.toUpperCase() : "MAIN";
                        if ("CONTRACT".equals(cat)) {
                            markerText = "§c§lContrato";
                            markerIcon = "quest_available_contract.png";
                            markerColor = 0xFF5555;
                        } else if ("SECONDARY".equals(cat) || "SIDE".equals(cat)) {
                            markerText = "§9§lSecundaria";
                            markerIcon = "quest_available_secondary.png";
                            markerColor = 0x55AAFF;
                        } else if ("REPEATABLE".equals(cat)) {
                            markerText = "§a§lRepetible";
                            markerIcon = "quest_available_repeatable.png";
                            markerColor = 0x55FF55;
                        } else {
                            markerText = "§6§lMisión";
                            markerIcon = "quest_available.png";
                            markerColor = 0xFFFF55;
                        }
                        break;
                    }
                }
            }
        }

        if (markerText != null) {
            renderFloatingMarker(event, mc.font, markerText, markerIcon, markerColor);
        }
    }

    private static boolean isMatchingNpc(LivingEntity living, QuestDefinition quest) {
        if (quest == null) return false;

        // 1. Si la misión define un npcId (tag de entidad interno), comprobación ESTRICTA y PRIORITARIA
        if (quest.npcId != null && !quest.npcId.isEmpty()) {
            boolean hasId = ClientQuestData.entityHasTag(living.getId(), quest.npcId);
            if (!hasId) {
                for (String tag : living.getTags()) {
                    if (tag.equalsIgnoreCase(quest.npcId)) {
                        hasId = true;
                        break;
                    }
                }
            }
            if (hasId) {
                ClientQuestData.rememberNpcPosition(quest.npcId, living.getX(), living.getZ());
                ClientQuestData.rememberNpcPosition(quest.npc, living.getX(), living.getZ());
                return true;
            }
            // Si la misión requiere un npcId interno y la entidad no lo tiene, NO debe coincidir nunca por nombre
            return false;
        }

        // 2. Coincidencia por nombre normalizado (únicamente si la misión NO define npcId)
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

    private static void renderFloatingMarker(RenderNameTagEvent event, Font font, String text, String iconFile, int color) {
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        // Elevar significativamente por encima del nametag, nivel y barra de vida (+1.35F)
        poseStack.translate(0.0D, event.getEntity().getBbHeight() + 1.35F, 0.0D);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();
        float halfW = font.width(text) / 2.0F;

        // Luz completa 0xF000F0 (brillo / emisión total en oscuridad y sombras)
        int fullBright = 0xF000F0;
        int bg = 0x90000000;

        // Dibujar icono moderno centrado arriba del texto
        if (iconFile != null) {
            ResourceLocation iconRl = new ResourceLocation(RPGQuestEngine.MODID, "textures/gui/icons/" + iconFile);
            com.mojang.blaze3d.vertex.VertexConsumer vc = event.getMultiBufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(iconRl));
            float size = 14.0F;
            float x0 = -size / 2.0F;
            float x1 = size / 2.0F;
            float y0 = -size - 2.0F;
            float y1 = -2.0F;
            vc.vertex(matrix, x0, y0, 0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(fullBright).normal(0, 0, 1).endVertex();
            vc.vertex(matrix, x0, y1, 0).color(255, 255, 255, 255).uv(0, 1).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(fullBright).normal(0, 0, 1).endVertex();
            vc.vertex(matrix, x1, y1, 0).color(255, 255, 255, 255).uv(1, 1).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(fullBright).normal(0, 0, 1).endVertex();
            vc.vertex(matrix, x1, y0, 0).color(255, 255, 255, 255).uv(1, 0).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(fullBright).normal(0, 0, 1).endVertex();
        }

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

