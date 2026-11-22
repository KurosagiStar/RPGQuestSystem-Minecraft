package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class OpenAdminUiPacket {
    public final Map<String, double[]> locations;
    public final boolean openScreen;

    public OpenAdminUiPacket(Map<String, double[]> locations, boolean openScreen) {
        this.locations = locations != null ? new HashMap<>(locations) : new HashMap<>();
        this.openScreen = openScreen;
    }

    public static void encode(OpenAdminUiPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.openScreen);
        buf.writeInt(msg.locations.size());
        for (Map.Entry<String, double[]> e : msg.locations.entrySet()) {
            buf.writeUtf(e.getKey());
            double[] v = e.getValue();
            buf.writeDouble(v != null && v.length > 0 ? v[0] : 0.0);
            buf.writeDouble(v != null && v.length > 1 ? v[1] : 0.0);
            buf.writeDouble(v != null && v.length > 2 ? v[2] : 0.0);
        }
    }

    public static OpenAdminUiPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int count = buf.readInt();
        Map<String, double[]> map = new HashMap<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            String key = buf.readUtf();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            map.put(key, new double[]{x, y, z});
        }
        return new OpenAdminUiPacket(map, open);
    }

    public static void handle(OpenAdminUiPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openAdminUi(msg)));
        ctx.get().setPacketHandled(true);
    }

    public static void broadcastLocationsToOps(MinecraftServer server) {
        if (server == null) return;
        OpenAdminUiPacket pkt = new OpenAdminUiPacket(QuestConfig.NPC_LOCATIONS, false);
        for (ServerPlayer op : server.getPlayerList().getPlayers()) {
            if (op.hasPermissions(2)) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> op), pkt);
            }
        }
    }
}
