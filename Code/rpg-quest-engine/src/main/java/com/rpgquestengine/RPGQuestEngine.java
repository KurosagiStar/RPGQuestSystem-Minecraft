package com.rpgquestengine;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
import java.util.Map;
import java.util.Set;

@Mod(RPGQuestEngine.MODID)
public class RPGQuestEngine {
    public static final String MODID = "rpgquestengine";
    private static final Logger LOGGER = LogManager.getLogger();

    public RPGQuestEngine() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            modEventBus.addListener(KeyBindings::registerKeys);
            MinecraftForge.EVENT_BUS.register(new QuestHudOverlay());
            MinecraftForge.EVENT_BUS.register(new QuestTitleBannerOverlay());
            MinecraftForge.EVENT_BUS.register(new ClientKeyEvents());
        });

        LOGGER.info("[RPGQuestEngine] Universal MMORPG Quest Engine Initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            QuestConfig.loadConfig();
        });
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            checkAndSyncQuests(sp);
        }
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
                boolean allNonOptionalDone = true;

                for (int sIdx = 0; sIdx < q.stages.size(); sIdx++) {
                    QuestDefinition.QuestStage stage = q.stages.get(sIdx);
                    int current = 0;
                    int required = stage.amount;

                    String type = stage.type != null ? stage.type.toUpperCase() : "SCORE";

                    switch (type) {
                        case "ITEM":
                            current = countItemInInventory(sp.getInventory(), stage.target);
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

                    // Si no es opcional y no se cumplió, la misión aún no está lista (Punto 2)
                    if (!stage.optional && current < required) {
                        allNonOptionalDone = false;
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

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new SyncPlayerQuestsPacket(sp.getTags(), scores));
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

    private int countItemInInventory(Inventory inv, String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item == null) return 0;
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static class ClientKeyEvents {
        @SubscribeEvent
        public void onKeyInput(InputEvent.Key event) {
            if (KeyBindings.OPEN_JOURNAL_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    QuestConfig.loadConfig();
                    mc.setScreen(new QuestJournalScreen());
                }
            }
        }
    }
}
