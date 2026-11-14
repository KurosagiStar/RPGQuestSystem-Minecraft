package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenAdminUiPacket {
    public static void encode(OpenAdminUiPacket msg, FriendlyByteBuf buf) {}

    public static OpenAdminUiPacket decode(FriendlyByteBuf buf) {
        return new OpenAdminUiPacket();
    }

    public static void handle(OpenAdminUiPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft.getInstance().setScreen(new AdminNpcManagerScreen());
        }));
        ctx.get().setPacketHandled(true);
    }
}
