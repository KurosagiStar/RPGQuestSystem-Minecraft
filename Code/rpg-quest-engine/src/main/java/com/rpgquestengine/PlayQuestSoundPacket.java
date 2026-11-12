package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayQuestSoundPacket {
    private final int soundType;

    public PlayQuestSoundPacket(int soundType) {
        this.soundType = soundType;
    }

    public static void encode(PlayQuestSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.soundType);
    }

    public static PlayQuestSoundPacket decode(FriendlyByteBuf buf) {
        return new PlayQuestSoundPacket(buf.readVarInt());
    }

    public static void handle(PlayQuestSoundPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientQuestData.playClientSound(msg.soundType);
        });
        ctx.get().setPacketHandled(true);
    }
}

