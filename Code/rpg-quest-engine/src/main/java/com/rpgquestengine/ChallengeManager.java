package com.rpgquestengine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

public class ChallengeManager {
    public static final long WITHER_COOLDOWN_MS = 6L * 3600L * 1000L; // 6 horas reales

    private static CompoundTag getData(ServerPlayer sp) {
        CompoundTag root = sp.getPersistentData();
        CompoundTag ch = root.getCompound("rqe_challenges");
        long now = System.currentTimeMillis();
        long nextReset = ch.getLong("wither_next_ms");
        if (nextReset > 0 && now >= nextReset) {
            ch.putBoolean("wither_done", false);
            ch.putLong("wither_next_ms", 0L);
            root.put("rqe_challenges", ch);
        }
        long wardenNext = ch.getLong("warden_next_ms");
        if (wardenNext > 0 && now >= wardenNext) {
            ch.putInt("warden_kills", 0);
            ch.putLong("warden_next_ms", 0L);
            root.put("rqe_challenges", ch);
        }
        return ch;
    }

    public static void onWitherKilled(ServerPlayer sp) {
        if (sp == null) return;
        for (ServerPlayer member : PartyManager.getOnlineMembers(sp)) {
            CompoundTag ch = getData(member);
            if (ch.getLong("wither_next_ms") > System.currentTimeMillis()) continue;
            if (ch.getBoolean("wither_done")) continue;

            ch.putBoolean("wither_done", true);
            member.getPersistentData().put("rqe_challenges", ch);
            member.sendSystemMessage(Component.literal("§6[Desafío Completado] §f¡Derrotaste al Wither! Reclama tu recompensa en §eJ → Desafíos§f."));
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> member), new PlayQuestSoundPacket(3));
            syncToPlayer(member);
        }
    }

    public static void onEndermanKilled(ServerPlayer sp) {
        if (sp == null) return;
        for (ServerPlayer member : PartyManager.getOnlineMembers(sp)) {
            CompoundTag ch = getData(member);
            if (ch.getBoolean("ender_claimed")) continue;
            int kills = ch.getInt("ender_kills");
            if (kills >= 10) continue;

            kills++;
            ch.putInt("ender_kills", kills);
            member.getPersistentData().put("rqe_challenges", ch);
            if (kills >= 10) {
                member.sendSystemMessage(Component.literal("§6[Desafío Completado] §f¡Derrotaste 10 Zombis! Reclama tu §aRing of Hungerless §fen §eJ → Desafíos§f."));
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> member), new PlayQuestSoundPacket(3));
            }
            syncToPlayer(member);
        }
    }

    public static void claimEnderman(ServerPlayer sp) {
        if (sp == null) return;
        CompoundTag ch = getData(sp);
        if (ch.getBoolean("ender_claimed") || ch.getInt("ender_kills") < 10) return;

        ch.putBoolean("ender_claimed", true);
        sp.getPersistentData().put("rqe_challenges", ch);

        giveItem(sp, "ringsofascension:ring_hungerless", 1, "");
        sp.sendSystemMessage(Component.literal("§a[Desafío Zombis Reclamado] §eRecibiste 1x Ring of Hungerless."));
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PlayQuestSoundPacket(4));
        syncToPlayer(sp);
    }

    public static void claimWither(ServerPlayer sp) {
        if (sp == null) return;
        CompoundTag ch = getData(sp);
        if (ch.getLong("wither_next_ms") > System.currentTimeMillis()) return;
        if (!ch.getBoolean("wither_done")) return;

        ch.putBoolean("wither_done", false);
        ch.putLong("wither_next_ms", System.currentTimeMillis() + WITHER_COOLDOWN_MS);
        sp.getPersistentData().put("rqe_challenges", ch);

        // Read rewards from config; fall back to legacy hardcode if not configured
        ChallengeDefinition def = QuestConfig.getChallengeById("wither");
        if (def != null && !def.rewardItems.isEmpty()) {
            for (QuestDefinition.RewardItem r : def.rewardItems) {
                giveItem(sp, r.id, r.count, r.nbt != null ? r.nbt : "");
            }
            if (def.xpLevels > 0) sp.giveExperienceLevels(def.xpLevels);

            StringBuilder msg = new StringBuilder("§a[Desafío Wither Reclamado] §e");
            for (int i = 0; i < def.rewardItems.size(); i++) {
                if (i > 0) msg.append(", ");
                msg.append(def.getTooltip(i));
            }
            if (def.xpLevels > 0) msg.append(", +").append(def.xpLevels).append(" Niveles");
            sp.sendSystemMessage(Component.literal(msg.toString()));
        } else {
            // Legacy fallback
            giveItem(sp, "cisco_mod:champion_coin", 15, "");
            giveItem(sp, "apotheosis:mythic_material", 3, "");
            giveItem(sp, "apotheosis:epic_material", 7, "");
            giveItem(sp, "apotheosis:sigil_of_socketing", 1, "");
            giveItem(sp, "apotheosis:gem", 1, "{affix_data:{rarity:\"apotheosis:epic\"},gem:\"apotheosis:core/warlord\"}");
            sp.giveExperienceLevels(15);
            sp.sendSystemMessage(Component.literal("§a[Desafío Wither Reclamado] §e+15 Monedas, 3x Godforged Pearl, 7x Epic Material, Sigil of Socketing, Gema Épica y 15 Niveles."));
        }

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PlayQuestSoundPacket(4));
        syncToPlayer(sp);
    }

    private static void giveItem(ServerPlayer sp, String itemId, int count, String nbt) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item != null) {
            ItemStack st = new ItemStack(item, count);
            if (nbt != null && !nbt.isEmpty()) {
                try {
                    st.setTag(TagParser.parseTag(nbt));
                } catch (Exception ignored) {}
            }
            if (!sp.getInventory().add(st)) {
                sp.drop(st, false);
            }
        }
    }

    public static void onWardenKilled(ServerPlayer sp) {
        if (sp == null) return;
        for (ServerPlayer member : PartyManager.getOnlineMembers(sp)) {
            CompoundTag ch = getData(member);
            if (ch.getLong("warden_next_ms") > System.currentTimeMillis()) continue;
            int kills = ch.getInt("warden_kills");
            if (kills >= 3) continue;

            kills++;
            ch.putInt("warden_kills", kills);
            member.getPersistentData().put("rqe_challenges", ch);
            if (kills >= 3) {
                member.sendSystemMessage(Component.literal("§6[Desafío Completado] §f¡Derrotaste 3 Wardens! Reclama tu recompensa en §eJ → Desafíos§f."));
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> member), new PlayQuestSoundPacket(3));
            }
            syncToPlayer(member);
        }
    }

    public static void claimWarden(ServerPlayer sp) {
        if (sp == null) return;
        CompoundTag ch = getData(sp);
        if (ch.getLong("warden_next_ms") > System.currentTimeMillis()) return;
        if (ch.getInt("warden_kills") < 3) return;

        ch.putInt("warden_kills", 0);
        ch.putLong("warden_next_ms", System.currentTimeMillis() + WITHER_COOLDOWN_MS);
        sp.getPersistentData().put("rqe_challenges", ch);

        ChallengeDefinition def = QuestConfig.getChallengeById("warden");
        if (def != null && !def.rewardItems.isEmpty()) {
            for (QuestDefinition.RewardItem r : def.rewardItems) {
                giveItem(sp, r.id, r.count, r.nbt != null ? r.nbt : "");
            }
            if (def.xpLevels > 0) sp.giveExperienceLevels(def.xpLevels);

            StringBuilder msg = new StringBuilder("§a[Desafío Warden Reclamado] §e");
            for (int i = 0; i < def.rewardItems.size(); i++) {
                if (i > 0) msg.append(", ");
                msg.append(def.getTooltip(i));
            }
            if (def.xpLevels > 0) msg.append(", +").append(def.xpLevels).append(" Niveles");
            sp.sendSystemMessage(Component.literal(msg.toString()));
        } else {
            giveItem(sp, "cisco_mod:champion_loot_bag", 6, "");
            giveItem(sp, "apotheotic_additions:esoteric_material", 9, "");
            sp.giveExperienceLevels(30);
            sp.sendSystemMessage(Component.literal("§a[Desafío Warden Reclamado] §e6x Champion Loot Bag, 9x Galactic Core y +30 Niveles."));
        }

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PlayQuestSoundPacket(4));
        syncToPlayer(sp);
    }

    public static void syncToPlayer(ServerPlayer sp) {
        if (sp == null) return;
        CompoundTag ch = getData(sp);
        boolean done = ch.getBoolean("wither_done");
        long nextMs = ch.getLong("wither_next_ms");
        int remSec = nextMs > System.currentTimeMillis()
                ? (int) ((nextMs - System.currentTimeMillis()) / 1000L)
                : 0;
        int enderKills = ch.getInt("ender_kills");
        boolean enderClaimed = ch.getBoolean("ender_claimed");
        int wardenKills = ch.getInt("warden_kills");
        long wardenNext = ch.getLong("warden_next_ms");
        int wardenRemSec = wardenNext > System.currentTimeMillis()
                ? (int) ((wardenNext - System.currentTimeMillis()) / 1000L)
                : 0;
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new SyncChallengesPacket(done, remSec, enderKills, enderClaimed, wardenKills, wardenRemSec));
    }
}
