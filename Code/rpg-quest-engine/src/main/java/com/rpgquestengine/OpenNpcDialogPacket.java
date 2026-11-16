package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenNpcDialogPacket {
    public final String npcId;
    public final String npcName;
    public final String state; // TURN_IN, IN_PROGRESS, AVAILABLE, LOCKED, COMPLETED_ALL
    public final String questId;
    public final String questTitle;
    public final String questDesc;
    public final int rewardXp;

    public OpenNpcDialogPacket(String npcId, String npcName, String state,
                               String questId, String questTitle, String questDesc, int rewardXp) {
        this.npcId = npcId != null ? npcId : "";
        this.npcName = npcName != null ? npcName : "";
        this.state = state != null ? state : "COMPLETED_ALL";
        this.questId = questId != null ? questId : "";
        this.questTitle = questTitle != null ? questTitle : "";
        this.questDesc = questDesc != null ? questDesc : "";
        this.rewardXp = rewardXp;
    }

    public static void encode(OpenNpcDialogPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.npcId);
        buf.writeUtf(msg.npcName);
        buf.writeUtf(msg.state);
        buf.writeUtf(msg.questId);
        buf.writeUtf(msg.questTitle);
        buf.writeUtf(msg.questDesc);
        buf.writeInt(msg.rewardXp);
    }

    public static OpenNpcDialogPacket decode(FriendlyByteBuf buf) {
        return new OpenNpcDialogPacket(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt()
        );
    }

    public static void handle(OpenNpcDialogPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft.getInstance().setScreen(new NpcDialogScreen(msg));
        }));
        ctx.get().setPacketHandled(true);
    }
}
