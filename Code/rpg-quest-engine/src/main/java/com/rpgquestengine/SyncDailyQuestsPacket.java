package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncDailyQuestsPacket {
    public static class DailyEntry {
        public final String id;
        public final String title;
        public final int progress;
        public final int target;
        public final boolean claimed;

        public DailyEntry(String id, String title, int progress, int target, boolean claimed) {
            this.id = id != null ? id : "";
            this.title = title != null ? title : "";
            this.progress = progress;
            this.target = Math.max(1, target);
            this.claimed = claimed;
        }
    }

    public final int points;
    public final int milestonesMask;
    public final int secondsUntilReset;
    public final List<DailyEntry> entries;

    public SyncDailyQuestsPacket(int points, int milestonesMask, int secondsUntilReset, List<DailyEntry> entries) {
        this.points = points;
        this.milestonesMask = milestonesMask;
        this.secondsUntilReset = secondsUntilReset;
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public static void encode(SyncDailyQuestsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.points);
        buf.writeInt(msg.milestonesMask);
        buf.writeInt(msg.secondsUntilReset);
        buf.writeInt(msg.entries.size());
        for (DailyEntry e : msg.entries) {
            buf.writeUtf(e.id);
            buf.writeUtf(e.title);
            buf.writeInt(e.progress);
            buf.writeInt(e.target);
            buf.writeBoolean(e.claimed);
        }
    }

    public static SyncDailyQuestsPacket decode(FriendlyByteBuf buf) {
        int pts = buf.readInt();
        int ms = buf.readInt();
        int sec = buf.readInt();
        int cnt = buf.readInt();
        List<DailyEntry> list = new ArrayList<>(cnt);
        for (int i = 0; i < cnt; i++) {
            list.add(new DailyEntry(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readBoolean()));
        }
        return new SyncDailyQuestsPacket(pts, ms, sec, list);
    }

    public static void handle(SyncDailyQuestsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientQuestData.DAILY_POINTS = msg.points;
            ClientQuestData.DAILY_MILESTONES_MASK = msg.milestonesMask;
            ClientQuestData.DAILY_RESET_SECONDS = msg.secondsUntilReset;
            ClientQuestData.DAILY_SYNCED_AT_MS = System.currentTimeMillis();
            ClientQuestData.DAILY_ENTRIES.clear();
            ClientQuestData.DAILY_ENTRIES.addAll(msg.entries);
        }));
        ctx.get().setPacketHandled(true);
    }
}
