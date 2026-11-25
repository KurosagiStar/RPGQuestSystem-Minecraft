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
        public final Map<UUID, String> memberNames = new HashMap<>();
        public final Set<String> completedMainIds = new HashSet<>();
        public final Set<String> activeMainIds = new HashSet<>();
        public final Map<UUID, List<String>> offlinePendingRewards = new HashMap<>();

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

    private static void runFtbTeamCmd(ServerPlayer actor, String cmd) {
        if (actor == null || actor.getServer() == null) return;
        try {
            actor.getServer().getCommands().performPrefixedCommand(
                    actor.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    cmd);
        } catch (Exception ignored) {}
    }

    public static void saveParties() {
        try {
            java.io.File file = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("rpg_parties.json").toFile();
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            Set<Party> seen = new HashSet<>();
            for (Party p : PLAYER_TO_PARTY.values()) {
                if (!seen.add(p) || p.members.isEmpty()) continue;
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("leader", p.leader.toString());
                com.google.gson.JsonArray mems = new com.google.gson.JsonArray();
                for (UUID m : p.members) mems.add(m.toString());
                obj.add("members", mems);

                com.google.gson.JsonArray compArr = new com.google.gson.JsonArray();
                for (String cid : p.completedMainIds) compArr.add(cid);
                obj.add("completedMain", compArr);

                com.google.gson.JsonArray actArr = new com.google.gson.JsonArray();
                for (String aid : p.activeMainIds) actArr.add(aid);
                obj.add("activeMain", actArr);

                com.google.gson.JsonObject offObj = new com.google.gson.JsonObject();
                for (Map.Entry<UUID, List<String>> en : p.offlinePendingRewards.entrySet()) {
                    if (en.getValue() == null || en.getValue().isEmpty()) continue;
                    com.google.gson.JsonArray qArr = new com.google.gson.JsonArray();
                    for (String qid : en.getValue()) qArr.add(qid);
                    offObj.add(en.getKey().toString(), qArr);
                }
                com.google.gson.JsonObject namesObj = new com.google.gson.JsonObject();
                for (Map.Entry<UUID, String> ne : p.memberNames.entrySet()) {
                    if (ne.getValue() != null && !ne.getValue().isEmpty()) {
                        namesObj.addProperty(ne.getKey().toString(), ne.getValue());
                    }
                }
                obj.add("memberNames", namesObj);
                obj.add("offlinePending", offObj);
                arr.add(obj);
            }
            try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr, w);
            }
        } catch (Exception ignored) {}
    }

    public static void loadParties() {
        try {
            java.io.File file = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("rpg_parties.json").toFile();
            if (!file.exists()) return;
            PLAYER_TO_PARTY.clear();
            try (java.io.InputStreamReader r = new java.io.InputStreamReader(new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseReader(r).getAsJsonArray();
                for (com.google.gson.JsonElement el : arr) {
                    com.google.gson.JsonObject obj = el.getAsJsonObject();
                    UUID leader = UUID.fromString(obj.get("leader").getAsString());
                    Party p = new Party(leader);
                    p.members.clear();
                    for (com.google.gson.JsonElement mEl : obj.getAsJsonArray("members")) {
                        UUID m = UUID.fromString(mEl.getAsString());
                        p.members.add(m);
                        PLAYER_TO_PARTY.put(m, p);
                    }
                    if (obj.has("completedMain") && obj.get("completedMain").isJsonArray()) {
                        for (com.google.gson.JsonElement cEl : obj.getAsJsonArray("completedMain")) {
                            p.completedMainIds.add(cEl.getAsString());
                        }
                    }
                    if (obj.has("activeMain") && obj.get("activeMain").isJsonArray()) {
                        for (com.google.gson.JsonElement aEl : obj.getAsJsonArray("activeMain")) {
                            p.activeMainIds.add(aEl.getAsString());
                        }
                    }
                    if (obj.has("memberNames") && obj.get("memberNames").isJsonObject()) {
                        com.google.gson.JsonObject namesObj = obj.getAsJsonObject("memberNames");
                        for (String uidKey : namesObj.keySet()) {
                            try {
                                p.memberNames.put(UUID.fromString(uidKey), namesObj.get(uidKey).getAsString());
                            } catch (Exception ignored) {}
                        }
                    }
                    if (obj.has("offlinePending") && obj.get("offlinePending").isJsonObject()) {
                        com.google.gson.JsonObject offObj = obj.getAsJsonObject("offlinePending");
                        for (String uidKey : offObj.keySet()) {
                            try {
                                UUID uid = UUID.fromString(uidKey);
                                List<String> qList = new ArrayList<>();
                                for (com.google.gson.JsonElement qEl : offObj.getAsJsonArray(uidKey)) {
                                    String qid = qEl.getAsString();
                                    if (!qid.isEmpty() && !qList.contains(qid)) qList.add(qid);
                                }
                                if (!qList.isEmpty()) p.offlinePendingRewards.put(uid, qList);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
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
        saveParties();
        runFtbTeamCmd(sp, "ftbteams party create \"Grupo\"");
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
        saveParties();
        ServerPlayer leader = sp.getServer() != null ? sp.getServer().getPlayerList().getPlayer(leaderId) : null;
        if (leader != null) {
            runFtbTeamCmd(leader, "ftbteams party invite " + sp.getScoreboardName());
            runFtbTeamCmd(sp, "ftbteams party join " + leader.getScoreboardName());
        }
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
            saveParties();
            runFtbTeamCmd(sp, "ftbteams party leave");
            syncToPlayer(sp);
            return;
        }
        if (p.leader.equals(sp.getUUID())) {
            p.leader = p.members.get(0);
        }
        saveParties();
        runFtbTeamCmd(sp, "ftbteams party leave");
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
        if (triggerPlayer == null) return;
        Party p = PLAYER_TO_PARTY.get(triggerPlayer.getUUID());
        if (p == null) return;

        List<ServerPlayer> members = getOnlineMembers(triggerPlayer);
        boolean dirtyParty = false;

        // Entregar a la cola de 'G' (PendingRewards) los Contratos/recompensas acumulados mientras estuvo desconectado
        List<String> offlineQueued = p.offlinePendingRewards.remove(triggerPlayer.getUUID());
        if (offlineQueued != null && !offlineQueued.isEmpty()) {
            dirtyParty = true;
            int queuedCount = 0;
            for (String qid : offlineQueued) {
                QuestDefinition q = QuestConfig.getQuestById(qid);
                if (q == null) continue;
                boolean isRep = "REPEATABLE".equalsIgnoreCase(q.category);
                if (!isRep && q.completedTag != null && RPGQuestEngine.hasQuestTag(triggerPlayer, q.completedTag)) continue;
                if (q.activeTag != null) RPGQuestEngine.removeQuestTag(triggerPlayer, q.activeTag);
                if (q.doneTag != null) RPGQuestEngine.removeQuestTag(triggerPlayer, q.doneTag);
                RPGQuestEngine.removeQuestTag(triggerPlayer, "rqe_party_obj_done_" + q.id);
                if (!isRep && q.completedTag != null) {
                    RPGQuestEngine.addQuestTag(triggerPlayer, q.completedTag);
                    RPGQuestEngine.addQuestTag(triggerPlayer, "rqe_rewarded_" + q.id);
                } else if (isRep) {
                    RPGQuestEngine.resetQuestStageScores(triggerPlayer, q);
                    RPGQuestEngine.onRepeatableCompleted(triggerPlayer, q);
                }
                addPendingReward(triggerPlayer, q.id);
                queuedCount++;
            }
            if (queuedCount > 0) {
                triggerPlayer.sendSystemMessage(Component.literal("§a[Grupo] §fTienes §e" + queuedCount
                        + " §frecompensa(s) completadas en tu ausencia. Pulsa §eG §fpara cobrarlas."));
            }
        }

        Set<String> allCompletedMain = new HashSet<>(p.completedMainIds);
        Set<String> allActiveMain = new HashSet<>(p.activeMainIds);

        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (!"MAIN".equalsIgnoreCase(q.category)) continue;
            for (ServerPlayer m : members) {
                Set<String> tags = RPGQuestEngine.getAllQuestTags(m);
                if (q.completedTag != null && tags.contains(q.completedTag)) {
                    if (p.completedMainIds.add(q.id)) dirtyParty = true;
                    if (p.activeMainIds.remove(q.id)) dirtyParty = true;
                    allCompletedMain.add(q.id);
                } else if (q.activeTag != null && tags.contains(q.activeTag)) {
                    if (!p.completedMainIds.contains(q.id) && p.activeMainIds.add(q.id)) dirtyParty = true;
                    allActiveMain.add(q.id);
                }
            }
        }
        allActiveMain.removeAll(allCompletedMain);
        if (dirtyParty) saveParties();

        if (allCompletedMain.isEmpty() && allActiveMain.isEmpty() && members.size() <= 1) {
            syncToPlayer(triggerPlayer);
            return;
        }

        Set<String> prereqOfCompleted = new HashSet<>();
        for (String qid : allCompletedMain) {
            QuestDefinition cq = QuestConfig.getQuestById(qid);
            if (cq != null && cq.prerequisiteQuestId != null && !cq.prerequisiteQuestId.isEmpty()) {
                prereqOfCompleted.add(cq.prerequisiteQuestId);
            }
        }

        for (ServerPlayer m : members) {
            Set<String> tags = RPGQuestEngine.getAllQuestTags(m);
            for (String qid : allCompletedMain) {
                QuestDefinition q = QuestConfig.getQuestById(qid);
                if (q == null || (q.completedTag != null && tags.contains(q.completedTag))) continue;
                boolean wasWorkingOnIt = (q.activeTag != null && tags.contains(q.activeTag))
                        || tags.contains("rqe_party_obj_done_" + q.id);
                if (wasWorkingOnIt && !prereqOfCompleted.contains(qid)) {
                    if (q.activeTag != null) RPGQuestEngine.addQuestTag(m, q.activeTag);
                    RPGQuestEngine.addQuestTag(m, "rqe_party_obj_done_" + q.id);
                    if (q.doneTag != null) RPGQuestEngine.addQuestTag(m, q.doneTag);
                } else {
                    if (q.activeTag != null) RPGQuestEngine.removeQuestTag(m, q.activeTag);
                    if (q.doneTag != null) RPGQuestEngine.removeQuestTag(m, q.doneTag);
                    if (q.completedTag != null) {
                        RPGQuestEngine.addQuestTag(m, q.completedTag);
                        RPGQuestEngine.addQuestTag(m, "rqe_rewarded_" + q.id);
                        addPendingReward(m, q.id);
                    }
                }
            }
            for (String qid : allActiveMain) {
                if (allCompletedMain.contains(qid)) continue;
                QuestDefinition q = QuestConfig.getQuestById(qid);
                if (q == null) continue;
                if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                    QuestDefinition pre = QuestConfig.getQuestById(q.prerequisiteQuestId);
                    if (pre != null && pre.completedTag != null && !RPGQuestEngine.hasQuestTag(m, pre.completedTag)) {
                        continue;
                    }
                }
                if (q.activeTag != null && !tags.contains(q.activeTag)) {
                    RPGQuestEngine.addQuestTag(m, q.activeTag);
                }
            }
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> m), new OpenAdminUiPacket(QuestConfig.NPC_LOCATIONS, false));
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
        Party p = PLAYER_TO_PARTY.get(actor.getUUID());
        if (p == null) return;
        if ("MAIN".equalsIgnoreCase(q.category) && !p.completedMainIds.contains(q.id)) {
            if (p.activeMainIds.add(q.id)) saveParties();
        }
        boolean isRep = "REPEATABLE".equalsIgnoreCase(q.category);
        Set<String> actorTags = RPGQuestEngine.getAllQuestTags(actor);
        for (ServerPlayer m : getOnlineMembers(actor)) {
            if (m == actor) continue;
            if (!isRep && q.completedTag != null && RPGQuestEngine.hasQuestTag(m, q.completedTag)) continue;
            if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                QuestDefinition pre = QuestConfig.getQuestById(q.prerequisiteQuestId);
                if (pre != null && pre.completedTag != null && !RPGQuestEngine.hasQuestTag(m, pre.completedTag)) {
                    continue;
                }
            }
            if (q.activeTag != null && !RPGQuestEngine.hasQuestTag(m, q.activeTag)) {
                RPGQuestEngine.resetQuestStageScores(m, q);
                if (q.doneTag != null) RPGQuestEngine.removeQuestTag(m, q.doneTag);
                RPGQuestEngine.addQuestTag(m, q.activeTag);
                for (String t : actorTags) {
                    if (t.startsWith("rqe_pos_" + q.id + "_") || t.startsWith("rqe_npc_" + q.id + "_")) {
                        RPGQuestEngine.addQuestTag(m, t);
                    }
                }
                m.sendSystemMessage(Component.literal("§6[Grupo] §e" + actor.getScoreboardName() + " §faceptó: §e" + q.title));
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> m), new OpenAdminUiPacket(QuestConfig.NPC_LOCATIONS, false));
                RPGQuestEngine.syncPlayerAfterDialog(m);
            }
        }
    }

    public static void syncUnlockedActiveMainQuests(ServerPlayer member) {
        if (member == null) return;
        List<ServerPlayer> online = getOnlineMembers(member);
        if (online.size() <= 1) return;
        Set<String> mTags = RPGQuestEngine.getAllQuestTags(member);
        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (!"MAIN".equalsIgnoreCase(q.category)) continue;
            if (q.completedTag != null && mTags.contains(q.completedTag)) continue;
            if (q.activeTag != null && mTags.contains(q.activeTag)) continue;
            if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                QuestDefinition pre = QuestConfig.getQuestById(q.prerequisiteQuestId);
                if (pre != null && pre.completedTag != null && !mTags.contains(pre.completedTag)) continue;
            }
            boolean mateHasActiveOrDone = false;
            for (ServerPlayer other : online) {
                if (other == member) continue;
                if (q.activeTag != null && RPGQuestEngine.hasQuestTag(other, q.activeTag)) {
                    mateHasActiveOrDone = true;
                    break;
                }
            }
            if (mateHasActiveOrDone && q.activeTag != null) {
                RPGQuestEngine.resetQuestStageScores(member, q);
                RPGQuestEngine.addQuestTag(member, q.activeTag);
                mTags.add(q.activeTag);
            }
        }
    }

    public static void onNpcQuestCompleted(ServerPlayer actor, QuestDefinition q, int choiceIndex) {
        if (actor == null || q == null || actor.getServer() == null) return;
        Party p = PLAYER_TO_PARTY.get(actor.getUUID());
        if (p == null) return;

        if (!"MAIN".equalsIgnoreCase(q.category)) {
            boolean isRep = "REPEATABLE".equalsIgnoreCase(q.category);
            boolean queuedOffline = false;
            for (UUID uid : p.members) {
                if (uid.equals(actor.getUUID())) continue;
                ServerPlayer m = actor.getServer().getPlayerList().getPlayer(uid);
                if (m != null) {
                    if (!isRep && q.completedTag != null && RPGQuestEngine.hasQuestTag(m, q.completedTag)) continue;
                    if (q.activeTag != null) RPGQuestEngine.removeQuestTag(m, q.activeTag);
                    if (q.doneTag != null) RPGQuestEngine.removeQuestTag(m, q.doneTag);
                    RPGQuestEngine.removeQuestTag(m, "rqe_party_obj_done_" + q.id);
                    if (!isRep && q.completedTag != null) {
                        RPGQuestEngine.addQuestTag(m, q.completedTag);
                        RPGQuestEngine.addQuestTag(m, "rqe_rewarded_" + q.id);
                    } else if (isRep) {
                        RPGQuestEngine.resetQuestStageScores(m, q);
                        RPGQuestEngine.onRepeatableCompleted(m, q);
                    }

                    NpcDialogActionPacket.grantAllQuestRewards(m, q, choiceIndex);
                    m.getInventory().setChanged();
                    m.inventoryMenu.broadcastChanges();
                    m.containerMenu.broadcastChanges();
                    m.inventoryMenu.sendAllDataToRemote();

                    String xpTxt = q.rewardXp > 0 ? " §e(+" + q.rewardXp + " XP)" : "";
                    String lvlTxt = q.rewardXpLevels > 0 ? " §b(+" + q.rewardXpLevels + " Niveles)" : "";
                    m.sendSystemMessage(Component.literal("§a[Grupo - Misión Completada] §e" + actor.getScoreboardName()
                            + " §fentregó §e" + q.title + "§f: recibiste tu recompensa" + xpTxt + lvlTxt));
                    DailyQuestManager.onTaskProgress(m, "NPC_TURNIN", 1);
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> m), new PlayQuestSoundPacket(4));
                    RPGQuestEngine.syncPlayerAfterDialog(m);
                } else {
                    List<String> offList = p.offlinePendingRewards.computeIfAbsent(uid, k -> new ArrayList<>());
                    if (!offList.contains(q.id)) {
                        offList.add(q.id);
                        queuedOffline = true;
                    }
                }
            }
            if (queuedOffline) saveParties();
            return;
        }

        if ("MAIN".equalsIgnoreCase(q.category)) {
            p.activeMainIds.remove(q.id);
            p.completedMainIds.add(q.id);
            saveParties();
            for (ServerPlayer m : getOnlineMembers(actor)) {
                if (m == actor) continue;
                if (q.completedTag != null && RPGQuestEngine.hasQuestTag(m, q.completedTag)) continue;
                if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                    QuestDefinition pre = QuestConfig.getQuestById(q.prerequisiteQuestId);
                    if (pre != null && pre.completedTag != null && !RPGQuestEngine.hasQuestTag(m, pre.completedTag)) {
                        continue;
                    }
                }
                if (q.activeTag != null) RPGQuestEngine.addQuestTag(m, q.activeTag);
                RPGQuestEngine.addQuestTag(m, "rqe_party_obj_done_" + q.id);
                if (q.doneTag != null) RPGQuestEngine.addQuestTag(m, q.doneTag);
                if (q.stages != null) {
                    for (int sIdx = 0; sIdx < q.stages.size(); sIdx++) {
                        QuestDefinition.QuestStage st = q.stages.get(sIdx);
                        RPGQuestEngine.setInternalScore(m, RPGQuestEngine.getInternalScoreKey(q.id, sIdx, st.type), Math.max(1, st.amount));
                    }
                }
                String npcTarget = (q.npc != null && !q.npc.isEmpty()) ? q.npc : "el NPC";
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> m),
                        new ShowBannerPacket(
                                actor.getScoreboardName() + " ENTREGÓ LA MISIÓN",
                                q.title,
                                "§eVe personalmente con " + npcTarget + " para cobrar",
                                0xFFFFCC00));
                m.sendSystemMessage(Component.literal("§6[Grupo - Principal] §e" + actor.getScoreboardName()
                        + " §fcompletó §e" + q.title + "§f. §aVe personalmente con " + npcTarget + " para entregar y cobrar."));
                RPGQuestEngine.syncPlayerAfterDialog(m);
            }
        }
    }

    private static String resolveMemberName(Party p, UUID uid, MinecraftServer server, ServerPlayer onlineMp) {
        if (onlineMp != null) {
            String n = onlineMp.getScoreboardName();
            if (p != null && !n.equals(p.memberNames.get(uid))) {
                p.memberNames.put(uid, n);
                saveParties();
            }
            return n;
        }
        if (p != null) {
            String cached = p.memberNames.get(uid);
            if (cached != null && !cached.isEmpty()) return cached;
        }
        String forgeCached = net.minecraftforge.common.UsernameCache.getLastKnownUsername(uid);
        if (forgeCached != null && !forgeCached.isEmpty()) {
            if (p != null) p.memberNames.put(uid, forgeCached);
            return forgeCached;
        }
        if (server != null && server.getProfileCache() != null) {
            var opt = server.getProfileCache().get(uid);
            if (opt.isPresent() && opt.get().getName() != null && !opt.get().getName().isEmpty()) {
                String profName = opt.get().getName();
                if (p != null) p.memberNames.put(uid, profName);
                return profName;
            }
        }
        return uid.toString().substring(0, 8);
    }

    public static void syncPartyOnLogout(ServerPlayer leaving) {
        if (leaving == null || leaving.getServer() == null) return;
        Party p = PLAYER_TO_PARTY.get(leaving.getUUID());
        if (p == null) return;
        p.memberNames.put(leaving.getUUID(), leaving.getScoreboardName());
        saveParties();
        for (UUID uid : p.members) {
            if (uid.equals(leaving.getUUID())) continue;
            ServerPlayer m = leaving.getServer().getPlayerList().getPlayer(uid);
            if (m != null) syncToPlayerInternal(m, leaving.getUUID());
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
        syncToPlayerInternal(sp, null);
    }

    private static void syncToPlayerInternal(ServerPlayer sp, UUID forceOfflineUuid) {
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
                ServerPlayer mp = uid.equals(forceOfflineUuid) ? null : sp.getServer().getPlayerList().getPlayer(uid);
                String name = resolveMemberName(p, uid, sp.getServer(), mp);
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
