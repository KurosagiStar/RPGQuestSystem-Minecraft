package com.rpgquestengine;

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

public class NpcDialogScreen extends Screen {
    private final OpenNpcDialogPacket data;

    private static final int BOX_W = 360;
    private static final int BOX_H = 246;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 6;

    private int selectedChoice = 0;

    private record RewardSlot(ItemStack stack, List<Component> customTooltip, int x, int y, int choiceIdx) {}
    private final List<RewardSlot> rewardSlots = new ArrayList<>();

    public NpcDialogScreen(OpenNpcDialogPacket data) {
        super(Component.literal(data.npcName));
        this.data = data;
    }

    @Override
    protected void init() {
        int left = (this.width - BOX_W) / 2;
        int top = (this.height - BOX_H) / 2;
        int btnY = top + BOX_H - 28;

        if ("AVAILABLE".equals(data.state)) {
            this.addRenderableWidget(Button.builder(Component.literal("§aAceptar Misión"), b -> {
                NetworkHandler.CHANNEL.sendToServer(new NpcDialogActionPacket("ACCEPT", data.questId, -1));
                this.onClose();
            }).bounds(left + 20, btnY, 135, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                    .bounds(left + BOX_W - 110, btnY, 90, 20).build());
        } else if ("TURN_IN".equals(data.state)) {
            this.addRenderableWidget(Button.builder(Component.literal("§6Completar Misión"), b -> {
                NetworkHandler.CHANNEL.sendToServer(new NpcDialogActionPacket("COMPLETE", data.questId, selectedChoice));
                this.onClose();
            }).bounds(left + 20, btnY, 145, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                    .bounds(left + BOX_W - 110, btnY, 90, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                    .bounds(left + (BOX_W - 100) / 2, btnY, 100, 20).build());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && "TURN_IN".equals(data.state)) {
            for (RewardSlot rs : rewardSlots) {
                if (rs.choiceIdx() >= 0 && mouseX >= rs.x() && mouseX <= rs.x() + SLOT_SIZE
                        && mouseY >= rs.y() && mouseY <= rs.y() + SLOT_SIZE) {
                    this.selectedChoice = rs.choiceIdx();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private ItemStack resolveRewardStack(OpenNpcDialogPacket.RewardEntry re) {
        if (re.itemId.startsWith("xp_levels:")) {
            return new ItemStack(Items.EXPERIENCE_BOTTLE, 1);
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(re.itemId));
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, re.count);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        rewardSlots.clear();

        int left = (this.width - BOX_W) / 2;
        int top = (this.height - BOX_H) / 2;

        g.fill(left - 2, top - 2, left + BOX_W + 2, top + BOX_H + 2, 0xFFD4AF37);
        g.fill(left, top, left + BOX_W, top + BOX_H, 0xEE141418);
        g.fill(left, top + 24, left + BOX_W, top + 25, 0x88D4AF37);

        g.drawString(this.font, "§6" + data.npcName, left + 14, top + 9, 0xFFFFFF);

        int textY = top + 33;
        int maxTextW = BOX_W - 28;

        String headline;
        String lore = data.questLore;
        String objective;

        switch (data.state) {
            case "AVAILABLE" -> {
                headline = "§e" + data.questTitle;
                if (lore.isEmpty()) lore = "Tengo un nuevo encargo importante para ti, aventurero.";
                objective = "§7Objetivo: §f" + data.questDesc;
            }
            case "TURN_IN" -> {
                headline = "§e" + data.questTitle + " §a(Completada)";
                lore = "¡Has regresado victorioso! Tu esfuerzo ha dado frutos.";
                objective = "§aLista para entregar y reclamar tu recompensa.";
            }
            case "IN_PROGRESS" -> {
                headline = "§e" + data.questTitle + " §7(En Progreso)";
                if (lore.isEmpty()) lore = "Aún tienes trabajo pendiente antes de poder completar este encargo.";
                objective = "§7Objetivo: §f" + data.questDesc;
            }
            case "LOCKED" -> {
                headline = "§cEncargo Bloqueado";
                lore = "Aún no estás preparado para los desafíos que tengo para ti.";
                objective = "§cCompleta primero: §e" + data.questTitle;
            }
            default -> {
                headline = "§aTodos los encargos completados";
                lore = "Has demostrado tu valía completando todas mis misiones por ahora.";
                objective = "§7Vuelve más adelante si surgen nuevos peligros.";
            }
        }

        g.drawString(this.font, headline, left + 14, textY, 0xFFFFFF);
        textY += 14;

        List<FormattedCharSequence> loreLines = this.font.split(Component.literal("§7" + lore), maxTextW);
        for (int i = 0; i < Math.min(4, loreLines.size()); i++) {
            g.drawString(this.font, loreLines.get(i), left + 14, textY, 0xCCCCCC);
            textY += 11;
        }

        textY += 4;
        List<FormattedCharSequence> objLines = this.font.split(Component.literal(objective), maxTextW);
        for (int i = 0; i < Math.min(2, objLines.size()); i++) {
            g.drawString(this.font, objLines.get(i), left + 14, textY, 0xFFFFFF);
            textY += 11;
        }

        boolean hasAnyReward = ("AVAILABLE".equals(data.state) || "TURN_IN".equals(data.state) || "IN_PROGRESS".equals(data.state))
                && (data.rewardXp > 0 || data.rewardXpLevels > 0
                || !data.rewardItems.isEmpty() || !data.randomRewards.isEmpty() || !data.choiceRewards.isEmpty());

        if (hasAnyReward) {
            int sepY = top + BOX_H - 96;
            g.fill(left + 14, sepY, left + BOX_W - 14, sepY + 1, 0x88D4AF37);
            g.drawString(this.font, "§6Recompensas", left + 14, sepY + 6, 0xFFD4AF37);

            int slotX = left + 14;
            int slotY = sepY + 18;

            if (data.rewardXp > 0) {
                ItemStack xpStack = new ItemStack(Items.EXPERIENCE_BOTTLE, 1);
                drawSlot(g, xpStack, slotX, slotY, 0xFF5A4E36);
                rewardSlots.add(new RewardSlot(xpStack, List.of(Component.literal("§a+" + data.rewardXp + " XP")), slotX, slotY, -1));
                slotX += SLOT_SIZE + SLOT_GAP;
            }

            if (data.rewardXpLevels > 0) {
                ItemStack lvlStack = new ItemStack(Items.ENCHANTED_BOOK, 1);
                drawSlot(g, lvlStack, slotX, slotY, 0xFF3A7A9E);
                rewardSlots.add(new RewardSlot(lvlStack, List.of(Component.literal("§b+" + data.rewardXpLevels + " Niveles de XP")), slotX, slotY, -1));
                slotX += SLOT_SIZE + SLOT_GAP;
            }

            for (OpenNpcDialogPacket.RewardEntry re : data.rewardItems) {
                if (slotX + SLOT_SIZE > left + BOX_W - 14) break;
                ItemStack stack = resolveRewardStack(re);
                if (stack.isEmpty()) continue;
                drawSlot(g, stack, slotX, slotY, 0xFF5A4E36);
                rewardSlots.add(new RewardSlot(stack, null, slotX, slotY, -1));
                slotX += SLOT_SIZE + SLOT_GAP;
            }

            if (!data.randomRewards.isEmpty() && slotX + SLOT_SIZE <= left + BOX_W - 14) {
                ItemStack chestStack = new ItemStack(Items.CHEST, 1);
                drawSlot(g, chestStack, slotX, slotY, 0xFFAA55FF);
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal("§dRecompensa Aleatoria (1 al azar):"));
                for (OpenNpcDialogPacket.RewardEntry rr : data.randomRewards) {
                    if (rr.itemId.startsWith("xp_levels:")) {
                        tip.add(Component.literal("§7- §b+" + rr.itemId.substring("xp_levels:".length()) + " Niveles XP"));
                    } else {
                        ItemStack s = resolveRewardStack(rr);
                        String name = s.isEmpty() ? rr.itemId : s.getHoverName().getString();
                        tip.add(Component.literal("§7- §f" + rr.count + "x " + name));
                    }
                }
                rewardSlots.add(new RewardSlot(chestStack, tip, slotX, slotY, -1));
                slotX += SLOT_SIZE + SLOT_GAP;
            }

            if (!data.choiceRewards.isEmpty()) {
                int choiceY = slotY + SLOT_SIZE + 6;
                String label = "TURN_IN".equals(data.state) ? "§aElige 1:" : "§eA elegir (1):";
                g.drawString(this.font, label, left + 14, choiceY + 6, 0xFFFFFF);
                int cx = left + 14 + this.font.width(label) + 8;
                for (int i = 0; i < data.choiceRewards.size(); i++) {
                    if (cx + SLOT_SIZE > left + BOX_W - 14) break;
                    ItemStack stack = resolveRewardStack(data.choiceRewards.get(i));
                    if (stack.isEmpty()) continue;
                    int border = (i == selectedChoice && "TURN_IN".equals(data.state)) ? 0xFF55FF55 : 0xFF3E6B48;
                    drawSlot(g, stack, cx, choiceY, border);
                    rewardSlots.add(new RewardSlot(stack, null, cx, choiceY, i));
                    cx += SLOT_SIZE + SLOT_GAP;
                }
            }
        }

        super.render(g, mouseX, mouseY, partialTick);

        for (RewardSlot rs : rewardSlots) {
            if (mouseX >= rs.x() && mouseX <= rs.x() + SLOT_SIZE && mouseY >= rs.y() && mouseY <= rs.y() + SLOT_SIZE) {
                if (rs.customTooltip() != null) {
                    g.renderComponentTooltip(this.font, rs.customTooltip(), mouseX, mouseY);
                } else {
                    g.renderTooltip(this.font, rs.stack(), mouseX, mouseY);
                }
                break;
            }
        }
    }

    private void drawSlot(GuiGraphics g, ItemStack stack, int x, int y, int borderColor) {
        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, borderColor);
        g.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF1E1E24);
        g.renderItem(stack, x + 2, y + 2);
        g.renderItemDecorations(this.font, stack, x + 2, y + 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
