package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PartyActionPacket {
    // 0=SYNC, 1=CREATE, 2=INVITE, 3=ACCEPT, 4=DECLINE, 5=LEAVE, 6=SYNC_MAIN, 7=KICK
    private final int action;
    private final String arg;

    public PartyActionPacket(int action, String arg) {
        this.action = action;
        this.arg = arg != null ? arg : "";
    }

    public static void encode(PartyActionPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.action);
        buf.writeUtf(msg.arg);
    }

    public static PartyActionPacket decode(FriendlyByteBuf buf) {
        return new PartyActionPacket(buf.readInt(), buf.readUtf());
    }

    public static void handle(PartyActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;
            switch (msg.action) {
                case 1 -> PartyManager.createParty(sp);
                case 2 -> PartyManager.invitePlayer(sp, msg.arg);
                case 3 -> PartyManager.acceptInvite(sp);
                case 4 -> PartyManager.declineInvite(sp);
                case 5 -> PartyManager.leaveParty(sp);
                case 6 -> PartyManager.syncMainQuestsWithParty(sp);
                case 7 -> PartyManager.kickMember(sp, msg.arg);
                case 8 -> PartyManager.claimPendingReward(sp, msg.arg);
                case 9 -> PartyManager.claimAllPendingRewards(sp);
                default -> PartyManager.syncToPlayer(sp);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
