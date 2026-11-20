package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenNpcDialogPacket {
    public static class RewardEntry {
        public final String itemId;
        public final int count;
        public final String nbt;

        public RewardEntry(String itemId, int count, String nbt) {
            this.itemId = itemId != null ? itemId : "minecraft:emerald";
            this.count = Math.max(1, count);
            this.nbt = nbt != null ? nbt : "";
        }
    }

    public final String npcId;
    public final String npcName;
    public final int npcEntityId;
    public final String state; // TURN_IN, IN_PROGRESS, AVAILABLE, LOCKED, COMPLETED_ALL
    public final String questId;
    public final String questTitle;
    public final String questLore;
    public final String questDesc;
    public final int rewardXp;
    public final int rewardXpLevels;
    public final List<RewardEntry> rewardItems;
    public final List<RewardEntry> randomRewards;
    public final List<RewardEntry> choiceRewards;

    public OpenNpcDialogPacket(String npcId, String npcName, int npcEntityId, String state,
                               String questId, String questTitle, String questLore,
                               String questDesc, int rewardXp, int rewardXpLevels,
                               List<RewardEntry> rewardItems,
                               List<RewardEntry> randomRewards,
                               List<RewardEntry> choiceRewards) {
        this.npcId = npcId != null ? npcId : "";
        this.npcName = npcName != null ? npcName : "";
        this.npcEntityId = npcEntityId;
        this.state = state != null ? state : "COMPLETED_ALL";
        this.questId = questId != null ? questId : "";
        this.questTitle = questTitle != null ? questTitle : "";
        this.questLore = questLore != null ? questLore : "";
        this.questDesc = questDesc != null ? questDesc : "";
        this.rewardXp = rewardXp;
        this.rewardXpLevels = rewardXpLevels;
        this.rewardItems = rewardItems != null ? rewardItems : new ArrayList<>();
        this.randomRewards = randomRewards != null ? randomRewards : new ArrayList<>();
        this.choiceRewards = choiceRewards != null ? choiceRewards : new ArrayList<>();
    }

    private static void encodeList(FriendlyByteBuf buf, List<RewardEntry> list) {
        buf.writeInt(list.size());
        for (RewardEntry r : list) {
            buf.writeUtf(r.itemId);
            buf.writeInt(r.count);
            buf.writeUtf(r.nbt);
        }
    }

    private static List<RewardEntry> decodeList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<RewardEntry> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new RewardEntry(buf.readUtf(), buf.readInt(), buf.readUtf()));
        }
        return items;
    }

    public static void encode(OpenNpcDialogPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.npcId);
        buf.writeUtf(msg.npcName);
        buf.writeInt(msg.npcEntityId);
        buf.writeUtf(msg.state);
        buf.writeUtf(msg.questId);
        buf.writeUtf(msg.questTitle);
        buf.writeUtf(msg.questLore);
        buf.writeUtf(msg.questDesc);
        buf.writeInt(msg.rewardXp);
        buf.writeInt(msg.rewardXpLevels);
        encodeList(buf, msg.rewardItems);
        encodeList(buf, msg.randomRewards);
        encodeList(buf, msg.choiceRewards);
    }

    public static OpenNpcDialogPacket decode(FriendlyByteBuf buf) {
        String npcId = buf.readUtf();
        String npcName = buf.readUtf();
        int npcEntityId = buf.readInt();
        String state = buf.readUtf();
        String questId = buf.readUtf();
        String questTitle = buf.readUtf();
        String questLore = buf.readUtf();
        String questDesc = buf.readUtf();
        int rewardXp = buf.readInt();
        int rewardXpLevels = buf.readInt();
        List<RewardEntry> rewardItems = decodeList(buf);
        List<RewardEntry> randomRewards = decodeList(buf);
        List<RewardEntry> choiceRewards = decodeList(buf);
        return new OpenNpcDialogPacket(npcId, npcName, npcEntityId, state, questId, questTitle, questLore, questDesc,
                rewardXp, rewardXpLevels, rewardItems, randomRewards, choiceRewards);
    }

    public static void handle(OpenNpcDialogPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openNpcDialog(msg)));
        ctx.get().setPacketHandled(true);
    }
}
