package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class QuestJournalScreen extends Screen {
    private QuestDefinition selectedQuest = null;

    private final List<QuestDefinition> mainQuests = new ArrayList<>();
    private final List<QuestDefinition> secondaryQuests = new ArrayList<>();
    private final List<QuestDefinition> contractQuests = new ArrayList<>();
    private final List<QuestDefinition> completedQuests = new ArrayList<>();

    private boolean openMain = true;
    private boolean openSecondary = true;
    private boolean openContract = true;
    private boolean openCompleted = false;

    private int leftScroll = 0;
    private int maxLeftScroll = 0;

    private Button trackButton = null;
    private Button abandonButton = null;

    private record ClickableRegion(int x, int y, int w, int h, QuestDefinition quest, int toggleCat) {}
    private final List<ClickableRegion> clickables = new ArrayList<>();

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
        }).bounds(16, this.height - 28, 100, 20).build());

        this.trackButton = Button.builder(Component.literal("Seguir"), b -> {
            if (selectedQuest != null) {
                if (selectedQuest.id.equals(QuestConfig.trackedQuestId)) {
                    QuestConfig.trackedQuestId = "none";
                } else {
                    QuestConfig.trackedQuestId = selectedQuest.id;
                }
                QuestConfig.saveSettings();
                updateActionButtons();
            }
        }).bounds(this.width - 146, this.height - 28, 130, 20).build();
        this.addRenderableWidget(this.trackButton);

        this.abandonButton = Button.builder(Component.literal("§cAbandonar"), b -> {
            if (selectedQuest != null && !"MAIN".equalsIgnoreCase(selectedQuest.category)) {
                NetworkHandler.CHANNEL.sendToServer(new AbandonQuestPacket(selectedQuest.id));
                ClientQuestData.removeTag(selectedQuest.activeTag);
                ClientQuestData.removeTag(selectedQuest.doneTag);
                if (selectedQuest.id.equals(QuestConfig.trackedQuestId)) {
                    QuestConfig.trackedQuestId = "none";
                    QuestConfig.saveSettings();
                }
                Minecraft.getInstance().getToasts().addToast(
                        new QuestToast("Misión Abandonada", selectedQuest.title, ItemStack.EMPTY, 0xFF5555));
                selectedQuest = null;
                refreshQuestLists();
                autoSelectQuest();
            }
        }).bounds(this.width - 252, this.height - 28, 100, 20).build();
        this.addRenderableWidget(this.abandonButton);

        autoSelectQuest();
    }

    private void autoSelectQuest() {
        if (selectedQuest != null) {
            updateActionButtons();
            return;
        }
        if (QuestConfig.trackedQuestId != null && !"none".equals(QuestConfig.trackedQuestId)) {
            QuestDefinition tr = findQuestById(QuestConfig.trackedQuestId);
            if (tr != null && ClientQuestData.hasTag(tr.activeTag)) {
                selectedQuest = tr;
                updateActionButtons();
                return;
            }
        }
        if (!mainQuests.isEmpty()) selectedQuest = mainQuests.get(0);
        else if (!secondaryQuests.isEmpty()) selectedQuest = secondaryQuests.get(0);
        else if (!contractQuests.isEmpty()) selectedQuest = contractQuests.get(0);
        else if (!completedQuests.isEmpty()) selectedQuest = completedQuests.get(0);
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (this.trackButton != null) {
            boolean canTrack = selectedQuest != null && !ClientQuestData.hasTag(selectedQuest.completedTag);
            this.trackButton.visible = canTrack;
            if (canTrack) {
                this.trackButton.setMessage(Component.literal(
                        selectedQuest.id.equals(QuestConfig.trackedQuestId) ? "§cDejar de seguir" : "§aSeguir"));
            }
        }
        if (this.abandonButton != null) {
            this.abandonButton.visible = (selectedQuest != null
                    && !"MAIN".equalsIgnoreCase(selectedQuest.category)
                    && ClientQuestData.hasTag(selectedQuest.activeTag)
                    && !ClientQuestData.hasTag(selectedQuest.completedTag));
        }
    }

    private void refreshQuestLists() {
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
                String cat = q.category != null ? q.category.toUpperCase() : "SECONDARY";
                switch (cat) {
                    case "MAIN" -> mainQuests.add(q);
                    case "CONTRACT", "REPEATABLE" -> contractQuests.add(q);
                    default -> secondaryQuests.add(q);
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

    private ItemStack getQuestIcon(QuestDefinition q) {
        if (q.iconItem != null && !q.iconItem.isEmpty()) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(q.iconItem));
            if (item != null && item != Items.AIR) return new ItemStack(item);
        }
        String cat = q.category != null ? q.category.toUpperCase() : "SECONDARY";
        return switch (cat) {
            case "MAIN" -> new ItemStack(Items.GOLDEN_SWORD);
            case "CONTRACT" -> new ItemStack(Items.CROSSBOW);
            case "REPEATABLE" -> new ItemStack(Items.FISHING_ROD);
            default -> new ItemStack(Items.WRITABLE_BOOK);
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Fondo oscuro estilo Witcher 3
        g.fill(0, 0, this.width, this.height, 0xEE0A0A0D);

        int topY = 16;
        int bottomY = this.height - 36;
        int colGap = 12;
        int leftX = 18;
        int usableW = this.width - 36;
        int leftW = Math.max(155, (int) (usableW * 0.31));
        int midW = Math.max(155, (int) (usableW * 0.34));
        int rightW = usableW - leftW - midW - colGap * 2;
        int midX = leftX + leftW + colGap;
        int rightX = midX + midW + colGap;

        // Columna izquierda: acordeones de misiones
        clickables.clear();
        g.enableScissor(leftX - 8, topY, leftX + leftW + 4, bottomY);
        int curY = topY - leftScroll;
        curY = renderAccordion(g, 0, "MISIONES PRINCIPALES", openMain, mainQuests, leftX, curY, leftW, topY, bottomY);
        curY = renderAccordion(g, 1, "MISIONES SECUNDARIAS", openSecondary, secondaryQuests, leftX, curY, leftW, topY, bottomY);
        curY = renderAccordion(g, 2, "CONTRATOS Y REPETIBLES", openContract, contractQuests, leftX, curY, leftW, topY, bottomY);
        curY = renderAccordion(g, 3, "COMPLETADA", openCompleted, completedQuests, leftX, curY, leftW, topY, bottomY);
        g.disableScissor();

        int contentHeight = (curY + leftScroll) - topY;
        maxLeftScroll = Math.max(0, contentHeight - (bottomY - topY));
        leftScroll = Math.max(0, Math.min(leftScroll, maxLeftScroll));

        // Separador vertical sutil entre centro y derecha
        g.fill(rightX - 6, topY, rightX - 5, bottomY, 0x33D4AF37);

        if (selectedQuest != null) {
            renderCenterColumn(g, midX, topY, midW, bottomY);
            renderRightColumn(g, rightX, topY, rightW, bottomY);
        } else {
            g.drawCenteredString(this.font, "§7No hay misiones activas. Habla con un NPC.", midX + (midW + rightW) / 2, topY + 60, 0xAAAAAA);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private int renderAccordion(GuiGraphics g, int catIdx, String title, boolean isOpen, List<QuestDefinition> list,
                                int x, int y, int w, int clipTop, int clipBottom) {
        int hdrH = 20;
        g.fill(x, y, x + w, y + hdrH, 0xCC171512);
        g.fill(x, y, x + w, y + 1, 0x668C734B);
        g.fill(x, y + hdrH - 1, x + w, y + hdrH, 0x668C734B);

        String arrow = isOpen ? "▲" : "▼";
        String hdrText = (isOpen ? "§e" : "§7") + title + " §8(" + list.size() + ")";
        g.drawString(this.font, hdrText, x + 6, y + 6, 0xFFFFFF);
        g.drawString(this.font, arrow, x + w - 12, y + 6, 0xAAAAAA);

        if (y + hdrH >= clipTop && y <= clipBottom) {
            clickables.add(new ClickableRegion(x, y, w, hdrH, null, catIdx));
        }
        y += hdrH + 3;

        if (!isOpen) return y + 2;

        if (list.isEmpty()) {
            g.drawString(this.font, "§8  Sin misiones", x + 6, y + 2, 0x666666);
            return y + 15;
        }

        for (QuestDefinition q : list) {
            boolean sel = (q == selectedQuest);
            boolean tracked = q.id.equals(QuestConfig.trackedQuestId);
            boolean comp = ClientQuestData.hasTag(q.completedTag);

            List<FormattedCharSequence> tLines = this.font.split(Component.literal(q.title), w - 42);
            int cardH = Math.max(28, 12 + tLines.size() * 10 + 4);

            if (sel) {
                g.fill(x, y, x + w, y + cardH, 0xFFD4AF37);
                g.fill(x + 1, y + 1, x + w - 1, y + cardH - 1, 0xEE1A1713);
            } else {
                g.fill(x, y, x + w, y + cardH, 0x99141311);
                g.fill(x, y + cardH - 1, x + w, y + cardH, 0x33FFFFFF);
            }

            if (tracked) {
                g.drawString(this.font, "§6◈", x - 8, y + cardH / 2 - 4, 0xFFD4AF37);
            }

            g.renderItem(getQuestIcon(q), x + 4, y + 4);
            String lvlStr = String.valueOf(Math.max(1, q.suggestedLevel));
            g.drawString(this.font, lvlStr, x + 22, y + 8, 0xAAAAAA);

            int textX = x + 36;
            int ty = y + 4;
            int titleColor = comp ? 0x888888 : (sel ? 0xFFD4AF37 : 0xEFEFEF);
            for (int i = 0; i < Math.min(2, tLines.size()); i++) {
                g.drawString(this.font, tLines.get(i), textX, ty, titleColor);
                ty += 10;
            }
            String sub = (q.npc != null && !q.npc.isEmpty()) ? q.npc : "Dragonfyre";
            g.drawString(this.font, "§8" + sub, textX, y + cardH - 11, 0x888888);

            if (y + cardH >= clipTop && y <= clipBottom) {
                clickables.add(new ClickableRegion(x, y, w, cardH, q, -1));
            }
            y += cardH + 3;
        }
        return y + 4;
    }

    private void renderCenterColumn(GuiGraphics g, int x, int y, int w, int bottomY) {
        int hdrH = 34;
        g.fill(x, y, x + w, y + hdrH, 0xFFD4AF37);
        g.fill(x + 1, y + 1, x + w - 1, y + hdrH - 1, 0xEE171512);

        g.renderItem(getQuestIcon(selectedQuest), x + 7, y + 9);
        List<FormattedCharSequence> titleLines = this.font.split(
                Component.literal(selectedQuest.title.toUpperCase()), w - 34);
        if (!titleLines.isEmpty()) {
            g.drawString(this.font, titleLines.get(0), x + 28, y + 6, 0xFFD4AF37);
        }
        String sub = (selectedQuest.npc != null && !selectedQuest.npc.isEmpty()) ? selectedQuest.npc : "Encargo";
        g.drawString(this.font, "§7" + sub, x + 28, y + 19, 0x999999);

        int cy = y + hdrH + 14;
        boolean isDone = ClientQuestData.hasTag(selectedQuest.doneTag);
        boolean isComp = ClientQuestData.hasTag(selectedQuest.completedTag);

        if (selectedQuest.stages != null) {
            for (int sIdx = 0; sIdx < selectedQuest.stages.size(); sIdx++) {
                QuestDefinition.QuestStage st = selectedQuest.stages.get(sIdx);
                int cur = ClientQuestData.getScore(RPGQuestEngine.getInternalScoreKey(selectedQuest.id, sIdx, st.type));
                int req = Math.max(1, st.amount);
                boolean stageDone = isComp || isDone || cur >= req;

                String icon = stageDone ? "§a[✔]" : "§6◈";
                String color = stageDone ? "§8" : "§f";
                String prog = (!stageDone && req > 1) ? " §7(" + Math.min(cur, req) + "/" + req + ")" : "";
                String lineTxt = icon + " " + color + st.description + prog;

                List<FormattedCharSequence> lines = this.font.split(Component.literal(lineTxt), w - 12);
                for (FormattedCharSequence l : lines) {
                    if (cy + 11 > bottomY - 54) break;
                    g.drawString(this.font, l, x + 6, cy, 0xFFFFFF);
                    cy += 11;
                }
                cy += 4;
            }
        }

        if (!isComp) {
            String turnInIcon = isDone ? "§6◈" : "§8□";
            String turnInColor = isDone ? "§e" : "§8";
            String turnInTxt = turnInIcon + " " + turnInColor + "Regresa con " + sub + " para entregar.";
            List<FormattedCharSequence> tLines = this.font.split(Component.literal(turnInTxt), w - 12);
            for (FormattedCharSequence l : tLines) {
                if (cy + 11 > bottomY - 54) break;
                g.drawString(this.font, l, x + 6, cy, 0xFFFFFF);
                cy += 11;
            }
        }

        // Recompensas en la parte inferior de la columna central
        int rewY = bottomY - 46;
        g.fill(x, rewY - 6, x + w, rewY - 5, 0x55D4AF37);
        g.drawString(this.font, "§6Recompensas:", x + 4, rewY, 0xFFD4AF37);
        int sx = x + 4;
        int sy = rewY + 13;
        if (selectedQuest.rewardXp > 0) {
            drawMiniSlot(g, new ItemStack(Items.EXPERIENCE_BOTTLE), sx, sy, 0xFF5A4E36);
            sx += 24;
        }
        if (selectedQuest.rewardXpLevels > 0) {
            drawMiniSlot(g, new ItemStack(Items.ENCHANTED_BOOK), sx, sy, 0xFF3A7A9E);
            sx += 24;
        }
        if (selectedQuest.rewardItems != null) {
            for (QuestDefinition.RewardItem ri : selectedQuest.rewardItems) {
                if (sx + 20 > x + w) break;
                Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ri.id));
                if (it != null && it != Items.AIR) {
                    drawMiniSlot(g, new ItemStack(it, ri.count), sx, sy, 0xFF5A4E36);
                    sx += 24;
                }
            }
        }
        if (selectedQuest.randomRewards != null && !selectedQuest.randomRewards.isEmpty() && sx + 20 <= x + w) {
            drawMiniSlot(g, new ItemStack(Items.CHEST), sx, sy, 0xFFAA55FF);
        }
    }

    private void drawMiniSlot(GuiGraphics g, ItemStack stack, int x, int y, int border) {
        g.fill(x, y, x + 20, y + 20, border);
        g.fill(x + 1, y + 1, x + 19, y + 19, 0xFF1A1815);
        g.renderItem(stack, x + 2, y + 2);
        g.renderItemDecorations(this.font, stack, x + 2, y + 2);
    }

    private void renderRightColumn(GuiGraphics g, int x, int y, int w, int bottomY) {
        String lore = (selectedQuest.description != null && !selectedQuest.description.isEmpty())
                ? selectedQuest.description
                : "Sin descripción adicional registrada para este encargo.";

        int ty = y + 4;
        List<FormattedCharSequence> lines = this.font.split(Component.literal(lore), w - 8);
        for (FormattedCharSequence line : lines) {
            if (ty + 12 > bottomY - 24) break;
            g.drawString(this.font, line, x + 4, ty, 0xC8C0B0);
            ty += 12;
        }

        g.drawString(this.font, "§8Nivel sugerido: §7" + Math.max(1, selectedQuest.suggestedLevel), x + 4, bottomY - 14, 0x888888);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickableRegion r : clickables) {
                if (mouseX >= r.x() && mouseX <= r.x() + r.w() && mouseY >= r.y() && mouseY <= r.y() + r.h()) {
                    if (r.toggleCat() >= 0) {
                        switch (r.toggleCat()) {
                            case 0 -> openMain = !openMain;
                            case 1 -> openSecondary = !openSecondary;
                            case 2 -> openContract = !openContract;
                            case 3 -> openCompleted = !openCompleted;
                        }
                        return true;
                    } else if (r.quest() != null) {
                        this.selectedQuest = r.quest();
                        updateActionButtons();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int leftW = Math.max(155, (int) ((this.width - 36) * 0.31));
        if (mouseX <= 18 + leftW + 10) {
            leftScroll = Math.max(0, Math.min(maxLeftScroll, leftScroll - (int) (delta * 18)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
