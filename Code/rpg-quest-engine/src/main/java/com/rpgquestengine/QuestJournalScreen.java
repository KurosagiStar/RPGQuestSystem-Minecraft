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

        this.addRenderableWidget(Button.builder(Component.literal("§cDesafíos"), b -> {
            activeMainTab = 2;
            NetworkHandler.CHANNEL.sendToServer(new ChallengeActionPacket(0));
            updateActionButtons();
        }).bounds(288, 8, 110, 18).build());

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
        int tabLineX = activeMainTab == 0 ? 16 : (activeMainTab == 1 ? 152 : 288);
        int tabLineW = activeMainTab == 2 ? 110 : 130;
        g.fill(tabLineX, 27, tabLineX + tabLineW, 29, 0xFFD4AF37);

        clickables.clear();
        int topY = 34;
        int bottomY = this.height - 36;

        if (activeMainTab == 1) {
            renderDailyTab(g, 16, topY, this.width - 32, bottomY, mouseX, mouseY);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (activeMainTab == 2) {
            renderChallengesTab(g, 16, topY, this.width - 32, bottomY, mouseX, mouseY);
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

        // Fondos redondeados modernos de las 3 columnas
        UiStyle.drawRoundedCard(g, leftX - 4, topY - 4, leftW + 8, bottomY - topY + 8, 6, UiStyle.BG_PANEL, 0, false);
        UiStyle.drawRoundedCard(g, midX - 4, topY - 4, midW + 8, bottomY - topY + 8, 6, UiStyle.BG_PANEL, 0, false);
        UiStyle.drawRoundedCard(g, rightX - 4, topY - 4, rightW + 8, bottomY - topY + 8, 6, UiStyle.BG_PANEL, 0, false);

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
        UiStyle.drawRoundedCard(g, x, y, w, hdrH, 4, UiStyle.BG_HEADER, 0, false);

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
                UiStyle.drawRoundedCard(g, x, y, w, cardH, 4, 0xEE232832, UiStyle.ACCENT_GREEN, false);
            } else {
                UiStyle.drawRoundedCard(g, x, y, w, cardH, 4, UiStyle.BG_CARD, 0, false);
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
        UiStyle.drawRoundedCard(g, x, y, w, hdrH, 5, UiStyle.BG_HEADER, UiStyle.BORDER_PANEL, false);

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
        UiStyle.drawRoundedCard(g, x, y, 20, 20, 4, 0xFF1F232C, border, false);
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
        // Marco principal redondeado con sombra
        UiStyle.drawRoundedCard(g, x - 1, y - 1, w + 2, bottomY - y + 2, 7, UiStyle.BG_DARK, UiStyle.BORDER_PANEL, true);

        // Barra superior de Progreso Diario (0..400)
        int barBoxH = 64;
        UiStyle.drawRoundedCard(g, x + 10, y + 8, w - 20, barBoxH, 5, UiStyle.BG_PANEL, 0, false);
        g.drawString(this.font, "§6PROGRESO DIARIO", x + 18, y + 14, 0xFFD4AF37);
        g.drawString(this.font, "§e" + ClientQuestData.DAILY_POINTS + " §7/ 400", x + 18, y + 28, 0xFFFFFF);

        int trackX = x + 130;
        int trackW = w - 155;
        int trackY = y + 21;
        UiStyle.drawProgressBar(g, trackX, trackY, trackW, 6, ClientQuestData.DAILY_POINTS / 400.0f, 0xFF2B2E38, UiStyle.ACCENT_GREEN);

        String[] msRewards = {"1x Moneda de Campeón + 100 XP", "2x Gem Dust + 1x Common Material", "2x Monedas + 2x Uncommon Material", "1x Sigil of Socketing + 250 XP"};
        String[] msItemIds = {"cisco_mod:champion_coin", "apotheosis:gem_dust", "cisco_mod:champion_coin", "apotheosis:sigil_of_socketing"};
        int[] msItemCounts = {1, 2, 2, 1};
        int hoverMs = -1;
        for (int m = 0; m < 4; m++) {
            int nx = trackX + (trackW * (m + 1)) / 4 - 8;
            int ny = trackY - 5;
            boolean reached = ClientQuestData.DAILY_POINTS >= (m + 1) * 100;
            g.fill(nx, ny, nx + 16, ny + 16, reached ? 0xFF45C945 : 0xFF5A4E36);
            g.fill(nx + 1, ny + 1, nx + 15, ny + 15, 0xFF171512);
            if (reached) {
                g.drawCenteredString(this.font, "§a✔", nx + 8, ny + 4, 0x55FF55);
            } else {
                g.drawCenteredString(this.font, "§7" + ((m + 1) * 100), nx + 8, ny + 4, 0xAAAAAA);
            }

            // Cuadrito con icono real de la recompensa debajo del nodo
            int slotX = nx - 2;
            int slotY = ny + 20;
            Item rItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(msItemIds[m]));
            ItemStack rStack = (rItem != null && rItem != Items.AIR)
                    ? new ItemStack(rItem, msItemCounts[m])
                    : new ItemStack(Items.EXPERIENCE_BOTTLE, msItemCounts[m]);
            drawMiniSlot(g, rStack, slotX, slotY, reached ? 0xFF45C945 : 0xFF5A4E36);

            if ((mouseX >= nx && mouseX <= nx + 16 && mouseY >= ny && mouseY <= ny + 16)
                    || (mouseX >= slotX && mouseX <= slotX + 20 && mouseY >= slotY && mouseY <= slotY + 20)) {
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

            int border = (done && !claimed) ? UiStyle.BORDER_GOLD : (claimed ? UiStyle.ACCENT_GREEN : UiStyle.BORDER_SUBTLE);
            UiStyle.drawRoundedCard(g, cx, cy, cardW, ch, 6, UiStyle.BG_CARD, border, true);

            if (entry == null) {
                g.drawCenteredString(this.font, "§7Cargando...", cx + cardW / 2, cy + ch / 2, 0x888888);
                continue;
            }

            // Header progreso
            UiStyle.drawRoundedRect(g, cx + 2, cy + 2, cardW - 4, 17, 4, UiStyle.BG_HEADER);
            String progTxt = (done ? "§a" : "§e") + "Progreso: " + entry.progress + "/" + entry.target;
            g.drawString(this.font, progTxt, cx + 8, cy + 6, 0xFFFFFF);

            // Título de tarea envuelto
            List<FormattedCharSequence> lines = this.font.split(Component.literal("§f" + entry.title), cardW - 14);
            int ty = cy + 28;
            for (int l = 0; l < Math.min(4, lines.size()); l++) {
                g.drawString(this.font, lines.get(l), cx + 7, ty, 0xEFEFEF);
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
                UiStyle.drawRoundedCard(g, btnX, btnY, btnW, btnH, 4, 0xFF203324, UiStyle.ACCENT_GREEN, false);
                g.drawCenteredString(this.font, "§aReclamado ✔", btnX + btnW / 2, btnY + 5, 0x55FF55);
            } else if (done) {
                boolean hov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                UiStyle.drawPillButton(g, this.font, btnX, btnY, btnW, btnH, "§f§lReclamar", UiStyle.ACCENT_GREEN_DARK, UiStyle.ACCENT_GREEN, 0, hov);
                clickables.add(new ClickableRegion(btnX, btnY, btnW, btnH, null, -1, i));
            } else {
                UiStyle.drawRoundedCard(g, btnX, btnY, btnW, btnH, 4, 0xFF242730, UiStyle.BORDER_SUBTLE, false);
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

    private void renderChallengesTab(GuiGraphics g, int x, int y, int w, int bottomY, int mouseX, int mouseY) {
        UiStyle.drawRoundedCard(g, x - 1, y - 1, w + 2, bottomY - y + 2, 7, UiStyle.BG_DARK, UiStyle.BORDER_PANEL, true);

        g.drawString(this.font, "§cDESAFÍOS DE JEFE §7(Reinicio cada 6 horas reales)", x + 14, y + 12, 0xFFFFFF);
        g.fill(x + 14, y + 25, x + w - 14, y + 26, 0x55E0B84C);

        long elapsedSec = Math.max(0, (System.currentTimeMillis() - ClientQuestData.CHALLENGE_SYNCED_AT_MS) / 1000L);
        long remCd = Math.max(0, ClientQuestData.CHALLENGE_WITHER_CD_SEC - elapsedSec);
        boolean onCooldown = remCd > 0;
        boolean done = ClientQuestData.CHALLENGE_WITHER_DONE && !onCooldown;

        int cx = x + 16;
        int cy = y + 36;
        int cw = Math.min(360, w - 32);
        int ch = Math.min(165, bottomY - cy - 14);

        int border = done ? UiStyle.BORDER_GOLD : (onCooldown ? UiStyle.ACCENT_GREEN : 0xFF993B3B);
        UiStyle.drawRoundedCard(g, cx, cy, cw, ch, 6, UiStyle.BG_CARD, border, true);

        // Cabecera
        UiStyle.drawRoundedRect(g, cx + 2, cy + 2, cw - 4, 24, 4, UiStyle.BG_HEADER);
        g.renderItem(new ItemStack(Items.NETHER_STAR), cx + 6, cy + 5);
        g.drawString(this.font, "§c§lJefe Mundial: El Wither", cx + 28, cy + 5, 0xFF5555);
        String progTxt = onCooldown ? "§7Completado" : (done ? "§aProgreso: 1/1" : "§eProgreso: 0/1");
        g.drawString(this.font, progTxt, cx + 28, cy + 15, 0xFFFFFF);

        // Instrucción
        List<FormattedCharSequence> descLines = this.font.split(
                Component.literal("§7Invoca y derrota a un Wither por tu cuenta (4 Arena de Almas + 3 Calaveras Wither)."),
                cw - 20);
        int ty = cy + 34;
        for (FormattedCharSequence line : descLines) {
            g.drawString(this.font, line, cx + 10, ty, 0xCCCCCC);
            ty += 11;
        }

        // Recompensas
        int rewY = cy + ch - 62;
        g.fill(cx + 10, rewY - 5, cx + cw - 10, rewY - 4, 0x55D4AF37);
        g.drawString(this.font, "§6Recompensas:", cx + 10, rewY, 0xFFD4AF37);

        String[] rIds = {"cisco_mod:champion_coin", "apotheosis:sigil_of_socketing", "apotheosis:epic_material", "minecraft:experience_bottle"};
        int[] rCnt = {3, 1, 2, 1};
        String[] rTips = {"3x Moneda de Campeón", "1x Sigil of Socketing", "2x Epic Material (+1 Gema Épica)", "+300 XP"};
        int hoverR = -1;
        int sx = cx + 10;
        int sy = rewY + 12;
        for (int i = 0; i < rIds.length; i++) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(rIds[i]));
            ItemStack st = (it != null && it != Items.AIR) ? new ItemStack(it, rCnt[i]) : new ItemStack(Items.EMERALD, rCnt[i]);
            drawMiniSlot(g, st, sx, sy, 0xFF5A4E36);
            if (mouseX >= sx && mouseX <= sx + 20 && mouseY >= sy && mouseY <= sy + 20) {
                hoverR = i;
            }
            sx += 26;
        }

        // Botón Reclamar / Cooldown
        int btnW = 135;
        int btnH = 20;
        int btnX = cx + cw - btnW - 10;
        int btnY = sy;
        if (onCooldown) {
            long hrs = remCd / 3600;
            long mins = (remCd % 3600) / 60;
            long secs = remCd % 60;
            String cdTxt = String.format("§7En %dh %02dm %02ds", hrs, mins, secs);
            UiStyle.drawRoundedCard(g, btnX, btnY, btnW, btnH, 4, 0xFF203324, UiStyle.ACCENT_GREEN, false);
            g.drawCenteredString(this.font, cdTxt, btnX + btnW / 2, btnY + 6, 0xAAAAAA);
        } else if (done) {
            boolean hov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            UiStyle.drawPillButton(g, this.font, btnX, btnY, btnW, btnH, "§f§lReclamar", UiStyle.ACCENT_GREEN_DARK, UiStyle.ACCENT_GREEN, 0, hov);
            clickables.add(new ClickableRegion(btnX, btnY, btnW, btnH, null, -1, 100));
        } else {
            UiStyle.drawRoundedCard(g, btnX, btnY, btnW, btnH, 4, 0xFF242730, UiStyle.BORDER_SUBTLE, false);
            g.drawCenteredString(this.font, "§eDerrota al Wither", btnX + btnW / 2, btnY + 6, 0xAAAAAA);
        }

        if (hoverR >= 0) {
            g.renderTooltip(this.font, Component.literal("§e" + rTips[hoverR]), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickableRegion r : clickables) {
                if (mouseX >= r.x() && mouseX <= r.x() + r.w() && mouseY >= r.y() && mouseY <= r.y() + r.h()) {
                    if (r.dailyClaimIdx() == 100) {
                        NetworkHandler.CHANNEL.sendToServer(new ChallengeActionPacket(1));
                        return true;
                    } else if (r.dailyClaimIdx() >= 0) {
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
