package com.rpgquestengine;

import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RPGQuestEngine.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncPlayerQuestsPacket.class,
                SyncPlayerQuestsPacket::encode,
                SyncPlayerQuestsPacket::decode,
                SyncPlayerQuestsPacket::handle);
        CHANNEL.registerMessage(id++, PlayQuestSoundPacket.class,
                PlayQuestSoundPacket::encode,
                PlayQuestSoundPacket::decode,
                PlayQuestSoundPacket::handle);
        CHANNEL.registerMessage(id++, SyncEntityTagsPacket.class,
                SyncEntityTagsPacket::encode,
                SyncEntityTagsPacket::decode,
                SyncEntityTagsPacket::handle);
    }
}

