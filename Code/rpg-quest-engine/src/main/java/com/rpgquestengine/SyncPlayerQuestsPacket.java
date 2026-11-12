package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class SyncPlayerQuestsPacket {
    private final Set<String> tags;
    private final Map<String, Integer> scores;
    private final Map<String, double[]> npcPositions; // questId -> [x, z] en tiempo real del NPC físico

    public SyncPlayerQuestsPacket(Set<String> tags, Map<String, Integer> scores, Map<String, double[]> npcPositions) {
        this.tags = tags != null ? tags : new HashSet<>();
        this.scores = scores != null ? scores : new HashMap<>();
        this.npcPositions = npcPositions != null ? npcPositions : new HashMap<>();
    }

    public static void encode(SyncPlayerQuestsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.tags.size());
        for (String tag : msg.tags) {
            buf.writeUtf(tag);
        }

        buf.writeVarInt(msg.scores.size());
        for (Map.Entry<String, Integer> entry : msg.scores.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }

        buf.writeVarInt(msg.npcPositions.size());
        for (Map.Entry<String, double[]> entry : msg.npcPositions.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeDouble(entry.getValue()[0]);
            buf.writeDouble(entry.getValue()[1]);
        }
    }

    public static SyncPlayerQuestsPacket decode(FriendlyByteBuf buf) {
        int tagSize = buf.readVarInt();
        Set<String> tags = new HashSet<>();
        for (int i = 0; i < tagSize; i++) {
            tags.add(buf.readUtf());
        }

        int scoreSize = buf.readVarInt();
        Map<String, Integer> scores = new HashMap<>();
        for (int i = 0; i < scoreSize; i++) {
            String obj = buf.readUtf();
            int val = buf.readVarInt();
            scores.put(obj, val);
        }

        int npcPosSize = buf.readVarInt();
        Map<String, double[]> npcPos = new HashMap<>();
        for (int i = 0; i < npcPosSize; i++) {
            String qId = buf.readUtf();
            double x = buf.readDouble();
            double z = buf.readDouble();
            npcPos.put(qId, new double[]{x, z});
        }

        return new SyncPlayerQuestsPacket(tags, scores, npcPos);
    }

    public static void handle(SyncPlayerQuestsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientQuestData.updateData(msg.tags, msg.scores, msg.npcPositions);
        });
        ctx.get().setPacketHandled(true);
    }
}


