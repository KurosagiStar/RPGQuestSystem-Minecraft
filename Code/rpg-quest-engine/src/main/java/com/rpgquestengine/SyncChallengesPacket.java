package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncChallengesPacket {
    public final boolean witherDone;
    public final int witherCooldownSec;

    public SyncChallengesPacket(boolean witherDone, int witherCooldownSec) {
        this.witherDone = witherDone;
        this.witherCooldownSec = Math.max(0, witherCooldownSec);
    }

    public static void encode(SyncChallengesPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.witherDone);
        buf.writeInt(msg.witherCooldownSec);
    }

    public static SyncChallengesPacket decode(FriendlyByteBuf buf) {
        return new SyncChallengesPacket(buf.readBoolean(), buf.readInt());
    }

    public static void handle(SyncChallengesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientQuestData.CHALLENGE_WITHER_DONE = msg.witherDone;
            ClientQuestData.CHALLENGE_WITHER_CD_SEC = msg.witherCooldownSec;
            ClientQuestData.CHALLENGE_SYNCED_AT_MS = System.currentTimeMillis();
        }));
        ctx.get().setPacketHandled(true);
    }
}
