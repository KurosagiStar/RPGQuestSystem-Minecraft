package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SyncQuestConfigPacket {
    private final byte[] compressedJson;

    public SyncQuestConfigPacket(String rawJson) {
        this.compressedJson = compress(rawJson != null ? rawJson : "[]");
    }

    private SyncQuestConfigPacket(byte[] compressedJson) {
        this.compressedJson = compressedJson;
    }

    public static void encode(SyncQuestConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeByteArray(msg.compressedJson);
    }

    public static SyncQuestConfigPacket decode(FriendlyByteBuf buf) {
        return new SyncQuestConfigPacket(buf.readByteArray());
    }

    public static void handle(SyncQuestConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            String json = decompress(msg.compressedJson);
            if (json != null && !json.isEmpty()) {
                QuestConfig.applyServerQuestsJson(json);
            }
        }));
        ctx.get().setPacketHandled(true);
    }

    private static byte[] compress(String str) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static String decompress(byte[] data) {
        if (data == null || data.length == 0) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzip = new GZIPInputStream(bais)) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
