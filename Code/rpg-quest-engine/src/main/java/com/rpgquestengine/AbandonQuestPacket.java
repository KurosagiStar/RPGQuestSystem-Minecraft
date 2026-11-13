package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public class AbandonQuestPacket {
    private final String questId;

    public AbandonQuestPacket(String questId) {
        this.questId = questId;
    }

    public static void encode(AbandonQuestPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.questId);
    }

    public static AbandonQuestPacket decode(FriendlyByteBuf buf) {
        return new AbandonQuestPacket(buf.readUtf());
    }

    public static void handle(AbandonQuestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            QuestDefinition target = null;
            for (QuestDefinition q : QuestConfig.QUESTS) {
                if (q.id != null && q.id.equals(msg.questId)) {
                    target = q;
                    break;
                }
            }
            if (target == null || "MAIN".equalsIgnoreCase(target.category)) return;

            if (target.activeTag != null && !target.activeTag.isEmpty()) sp.removeTag(target.activeTag);
            if (target.doneTag != null && !target.doneTag.isEmpty()) sp.removeTag(target.doneTag);

            for (String t : new ArrayList<>(sp.getTags())) {
                if (t.startsWith("rqe_zone_" + target.id + "_")
                        || t.startsWith("rqe_npc_" + target.id + "_")
                        || t.startsWith("rqe_pos_" + target.id + "_")
                        || t.startsWith("rqe_ambush_" + target.id + "_")) {
                    sp.removeTag(t);
                }
            }

            Scoreboard sb = sp.getServer().getScoreboard();
            for (int sIdx = 0; sIdx < target.stages.size(); sIdx++) {
                QuestDefinition.QuestStage st = target.stages.get(sIdx);
                String key = RPGQuestEngine.getInternalScoreKey(target.id, sIdx, st.type);
                Objective obj = sb.getObjective(key);
                if (obj != null) {
                    sb.resetPlayerScore(sp.getScoreboardName(), obj);
                }
                String remKey = "rqe_wave_rem_" + target.id + "_" + sIdx;
                Objective remObj = sb.getObjective(remKey);
                if (remObj != null) {
                    sb.resetPlayerScore(sp.getScoreboardName(), remObj);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

