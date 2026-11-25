package com.rpgquestengine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod(RPGQuestEngine.MODID)
public class RPGQuestEngine {
    public static final String MODID = "rpgquestengine";
    private static final Logger LOGGER = LogManager.getLogger();
    public static RPGQuestEngine INSTANCE;

    public RPGQuestEngine() {
        INSTANCE = this;
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.initClient(modEventBus));

        LOGGER.info("[RPGQuestEngine] Universal MMORPG Quest Engine Initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();
            QuestConfig.loadConfig();
        });
    }

    public static void addBuiltInPacks(net.minecraftforge.event.AddPackFindersEvent event) {
        if (event.getPackType() != net.minecraft.server.packs.PackType.CLIENT_RESOURCES) return;
        java.nio.file.Path resourcePath = net.minecraftforge.fml.ModList.get()
                .getModFileById(MODID).getFile().findResource("resourcepacks/quest_radar_icons");
        net.minecraft.server.packs.repository.Pack pack = net.minecraft.server.packs.repository.Pack.readMetaAndCreate(
                MODID + ":quest_radar_icons",
                Component.literal("Quest Radar Icons (RPG)"),
                true,
                id -> new net.minecraftforge.resource.PathPackResources(id, true, resourcePath),
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                net.minecraft.server.packs.repository.Pack.Position.TOP,
                net.minecraft.server.packs.repository.PackSource.BUILT_IN
        );
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    public static void ensureXaeroEasyNpcCategory() {
        try {
            java.nio.file.Path cfgPath = java.nio.file.Paths.get("config", "xaero", "minimap", "default_radar_categories_client.json");
            if (!java.nio.file.Files.exists(cfgPath)) return;
            String raw = java.nio.file.Files.readString(cfgPath, java.nio.charset.StandardCharsets.UTF_8);
            if (raw.contains("easy_npc:*")) return;

            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            com.google.gson.JsonArray subs = root.getAsJsonArray("subCategories");
            if (subs == null) return;
            for (com.google.gson.JsonElement el : subs) {
                com.google.gson.JsonObject obj = el.getAsJsonObject();
                if ("gui.xaero_entity_category_living".equals(obj.has("name") ? obj.get("name").getAsString() : "")) {
                    com.google.gson.JsonArray livingSubs = obj.getAsJsonArray("subCategories");
                    if (livingSubs == null) {
                        livingSubs = new com.google.gson.JsonArray();
                        obj.add("subCategories", livingSubs);
                    }
                    com.google.gson.JsonObject easyNpcCat = new com.google.gson.JsonObject();
                    easyNpcCat.addProperty("hardInclude", "nothing");
                    com.google.gson.JsonArray inc = new com.google.gson.JsonArray();
                    inc.add("easy_npc:*");
                    easyNpcCat.add("includeList", inc);
                    easyNpcCat.addProperty("includeListInSuperCategory", true);
                    easyNpcCat.addProperty("excludeMode", "ONLY");
                    easyNpcCat.add("excludeList", new com.google.gson.JsonArray());
                    easyNpcCat.addProperty("name", "Easy NPCs");
                    easyNpcCat.addProperty("protection", false);
                    com.google.gson.JsonObject overrides = new com.google.gson.JsonObject();
                    overrides.addProperty("displayed", true);
                    overrides.addProperty("icons", 2.0);
                    overrides.addProperty("iconScale", 1.0);
                    easyNpcCat.add("settingOverrides", overrides);
                    easyNpcCat.add("subCategories", new com.google.gson.JsonArray());

                    com.google.gson.JsonArray newSubs = new com.google.gson.JsonArray();
                    newSubs.add(easyNpcCat);
                    for (com.google.gson.JsonElement oldEl : livingSubs) newSubs.add(oldEl);
                    obj.add("subCategories", newSubs);

                    String out = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
                    java.nio.file.Files.writeString(cfgPath, out, java.nio.charset.StandardCharsets.UTF_8);
                    LOGGER.info("[RPGQuestEngine] Added Easy NPCs blue icon rule to Xaero minimap config.");
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[RPGQuestEngine] Could not auto-patch Xaero radar config: " + e.getMessage());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new SyncQuestConfigPacket(QuestConfig.getRawQuestsJson()));
            checkAndSyncQuests(sp);
            DailyQuestManager.syncToPlayer(sp);
            ChallengeManager.syncToPlayer(sp);
            PartyManager.syncToPlayer(sp);
            if (PartyManager.getParty(sp.getUUID()) != null) {
                PartyManager.syncMainQuestsWithParty(sp);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer oldSp && event.getEntity() instanceof ServerPlayer newSp) {
            var oldData = oldSp.getPersistentData();
            var newData = newSp.getPersistentData();
            for (String key : oldData.getAllKeys()) {
                if (key.startsWith("rqe_") || key.startsWith("rpgquest") || key.equals(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) {
                    var tag = oldData.get(key);
                    if (tag != null) newData.put(key, tag.copy());
                }
            }
            for (String tag : oldSp.getTags()) {
                newSp.addTag(tag);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new SyncQuestConfigPacket(QuestConfig.getRawQuestsJson()));
            checkAndSyncQuests(sp);
            DailyQuestManager.syncToPlayer(sp);
            ChallengeManager.syncToPlayer(sp);
            PartyManager.syncToPlayer(sp);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("rpgquest")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("reset")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        Scoreboard sb = sp.getServer().getScoreboard();

                        int removedTags = 0;
                        for (String t : new java.util.ArrayList<>(sp.getTags())) {
                            if (t.startsWith("q_") || t.startsWith("rqe_")) {
                                sp.removeTag(t);
                                removedTags++;
                            }
                        }

                        for (QuestDefinition q : QuestConfig.QUESTS) {
                            if (q.activeTag != null) sp.removeTag(q.activeTag);
                            if (q.doneTag != null) sp.removeTag(q.doneTag);
                            if (q.completedTag != null) sp.removeTag(q.completedTag);

                            for (int sIdx = 0; sIdx < q.stages.size(); sIdx++) {
                                QuestDefinition.QuestStage st = q.stages.get(sIdx);
                                String key = getInternalScoreKey(q.id, sIdx, st.type);
                                Objective obj = sb.getObjective(key);
                                if (obj != null) {
                                    sb.resetPlayerScore(sp.getScoreboardName(), obj);
                                }
                            }
                        }

                        // Forzar sincronización inmediata al cliente
                        checkAndSyncQuests(sp);

                        ctx.getSource().sendSuccess(() -> Component.literal("§a[RPGQuestEngine] Progreso de todas las misiones reseteado exitosamente."), false);
                        return 1;
                    })
                )
                .then(Commands.literal("reload")
                    .executes(ctx -> {
                        QuestConfig.loadConfig();
                        if (ctx.getSource().getEntity() instanceof ServerPlayer sp) {
                            checkAndSyncQuests(sp);
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("§a[RPGQuestEngine] Configuración rpg_quests.json recargada (" + QuestConfig.QUESTS.size() + " misiones)."), false);
                        return 1;
                    })
                )
                .then(Commands.literal("talk")
                    .then(Commands.argument("npcId", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> handleNpcTalk(ctx.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "npcId")))
                    )
                )
                .then(Commands.literal("sync_npcs")
                    .requires(s -> s.hasPermission(2))
                    .executes(ctx -> {
                        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
                        record NpcTarget(ServerLevel level, net.minecraft.world.entity.Entity entity, String tag) {}
                        List<NpcTarget> targets = new java.util.ArrayList<>();

                        for (ServerLevel level : server.getAllLevels()) {
                            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                                for (String tag : entity.getTags()) {
                                    if (tag.startsWith("npc_")) {
                                        targets.add(new NpcTarget(level, entity, tag));
                                        break;
                                    }
                                }
                            }
                        }

                        int synced = 0;
                        for (NpcTarget t : targets) {
                            double x = t.entity().getX();
                            double y = t.entity().getY();
                            double z = t.entity().getZ();
                            float yRot = t.entity().getYRot();
                            float xRot = t.entity().getXRot();
                            QuestConfig.setNpcLocation(t.tag(), x, y, z);

                            java.util.UUID oldUuid = t.entity().getUUID();
                            var posSource = server.createCommandSourceStack()
                                    .withSuppressedOutput()
                                    .withPermission(4)
                                    .withLevel(t.level())
                                    .withPosition(new net.minecraft.world.phys.Vec3(x, y, z));
                            String cmd = "easy_npc preset import custom easy_npc:preset/villager/" + t.tag() + ".npc.snbt";
                            int res = server.getCommands().performPrefixedCommand(posSource, cmd);
                            if (res > 0) {
                                net.minecraft.world.entity.Entity spawned = null;
                                for (net.minecraft.world.entity.Entity e : t.level().getAllEntities()) {
                                    if (e.getTags().contains(t.tag()) && !e.getUUID().equals(oldUuid)) {
                                        spawned = e;
                                        break;
                                    }
                                }
                                if (spawned != null) {
                                    spawned.teleportTo(t.level(), x, y, z, java.util.Set.of(), yRot, xRot);
                                    t.entity().discard();
                                    synced++;
                                }
                            }
                        }

                        final int count = synced;
                        ctx.getSource().sendSuccess(() -> Component.literal("§a[RPGQuestEngine] Sincronizados " + count + " NPCs cargados."), true);
                        return count;
                    })
                )
                .then(Commands.literal("set_npc_pos")
                    .then(Commands.argument("npc_id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            String npcId = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "npc_id");
                            QuestConfig.setNpcLocation(npcId, sp.getX(), sp.getY(), sp.getZ());
                            OpenAdminUiPacket.broadcastLocationsToOps(sp.getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[RPGQuestEngine] Posición guardada para " + npcId + " (" + (int)sp.getX() + ", " + (int)sp.getY() + ", " + (int)sp.getZ() + ")."), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("spawn_npc")
                    .then(Commands.argument("npc_id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            String npcId = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "npc_id");
                            String cmd = "easy_npc preset import custom easy_npc:preset/villager/" + npcId + ".npc.snbt";
                            sp.getServer().getCommands().performPrefixedCommand(
                                sp.createCommandSourceStack().withPermission(4), cmd);
                            QuestConfig.setNpcLocation(npcId, sp.getX(), sp.getY(), sp.getZ());
                            OpenAdminUiPacket.broadcastLocationsToOps(sp.getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[RPGQuestEngine] NPC " + npcId + " spawneado y posición guardada."), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("ui")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                                new OpenAdminUiPacket(QuestConfig.NPC_LOCATIONS, true));
                        return 1;
                    })
                )
                .then(Commands.literal("respawn_saved")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        ServerLevel level = sp.serverLevel();
                        int respawned = 0;
                        int moved = 0;
                        for (Map.Entry<String, double[]> entry : QuestConfig.NPC_LOCATIONS.entrySet()) {
                            String npcId = entry.getKey();
                            double[] pos = entry.getValue();
                            if (pos == null || pos.length < 3) continue;
                            level.getChunk(((int) Math.floor(pos[0])) >> 4, ((int) Math.floor(pos[2])) >> 4);

                            boolean exists = false;
                            for (ServerLevel lvl : sp.getServer().getAllLevels()) {
                                for (net.minecraft.world.entity.Entity e : lvl.getAllEntities()) {
                                    if (e.getTags().contains(npcId)) {
                                        e.teleportTo(level, pos[0], pos[1], pos[2], java.util.Set.of(), e.getYRot(), e.getXRot());
                                        exists = true;
                                        moved++;
                                    }
                                }
                            }
                            if (!exists) {
                                var posSource = sp.getServer().createCommandSourceStack()
                                        .withSuppressedOutput()
                                        .withPermission(4)
                                        .withLevel(level)
                                        .withPosition(new net.minecraft.world.phys.Vec3(pos[0], pos[1], pos[2]));
                                String cmd = "easy_npc preset import custom easy_npc:preset/villager/" + npcId + ".npc.snbt";
                                int res = sp.getServer().getCommands().performPrefixedCommand(posSource, cmd);
                                if (res > 0) {
                                    for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                                        if (e.getTags().contains(npcId)) {
                                            e.teleportTo(level, pos[0], pos[1], pos[2], java.util.Set.of(), e.getYRot(), e.getXRot());
                                        }
                                    }
                                    respawned++;
                                }
                            }
                        }
                        final int rCount = respawned;
                        final int mCount = moved;
                        ctx.getSource().sendSuccess(() -> Component.literal("§a[RPGQuestEngine] Re-spawneados: " + rCount + ", reposicionados: " + mCount + " NPCs."), true);
                        return rCount + mCount;
                    })
                )
        );
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer sp) {
            if (sp.tickCount % 10 == 0) {
                checkAndSyncQuests(sp);
            }
        }
    }

    private void checkAndSyncQuests(ServerPlayer sp) {
        Set<String> playerTags = sp.getTags();
        ServerLevel level = sp.serverLevel();
        Scoreboard sb = sp.getServer().getScoreboard();
        Map<String, Integer> scores = new HashMap<>();

        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (playerTags.contains(q.activeTag) && !playerTags.contains(q.completedTag)) {
                // 1. Validar Prerrequisito si tiene uno configurado
                if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                    QuestDefinition prereq = QuestConfig.getQuestById(q.prerequisiteQuestId);
                    if (prereq != null && prereq.completedTag != null && !playerTags.contains(prereq.completedTag)) {
                        // El jugador no completó la misión previa: cancelar activación
                        sp.removeTag(q.activeTag);
                        sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[RPGQuestEngine] Bloqueado: Completa primero \"" + prereq.title + "\"."), true);
                        continue;
                    }
                }

                // 1b. Validar blockedByTags (rutas ramificadas excluyentes)
                if (q.blockedByTags != null && !q.blockedByTags.isEmpty()) {
                    boolean blocked = false;
                    for (String bTag : q.blockedByTags) {
                        if (playerTags.contains(bTag)) {
                            blocked = true;
                            break;
                        }
                    }
                    if (blocked) {
                        sp.removeTag(q.activeTag);
                        sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[RPGQuestEngine] Esta ruta ha quedado bloqueada por tus decisiones."), true);
                        continue;
                    }
                }

                // Si la misión está activa pero aún no tiene NPC asignado, asignarle el último NPC interactuado
                boolean hasAssignedPos = false;
                for (String t : playerTags) {
                    if (t.startsWith("rqe_pos_" + q.id + "_")) {
                        hasAssignedPos = true;
                        break;
                    }
                }
                if (!hasAssignedPos) {
                    for (String t : playerTags) {
                        if (t.startsWith("rqe_last_npc_")) {
                            // formato: rqe_last_npc_{uuid}_{x}_{z}
                            String rem = t.substring("rqe_last_npc_".length());
                            int uEnd = rem.indexOf('_');
                            if (uEnd > 0) {
                                String uStr = rem.substring(0, uEnd);
                                String posPart = rem.substring(uEnd + 1);
                                sp.addTag("rqe_npc_" + q.id + "_" + uStr);
                                sp.addTag("rqe_pos_" + q.id + "_" + posPart);
                            }
                            break;
                        }
                    }
                }

                boolean allNonOptionalDone = true;

                for (int sIdx = 0; sIdx < q.stages.size(); sIdx++) {
                    QuestDefinition.QuestStage stage = q.stages.get(sIdx);
                    int current = 0;
                    int required = stage.amount;

                    String type = stage.type != null ? stage.type.toUpperCase() : "SCORE";

                    switch (type) {
                        case "ITEM":
                            current = countItemInInventory(sp.getInventory(), stage.target, stage.nbtKey, stage.nbtValue);
                            break;

                        case "TAG":
                            current = playerTags.contains(stage.target) ? 1 : 0;
                            required = 1;
                            break;

                        case "SCORE":
                            Objective obj = sb.getObjective(stage.target);
                            if (obj != null) {
                                Score score = sb.getOrCreatePlayerScore(sp.getScoreboardName(), obj);
                                current = score.getScore();
                            }
                            scores.put(stage.target, current);
                            break;

                        case "SURVIVE": {
                            String internalKey = getInternalScoreKey(q.id, sIdx, stage.type);
                            current = getInternalScore(sp, internalKey);
                            boolean inZone = true;
                            if (stage.hasLocation) {
                                double zx = stage.posX;
                                double zz = stage.posZ;
                                if (stage.relativeToNpc) {
                                    String zonePrefix = "rqe_zone_" + q.id + "_" + sIdx + "_";
                                    for (String t : playerTags) {
                                        if (t.startsWith(zonePrefix)) {
                                            String[] parts = t.substring(zonePrefix.length()).split("_");
                                            if (parts.length == 2) {
                                                try {
                                                    zx = Double.parseDouble(parts[0]);
                                                    zz = Double.parseDouble(parts[1]);
                                                } catch (Exception ignored) {}
                                            }
                                            break;
                                        }
                                    }
                                }
                                double dxZ = zx - sp.getX();
                                double dzZ = zz - sp.getZ();
                                inZone = Math.sqrt(dxZ * dxZ + dzZ * dzZ) <= stage.radius;
                            }
                            if (inZone && current < required && sp.tickCount % 20 == 0) {
                                current++;
                                setInternalScore(sp, internalKey, current);
                            }
                            scores.put(internalKey, current);
                            break;
                        }

                        case "DIMENSION": {
                            String dimId = level.dimension().location().toString();
                            String internalKey = getInternalScoreKey(q.id, sIdx, stage.type);
                            if (stage.target.equalsIgnoreCase(dimId)) {
                                setInternalScore(sp, internalKey, 1);
                                current = 1;
                            } else {
                                current = getInternalScore(sp, internalKey);
                            }
                            scores.put(internalKey, current);
                            required = 1;
                            break;
                        }

                        case "BIOME": {
                            BlockPos pos = sp.blockPosition();
                            Holder<Biome> biomeHolder = level.getBiome(pos);
                            ResourceLocation biomeKey = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biomeHolder.value());
                            String internalKey = getInternalScoreKey(q.id, sIdx, stage.type);
                            if (biomeKey != null && stage.target.equalsIgnoreCase(biomeKey.toString())) {
                                setInternalScore(sp, internalKey, 1);
                                current = 1;
                            } else {
                                current = getInternalScore(sp, internalKey);
                            }
                            scores.put(internalKey, current);
                            required = 1;
                            break;
                        }

                        case "STRUCTURE": {
                            BlockPos pos = sp.blockPosition();
                            String internalKey = getInternalScoreKey(q.id, sIdx, stage.type);
                            ResourceLocation targetRL = new ResourceLocation(stage.target);
                            ResourceKey<Structure> structKey = ResourceKey.create(Registries.STRUCTURE, targetRL);

                            StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structKey);
                            if (start.isValid()) {
                                setInternalScore(sp, internalKey, 1);
                                current = 1;
                            } else {
                                current = getInternalScore(sp, internalKey);
                            }
                            scores.put(internalKey, current);
                            required = 1;
                            break;
                        }

                        default: {
                            String internalKey = getInternalScoreKey(q.id, sIdx, stage.type);
                            current = getInternalScore(sp, internalKey);
                            scores.put(internalKey, current);
                            break;
                        }
                    }

                    // Spawneo si el jugador está en la zona, la misión NO está completada/lista, y aún faltan kills de ese mob
                    if (stage.hasLocation && stage.spawnMobsOnEntry && stage.mobToSpawn != null && !stage.mobToSpawn.isEmpty()) {
                        boolean questAlreadyDone = (q.doneTag != null && playerTags.contains(q.doneTag))
                                || playerTags.contains(q.completedTag)
                                || ("SURVIVE".equals(type) && current >= required);
                        
                        // Verificar si el objetivo de matar a ese mob ya se cumplió en esta misión
                        boolean mobTargetFinished = false;
                        for (int kIdx = 0; kIdx < q.stages.size(); kIdx++) {
                            QuestDefinition.QuestStage otherSt = q.stages.get(kIdx);
                            if ("KILL".equalsIgnoreCase(otherSt.type) && stage.mobToSpawn.equalsIgnoreCase(otherSt.target)) {
                                String killKey = getInternalScoreKey(q.id, kIdx, otherSt.type);
                                int killed = getInternalScore(sp, killKey);
                                if (killed >= otherSt.amount) {
                                    mobTargetFinished = true;
                                    break;
                                }
                            }
                        }

                        if (!questAlreadyDone && !mobTargetFinished) {
                            double targetX = stage.posX;
                            double targetZ = stage.posZ;
                            if (stage.relativeToNpc) {
                                String zonePrefix = "rqe_zone_" + q.id + "_" + sIdx + "_";
                                String existingZone = null;
                                for (String t : playerTags) {
                                    if (t.startsWith(zonePrefix)) {
                                        existingZone = t;
                                        break;
                                    }
                                }
                                if (existingZone != null) {
                                    String[] parts = existingZone.substring(zonePrefix.length()).split("_");
                                    if (parts.length == 2) {
                                        try {
                                            targetX = Double.parseDouble(parts[0]);
                                            targetZ = Double.parseDouble(parts[1]);
                                        } catch (Exception ignored) {}
                                    }
                                } else {
                                    double dxOff = stage.posX;
                                    double dzOff = stage.posZ;
                                    double len = Math.sqrt(dxOff * dxOff + dzOff * dzOff);
                                    if (len < 1.0) { dxOff = 80; dzOff = 0; len = 80; }
                                    if (len > 300.0) {
                                        dxOff = (dxOff / len) * 300.0;
                                        dzOff = (dzOff / len) * 300.0;
                                    }
                                    int zx = (int) Math.round(sp.getX() + dxOff);
                                    int zz = (int) Math.round(sp.getZ() + dzOff);
                                    sp.addTag(zonePrefix + zx + "_" + zz);
                                    targetX = zx;
                                    targetZ = zz;
                                }
                            }
                            double dx = targetX - sp.getX();
                            double dz = targetZ - sp.getZ();
                            double dist = Math.sqrt(dx * dx + dz * dz);
                            if (dist <= stage.radius) {
                                String waveTag = "rqe_wave_" + q.id + "_" + sIdx;
                                checkAndSpawnNearbyMobs(level, sp, stage.mobToSpawn, stage.mobSpawnCount, stage.radius, waveTag);
                                String ambushTag = "rqe_ambush_" + q.id + "_" + sIdx;
                                if (stage.ambushTitle != null && !stage.ambushTitle.isEmpty() && !playerTags.contains(ambushTag)) {
                                    sp.addTag(ambushTag);
                                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                                            net.minecraft.network.chat.Component.literal(stage.ambushTitle)));
                                    level.playSound(null, sp.blockPosition(), net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,
                                            net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.0F);
                                }
                            }
                        }
                    }

                    // Si no es opcional y no se cumplió, la misión aún no está lista (Punto 2)
                    String remKey = "rqe_wave_rem_" + q.id + "_" + sIdx;
                    if (!stage.optional && current < required) {
                        allNonOptionalDone = false;
                        if ("SURVIVE".equals(type)) setInternalScore(sp, remKey, 0);
                    } else if ("SURVIVE".equals(type) && current >= required && stage.spawnMobsOnEntry) {
                        String waveTag = "rqe_wave_" + q.id + "_" + sIdx;
                        int remaining = 0;
                        for (net.minecraft.world.entity.Entity ent : level.getAllEntities()) {
                            if (ent instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive() && living.getTags().contains(waveTag)) {
                                remaining++;
                            }
                        }
                        setInternalScore(sp, remKey, remaining);
                        scores.put(remKey, remaining);
                        if (remaining > 0) {
                            allNonOptionalDone = false;
                        }
                    } else if ("SURVIVE".equals(type)) {
                        setInternalScore(sp, remKey, 0);
                    }
                }

                if (q.doneTag != null && !q.doneTag.isEmpty()) {
                    if (allNonOptionalDone && !playerTags.contains(q.doneTag)) {
                        sp.addTag(q.doneTag);
                    } else if (!allNonOptionalDone && playerTags.contains(q.doneTag)) {
                        sp.removeTag(q.doneTag);
                    }
                }
            }
        }

        // Entrega automática de recompensas configuradas (XP + Ítems) al completar
        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (q.completedTag != null && playerTags.contains(q.completedTag)) {
                String rewardedTag = "rqe_rewarded_" + q.id;
                if (!playerTags.contains(rewardedTag)) {
                    sp.addTag(rewardedTag);
                    if (q.rewardXp > 0) {
                        sp.giveExperiencePoints(q.rewardXp);
                    }
                    if (q.rewardItems != null) {
                        for (QuestDefinition.RewardItem ri : q.rewardItems) {
                            if (ri.id != null && !ri.id.isEmpty() && ri.count > 0) {
                                Item rItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ri.id));
                                if (rItem != null && rItem != net.minecraft.world.item.Items.AIR) {
                                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(rItem, ri.count);
                                    if (!sp.getInventory().add(stack)) {
                                        sp.drop(stack, false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Rastrear posición de los NPCs asignados a misiones activas
        Map<String, double[]> npcLivePositions = new HashMap<>();

        // 1. Primero cargar última posición persistente de tags (funciona aún si el chunk está descargado)
        for (String tag : playerTags) {
            if (tag.startsWith("rqe_pos_")) {
                String remainder = tag.substring("rqe_pos_".length());
                // formato: {questId}_{x}_{z}
                int first_ = remainder.indexOf('_');
                int second_ = remainder.lastIndexOf('_');
                if (first_ > 0 && second_ > first_) {
                    String qId = remainder.substring(0, first_);
                    try {
                        double px = Double.parseDouble(remainder.substring(first_ + 1, second_));
                        double pz = Double.parseDouble(remainder.substring(second_ + 1));
                        npcLivePositions.put(qId, new double[]{px, pz});
                    } catch (Exception ignored) {}
                }
            }
        }

        // 2. Si la entidad está viva y en chunks cargados, sobreescribir con su posición exacta en tiempo real
        for (String tag : playerTags) {
            if (tag.startsWith("rqe_npc_")) {
                String remainder = tag.substring("rqe_npc_".length());
                int lastUnderscore = remainder.lastIndexOf('_');
                if (lastUnderscore > 0) {
                    String qId = remainder.substring(0, lastUnderscore);
                    String uuidStr = remainder.substring(lastUnderscore + 1);
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                        Entity npcEnt = level.getEntity(uuid);
                        if (npcEnt != null && npcEnt.isAlive()) {
                            npcLivePositions.put(qId, new double[]{npcEnt.getX(), npcEnt.getZ()});
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new SyncPlayerQuestsPacket(sp.getTags(), scores, npcLivePositions));

        // 3. Sincronizar tags de entidades vivas cercanas (radio 48 bloques) para garantizar marcadores actualizados en tiempo real
        net.minecraft.world.phys.AABB syncBox = sp.getBoundingBox().inflate(48.0D);
        for (net.minecraft.world.entity.LivingEntity nearby : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, syncBox)) {
            if (nearby != sp && !nearby.getTags().isEmpty()) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> sp),
                        new SyncEntityTagsPacket(nearby.getId(), new java.util.ArrayList<>(nearby.getTags()))
                );
            }
        }
    }

    public static String getInternalScoreKey(String questId, int stageIndex, String type) {
        String key = "rqe_" + questId.hashCode() + "_" + stageIndex;
        if (key.length() > 16) key = key.substring(0, 16);
        return key;
    }

    public static String getInternalScoreKey(String questId, String type) {
        return getInternalScoreKey(questId, 0, type);
    }

    public static int getInternalScore(ServerPlayer sp, String key) {
        Scoreboard sb = sp.getServer().getScoreboard();
        Objective obj = sb.getObjective(key);
        if (obj != null) {
            return sb.getOrCreatePlayerScore(sp.getScoreboardName(), obj).getScore();
        }
        return 0;
    }

    public static void setInternalScore(ServerPlayer sp, String key, int val) {
        Scoreboard sb = sp.getServer().getScoreboard();
        Objective obj = sb.getObjective(key);
        if (obj == null) {
            obj = sb.addObjective(key, net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY, net.minecraft.network.chat.Component.literal(key), net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER);
        }
        sb.getOrCreatePlayerScore(sp.getScoreboardName(), obj).setScore(val);
    }

    private void checkAndSpawnNearbyMobs(ServerLevel level, ServerPlayer sp, String mobIds, int minRequired, int searchRadius, String waveTag) {
        if (mobIds == null || mobIds.isEmpty()) return;
        String[] idArray = mobIds.split(",");
        double checkRadius = Math.max(25.0, (double) searchRadius);
        net.minecraft.world.phys.AABB area = sp.getBoundingBox().inflate(checkRadius, 16.0, checkRadius);

        int existingCount = 0;
        for (String rawId : idArray) {
            EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(rawId.trim()));
            if (et != null) {
                existingCount += level.getEntities(et, area, Entity::isAlive).size();
            }
        }

        // Solo spawnea al inicio (0) o cuando quedan menos de la mitad de enemigos vivos
        if (existingCount > 0 && existingCount >= (minRequired / 2)) return;
        int needed = minRequired - existingCount;
        if (needed <= 0) return;

        spawnQuestMobs(level, sp, idArray, needed, waveTag);
    }

    private void spawnQuestMobs(ServerLevel level, ServerPlayer sp, String[] idArray, int count, String waveTag) {
        java.util.List<EntityType<?>> validTypes = new java.util.ArrayList<>();
        for (String rawId : idArray) {
            EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(rawId.trim()));
            if (et != null) validTypes.add(et);
        }
        if (validTypes.isEmpty()) return;

        BlockPos playerPos = sp.blockPosition();
        java.util.Random rnd = new java.util.Random();
        int spawned = 0;

        for (int attempts = 0; attempts < 40 && spawned < count; attempts++) {
            int offsetX = rnd.nextInt(16) - 8;
            int offsetZ = rnd.nextInt(16) - 8;
            if (offsetX == 0 && offsetZ == 0) offsetX = 2;

            BlockPos checkPos = playerPos.offset(offsetX, 0, offsetZ);
            
            // Buscar verticalmente suelo adecuado (-1 a +4) para no spawnear bajo tierra
            BlockPos spawnPos = null;
            for (int dy = 4; dy >= -1; dy--) {
                BlockPos p = checkPos.offset(0, dy, 0);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).isAir()) {
                    spawnPos = p;
                    break;
                }
            }

            // Fallback: cerca directo del jugador si no halló suelo
            if (spawnPos == null && level.getBlockState(checkPos).isAir()) {
                spawnPos = checkPos;
            }

            if (spawnPos != null) {
                EntityType<?> chosenType = validTypes.get(rnd.nextInt(validTypes.size()));
                Entity entity = chosenType.create(level);
                if (entity instanceof net.minecraft.world.entity.Mob mob) {
                    mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, rnd.nextFloat() * 360F, 0.0F);
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
                    if (mob.getMainHandItem().isEmpty()) {
                        String typeId = ForgeRegistries.ENTITY_TYPES.getKey(chosenType).toString();
                        if (typeId.contains("pillager") || typeId.contains("provoker")) {
                            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CROSSBOW));
                        } else if (typeId.contains("vindicator") || typeId.contains("basher")) {
                            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
                        } else if (typeId.contains("marauder") || typeId.contains("illager")) {
                            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                        }
                    }
                    if (waveTag != null && !waveTag.isEmpty()) mob.addTag(waveTag);
                    mob.setPersistenceRequired(); // No desaparece
                    level.addFreshEntity(mob);
                    spawned++;
                }
            }
        }

        if (spawned > 0) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚔ ¡Bestias acechando en la zona! (" + spawned + " generadas)"), true);
            // Mandar paquete directo al cliente para reproducir el sonido en la UI/auriculares del jugador sin atenuación de distancia
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PlayQuestSoundPacket(5));
        }
    }

    private int countItemInInventory(Inventory inv, String itemId, String nbtKey, String nbtValue) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item == null) return 0;
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                if (nbtKey != null && !nbtKey.isEmpty()) {
                    net.minecraft.nbt.CompoundTag tag = stack.getTag();
                    if (tag == null) continue;
                    
                    // Soporte de claves anidadas separadas por punto (ej. "affix_data.gem")
                    String[] parts = nbtKey.split("\\.");
                    net.minecraft.nbt.CompoundTag currentTag = tag;
                    boolean match = true;
                    for (int p = 0; p < parts.length - 1; p++) {
                        if (currentTag.contains(parts[p], net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                            currentTag = currentTag.getCompound(parts[p]);
                        } else {
                            match = false;
                            break;
                        }
                    }
                    if (!match) continue;
                    
                    String leafKey = parts[parts.length - 1];
                    if (!currentTag.contains(leafKey)) continue;

                    if (nbtValue != null && !nbtValue.isEmpty()) {
                        String valStr = currentTag.getString(leafKey);
                        if (!nbtValue.equalsIgnoreCase(valStr)) continue;
                    }
                }
                count += stack.getCount();
            }
        }
        return count;
    }

    public static void syncPlayerAfterDialog(ServerPlayer sp) {
        if (INSTANCE != null && sp != null) {
            INSTANCE.checkAndSyncQuests(sp);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlayerEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.isShiftKeyDown()) return; // Shift + clic derecho deja pasar interacción admin/Easy NPC

        Entity target = event.getTarget();
        Set<String> targetTags = target.getTags();
        if (targetTags.isEmpty()) return;

        String matchedNpcId = null;
        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (q.npcId != null && !q.npcId.isEmpty() && targetTags.contains(q.npcId)) {
                matchedNpcId = q.npcId;
                break;
            }
        }
        if (matchedNpcId == null) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        openNpcDialog(sp, matchedNpcId, target.getName().getString(), target.getId());
    }

    private void openNpcDialog(ServerPlayer sp, String npcId, String npcName, int npcEntityId) {
        java.util.List<QuestDefinition> npcQuests = QuestConfig.getQuestsForNpc(npcId);
        if (npcQuests.isEmpty()) {
            sp.sendSystemMessage(Component.literal("§c[RPGQuestEngine] No hay misiones para " + npcId));
            return;
        }

        checkAndSyncQuests(sp);
        Set<String> tags = sp.getTags();

        for (QuestDefinition q : npcQuests) {
            if (q.doneTag != null && tags.contains(q.doneTag)) {
                sendDialogPacket(sp, npcId, npcName, npcEntityId, "TURN_IN", q);
                return;
            }
        }

        for (QuestDefinition q : npcQuests) {
            if (q.activeTag != null && tags.contains(q.activeTag)) {
                sendDialogPacket(sp, npcId, npcName, npcEntityId, "IN_PROGRESS", q);
                return;
            }
        }

        QuestDefinition firstBlockedPrereq = null;
        for (QuestDefinition q : npcQuests) {
            if (q.completedTag != null && tags.contains(q.completedTag)) continue;
            if (q.prerequisiteQuestId != null && !q.prerequisiteQuestId.isEmpty()) {
                QuestDefinition prereq = QuestConfig.getQuestById(q.prerequisiteQuestId);
                if (prereq != null && (prereq.completedTag == null || !tags.contains(prereq.completedTag))) {
                    if (firstBlockedPrereq == null) firstBlockedPrereq = prereq;
                    continue;
                }
            }
            sendDialogPacket(sp, npcId, npcName, npcEntityId, "AVAILABLE", q);
            return;
        }

        if (firstBlockedPrereq != null) {
            String reqNpc = (firstBlockedPrereq.npc != null && !firstBlockedPrereq.npc.isEmpty())
                    ? firstBlockedPrereq.npc : firstBlockedPrereq.npcId;
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new OpenNpcDialogPacket(npcId, npcName, npcEntityId, "LOCKED", firstBlockedPrereq.id,
                            firstBlockedPrereq.title, reqNpc != null ? reqNpc : "", "", 0, 0, null, null, null));
            return;
        }

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new OpenNpcDialogPacket(npcId, npcName, npcEntityId, "COMPLETED_ALL", "", "", "", "", 0, 0, null, null, null));
    }

    private static java.util.List<OpenNpcDialogPacket.RewardEntry> toRewardEntries(java.util.List<QuestDefinition.RewardItem> src) {
        java.util.List<OpenNpcDialogPacket.RewardEntry> out = new java.util.ArrayList<>();
        if (src != null) {
            for (QuestDefinition.RewardItem ri : src) {
                if (ri != null && ri.id != null && !ri.id.isEmpty()) {
                    out.add(new OpenNpcDialogPacket.RewardEntry(ri.id, ri.count, ri.nbt));
                }
            }
        }
        return out;
    }

    private void sendDialogPacket(ServerPlayer sp, String npcId, String npcName, int npcEntityId, String state, QuestDefinition q) {
        String desc = (q.stages != null && !q.stages.isEmpty()) ? q.stages.get(0).description : q.title;
        String lore = (q.description != null) ? q.description : "";
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new OpenNpcDialogPacket(npcId, npcName, npcEntityId, state, q.id, q.title, lore, desc,
                        q.rewardXp, q.rewardXpLevels,
                        toRewardEntries(q.rewardItems),
                        toRewardEntries(q.randomRewards),
                        toRewardEntries(q.choiceRewards)));
    }

    private int handleNpcTalk(ServerPlayer sp, String npcId) {
        openNpcDialog(sp, npcId, npcId, -1);
        return 1;
    }
}
