package com.rpgquestengine;

import net.minecraft.client.Minecraft;
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

        public RewardEntry(String itemId, int count) {
            this.itemId = itemId != null ? itemId : "minecraft:emerald";
            this.count = Math.max(1, count);
        }
    }

    public final String npcId;
    public final String npcName;
    public final String state; // TURN_IN, IN_PROGRESS, AVAILABLE, LOCKED, COMPLETED_ALL
    public final String questId;
    public final String questTitle;
    public final String questLore;
    public final String questDesc;
    public final int rewardXp;
    public final List<RewardEntry> rewardItems;

    public OpenNpcDialogPacket(String npcId, String npcName, String state,
                               String questId, String questTitle, String questLore,
                               String questDesc, int rewardXp, List<RewardEntry> rewardItems) {
        this.npcId = npcId != null ? npcId : "";
        this.npcName = npcName != null ? npcName : "";
        this.state = state != null ? state : "COMPLETED_ALL";
        this.questId = questId != null ? questId : "";
        this.questTitle = questTitle != null ? questTitle : "";
        this.questLore = questLore != null ? questLore : "";
        this.questDesc = questDesc != null ? questDesc : "";
        this.rewardXp = rewardXp;
        this.rewardItems = rewardItems != null ? rewardItems : new ArrayList<>();
    }

    public static void encode(OpenNpcDialogPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.npcId);
        buf.writeUtf(msg.npcName);
        buf.writeUtf(msg.state);
        buf.writeUtf(msg.questId);
        buf.writeUtf(msg.questTitle);
        buf.writeUtf(msg.questLore);
        buf.writeUtf(msg.questDesc);
        buf.writeInt(msg.rewardXp);
        buf.writeInt(msg.rewardItems.size());
        for (RewardEntry r : msg.rewardItems) {
            buf.writeUtf(r.itemId);
            buf.writeInt(r.count);
        }
    }

    public static OpenNpcDialogPacket decode(FriendlyByteBuf buf) {
        String npcId = buf.readUtf();
        String npcName = buf.readUtf();
        String state = buf.readUtf();
        String questId = buf.readUtf();
        String questTitle = buf.readUtf();
        String questLore = buf.readUtf();
        String questDesc = buf.readUtf();
        int rewardXp = buf.readInt();
        int count = buf.readInt();
        List<RewardEntry> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new RewardEntry(buf.readUtf(), buf.readInt()));
        }
        return new OpenNpcDialogPacket(npcId, npcName, state, questId, questTitle, questLore, questDesc, rewardXp, items);
    }

    public static void handle(OpenNpcDialogPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft.getInstance().setScreen(new NpcDialogScreen(msg));
        }));
        ctx.get().setPacketHandled(true);
    }
}
