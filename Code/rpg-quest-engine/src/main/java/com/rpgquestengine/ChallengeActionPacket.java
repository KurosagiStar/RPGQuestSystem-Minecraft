package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChallengeActionPacket {
    private final int action; // 0 = sync, 1 = claim Wither

    public ChallengeActionPacket(int action) {
        this.action = action;
    }

    public static void encode(ChallengeActionPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.action);
    }

    public static ChallengeActionPacket decode(FriendlyByteBuf buf) {
        return new ChallengeActionPacket(buf.readInt());
    }

    public static void handle(ChallengeActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;
            if (msg.action == 1) {
                ChallengeManager.claimWither(sp);
            } else if (msg.action == 2) {
                ChallengeManager.claimEnderman(sp);
            } else {
                ChallengeManager.syncToPlayer(sp);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
