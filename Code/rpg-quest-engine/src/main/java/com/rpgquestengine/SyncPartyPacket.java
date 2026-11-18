package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncPartyPacket {
    public static class MemberEntry {
        public final String uuid;
        public final String name;
        public final boolean isLeader;
        public final boolean online;

        public MemberEntry(String uuid, String name, boolean isLeader, boolean online) {
            this.uuid = uuid;
            this.name = name;
            this.isLeader = isLeader;
            this.online = online;
        }
    }

    public final boolean inParty;
    public final boolean isLeader;
    public final String pendingInviteFrom;
    public final List<MemberEntry> members;
    public final List<String> invitablePlayers;

    public SyncPartyPacket(boolean inParty, boolean isLeader, String pendingInviteFrom,
                           List<MemberEntry> members, List<String> invitablePlayers) {
        this.inParty = inParty;
        this.isLeader = isLeader;
        this.pendingInviteFrom = pendingInviteFrom != null ? pendingInviteFrom : "";
        this.members = members != null ? members : new ArrayList<>();
        this.invitablePlayers = invitablePlayers != null ? invitablePlayers : new ArrayList<>();
    }

    public static void encode(SyncPartyPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.inParty);
        buf.writeBoolean(msg.isLeader);
        buf.writeUtf(msg.pendingInviteFrom);
        buf.writeInt(msg.members.size());
        for (MemberEntry m : msg.members) {
            buf.writeUtf(m.uuid);
            buf.writeUtf(m.name);
            buf.writeBoolean(m.isLeader);
            buf.writeBoolean(m.online);
        }
        buf.writeInt(msg.invitablePlayers.size());
        for (String p : msg.invitablePlayers) {
            buf.writeUtf(p);
        }
    }

    public static SyncPartyPacket decode(FriendlyByteBuf buf) {
        boolean inParty = buf.readBoolean();
        boolean isLeader = buf.readBoolean();
        String inviteFrom = buf.readUtf();
        int mCount = buf.readInt();
        List<MemberEntry> members = new ArrayList<>(mCount);
        for (int i = 0; i < mCount; i++) {
            members.add(new MemberEntry(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
        }
        int pCount = buf.readInt();
        List<String> players = new ArrayList<>(pCount);
        for (int i = 0; i < pCount; i++) {
            players.add(buf.readUtf());
        }
        return new SyncPartyPacket(inParty, isLeader, inviteFrom, members, players);
    }

    public static void handle(SyncPartyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientQuestData.PARTY_IN_PARTY = msg.inParty;
            ClientQuestData.PARTY_IS_LEADER = msg.isLeader;
            ClientQuestData.PARTY_INVITE_FROM = msg.pendingInviteFrom;
            ClientQuestData.PARTY_MEMBERS.clear();
            ClientQuestData.PARTY_MEMBERS.addAll(msg.members);
            ClientQuestData.PARTY_INVITABLE.clear();
            ClientQuestData.PARTY_INVITABLE.addAll(msg.invitablePlayers);
        }));
        ctx.get().setPacketHandled(true);
    }
}
