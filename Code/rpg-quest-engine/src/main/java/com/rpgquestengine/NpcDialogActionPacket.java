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
            if (ri.nbt != null && !ri.nbt.isEmpty()) {
                try {
                    stack.setTag(net.minecraft.nbt.TagParser.parseTag(ri.nbt));
                } catch (Exception ignored) {}
            }
            if (!sp.getInventory().add(stack)) {
                sp.drop(stack, false);
            }
        }
    }

    public static void grantAllQuestRewards(ServerPlayer sp, QuestDefinition q, int choiceIndex) {
        if (sp == null || q == null) return;
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
            int idx = (choiceIndex >= 0 && choiceIndex < q.choiceRewards.size()) ? choiceIndex : 0;
            grantRewardItem(sp, q.choiceRewards.get(idx));
        }

        if (q.ftbQuestId != null && !q.ftbQuestId.isEmpty() && sp.getServer() != null) {
            sp.getServer().getCommands().performPrefixedCommand(
                    sp.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(4),
                    "ftbquests change_progress " + sp.getScoreboardName() + " complete " + q.ftbQuestId);
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

            Set<String> tags = RPGQuestEngine.getAllQuestTags(sp);
            if ("COMPLETE".equalsIgnoreCase(msg.action)) {
                if (q.doneTag == null || !tags.contains(q.doneTag)) return;

                boolean partyObjAlreadyDone = tags.contains("rqe_party_obj_done_" + q.id);

                // Verificar y consumir ítems requeridos en etapas ITEM / COLLECT / DELIVER (salvo que un miembro del grupo ya los haya entregado)
                if (q.stages != null && !partyObjAlreadyDone) {
                    for (QuestDefinition.QuestStage st : q.stages) {
                        if (("ITEM".equalsIgnoreCase(st.type) || "COLLECT".equalsIgnoreCase(st.type) || "DELIVER".equalsIgnoreCase(st.type))
                                && st.target != null && st.amount > 0) {
                            ResourceLocation rl = ResourceLocation.tryParse(st.target);
                            Item reqItem = rl != null ? ForgeRegistries.ITEMS.getValue(rl) : null;
                            if (reqItem != null && reqItem != net.minecraft.world.item.Items.AIR) {
                                int have = 0;
                                for (int s = 0; s < sp.getInventory().getContainerSize(); s++) {
                                    ItemStack stStack = sp.getInventory().getItem(s);
                                    if (!stStack.isEmpty() && stStack.getItem() == reqItem) {
                                        have += stStack.getCount();
                                    }
                                }
                                if (have < st.amount) {
                                    sp.sendSystemMessage(Component.literal("§c[Misión] Te faltan ítems requeridos en tu inventario."));
                                    return;
                                }
                            }
                        }
                    }
                    for (QuestDefinition.QuestStage st : q.stages) {
                        if (("ITEM".equalsIgnoreCase(st.type) || "COLLECT".equalsIgnoreCase(st.type) || "DELIVER".equalsIgnoreCase(st.type))
                                && st.target != null && st.amount > 0) {
                            ResourceLocation rl = ResourceLocation.tryParse(st.target);
                            Item reqItem = rl != null ? ForgeRegistries.ITEMS.getValue(rl) : null;
                            if (reqItem != null && reqItem != net.minecraft.world.item.Items.AIR) {
                                int toRemove = st.amount;
                                for (int s = 0; s < sp.getInventory().getContainerSize() && toRemove > 0; s++) {
                                    ItemStack stStack = sp.getInventory().getItem(s);
                                    if (!stStack.isEmpty() && stStack.getItem() == reqItem) {
                                        int take = Math.min(toRemove, stStack.getCount());
                                        stStack.shrink(take);
                                        if (stStack.isEmpty()) {
                                            sp.getInventory().setItem(s, ItemStack.EMPTY);
                                        }
                                        toRemove -= take;
                                    }
                                }
                            }
                        }
                    }
                }

                if (q.activeTag != null) RPGQuestEngine.removeQuestTag(sp, q.activeTag);
                RPGQuestEngine.removeQuestTag(sp, q.doneTag);
                RPGQuestEngine.removeQuestTag(sp, "rqe_party_obj_done_" + q.id);
                if (!"REPEATABLE".equalsIgnoreCase(q.category) && q.completedTag != null) {
                    RPGQuestEngine.addQuestTag(sp, q.completedTag);
                    RPGQuestEngine.addQuestTag(sp, "rqe_rewarded_" + q.id);
                } else if ("REPEATABLE".equalsIgnoreCase(q.category)) {
                    RPGQuestEngine.resetQuestStageScores(sp, q);
                    RPGQuestEngine.onRepeatableCompleted(sp, q);
                }

                grantAllQuestRewards(sp, q, msg.choiceIndex);
                sp.getInventory().setChanged();
                sp.inventoryMenu.broadcastChanges();
                sp.containerMenu.broadcastChanges();
                sp.inventoryMenu.sendAllDataToRemote();

                String xpTxt = q.rewardXp > 0 ? " §e(+" + q.rewardXp + " XP)" : "";
                String lvlTxt = q.rewardXpLevels > 0 ? " §b(+" + q.rewardXpLevels + " Niveles)" : "";
                sp.sendSystemMessage(Component.literal("§a[Misión Completada] §f" + q.title + xpTxt + lvlTxt));
                DailyQuestManager.onTaskProgress(sp, "NPC_TURNIN", 1);
                NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                        new PlayQuestSoundPacket(4));
                PartyManager.onNpcQuestCompleted(sp, q, msg.choiceIndex);
                PartyManager.syncUnlockedActiveMainQuests(sp);
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
                RPGQuestEngine.resetQuestStageScores(sp, q);
                if (q.doneTag != null) RPGQuestEngine.removeQuestTag(sp, q.doneTag);
                if (q.activeTag != null) RPGQuestEngine.addQuestTag(sp, q.activeTag);
                String desc = (q.stages != null && !q.stages.isEmpty()) ? q.stages.get(0).description : q.title;
                sp.sendSystemMessage(Component.literal("§6[Misión Aceptada] §f" + q.title + ": §e" + desc));
                PartyManager.onNpcQuestAccepted(sp, q);
                RPGQuestEngine.syncPlayerAfterDialog(sp);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
