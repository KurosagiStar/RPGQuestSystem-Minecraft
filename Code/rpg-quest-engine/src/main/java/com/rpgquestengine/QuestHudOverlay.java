package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class QuestHudOverlay {

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
            return;
        }

        if (!QuestConfig.hudEnabled) return;

        if ("none".equalsIgnoreCase(QuestConfig.trackedQuestId)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;

        QuestDefinition questToTrack = null;

        if (!QuestConfig.trackedQuestId.isEmpty()) {
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if (q.id.equals(QuestConfig.trackedQuestId) && ClientQuestData.hasTag(q.activeTag) && !ClientQuestData.hasTag(q.completedTag)) {
                    questToTrack = q;
                    break;
                }
            }
        }

        if (questToTrack == null && QuestConfig.trackedQuestId.isEmpty()) {
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if ("MAIN".equalsIgnoreCase(q.category) && ClientQuestData.hasTag(q.activeTag) && !ClientQuestData.hasTag(q.completedTag)) {
                    questToTrack = q;
                    break;
                }
            }
        }

        if (questToTrack == null && QuestConfig.trackedQuestId.isEmpty()) {
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if (ClientQuestData.hasTag(q.activeTag) && !ClientQuestData.hasTag(q.completedTag)) {
                    questToTrack = q;
                    break;
                }
            }
        }

        if (questToTrack == null) return;

        renderTrackedQuest(event.getGuiGraphics(), mc.font, player, questToTrack);
    }

    private void renderTrackedQuest(GuiGraphics graphics, Font font, LocalPlayer player, QuestDefinition q) {
        int x = QuestConfig.hudPosX;
        int y = QuestConfig.hudPosY;
        int maxTextWidth = 194;
        int cardWidth = 210;

        boolean isDone = ClientQuestData.hasTag(q.doneTag);

        // Badge pill estilizado
        String catName = getCategoryName(q.category);
        int badgeTextW = font.width(catName);
        int badgeBoxW = badgeTextW + 8;
        int badgeBoxH = 10;
        int badgeBgColor = getCategoryBadgeBgColor(q.category);
        int badgeBorderColor = getCategoryColorInt(q.category);

        // Limpiar redundancias: Si el título empieza con "Contrato:" o "Misión:", limpiarlo
        String cleanedTitle = cleanTitlePrefixes(q.title);
        // Título estilizado en negrita con color temático
        String styledTitle = getCategoryTitleColorCode(q.category) + "§l" + cleanedTitle;

        List<FormattedCharSequence> titleLines = font.split(Component.literal(styledTitle), maxTextWidth);

        if (isDone) {
            List<FormattedCharSequence> returnLines = font.split(Component.literal("§a✔ Regresa con " + q.npc + " para entregar."), maxTextWidth);

            String npcCompassInfo = "";
            double targetNpcX = 0;
            double targetNpcZ = 0;
            boolean hasValidNpcTarget = false;

            // 0. Prioridad máxima: entidad viva en el cliente con npcId coincidente (precisión exacta frame a frame)
            Minecraft mcInstance = Minecraft.getInstance();
            if (q.npcId != null && !q.npcId.isEmpty() && mcInstance.level != null) {
                for (net.minecraft.world.entity.Entity ent : mcInstance.level.entitiesForRendering()) {
                    if (ent instanceof net.minecraft.world.entity.LivingEntity && ent.isAlive()
                            && ClientQuestData.entityHasTag(ent.getId(), q.npcId)) {
                        targetNpcX = ent.getX();
                        targetNpcZ = ent.getZ();
                        hasValidNpcTarget = true;
                        break;
                    }
                }
            }

            if (!hasValidNpcTarget && q.hasNpcLocation) {
                targetNpcX = q.npcX;
                targetNpcZ = q.npcZ;
                hasValidNpcTarget = true;
            } else if (!hasValidNpcTarget) {
                // 1. Intentar por posición en vivo del servidor vía UUID tracking
                double[] livePos = ClientQuestData.getServerNpcPosition(q.id);
                if (livePos != null) {
                    targetNpcX = livePos[0];
                    targetNpcZ = livePos[1];
                    hasValidNpcTarget = true;
                } else {
                    // 2. Intentar por npcId explícito si está configurado
                    double[] remembered = null;
                    if (q.npcId != null && !q.npcId.isEmpty()) {
                        remembered = ClientQuestData.getKnownNpcPosition(q.npcId);
                    }
                    // 3. Si no, por nombre del NPC
                    if (remembered == null) {
                        remembered = ClientQuestData.getKnownNpcPosition(q.npc);
                    }

                    if (remembered != null) {
                        targetNpcX = remembered[0];
                        targetNpcZ = remembered[1];
                        hasValidNpcTarget = true;
                    }
                }
            }

            if (hasValidNpcTarget) {
                double dx = targetNpcX - player.getX();
                double dz = targetNpcZ - player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= 6.0) {
                    npcCompassInfo = "§a◎ Cerca de " + q.npc;
                } else {
                    String arrow = calculateDirectionArrow(player.getYRot(), dx, dz);
                    npcCompassInfo = "§e" + arrow + " " + q.npc + " (" + (int) dist + "m)";
                }
            }

            int extraH = !npcCompassInfo.isEmpty() ? 12 : 0;
            int totalH = 8 + badgeBoxH + 4 + (titleLines.size() * 11) + 4 + 1 + 6 + (returnLines.size() * 11) + extraH + 6;

            // Fondo translúcido degradado
            graphics.fill(x - 4, y - 4, x + cardWidth + 4, y + totalH, 0xCC101012);
            graphics.fill(x - 5, y - 4, x - 4, y + totalH, 0xFF55FF55);

            int drawY = y;
            drawBadge(graphics, font, catName, x, drawY, badgeBoxW, badgeBoxH, badgeBgColor, badgeBorderColor);
            drawY += badgeBoxH + 4;

            for (FormattedCharSequence line : titleLines) {
                graphics.drawString(font, line, x, drawY, 0xFFFFFF);
                drawY += 11;
            }
            drawY += 3;
            graphics.fill(x, drawY, x + cardWidth - 8, drawY + 1, 0x33FFFFFF);
            drawY += 5;

            for (FormattedCharSequence line : returnLines) {
                graphics.drawString(font, line, x + 2, drawY, 0x55FF55);
                drawY += 11;
            }

            if (!npcCompassInfo.isEmpty()) {
                graphics.drawString(font, npcCompassInfo, x + 4, drawY, 0xFFFF55);
                drawY += 12;
            }
            return;
        }

        class StageItem {
            List<FormattedCharSequence> lines;
            int cur;
            int max;
            boolean showBar;
            String compassInfo = "";
        }

        List<StageItem> stageItems = new ArrayList<>();
        // Altura base: badge + spacing + títulos + separador + spacing
        int calculatedHeight = 8 + badgeBoxH + 4 + (titleLines.size() * 11) + 4 + 1 + 5;

        for (int sIdx = 0; sIdx < q.stages.size(); sIdx++) {
            QuestDefinition.QuestStage stage = q.stages.get(sIdx);
            StageItem item = new StageItem();
            item.cur = getStageCurrentProgress(player, q.id, sIdx, stage);
            item.max = stage.amount;

            String optPrefix = stage.optional ? "§8[Opcional] " : "";
            boolean isStageComplete = (item.max > 0 && item.cur >= item.max);
            String checkIcon = isStageComplete ? "§a✔ " : "§6▸ ";
            String textColor = isStageComplete ? "§8<s>" : "§7";

            String progressText;
            if (stage.hasLocation && item.max == 0) {
                progressText = checkIcon + optPrefix + textColor + stage.description;
            } else {
                String countColor = isStageComplete ? "§8" : "§f";
                progressText = checkIcon + optPrefix + textColor + stage.description + " §r" + countColor + "[" + item.cur + "/" + item.max + "]";
            }

            item.lines = font.split(Component.literal(progressText), maxTextWidth);
            item.showBar = (item.max > 0 && !isStageComplete);

            if (stage.hasLocation && !isStageComplete) {
                double dx = stage.posX - player.getX();
                double dz = stage.posZ - player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist <= stage.radius) {
                    item.compassInfo = "§a◎ Dentro de la zona";
                } else {
                    String arrow = calculateDirectionArrow(player.getYRot(), dx, dz);
                    item.compassInfo = "§e" + arrow + " Hacia la zona (" + (int) dist + "m)";
                }
            }

            calculatedHeight += 4 + (item.lines.size() * 10);
            if (item.showBar) calculatedHeight += 7;
            if (!item.compassInfo.isEmpty()) calculatedHeight += 11;

            stageItems.add(item);
        }

        calculatedHeight += 6;

        // Fondo oscuro RPG moderno
        graphics.fill(x - 4, y - 4, x + cardWidth + 4, y + calculatedHeight, 0xCC101012);
        // Barra lateral con degradado visual
        graphics.fill(x - 5, y - 4, x - 4, y + calculatedHeight, badgeBorderColor);

        int drawY = y;

        // 1. Badge Pill
        drawBadge(graphics, font, catName, x, drawY, badgeBoxW, badgeBoxH, badgeBgColor, badgeBorderColor);
        drawY += badgeBoxH + 4;

        // 2. Título de la Misión (Destacado en negrita y color temático)
        for (FormattedCharSequence line : titleLines) {
            graphics.drawString(font, line, x, drawY, 0xFFFFFF);
            drawY += 11;
        }
        drawY += 3;

        // 3. Línea divisoria elegante entre título y objetivos
        graphics.fill(x, drawY, x + cardWidth - 8, drawY + 1, 0x22FFFFFF);
        graphics.fill(x, drawY, x + 35, drawY + 1, (0x88000000 | (badgeBorderColor & 0x00FFFFFF)));
        drawY += 6;

        // 4. Objetivos
        for (StageItem item : stageItems) {
            for (FormattedCharSequence line : item.lines) {
                graphics.drawString(font, line, x + 2, drawY, 0xFFFFFF);
                drawY += 10;
            }

            if (item.showBar) {
                int barW = cardWidth - 10;
                int barY = drawY + 1;
                // Fondo de la barra con esquinas oscuras
                graphics.fill(x + 2, barY, x + 2 + barW, barY + 3, 0xFF222225);
                int fillW = (int) (barW * ((float) item.cur / (float) item.max));
                if (fillW > 0) {
                    graphics.fill(x + 2, barY, x + 2 + fillW, barY + 3, badgeBorderColor);
                }
                drawY += 7;
            }

            if (!item.compassInfo.isEmpty()) {
                graphics.drawString(font, item.compassInfo, x + 8, drawY, 0xFFFFFF);
                drawY += 11;
            }

            drawY += 2;
        }
    }

    private String cleanTitlePrefixes(String title) {
        if (title == null) return "Misión";
        String lower = title.toLowerCase();
        if (lower.startsWith("contrato:") || lower.startsWith("contrato -")) {
            return title.substring(title.indexOf(':') != -1 ? title.indexOf(':') + 1 : title.indexOf('-') + 1).trim();
        }
        if (lower.startsWith("misión:") || lower.startsWith("mision:")) {
            return title.substring(title.indexOf(':') + 1).trim();
        }
        return title;
    }

    private void drawBadge(GuiGraphics graphics, Font font, String text, int x, int y, int w, int h, int bgColor, int borderColor) {
        graphics.fill(x, y, x + w, y + h, bgColor);
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        graphics.drawString(font, text, x + 4, y + 1, 0xFFFFFF);
    }

    private String calculateDirectionArrow(float playerYaw, double dx, double dz) {
        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double diff = Mth.wrapDegrees(angleToTarget - playerYaw);

        if (diff >= -22.5 && diff < 22.5) return "▲";
        if (diff >= 22.5 && diff < 67.5) return "↗";
        if (diff >= 67.5 && diff < 112.5) return "▶";
        if (diff >= 112.5 && diff < 157.5) return "↘";
        if (diff >= 157.5 || diff < -157.5) return "▼";
        if (diff >= -157.5 && diff < -112.5) return "↙";
        if (diff >= -112.5 && diff < -67.5) return "◀";
        return "↖";
    }

    private int getStageCurrentProgress(LocalPlayer player, String questId, int stageIndex, QuestDefinition.QuestStage stage) {
        String type = stage.type != null ? stage.type.toUpperCase() : "SCORE";
        if ("ITEM".equals(type)) {
            return countItemInInventory(player.getInventory(), stage.target);
        } else if ("SCORE".equals(type)) {
            return ClientQuestData.getScore(stage.target);
        } else if ("TAG".equals(type)) {
            return ClientQuestData.hasTag(stage.target) ? 1 : 0;
        } else {
            String internalKey = RPGQuestEngine.getInternalScoreKey(questId, stageIndex, stage.type);
            return ClientQuestData.getScore(internalKey);
        }
    }

    private int countItemInInventory(Inventory inv, String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item == null) return 0;
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private String getCategoryName(String cat) {
        if (cat == null) return "SECUNDARIA";
        switch (cat.toUpperCase()) {
            case "MAIN": return "PRINCIPAL";
            case "REPEATABLE": return "REPETIBLE";
            case "CONTRACT": return "CONTRATO";
            default: return "SECUNDARIA";
        }
    }

    private String getCategoryTitleColorCode(String cat) {
        if (cat == null) return "§e";
        switch (cat.toUpperCase()) {
            case "MAIN": return "§6";       // Dorado brillante
            case "REPEATABLE": return "§b"; // Cyan brillante
            case "CONTRACT": return "§c";   // Rojo brillante
            default: return "§e";           // Amarillo brillante
        }
    }

    private int getCategoryBadgeBgColor(String cat) {
        if (cat == null) return 0xCC555511;
        switch (cat.toUpperCase()) {
            case "MAIN": return 0xCC553300;
            case "REPEATABLE": return 0xCC003344;
            case "CONTRACT": return 0xCC551111;
            default: return 0xCC555511;
        }
    }

    private int getCategoryColorInt(String cat) {
        if (cat == null) return 0xFFEEEE55;
        switch (cat.toUpperCase()) {
            case "MAIN": return 0xFFFFAA00;
            case "REPEATABLE": return 0xFF55FFFF;
            case "CONTRACT": return 0xFFFF4444;
            default: return 0xFFEEEE55;
        }
    }
}
