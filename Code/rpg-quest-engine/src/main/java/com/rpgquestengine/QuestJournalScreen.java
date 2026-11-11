package com.rpgquestengine;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class QuestJournalScreen extends Screen {
    private QuestDefinition selectedQuest = null;

    private final List<QuestDefinition> allActiveQuests = new ArrayList<>();
    private final List<QuestDefinition> mainQuests = new ArrayList<>();
    private final List<QuestDefinition> secondaryQuests = new ArrayList<>();
    private final List<QuestDefinition> contractQuests = new ArrayList<>();
    private final List<QuestDefinition> completedQuests = new ArrayList<>();

    private int activeTab = 0;
    private Button trackButton = null;

    private static class ClickableItem {
        QuestDefinition quest;
        int yStart;
        int height;

        ClickableItem(QuestDefinition quest, int yStart, int height) {
            this.quest = quest;
            this.yStart = yStart;
            this.height = height;
        }
    }
    private final List<ClickableItem> currentClickables = new ArrayList<>();

    public QuestJournalScreen() {
        super(Component.literal("Diario de Misiones"));
    }

    @Override
    protected void init() {
        super.init();
        refreshQuestLists();

        this.addRenderableWidget(Button.builder(Component.literal("⚙ Ajustar HUD"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new HudPositionScreen(this));
            }
        }).bounds(15, this.height - 28, 100, 20).build());

        // Botón Dinámico: "Seguir" o "Dejar de seguir"
        this.trackButton = Button.builder(Component.literal("Seguir"), b -> {
            if (selectedQuest != null) {
                if (selectedQuest.id.equals(QuestConfig.trackedQuestId)) {
                    // Si ya se está siguiendo, se deja de seguir explícitamente (valor "none")
                    QuestConfig.trackedQuestId = "none";
                } else {
                    // Si no se está siguiendo, se sigue esta misión
                    QuestConfig.trackedQuestId = selectedQuest.id;
                }
                QuestConfig.saveSettings();
                updateTrackButtonLabel();
            }
        }).bounds(this.width - 150, this.height - 28, 130, 20).build();
        this.addRenderableWidget(this.trackButton);

        int tabW = (this.width - 30) / 5;
        this.addRenderableWidget(Button.builder(Component.literal("§aActivas"), b -> { activeTab = 0; selectFirstInCurrentTab(); }).bounds(15 + (tabW * 0), 10, tabW - 4, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("§6Principales"), b -> { activeTab = 1; selectFirstInCurrentTab(); }).bounds(15 + (tabW * 1), 10, tabW - 4, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("§eSecundarias"), b -> { activeTab = 2; selectFirstInCurrentTab(); }).bounds(15 + (tabW * 2), 10, tabW - 4, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("§cContratos"), b -> { activeTab = 3; selectFirstInCurrentTab(); }).bounds(15 + (tabW * 3), 10, tabW - 4, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("§7Completadas"), b -> { activeTab = 4; selectFirstInCurrentTab(); }).bounds(15 + (tabW * 4), 10, tabW - 4, 18).build());

        selectFirstInCurrentTab();
    }

    private void updateTrackButtonLabel() {
        if (this.trackButton != null) {
            if (selectedQuest != null && selectedQuest.id.equals(QuestConfig.trackedQuestId)) {
                this.trackButton.setMessage(Component.literal("§cDejar de seguir"));
            } else {
                this.trackButton.setMessage(Component.literal("§aSeguir"));
            }
        }
    }

    private void selectFirstInCurrentTab() {
        List<QuestDefinition> cur = getCurrentCategoryList();
        if (!cur.isEmpty()) {
            selectedQuest = cur.get(0);
        } else {
            selectedQuest = null;
        }
        updateTrackButtonLabel();
    }

    private List<QuestDefinition> getCurrentCategoryList() {
        switch (activeTab) {
            case 0: return allActiveQuests;
            case 1: return mainQuests;
            case 2: return secondaryQuests;
            case 3: return contractQuests;
            case 4: return completedQuests;
            default: return allActiveQuests;
        }
    }

    private void refreshQuestLists() {
        allActiveQuests.clear();
        mainQuests.clear();
        secondaryQuests.clear();
        contractQuests.clear();
        completedQuests.clear();

        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                QuestDefinition prereq = findQuestById(q.prerequisiteQuestId);
                if (prereq != null && !ClientQuestData.hasTag(prereq.completedTag)) {
                    continue;
                }
            }

            if (ClientQuestData.hasTag(q.completedTag)) {
                completedQuests.add(q);
            } else if (ClientQuestData.hasTag(q.activeTag)) {
                allActiveQuests.add(q);

                String cat = q.category != null ? q.category.toUpperCase() : "SECONDARY";
                switch (cat) {
                    case "MAIN":
                        mainQuests.add(q);
                        break;
                    case "CONTRACT":
                    case "REPEATABLE":
                        contractQuests.add(q);
                        break;
                    default:
                        secondaryQuests.add(q);
                        break;
                }
            }
        }
    }

    private QuestDefinition findQuestById(String id) {
        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (q.id.equalsIgnoreCase(id)) return q;
        }
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int panelLeftW = 150;
        int maxTextWidth = panelLeftW - 14;
        int startY = 34;

        graphics.fill(15, startY, 15 + panelLeftW, this.height - 35, 0xBB111111);

        currentClickables.clear();
        int currentY = startY + 6;

        if (activeTab == 0) {
            currentY = renderCategorySection(graphics, "§6─── Principales ───", filterByCategory(allActiveQuests, "MAIN"), currentY, maxTextWidth, panelLeftW);
            currentY = renderCategorySection(graphics, "§e─── Secundarias ───", filterByCategory(allActiveQuests, "SECONDARY"), currentY, maxTextWidth, panelLeftW);
            currentY = renderCategorySection(graphics, "§c─── Contratos ───", filterByCategory(allActiveQuests, "CONTRACT", "REPEATABLE"), currentY, maxTextWidth, panelLeftW);

            if (allActiveQuests.isEmpty()) {
                graphics.drawString(this.font, "§7No hay misiones activas", 22, currentY + 4, 0x888888);
            }
        } else {
            List<QuestDefinition> currentList = getCurrentCategoryList();
            if (currentList.isEmpty()) {
                graphics.drawString(this.font, "§7No hay misiones", 22, currentY + 4, 0x888888);
            } else {
                for (QuestDefinition q : currentList) {
                    currentY = renderQuestItem(graphics, q, currentY, maxTextWidth, panelLeftW);
                }
            }
        }

        int rightX = 22 + panelLeftW;
        int rightW = this.width - rightX - 15;
        graphics.fill(rightX, startY, rightX + rightW, this.height - 35, 0xDD151515);

        if (selectedQuest != null) {
            renderQuestDetails(graphics, rightX + 15, startY + 12, rightW - 30);
        } else {
            graphics.drawCenteredString(this.font, "§7Selecciona una misión para ver los detalles", rightX + rightW / 2, startY + 60, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int renderCategorySection(GuiGraphics graphics, String header, List<QuestDefinition> list, int startY, int maxTextWidth, int panelW) {
        if (list.isEmpty()) return startY;

        graphics.drawString(this.font, header, 20, startY, 0xFFFFFF);
        int nextY = startY + 12;

        for (QuestDefinition q : list) {
            nextY = renderQuestItem(graphics, q, nextY, maxTextWidth, panelW);
        }
        return nextY + 4;
    }

    private int renderQuestItem(GuiGraphics graphics, QuestDefinition q, int y, int maxTextWidth, int panelW) {
        boolean isSelected = (selectedQuest == q);
        boolean isTracked = q.id.equals(QuestConfig.trackedQuestId);

        String prefix = isTracked ? "§a★ " : (isSelected ? "§6> " : "  ");
        String fullTitle = prefix + "§f" + q.title;

        List<FormattedCharSequence> lines = this.font.split(Component.literal(fullTitle), maxTextWidth);

        int itemH = Math.max(14, lines.size() * 10 + 4);

        if (isSelected) {
            graphics.fill(17, y - 2, 13 + panelW, y + itemH - 2, 0x44FFAA00);
        }

        int textY = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, 20, textY, 0xFFFFFF);
            textY += 10;
        }

        currentClickables.add(new ClickableItem(q, y - 2, itemH));
        return y + itemH + 2;
    }

    private List<QuestDefinition> filterByCategory(List<QuestDefinition> source, String... categories) {
        List<QuestDefinition> filtered = new ArrayList<>();
        for (QuestDefinition q : source) {
            String cat = q.category != null ? q.category.toUpperCase() : "SECONDARY";
            for (String c : categories) {
                if (cat.equalsIgnoreCase(c)) {
                    filtered.add(q);
                    break;
                }
            }
        }
        return filtered;
    }

    private void renderQuestDetails(GuiGraphics graphics, int x, int y, int wrapWidth) {
        graphics.drawString(this.font, "§6§l" + selectedQuest.title, x, y, 0xFFFFFF);
        y += 14;

        graphics.drawString(this.font, "§7Asignada por: §e" + selectedQuest.npc + "  §7| Nivel sugerido: §b" + selectedQuest.suggestedLevel, x, y, 0xAAAAAA);
        y += 16;

        graphics.fill(x, y, x + wrapWidth, y + 1, 0x44FFFFFF);
        y += 8;

        graphics.drawString(this.font, "§f" + selectedQuest.description, x, y, 0xDDDDDD);
        y += 30;

        graphics.drawString(this.font, "§6§nObjetivos:", x, y, 0xFFFFFF);
        y += 14;

        for (QuestDefinition.QuestStage stage : selectedQuest.stages) {
            String optText = stage.optional ? "§7[Opcional] " : "";
            graphics.drawString(this.font, "§e◆ " + optText + "§f" + stage.description + " §7(" + stage.amount + ")", x + 8, y, 0xFFFFFF);
            y += 14;
        }

        y += 10;
        if (selectedQuest.rewards != null && !selectedQuest.rewards.isEmpty()) {
            graphics.drawString(this.font, "§a§nRecompensas:", x, y, 0xFFFFFF);
            y += 14;
            for (String rew : selectedQuest.rewards) {
                graphics.drawString(this.font, "§2✦ §f" + rew, x + 8, y, 0xFFFFFF);
                y += 12;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelLeftW = 150;

        if (button == 0 && mouseX >= 15 && mouseX <= 15 + panelLeftW) {
            for (ClickableItem ci : currentClickables) {
                if (mouseY >= ci.yStart && mouseY <= ci.yStart + ci.height) {
                    this.selectedQuest = ci.quest;
                    updateTrackButtonLabel();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
