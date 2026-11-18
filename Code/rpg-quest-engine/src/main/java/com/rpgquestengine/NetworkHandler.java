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
        CHANNEL.registerMessage(id++, AbandonQuestPacket.class,
                AbandonQuestPacket::encode,
                AbandonQuestPacket::decode,
                AbandonQuestPacket::handle);
        CHANNEL.registerMessage(id++, AdminNpcActionPacket.class,
                AdminNpcActionPacket::encode,
                AdminNpcActionPacket::decode,
                AdminNpcActionPacket::handle);
        CHANNEL.registerMessage(id++, OpenAdminUiPacket.class,
                OpenAdminUiPacket::encode,
                OpenAdminUiPacket::decode,
                OpenAdminUiPacket::handle);
        CHANNEL.registerMessage(id++, OpenNpcDialogPacket.class,
                OpenNpcDialogPacket::encode,
                OpenNpcDialogPacket::decode,
                OpenNpcDialogPacket::handle);
        CHANNEL.registerMessage(id++, NpcDialogActionPacket.class,
                NpcDialogActionPacket::encode,
                NpcDialogActionPacket::decode,
                NpcDialogActionPacket::handle);
        CHANNEL.registerMessage(id++, SyncDailyQuestsPacket.class,
                SyncDailyQuestsPacket::encode,
                SyncDailyQuestsPacket::decode,
                SyncDailyQuestsPacket::handle);
        CHANNEL.registerMessage(id++, DailyQuestActionPacket.class,
                DailyQuestActionPacket::encode,
                DailyQuestActionPacket::decode,
                DailyQuestActionPacket::handle);
        CHANNEL.registerMessage(id++, SyncChallengesPacket.class,
                SyncChallengesPacket::encode,
                SyncChallengesPacket::decode,
                SyncChallengesPacket::handle);
        CHANNEL.registerMessage(id++, ChallengeActionPacket.class,
                ChallengeActionPacket::encode,
                ChallengeActionPacket::decode,
                ChallengeActionPacket::handle);
        CHANNEL.registerMessage(id++, SyncPartyPacket.class,
                SyncPartyPacket::encode,
                SyncPartyPacket::decode,
                SyncPartyPacket::handle);
        CHANNEL.registerMessage(id++, PartyActionPacket.class,
                PartyActionPacket::encode,
                PartyActionPacket::decode,
                PartyActionPacket::handle);
        CHANNEL.registerMessage(id++, ShowBannerPacket.class,
                ShowBannerPacket::encode,
                ShowBannerPacket::decode,
                ShowBannerPacket::handle);
    }
}

