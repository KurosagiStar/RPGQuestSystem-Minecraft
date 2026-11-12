package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncEntityTagsPacket {
    private final int entityId;
    private final List<String> tags;

    public SyncEntityTagsPacket(int entityId, List<String> tags) {
        this.entityId = entityId;
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public static void encode(SyncEntityTagsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.tags.size());
        for (String tag : msg.tags) {
            buf.writeUtf(tag);
        }
    }

    public static SyncEntityTagsPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int size = buf.readVarInt();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }
        return new SyncEntityTagsPacket(id, list);
    }

    public static void handle(SyncEntityTagsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientQuestData.updateEntityTags(msg.entityId, msg.tags);
        });
        ctx.get().setPacketHandled(true);
    }
}

