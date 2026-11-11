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

    public SyncPlayerQuestsPacket(Set<String> tags, Map<String, Integer> scores) {
        this.tags = tags != null ? tags : new HashSet<>();
        this.scores = scores != null ? scores : new HashMap<>();
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

        return new SyncPlayerQuestsPacket(tags, scores);
    }

    public static void handle(SyncPlayerQuestsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientQuestData.updateData(msg.tags, msg.scores);
        });
        ctx.get().setPacketHandled(true);
    }
}

