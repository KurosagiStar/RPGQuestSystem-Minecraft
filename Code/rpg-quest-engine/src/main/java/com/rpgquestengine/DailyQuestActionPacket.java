package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DailyQuestActionPacket {
    private final int taskIdx; // 0..3 para reclamar tarea diaria; -1 para pedir sync

    public DailyQuestActionPacket(int taskIdx) {
        this.taskIdx = taskIdx;
    }

    public static void encode(DailyQuestActionPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.taskIdx);
    }

    public static DailyQuestActionPacket decode(FriendlyByteBuf buf) {
        return new DailyQuestActionPacket(buf.readInt());
    }

    public static void handle(DailyQuestActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;
            if (msg.taskIdx >= 0 && msg.taskIdx < 4) {
                DailyQuestManager.claimTask(sp, msg.taskIdx);
            } else {
                DailyQuestManager.syncToPlayer(sp);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
