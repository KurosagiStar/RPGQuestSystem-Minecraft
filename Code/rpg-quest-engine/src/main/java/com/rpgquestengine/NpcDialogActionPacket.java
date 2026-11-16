package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Set;
import java.util.function.Supplier;

public class NpcDialogActionPacket {
    public final String action; // ACCEPT, COMPLETE
    public final String questId;

    public NpcDialogActionPacket(String action, String questId) {
        this.action = action != null ? action : "";
        this.questId = questId != null ? questId : "";
    }

    public static void encode(NpcDialogActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action);
        buf.writeUtf(msg.questId);
    }

    public static NpcDialogActionPacket decode(FriendlyByteBuf buf) {
        return new NpcDialogActionPacket(buf.readUtf(), buf.readUtf());
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
                sp.sendSystemMessage(Component.literal("§a[Misión Completada] §f" + q.title + (q.rewardXp > 0 ? " §e(+" + q.rewardXp + " XP)" : "")));
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
