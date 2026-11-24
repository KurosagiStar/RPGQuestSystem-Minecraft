package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncChallengesPacket {
    public final boolean witherDone;
    public final int witherCooldownSec;
    public final int enderKills;
    public final boolean enderClaimed;

    public SyncChallengesPacket(boolean witherDone, int witherCooldownSec, int enderKills, boolean enderClaimed) {
        this.witherDone = witherDone;
        this.witherCooldownSec = Math.max(0, witherCooldownSec);
        this.enderKills = Math.max(0, enderKills);
        this.enderClaimed = enderClaimed;
    }

    public static void encode(SyncChallengesPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.witherDone);
        buf.writeInt(msg.witherCooldownSec);
        buf.writeInt(msg.enderKills);
        buf.writeBoolean(msg.enderClaimed);
    }

    public static SyncChallengesPacket decode(FriendlyByteBuf buf) {
        return new SyncChallengesPacket(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(SyncChallengesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientQuestData.CHALLENGE_WITHER_DONE = msg.witherDone;
            ClientQuestData.CHALLENGE_WITHER_CD_SEC = msg.witherCooldownSec;
            ClientQuestData.CHALLENGE_ENDER_KILLS = msg.enderKills;
            ClientQuestData.CHALLENGE_ENDER_CLAIMED = msg.enderClaimed;
            ClientQuestData.CHALLENGE_SYNCED_AT_MS = System.currentTimeMillis();
        }));
        ctx.get().setPacketHandled(true);
    }
}
