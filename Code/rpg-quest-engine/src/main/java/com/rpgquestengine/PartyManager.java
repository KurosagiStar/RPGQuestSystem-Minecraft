package com.rpgquestengine;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

public class PartyManager {
    public static class Party {
        public UUID leader;
        public final List<UUID> members = new ArrayList<>();

        public Party(UUID leader) {
            this.leader = leader;
            this.members.add(leader);
        }
    }

    private static final Map<UUID, Party> PLAYER_TO_PARTY = new HashMap<>();
    private static final Map<UUID, UUID> PENDING_INVITES = new HashMap<>(); // targetUuid -> leaderUuid

    public static Party getParty(UUID playerUuid) {
        return PLAYER_TO_PARTY.get(playerUuid);
    }

    public static List<ServerPlayer> getOnlineMembers(ServerPlayer sp) {
        if (sp == null || sp.getServer() == null) return Collections.emptyList();
        Party p = PLAYER_TO_PARTY.get(sp.getUUID());
        if (p == null) return Collections.singletonList(sp);
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID uid : p.members) {
            ServerPlayer m = sp.getServer().getPlayerList().getPlayer(uid);
            if (m != null) list.add(m);
        }
        return list;
    }

    public static void createParty(ServerPlayer sp) {
        if (sp == null || PLAYER_TO_PARTY.containsKey(sp.getUUID())) return;
        Party p = new Party(sp.getUUID());
        PLAYER_TO_PARTY.put(sp.getUUID(), p);
        sp.sendSystemMessage(Component.literal("§6[Grupo] §fHas creado un grupo. Pulsa §eG §fpara invitar amigos."));
        syncParty(p, sp.getServer());
    }

    public static void invitePlayer(ServerPlayer leader, String targetName) {
        if (leader == null || leader.getServer() == null || targetName == null || targetName.isEmpty()) return;
        Party p = PLAYER_TO_PARTY.get(leader.getUUID());
        if (p == null) {
            createParty(leader);
            p = PLAYER_TO_PARTY.get(leader.getUUID());
        }
        if (!p.leader.equals(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§c[Grupo] Solo el líder puede invitar."));
            return;
        }
        ServerPlayer target = leader.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null || target.getUUID().equals(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§c[Grupo] Jugador no encontrado."));
            return;
        }
        if (PLAYER_TO_PARTY.containsKey(target.getUUID())) {
            leader.sendSystemMessage(Component.literal("§c[Grupo] " + target.getScoreboardName() + " ya está en un grupo."));
            return;
        }
        PENDING_INVITES.put(target.getUUID(), leader.getUUID());
        leader.sendSystemMessage(Component.literal("§a[Grupo] §fInvitación enviada a §e" + target.getScoreboardName()));
        target.sendSystemMessage(Component.literal("§6[Grupo] §e" + leader.getScoreboardName() + " §fte invitó a su grupo. Pulsa §aG §fpara aceptar."));
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new PlayQuestSoundPacket(1));
        syncToPlayer(target);
    }

    public static void acceptInvite(ServerPlayer sp) {
        if (sp == null || sp.getServer() == null) return;
        UUID leaderId = PENDING_INVITES.remove(sp.getUUID());
        if (leaderId == null) return;
        if (PLAYER_TO_PARTY.containsKey(sp.getUUID())) return;

        Party p = PLAYER_TO_PARTY.get(leaderId);
        if (p == null) {
            sp.sendSystemMessage(Component.literal("§c[Grupo] El grupo ya no existe."));
            syncToPlayer(sp);
            return;
        }
        p.members.add(sp.getUUID());
        PLAYER_TO_PARTY.put(sp.getUUID(), p);
        for (ServerPlayer m : getOnlineMembers(sp)) {
            m.sendSystemMessage(Component.literal("§a[Grupo] §e" + sp.getScoreboardName() + " §fse unió al grupo."));
        }
        syncMainQuestsWithParty(sp);
        syncParty(p, sp.getServer());
    }

    public static void declineInvite(ServerPlayer sp) {
        if (sp == null) return;
        PENDING_INVITES.remove(sp.getUUID());
        syncToPlayer(sp);
    }

    public static void leaveParty(ServerPlayer sp) {
        if (sp == null || sp.getServer() == null) return;
        Party p = PLAYER_TO_PARTY.remove(sp.getUUID());
        if (p == null) return;
        p.members.remove(sp.getUUID());
        sp.sendSystemMessage(Component.literal("§e[Grupo] §fSaliste del grupo."));
        if (p.members.isEmpty()) {
            syncToPlayer(sp);
            return;
        }
        if (p.leader.equals(sp.getUUID())) {
            p.leader = p.members.get(0);
        }
        for (UUID uid : p.members) {
            ServerPlayer m = sp.getServer().getPlayerList().getPlayer(uid);
            if (m != null) {
                m.sendSystemMessage(Component.literal("§e[Grupo] §f" + sp.getScoreboardName() + " salió del grupo."));
            }
        }
        syncToPlayer(sp);
        syncParty(p, sp.getServer());
    }

    public static void kickMember(ServerPlayer leader, String targetUuidStr) {
        if (leader == null || leader.getServer() == null) return;
        Party p = PLAYER_TO_PARTY.get(leader.getUUID());
        if (p == null || !p.leader.equals(leader.getUUID())) return;
        try {
            UUID targetId = UUID.fromString(targetUuidStr);
            if (targetId.equals(leader.getUUID()) || !p.members.contains(targetId)) return;
            p.members.remove(targetId);
            PLAYER_TO_PARTY.remove(targetId);
            ServerPlayer kicked = leader.getServer().getPlayerList().getPlayer(targetId);
            if (kicked != null) {
                kicked.sendSystemMessage(Component.literal("§c[Grupo] Fuiste expulsado del grupo."));
                syncToPlayer(kicked);
            }
            syncParty(p, leader.getServer());
        } catch (Exception ignored) {}
    }

    public static void syncMainQuestsWithParty(ServerPlayer triggerPlayer) {
        List<ServerPlayer> members = getOnlineMembers(triggerPlayer);
        if (members.size() <= 1) return;

        Set<String> allCompletedMain = new HashSet<>();
        Set<String> allActiveMain = new HashSet<>();

        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (!"MAIN".equalsIgnoreCase(q.category)) continue;
            for (ServerPlayer m : members) {
                Set<String> tags = m.getTags();
                if (q.completedTag != null && tags.contains(q.completedTag)) {
                    allCompletedMain.add(q.id);
                } else if (q.activeTag != null && tags.contains(q.activeTag)) {
                    allActiveMain.add(q.id);
                }
            }
        }

        for (ServerPlayer m : members) {
            Set<String> tags = m.getTags();
            for (String qid : allCompletedMain) {
                QuestDefinition q = QuestConfig.getQuestById(qid);
                if (q == null) continue;
                if (q.activeTag != null) m.removeTag(q.activeTag);
                if (q.doneTag != null) m.removeTag(q.doneTag);
                if (q.completedTag != null && !tags.contains(q.completedTag)) {
                    m.addTag(q.completedTag);
                    addPendingReward(m, q.id);
                }
            }
            for (String qid : allActiveMain) {
                if (allCompletedMain.contains(qid)) continue;
                QuestDefinition q = QuestConfig.getQuestById(qid);
                if (q == null) continue;
                if (q.activeTag != null && !tags.contains(q.activeTag)) {
                    m.addTag(q.activeTag);
                }
            }
            RPGQuestEngine.syncPlayerAfterDialog(m);
            syncToPlayer(m);
            m.sendSystemMessage(Component.literal("§b[Grupo] §fMisiones principales y NPCs sincronizados."));
        }
    }

    public static List<String> getPendingRewards(ServerPlayer sp) {
        List<String> out = new ArrayList<>();
        if (sp == null) return out;
        var list = sp.getPersistentData().getList("rqe_pending_rewards", 8);
        for (int i = 0; i < list.size(); i++) {
            String s = list.getString(i);
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    private static void savePendingRewards(ServerPlayer sp, List<String> ids) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (String id : ids) {
            list.add(net.minecraft.nbt.StringTag.valueOf(id));
        }
        sp.getPersistentData().put("rqe_pending_rewards", list);
    }

    public static void addPendingReward(ServerPlayer sp, String questId) {
        if (sp == null || questId == null || questId.isEmpty()) return;
        List<String> list = getPendingRewards(sp);
        if (!list.contains(questId)) {
            list.add(questId);
            savePendingRewards(sp, list);
        }
    }

    public static void claimPendingReward(ServerPlayer sp, String questId) {
        if (sp == null || questId == null || questId.isEmpty()) return;
        List<String> list = getPendingRewards(sp);
        if (!list.remove(questId)) return;
        savePendingRewards(sp, list);
        QuestDefinition q = QuestConfig.getQuestById(questId);
        if (q != null) {
            NpcDialogActionPacket.grantAllQuestRewards(sp, q, 0);
            sp.sendSystemMessage(Component.literal("§a[Recompensa de Grupo] §fReclamaste: §e" + q.title));
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PlayQuestSoundPacket(4));
        }
        syncToPlayer(sp);
    }

    public static void claimAllPendingRewards(ServerPlayer sp) {
        if (sp == null) return;
        List<String> list = getPendingRewards(sp);
        if (list.isEmpty()) return;
        savePendingRewards(sp, new ArrayList<>());
        int count = 0;
        for (String qid : list) {
            QuestDefinition q = QuestConfig.getQuestById(qid);
            if (q != null) {
                NpcDialogActionPacket.grantAllQuestRewards(sp, q, 0);
                count++;
            }
        }
        if (count > 0) {
            sp.sendSystemMessage(Component.literal("§a[Recompensas de Grupo] §fReclamadas §e" + count + " §frecompensas acumuladas."));
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PlayQuestSoundPacket(4));
        }
        syncToPlayer(sp);
    }

    public static void onNpcQuestAccepted(ServerPlayer actor, QuestDefinition q) {
        if (actor == null || q == null) return;
        if (!"MAIN".equalsIgnoreCase(q.category) && !"CONTRACT".equalsIgnoreCase(q.category)) return;
        for (ServerPlayer m : getOnlineMembers(actor)) {
            if (m == actor) continue;
            if (q.completedTag != null && m.getTags().contains(q.completedTag)) continue;
            if (q.activeTag != null && !m.getTags().contains(q.activeTag)) {
                m.addTag(q.activeTag);
                m.sendSystemMessage(Component.literal("§6[Grupo] §e" + actor.getScoreboardName() + " §faceptó: §e" + q.title));
                RPGQuestEngine.syncPlayerAfterDialog(m);
            }
        }
    }

    public static void onNpcQuestCompleted(ServerPlayer actor, QuestDefinition q) {
        if (actor == null || q == null || !"MAIN".equalsIgnoreCase(q.category)) return;
        for (ServerPlayer m : getOnlineMembers(actor)) {
            if (m == actor) continue;
            if (q.activeTag != null) m.removeTag(q.activeTag);
            if (q.doneTag != null) m.removeTag(q.doneTag);
            if (q.completedTag != null && !m.getTags().contains(q.completedTag)) {
                m.addTag(q.completedTag);
                addPendingReward(m, q.id);
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> m),
                        new ShowBannerPacket(
                                actor.getScoreboardName() + " COMPLETÓ UNA MISIÓN",
                                q.title,
                                "§a[Presiona G para reclamar recompensas pendientes]",
                                0xFFFFCC00));
                m.sendSystemMessage(Component.literal("§a[Grupo] §e" + actor.getScoreboardName() + " §fcompletó: §e" + q.title + " §7(Pulsa G para reclamar)"));
                RPGQuestEngine.syncPlayerAfterDialog(m);
                syncToPlayer(m);
            }
        }
    }

    public static void syncParty(Party p, MinecraftServer server) {
        if (p == null || server == null) return;
        for (UUID uid : p.members) {
            ServerPlayer m = server.getPlayerList().getPlayer(uid);
            if (m != null) syncToPlayer(m);
        }
    }

    public static void syncToPlayer(ServerPlayer sp) {
        if (sp == null || sp.getServer() == null) return;
        Party p = PLAYER_TO_PARTY.get(sp.getUUID());
        boolean inParty = p != null;
        boolean isLeader = p != null && p.leader.equals(sp.getUUID());

        String inviteFrom = "";
        UUID invLeader = PENDING_INVITES.get(sp.getUUID());
        if (invLeader != null) {
            ServerPlayer lp = sp.getServer().getPlayerList().getPlayer(invLeader);
            if (lp != null) inviteFrom = lp.getScoreboardName();
        }

        List<SyncPartyPacket.MemberEntry> members = new ArrayList<>();
        if (p != null) {
            for (UUID uid : p.members) {
                ServerPlayer mp = sp.getServer().getPlayerList().getPlayer(uid);
                String name = mp != null ? mp.getScoreboardName() : uid.toString().substring(0, 8);
                members.add(new SyncPartyPacket.MemberEntry(uid.toString(), name, p.leader.equals(uid), mp != null));
            }
        }

        List<String> onlineOthers = new ArrayList<>();
        for (ServerPlayer other : sp.getServer().getPlayerList().getPlayers()) {
            if (other.getUUID().equals(sp.getUUID())) continue;
            if (!PLAYER_TO_PARTY.containsKey(other.getUUID())) {
                onlineOthers.add(other.getScoreboardName());
            }
        }

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new SyncPartyPacket(inParty, isLeader, inviteFrom, members, onlineOthers, getPendingRewards(sp)));
    }
}
