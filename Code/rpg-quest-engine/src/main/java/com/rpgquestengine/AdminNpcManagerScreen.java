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

    private static class QuestChain {
        String rootId;
        String title;
        List<QuestDefinition> quests = new ArrayList<>();
        List<String> npcIds = new ArrayList<>();
    }

    private final List<NpcEntry> visibleNpcs = new ArrayList<>();
    private final List<QuestChain> visibleChains = new ArrayList<>();
    private String selectedChainId = null;
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

        viewToggleBtn = this.addRenderableWidget(Button.builder(Component.literal(questView ? "Vista: Cadenas" : "Vista: NPCs"), b -> {
            questView = !questView;
            b.setMessage(Component.literal(questView ? "Vista: Cadenas" : "Vista: NPCs"));
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
                updateButtons();
            }
        }).bounds(rightX, btnY, 110, 20).build());

        moveBtn = this.addRenderableWidget(Button.builder(Component.literal("Mover a pos. actual"), b -> {
            if (selectedNpcId != null && minecraft != null && minecraft.player != null) {
                NetworkHandler.CHANNEL.sendToServer(new AdminNpcActionPacket("MOVE", selectedNpcId));
                QuestConfig.NPC_LOCATIONS.put(selectedNpcId, new double[]{
                    minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()
                });
                updateButtons();
            }
        }).bounds(rightX + 116, btnY, 125, 20).build());

        rebuildNpcList();
    }

    private void rebuildNpcList() {
        visibleNpcs.clear();
        visibleChains.clear();
        Map<String, NpcEntry> map = new LinkedHashMap<>();
        List<QuestDefinition> catQuests = new ArrayList<>();
        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (q.npcId == null || q.npcId.isEmpty()) continue;
            if (!"ALL".equals(selectedCategory) && !selectedCategory.equalsIgnoreCase(q.category)) continue;

            catQuests.add(q);
            NpcEntry entry = map.computeIfAbsent(q.npcId, k -> {
                NpcEntry e = new NpcEntry();
                e.npcId = q.npcId;
                e.npcName = q.npc != null && !q.npc.isEmpty() ? q.npc : q.npcId;
                e.category = q.category != null ? q.category : "SECONDARY";
                return e;
            });
            entry.quests.add(q);
        }

        // Build quest chains starting from root quests
        for (QuestDefinition root : catQuests) {
            boolean isRoot = root.prerequisiteQuestId == null || root.prerequisiteQuestId.isEmpty()
                || catQuests.stream().noneMatch(p -> p.id.equals(root.prerequisiteQuestId));
            if (!isRoot) continue;

            QuestChain chain = new QuestChain();
            chain.rootId = root.id;
            if (root.id.startsWith("cisco_trials_")) chain.title = "The Champion's Trials";
            else if (root.id.startsWith("cisco_traveller_")) chain.title = "Traveller's Journey";
            else if (root.id.startsWith("cisco_dragons_")) chain.title = "Age of Dragons";
            else chain.title = root.title != null ? root.title : root.id;
            collectChain(root, catQuests, chain, 0);
            visibleChains.add(chain);
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

        if (questView && !visibleChains.isEmpty()) {
            QuestChain selC = visibleChains.stream().filter(c -> c.rootId.equals(selectedChainId)).findFirst().orElse(visibleChains.get(0));
            selectedChainId = selC.rootId;
        } else if (!visibleNpcs.isEmpty() && (selectedNpcId == null || visibleNpcs.stream().noneMatch(e -> e.npcId.equals(selectedNpcId)))) {
            selectedNpcId = visibleNpcs.get(0).npcId;
        }
        updateButtons();
    }

    private void collectChain(QuestDefinition current, List<QuestDefinition> pool, QuestChain chain, int guard) {
        if (guard > 15 || chain.quests.contains(current)) return;
        chain.quests.add(current);
        if (current.npcId != null && !current.npcId.isEmpty() && !chain.npcIds.contains(current.npcId)) {
            chain.npcIds.add(current.npcId);
        }
        for (QuestDefinition next : pool) {
            if (current.id.equals(next.prerequisiteQuestId)) {
                collectChain(next, pool, chain, guard + 1);
            }
        }
    }

    private int getChainDepth(QuestDefinition q, int guard) {
        if (q == null || guard > 15 || q.prerequisiteQuestId == null || q.prerequisiteQuestId.isEmpty()) return 1;
        QuestDefinition prereq = QuestConfig.getQuestById(q.prerequisiteQuestId);
        return prereq == null ? 1 : 1 + getChainDepth(prereq, guard + 1);
    }

    private void updateButtons() {
        if (questView) {
            QuestChain selC = visibleChains.stream().filter(c -> c.rootId.equals(selectedChainId)).findFirst().orElse(null);
            if (selC != null && !selC.npcIds.isEmpty()) {
                int targetIdx = 0;
                for (int i = 0; i < selC.npcIds.size(); i++) {
                    if (!QuestConfig.NPC_LOCATIONS.containsKey(selC.npcIds.get(i))) {
                        targetIdx = i;
                        break;
                    }
                }
                selectedNpcId = selC.npcIds.get(targetIdx);
                int num = targetIdx + 1;
                if (spawnBtn != null) {
                    spawnBtn.active = true;
                    spawnBtn.setMessage(Component.literal("Spawnear #" + num + " NPC"));
                }
                if (moveBtn != null) {
                    moveBtn.active = true;
                    moveBtn.setMessage(Component.literal("Mover #" + num + " NPC"));
                }
                return;
            }
        }
        boolean hasSel = selectedNpcId != null;
        if (spawnBtn != null) {
            spawnBtn.active = hasSel;
            spawnBtn.setMessage(Component.literal("Spawnear aquí"));
        }
        if (moveBtn != null) {
            moveBtn.active = hasSel;
            moveBtn.setMessage(Component.literal("Mover a pos. actual"));
        }
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
            int listSize = questView ? visibleChains.size() : visibleNpcs.size();
            if (idx >= 0 && idx < listSize && (idx - scrollOffset) < maxRows) {
                if (questView) {
                    selectedChainId = visibleChains.get(idx).rootId;
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
        int listSize = questView ? visibleChains.size() : visibleNpcs.size();
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

        int listSize = questView ? visibleChains.size() : visibleNpcs.size();
        for (int i = 0; i < maxRows && (i + scrollOffset) < listSize; i++) {
            int y = listY + i * itemH;
            if (questView) {
                QuestChain c = visibleChains.get(i + scrollOffset);
                boolean sel = c.rootId.equals(selectedChainId);
                g.fill(leftX, y, leftX + listW, y + itemH - 2, sel ? 0xAA335588 : 0x66222222);
                boolean allSpawned = c.npcIds.stream().allMatch(QuestConfig.NPC_LOCATIONS::containsKey);
                String dot = allSpawned ? "§a● " : "§7○ ";
                g.drawString(font, dot + "§f" + c.title, leftX + 4, y + 3, 0xFFFFFF);
                g.drawString(font, "§8" + c.quests.size() + " misiones | " + c.npcIds.size() + " NPCs", leftX + 14, y + 12, 0xAAAAAA);
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
            QuestChain selC = visibleChains.stream().filter(c -> c.rootId.equals(selectedChainId)).findFirst().orElse(null);
            if (selC != null) {
                int y = listY + 4;
                g.drawString(font, "§6§l" + selC.title, rightX + 4, y, 0xFFFFFF);
                y += 12;
                g.drawString(font, "§7NPCs relacionados: §e" + selC.npcIds.size() + "  §7Misiones: §f" + selC.quests.size(), rightX + 4, y, 0xFFFFFF);
                y += 16;

                g.drawString(font, "§eSub-misiones en orden:", rightX + 4, y, 0xFFFFFF);
                y += 12;
                for (int i = 0; i < selC.quests.size(); i++) {
                    if (y > this.height - 58) break;
                    QuestDefinition q = selC.quests.get(i);
                    double[] pos = QuestConfig.NPC_LOCATIONS.get(q.npcId);
                    String posStr = pos != null && pos.length >= 3
                        ? String.format("§a(%d,%d,%d)", (int) pos[0], (int) pos[1], (int) pos[2])
                        : "§cSin pos";
                    g.drawString(font, "§e#" + (i + 1) + " §f" + q.title, rightX + 6, y, 0xFFFFFF);
                    y += 10;
                    if (y > this.height - 58) break;
                    g.drawString(font, "  §7NPC: §b" + (q.npc != null ? q.npc : q.npcId) + " " + posStr, rightX + 6, y, 0xAAAAAA);
                    y += 11;
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

