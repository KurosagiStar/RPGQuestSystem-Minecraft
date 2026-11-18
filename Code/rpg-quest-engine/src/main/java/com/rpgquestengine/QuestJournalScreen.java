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

    private int activeMainTab = 0; // 0 = Diario de Misiones, 1 = Misiones Diarias
    private int leftScroll = 0;
    private int maxLeftScroll = 0;

    private Button trackButton = null;
    private Button abandonButton = null;

    private record ClickableRegion(int x, int y, int w, int h, QuestDefinition quest, int toggleCat, int dailyClaimIdx) {}
    private final List<ClickableRegion> clickables = new ArrayList<>();

    public QuestJournalScreen() {
        super(Component.literal("Diario de Misiones"));
    }

    @Override
    protected void init() {
        super.init();
        refreshQuestLists();
        NetworkHandler.CHANNEL.sendToServer(new DailyQuestActionPacket(-1));

        this.addRenderableWidget(Button.builder(Component.literal("§6Diario de Misiones"), b -> {
            activeMainTab = 0;
            updateActionButtons();
        }).bounds(16, 8, 130, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("§eMisiones Diarias"), b -> {
            activeMainTab = 1;
            NetworkHandler.CHANNEL.sendToServer(new DailyQuestActionPacket(-1));
            updateActionButtons();
        }).bounds(152, 8, 130, 18).build());

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
            boolean canTrack = activeMainTab == 0 && selectedQuest != null && !ClientQuestData.hasTag(selectedQuest.completedTag);
            this.trackButton.visible = canTrack;
            if (canTrack) {
                this.trackButton.setMessage(Component.literal(
                        selectedQuest.id.equals(QuestConfig.trackedQuestId) ? "§cDejar de seguir" : "§aSeguir"));
            }
        }
        if (this.abandonButton != null) {
            this.abandonButton.visible = (activeMainTab == 0 && selectedQuest != null
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
        // Indicador dorado debajo de la pestaña activa
        int tabLineX = activeMainTab == 0 ? 16 : 152;
        g.fill(tabLineX, 27, tabLineX + 130, 29, 0xFFD4AF37);

        clickables.clear();
        int topY = 34;
        int bottomY = this.height - 36;

        if (activeMainTab == 1) {
            renderDailyTab(g, 16, topY, this.width - 32, bottomY, mouseX, mouseY);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int colGap = 10;
        int leftX = 16;
        int usableW = this.width - 32;
        int leftW = (int) (usableW * 0.31);
        int midW = (int) (usableW * 0.34);
        int rightW = Math.max(80, usableW - leftW - midW - colGap * 2);
        int midX = leftX + leftW + colGap;
        int rightX = midX + midW + colGap;

        // Fondos de las 3 columnas para separación visual clara
        g.fill(leftX - 2, topY - 2, leftX + leftW + 2, bottomY + 2, 0x66141210);
        g.fill(midX - 2, topY - 2, midX + midW + 2, bottomY + 2, 0x55141210);
        g.fill(rightX - 2, topY - 2, rightX + rightW + 2, bottomY + 2, 0x77141210);
        g.fill(midX - 6, topY, midX - 5, bottomY, 0x33D4AF37);
        g.fill(rightX - 6, topY, rightX - 5, bottomY, 0x44D4AF37);

        // Columna izquierda: acordeones de misiones
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

        if (selectedQuest != null) {
            renderCenterColumn(g, midX, topY, midW, bottomY);
            renderRightColumn(g, rightX, topY, rightW, bottomY);
        } else {
            g.drawCenteredString(this.font, "§7No hay misiones activas.", midX + midW / 2, topY + 60, 0xAAAAAA);
            g.drawCenteredString(this.font, "§8Habla con un NPC para comenzar.", midX + midW / 2, topY + 74, 0x777777);
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
            clickables.add(new ClickableRegion(x, y, w, hdrH, null, catIdx, -1));
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
                drawTrackingEmblem(g, x - 6, y + cardH / 2);
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
                clickables.add(new ClickableRegion(x, y, w, cardH, q, -1, -1));
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

                if (stageDone) {
                    g.drawString(this.font, "§a[✔]", x + 4, cy, 0x55FF55);
                } else {
                    drawTrackingEmblem(g, x + 10, cy + 4);
                }
                String color = stageDone ? "§8" : "§f";
                String prog = (!stageDone && req > 1) ? " §7(" + Math.min(cur, req) + "/" + req + ")" : "";
                String lineTxt = color + st.description + prog;

                List<FormattedCharSequence> lines = this.font.split(Component.literal(lineTxt), w - 24);
                for (FormattedCharSequence l : lines) {
                    if (cy + 11 > bottomY - 54) break;
                    g.drawString(this.font, l, x + 22, cy, 0xFFFFFF);
                    cy += 11;
                }
                cy += 4;
            }
        }

        if (!isComp) {
            if (isDone) {
                drawTrackingEmblem(g, x + 10, cy + 4);
            } else {
                g.drawString(this.font, "§8□", x + 7, cy, 0x666666);
            }
            String turnInColor = isDone ? "§e" : "§8";
            String turnInTxt = turnInColor + "Regresa con " + sub + " para entregar.";
            List<FormattedCharSequence> tLines = this.font.split(Component.literal(turnInTxt), w - 24);
            for (FormattedCharSequence l : tLines) {
                if (cy + 11 > bottomY - 54) break;
                g.drawString(this.font, l, x + 22, cy, 0xFFFFFF);
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

    private void drawTrackingEmblem(GuiGraphics g, int cx, int cy) {
        // Anillo/rombo dorado estilo Witcher 3 de 9x9 px
        g.fill(cx - 4, cy - 2, cx + 5, cy + 3, 0xFFD4AF37);
        g.fill(cx - 2, cy - 4, cx + 3, cy + 5, 0xFFD4AF37);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF1A1713);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFE066);
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

    private void renderDailyTab(GuiGraphics g, int x, int y, int w, int bottomY, int mouseX, int mouseY) {
        // Marco principal oscuro + borde dorado
        g.fill(x - 1, y - 1, x + w + 1, bottomY + 1, 0xFFD4AF37);
        g.fill(x, y, x + w, bottomY, 0xEE141311);

        // Barra superior de Progreso Diario (0..400)
        int barBoxH = 44;
        g.fill(x + 10, y + 8, x + w - 10, y + 8 + barBoxH, 0xCC1B1916);
        g.drawString(this.font, "§6PROGRESO DIARIO", x + 18, y + 14, 0xFFD4AF37);
        g.drawString(this.font, "§e" + ClientQuestData.DAILY_POINTS + " §7/ 400", x + 18, y + 28, 0xFFFFFF);

        int trackX = x + 130;
        int trackW = w - 155;
        int trackY = y + 26;
        g.fill(trackX, trackY, trackX + trackW, trackY + 6, 0xFF2B2823);
        int fillW = (int) (trackW * Math.min(1.0f, ClientQuestData.DAILY_POINTS / 400.0f));
        if (fillW > 0) {
            g.fill(trackX, trackY, trackX + fillW, trackY + 6, 0xFF45C945);
        }

        String[] msRewards = {"1x Moneda + XP", "Gem Dust + Mat.", "2x Monedas + Mat.", "Sigil of Socketing"};
        int hoverMs = -1;
        for (int m = 0; m < 4; m++) {
            int nx = trackX + (trackW * (m + 1)) / 4 - 8;
            int ny = trackY - 5;
            boolean reached = ClientQuestData.DAILY_POINTS >= (m + 1) * 100;
            g.fill(nx, ny, nx + 16, ny + 16, reached ? 0xFF45C945 : 0xFF5A4E36);
            g.fill(nx + 1, ny + 1, nx + 15, ny + 15, 0xFF171512);
            String mark = reached ? "§a✔" : "§7" + ((m + 1) * 100);
            if (reached) {
                g.drawCenteredString(this.font, mark, nx + 8, ny + 4, 0x55FF55);
            } else {
                g.drawCenteredString(this.font, "§7•", nx + 8, ny + 4, 0x888888);
            }
            g.drawCenteredString(this.font, reached ? "§a" + ((m + 1) * 100) : "§7" + ((m + 1) * 100), nx + 8, ny + 18, 0xCCCCCC);
            if (mouseX >= nx && mouseX <= nx + 16 && mouseY >= ny && mouseY <= ny + 16) {
                hoverMs = m;
            }
        }

        // 4 Tarjetas de Misiones Diarias
        int cardsTop = y + barBoxH + 18;
        int cardsBottom = bottomY - 22;
        int gap = 8;
        int cardW = (w - 20 - gap * 3) / 4;
        List<SyncDailyQuestsPacket.DailyEntry> entries = ClientQuestData.DAILY_ENTRIES;

        for (int i = 0; i < 4; i++) {
            int cx = x + 10 + i * (cardW + gap);
            int cy = cardsTop;
            int ch = cardsBottom - cardsTop;

            SyncDailyQuestsPacket.DailyEntry entry = i < entries.size() ? entries.get(i) : null;
            boolean done = entry != null && entry.progress >= entry.target;
            boolean claimed = entry != null && entry.claimed;

            int border = (done && !claimed) ? 0xFFD4AF37 : (claimed ? 0xFF3E6B48 : 0xFF444038);
            g.fill(cx, cy, cx + cardW, cy + ch, border);
            g.fill(cx + 1, cy + 1, cx + cardW - 1, cy + ch - 1, 0xEE181613);

            if (entry == null) {
                g.drawCenteredString(this.font, "§7Cargando...", cx + cardW / 2, cy + ch / 2, 0x888888);
                continue;
            }

            // Header progreso
            g.fill(cx + 1, cy + 1, cx + cardW - 1, cy + 18, 0xCC221F1A);
            String progTxt = (done ? "§a" : "§e") + "Progreso: " + entry.progress + "/" + entry.target;
            g.drawString(this.font, progTxt, cx + 6, cy + 5, 0xFFFFFF);

            // Título de tarea envuelto
            List<FormattedCharSequence> lines = this.font.split(Component.literal("§f" + entry.title), cardW - 12);
            int ty = cy + 28;
            for (int l = 0; l < Math.min(4, lines.size()); l++) {
                g.drawString(this.font, lines.get(l), cx + 6, ty, 0xEFEFEF);
                ty += 11;
            }

            // Puntos
            g.drawCenteredString(this.font, "§6+100 Pts", cx + cardW / 2, cy + ch - 42, 0xFFD4AF37);

            // Botón Reclamar / Estado
            int btnX = cx + 8;
            int btnY = cy + ch - 26;
            int btnW = cardW - 16;
            int btnH = 18;
            if (claimed) {
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xFF253528);
                g.drawCenteredString(this.font, "§aReclamado ✔", btnX + btnW / 2, btnY + 5, 0x55FF55);
            } else if (done) {
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xFFD4AF37);
                g.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, 0xFF2E4E2E);
                g.drawCenteredString(this.font, "§a§lReclamar", btnX + btnW / 2, btnY + 5, 0xFFFFFF);
                clickables.add(new ClickableRegion(btnX, btnY, btnW, btnH, null, -1, i));
            } else {
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xFF252320);
                g.drawCenteredString(this.font, "§7En progreso", btnX + btnW / 2, btnY + 5, 0x888888);
            }
        }

        // Temporizador de reinicio diario
        long elapsedSec = Math.max(0, (System.currentTimeMillis() - ClientQuestData.DAILY_SYNCED_AT_MS) / 1000L);
        long remSec = Math.max(60, ClientQuestData.DAILY_RESET_SECONDS - elapsedSec);
        long hrs = remSec / 3600;
        long mins = (remSec % 3600) / 60;
        String resetTxt = String.format("§7Se reinicia en §e%dh %02dm", hrs, mins);
        g.drawString(this.font, resetTxt, x + w - this.font.width(resetTxt) - 12, bottomY - 14, 0xAAAAAA);

        if (hoverMs >= 0) {
            g.renderTooltip(this.font, Component.literal("§6Hito " + ((hoverMs + 1) * 100) + ": §f" + msRewards[hoverMs]), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickableRegion r : clickables) {
                if (mouseX >= r.x() && mouseX <= r.x() + r.w() && mouseY >= r.y() && mouseY <= r.y() + r.h()) {
                    if (r.dailyClaimIdx() >= 0) {
                        NetworkHandler.CHANNEL.sendToServer(new DailyQuestActionPacket(r.dailyClaimIdx()));
                        return true;
                    } else if (r.toggleCat() >= 0) {
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
        int leftW = (int) ((this.width - 32) * 0.31);
        if (mouseX <= 16 + leftW + 10) {
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
