package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShowBannerPacket {
    private final String header;
    private final String title;
    private final String customPrompt;
    private final int color;

    public ShowBannerPacket(String header, String title, String customPrompt, int color) {
        this.header = header != null ? header : "";
        this.title = title != null ? title : "";
        this.customPrompt = customPrompt != null ? customPrompt : "";
        this.color = color;
    }

    public static void encode(ShowBannerPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.header);
        buf.writeUtf(msg.title);
        buf.writeUtf(msg.customPrompt);
        buf.writeInt(msg.color);
    }

    public static ShowBannerPacket decode(FriendlyByteBuf buf) {
        return new ShowBannerPacket(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt());
    }

    public static void handle(ShowBannerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            QuestTitleBannerOverlay.showPartyBanner(msg.header, msg.title, new ItemStack(Items.EMERALD), msg.color, msg.customPrompt);
        }));
        ctx.get().setPacketHandled(true);
    }
}
