package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class NpcDialogActionPacket {
    public final String action; // ACCEPT, COMPLETE
    public final String questId;
    public final int choiceIndex;

    public NpcDialogActionPacket(String action, String questId, int choiceIndex) {
        this.action = action != null ? action : "";
        this.questId = questId != null ? questId : "";
        this.choiceIndex = choiceIndex;
    }

    public static void encode(NpcDialogActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action);
        buf.writeUtf(msg.questId);
        buf.writeInt(msg.choiceIndex);
    }

    public static NpcDialogActionPacket decode(FriendlyByteBuf buf) {
        return new NpcDialogActionPacket(buf.readUtf(), buf.readUtf(), buf.readInt());
    }

    private static void grantRewardItem(ServerPlayer sp, QuestDefinition.RewardItem ri) {
        if (ri == null || ri.id == null || ri.id.isEmpty()) return;
        if (ri.id.startsWith("xp_levels:")) {
            try {
                int lvls = Integer.parseInt(ri.id.substring("xp_levels:".length()));
                if (lvls > 0) sp.giveExperienceLevels(lvls);
            } catch (NumberFormatException ignored) {}
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ri.id));
        if (item != null) {
            ItemStack stack = new ItemStack(item, Math.max(1, ri.count));
            if (!sp.getInventory().add(stack)) {
                sp.drop(stack, false);
            }
        }
    }

    public static void handle(NpcDialogActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            QuestDefinition q = null;
            for (QuestDefinition cand : QuestConfig.QUESTS) {
                if (msg.questId.equals(cand.id)) {
                    q = cand;
                    break;
                }
            }
            if (q == null) return;

            Set<String> tags = sp.getTags();
            if ("COMPLETE".equalsIgnoreCase(msg.action)) {
                if (q.doneTag == null || !tags.contains(q.doneTag)) return;
                if (q.activeTag != null) sp.removeTag(q.activeTag);
                sp.removeTag(q.doneTag);
                if (q.completedTag != null) sp.addTag(q.completedTag);

                if (q.rewardXp > 0) sp.giveExperiencePoints(q.rewardXp);
                if (q.rewardXpLevels > 0) sp.giveExperienceLevels(q.rewardXpLevels);

                if (q.rewardItems != null) {
                    for (QuestDefinition.RewardItem ri : q.rewardItems) {
                        grantRewardItem(sp, ri);
                    }
                }

                if (q.randomRewards != null && !q.randomRewards.isEmpty()) {
                    QuestDefinition.RewardItem picked = q.randomRewards.get(sp.getRandom().nextInt(q.randomRewards.size()));
                    grantRewardItem(sp, picked);
                    sp.sendSystemMessage(Component.literal("§d[Recompensa Aleatoria] §fRecibiste: §e" + picked.count + "x " + picked.id));
                }

                if (q.choiceRewards != null && !q.choiceRewards.isEmpty()) {
                    int idx = (msg.choiceIndex >= 0 && msg.choiceIndex < q.choiceRewards.size()) ? msg.choiceIndex : 0;
                    grantRewardItem(sp, q.choiceRewards.get(idx));
                }

                String xpTxt = q.rewardXp > 0 ? " §e(+" + q.rewardXp + " XP)" : "";
                String lvlTxt = q.rewardXpLevels > 0 ? " §b(+" + q.rewardXpLevels + " Niveles)" : "";
                sp.sendSystemMessage(Component.literal("§a[Misión Completada] §f" + q.title + xpTxt + lvlTxt));
                RPGQuestEngine.syncPlayerAfterDialog(sp);
            } else if ("ACCEPT".equalsIgnoreCase(msg.action)) {
                if (q.completedTag != null && tags.contains(q.completedTag)) return;
                if (q.activeTag != null && tags.contains(q.activeTag)) return;
                if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                    for (QuestDefinition cand : QuestConfig.QUESTS) {
                        if (q.prerequisiteQuestId.equals(cand.id)) {
                            if (cand.completedTag == null || !tags.contains(cand.completedTag)) {
                                return;
                            }
                            break;
                        }
                    }
                }
                if (q.activeTag != null) sp.addTag(q.activeTag);
                String desc = (q.stages != null && !q.stages.isEmpty()) ? q.stages.get(0).description : q.title;
                sp.sendSystemMessage(Component.literal("§6[Misión Aceptada] §f" + q.title + ": §e" + desc));
                RPGQuestEngine.syncPlayerAfterDialog(sp);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
