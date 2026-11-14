package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdminNpcManagerScreen extends Screen {
    private static final String[] CATEGORIES = {"ALL", "MAIN", "SECONDARY", "CONTRACT", "REPEATABLE"};
    private String selectedCategory = "MAIN";
    private String selectedNpcId = null;
    private String selectedQuestId = null;
    private boolean questView = true;
    private int scrollOffset = 0;

    private static class NpcEntry {
        String npcId;
        String npcName;
        String category;
        int chainStep = 1;
        List<QuestDefinition> quests = new ArrayList<>();
    }

    private final List<NpcEntry> visibleNpcs = new ArrayList<>();
    private final List<QuestDefinition> visibleQuests = new ArrayList<>();
    private Button spawnBtn;
    private Button moveBtn;
    private Button viewToggleBtn;

    public AdminNpcManagerScreen() {
        super(Component.literal("Gestor de NPCs y Quests"));
    }

    @Override
    protected void init() {
        super.init();
        int leftX = 20;
        int topY = 16;

        // Category tabs
        int tabX = leftX;
        for (String cat : CATEGORIES) {
            final String c = cat;
            int w = 64;
            this.addRenderableWidget(Button.builder(Component.literal(c), b -> {
                selectedCategory = c;
                scrollOffset = 0;
                rebuildNpcList();
            }).bounds(tabX, topY, w, 18).build());
            tabX += w + 4;
        }

        viewToggleBtn = this.addRenderableWidget(Button.builder(Component.literal(questView ? "Vista: Quests" : "Vista: NPCs"), b -> {
            questView = !questView;
            b.setMessage(Component.literal(questView ? "Vista: Quests" : "Vista: NPCs"));
            scrollOffset = 0;
            rebuildNpcList();
        }).bounds(this.width - 110, topY, 90, 18).build());

        int rightX = this.width / 2 + 10;
        int btnY = this.height - 36;
        spawnBtn = this.addRenderableWidget(Button.builder(Component.literal("Spawnear aquí"), b -> {
            if (selectedNpcId != null && minecraft != null && minecraft.player != null) {
                NetworkHandler.CHANNEL.sendToServer(new AdminNpcActionPacket("SPAWN", selectedNpcId));
                QuestConfig.NPC_LOCATIONS.put(selectedNpcId, new double[]{
                    minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()
                });
            }
        }).bounds(rightX, btnY, 110, 20).build());

        moveBtn = this.addRenderableWidget(Button.builder(Component.literal("Mover a pos. actual"), b -> {
            if (selectedNpcId != null && minecraft != null && minecraft.player != null) {
                NetworkHandler.CHANNEL.sendToServer(new AdminNpcActionPacket("MOVE", selectedNpcId));
                QuestConfig.NPC_LOCATIONS.put(selectedNpcId, new double[]{
                    minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()
                });
            }
        }).bounds(rightX + 116, btnY, 125, 20).build());

        rebuildNpcList();
    }

    private void rebuildNpcList() {
        visibleNpcs.clear();
        visibleQuests.clear();
        Map<String, NpcEntry> map = new LinkedHashMap<>();
        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (q.npcId == null || q.npcId.isEmpty()) continue;
            if (!"ALL".equals(selectedCategory) && !selectedCategory.equalsIgnoreCase(q.category)) continue;

            visibleQuests.add(q);
            NpcEntry entry = map.computeIfAbsent(q.npcId, k -> {
                NpcEntry e = new NpcEntry();
                e.npcId = q.npcId;
                e.npcName = q.npc != null && !q.npc.isEmpty() ? q.npc : q.npcId;
                e.category = q.category != null ? q.category : "SECONDARY";
                return e;
            });
            entry.quests.add(q);
        }
        visibleNpcs.addAll(map.values());
        for (NpcEntry e : visibleNpcs) {
            int minStep = Integer.MAX_VALUE;
            for (QuestDefinition q : e.quests) {
                minStep = Math.min(minStep, getChainDepth(q, 0));
            }
            e.chainStep = minStep == Integer.MAX_VALUE ? 1 : minStep;
        }
        visibleNpcs.sort((a, b) -> {
            int cmp = Integer.compare(a.chainStep, b.chainStep);
            return cmp != 0 ? cmp : a.npcName.compareToIgnoreCase(b.npcName);
        });
        visibleQuests.sort((a, b) -> {
            int cmp = Integer.compare(getChainDepth(a, 0), getChainDepth(b, 0));
            return cmp != 0 ? cmp : (a.title != null ? a.title : a.id).compareToIgnoreCase(b.title != null ? b.title : b.id);
        });
        if (questView && !visibleQuests.isEmpty()) {
            QuestDefinition selQ = visibleQuests.stream().filter(q -> q.id.equals(selectedQuestId)).findFirst().orElse(visibleQuests.get(0));
            selectedQuestId = selQ.id;
            selectedNpcId = selQ.npcId;
        } else if (!visibleNpcs.isEmpty() && (selectedNpcId == null || visibleNpcs.stream().noneMatch(e -> e.npcId.equals(selectedNpcId)))) {
            selectedNpcId = visibleNpcs.get(0).npcId;
        }
        updateButtons();
    }

    private int getChainDepth(QuestDefinition q, int guard) {
        if (q == null || guard > 15 || q.prerequisiteQuestId == null || q.prerequisiteQuestId.isEmpty()) return 1;
        QuestDefinition prereq = QuestConfig.getQuestById(q.prerequisiteQuestId);
        return prereq == null ? 1 : 1 + getChainDepth(prereq, guard + 1);
    }

    private void updateButtons() {
        boolean hasSel = selectedNpcId != null;
        if (spawnBtn != null) spawnBtn.active = hasSel;
        if (moveBtn != null) moveBtn.active = hasSel;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int leftX = 20;
        int listY = 44;
        int listW = this.width / 2 - 30;
        int itemH = 22;
        int maxRows = Math.max(1, (this.height - 60 - listY) / itemH);

        if (mouseX >= leftX && mouseX <= leftX + listW && mouseY >= listY) {
            int idx = (int) ((mouseY - listY) / itemH) + scrollOffset;
            int listSize = questView ? visibleQuests.size() : visibleNpcs.size();
            if (idx >= 0 && idx < listSize && (idx - scrollOffset) < maxRows) {
                if (questView) {
                    QuestDefinition q = visibleQuests.get(idx);
                    selectedQuestId = q.id;
                    selectedNpcId = q.npcId;
                } else {
                    selectedNpcId = visibleNpcs.get(idx).npcId;
                }
                updateButtons();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int itemH = 22;
        int maxRows = Math.max(1, (this.height - 60 - 44) / itemH);
        int listSize = questView ? visibleQuests.size() : visibleNpcs.size();
        int maxScroll = Math.max(0, listSize - maxRows);
        if (delta < 0) scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        else if (delta > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        int leftX = 20;
        int listY = 44;
        int listW = this.width / 2 - 30;
        int itemH = 22;
        int maxRows = Math.max(1, (this.height - 60 - listY) / itemH);

        g.fill(leftX - 2, listY - 2, leftX + listW + 2, this.height - 16, 0x88000000);

        int listSize = questView ? visibleQuests.size() : visibleNpcs.size();
        for (int i = 0; i < maxRows && (i + scrollOffset) < listSize; i++) {
            int y = listY + i * itemH;
            if (questView) {
                QuestDefinition q = visibleQuests.get(i + scrollOffset);
                boolean sel = q.id.equals(selectedQuestId);
                g.fill(leftX, y, leftX + listW, y + itemH - 2, sel ? 0xAA335588 : 0x66222222);
                boolean hasPos = QuestConfig.NPC_LOCATIONS.containsKey(q.npcId);
                String dot = hasPos ? "§a● " : "§7○ ";
                int step = getChainDepth(q, 0);
                g.drawString(font, dot + "§e[#" + step + "] §f" + q.title, leftX + 4, y + 3, 0xFFFFFF);
                g.drawString(font, "§8" + (q.npc != null ? q.npc : q.npcId) + " (" + q.npcId + ")", leftX + 14, y + 12, 0xAAAAAA);
            } else {
                NpcEntry e = visibleNpcs.get(i + scrollOffset);
                boolean sel = e.npcId.equals(selectedNpcId);
                g.fill(leftX, y, leftX + listW, y + itemH - 2, sel ? 0xAA335588 : 0x66222222);

                boolean hasPos = QuestConfig.NPC_LOCATIONS.containsKey(e.npcId);
                String dot = hasPos ? "§a● " : "§7○ ";
                g.drawString(font, dot + "§e[#" + e.chainStep + "] §f" + e.npcName + " §7(" + e.quests.size() + ")", leftX + 4, y + 3, 0xFFFFFF);
                g.drawString(font, "§8" + e.npcId, leftX + 14, y + 12, 0xAAAAAA);
            }
        }

        // Right panel
        int rightX = this.width / 2 + 10;
        int rightW = this.width - rightX - 20;
        g.fill(rightX - 4, listY - 2, rightX + rightW, this.height - 44, 0x88000000);

        if (questView) {
            QuestDefinition selQ = visibleQuests.stream().filter(q -> q.id.equals(selectedQuestId)).findFirst().orElse(null);
            if (selQ != null) {
                int y = listY + 4;
                g.drawString(font, "§6§l" + selQ.title, rightX + 4, y, 0xFFFFFF);
                y += 12;
                g.drawString(font, "§7Quest ID: §f" + selQ.id, rightX + 4, y, 0xFFFFFF);
                y += 12;
                g.drawString(font, "§7NPC: §f" + (selQ.npc != null ? selQ.npc : selQ.npcId) + " §8(" + selQ.npcId + ")", rightX + 4, y, 0xFFFFFF);
                y += 12;

                double[] pos = QuestConfig.NPC_LOCATIONS.get(selQ.npcId);
                String posStr = pos != null && pos.length >= 3
                    ? String.format("§a(%d, %d, %d)", (int) pos[0], (int) pos[1], (int) pos[2])
                    : "§cSin posición guardada";
                g.drawString(font, "§7Posición NPC: " + posStr, rightX + 4, y, 0xFFFFFF);
                y += 12;

                if (selQ.prerequisiteQuestId != null && !selQ.prerequisiteQuestId.isEmpty()) {
                    g.drawString(font, "§7Prerrequisito: §b" + selQ.prerequisiteQuestId, rightX + 4, y, 0xFFFFFF);
                    y += 12;
                }
                y += 4;
                g.drawString(font, "§eObjetivos:", rightX + 4, y, 0xFFFFFF);
                y += 12;
                if (selQ.stages != null) {
                    for (QuestDefinition.QuestStage st : selQ.stages) {
                        if (y > this.height - 58) break;
                        g.drawString(font, "§f- [" + st.type + "] " + st.description, rightX + 6, y, 0xFFFFFF);
                        y += 10;
                    }
                }
            }
        } else {
            NpcEntry selEntry = visibleNpcs.stream().filter(e -> e.npcId.equals(selectedNpcId)).findFirst().orElse(null);
            if (selEntry != null) {
                int y = listY + 4;
                g.drawString(font, "§6§l" + selEntry.npcName, rightX + 4, y, 0xFFFFFF);
                y += 12;
                g.drawString(font, "§7ID: §f" + selEntry.npcId + "  §e[Paso #" + selEntry.chainStep + (selEntry.chainStep == 1 ? " - Inicio" : " - Encadenado") + "]", rightX + 4, y, 0xFFFFFF);
                y += 12;

                double[] pos = QuestConfig.NPC_LOCATIONS.get(selEntry.npcId);
                String posStr = pos != null && pos.length >= 3
                    ? String.format("§a(%d, %d, %d)", (int) pos[0], (int) pos[1], (int) pos[2])
                    : "§cSin posición guardada";
                g.drawString(font, "§7Posición: " + posStr, rightX + 4, y, 0xFFFFFF);
                y += 16;

                g.drawString(font, "§eMisiones asignadas:", rightX + 4, y, 0xFFFFFF);
                y += 12;

                for (QuestDefinition q : selEntry.quests) {
                    if (y > this.height - 58) break;
                    g.drawString(font, "§f- [" + q.category + "] " + q.title, rightX + 6, y, 0xFFFFFF);
                    y += 10;
                    if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                        g.drawString(font, "  §7Prerreq: §b" + q.prerequisiteQuestId, rightX + 6, y, 0xAAAAAA);
                        y += 10;
                    }
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

