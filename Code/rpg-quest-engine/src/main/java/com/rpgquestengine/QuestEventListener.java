package com.rpgquestengine;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@Mod.EventBusSubscriber(modid = RPGQuestEngine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QuestEventListener {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player instanceof ServerPlayer sp && !player.isCreative()) {
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
            if (blockId != null) {
                incrementQuestTarget(sp, "MINE", blockId.toString());
            }
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer sp) {
            ItemStack stack = event.getCrafting();
            if (!stack.isEmpty()) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (itemId != null) {
                    incrementQuestTarget(sp, "CRAFT", itemId.toString(), stack.getCount());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer sp) {
            List<ItemStack> drops = event.getDrops();
            for (ItemStack stack : drops) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (itemId != null) {
                    incrementQuestTarget(sp, "FISH", itemId.toString(), stack.getCount());
                }
            }
            incrementQuestTarget(sp, "FISH", "any", 1);
        }
    }

    @SubscribeEvent
    public static void onEntityKilled(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer sp) {
            ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
            if (entityId != null) {
                incrementQuestTarget(sp, "KILL", entityId.toString());
            }
        }
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer sp) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(event.getSmelting().getItem());
            if (itemId != null) {
                incrementQuestTarget(sp, "SMELT", itemId.toString(), event.getSmelting().getCount());
            }
        }
    }

    private static void incrementQuestTarget(ServerPlayer sp, String type, String target) {
        incrementQuestTarget(sp, type, target, 1);
    }

    private static void incrementQuestTarget(ServerPlayer sp, String type, String target, int amount) {
        for (QuestDefinition q : QuestConfig.QUESTS) {
            if (sp.getTags().contains(q.activeTag) && !sp.getTags().contains(q.completedTag)) {
                for (int sIdx = 0; sIdx < q.stages.size(); sIdx++) {
                    QuestDefinition.QuestStage stage = q.stages.get(sIdx);
                    if (type.equalsIgnoreCase(stage.type)) {
                        if ("any".equalsIgnoreCase(stage.target) || target.equalsIgnoreCase(stage.target)) {
                            String internalScoreKey = RPGQuestEngine.getInternalScoreKey(q.id, sIdx, stage.type);
                            var sb = sp.getServer().getScoreboard();
                            var obj = sb.getObjective(internalScoreKey);
                            if (obj == null) {
                                obj = sb.addObjective(internalScoreKey, net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY, net.minecraft.network.chat.Component.literal(internalScoreKey), net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER);
                            }
                            var score = sb.getOrCreatePlayerScore(sp.getScoreboardName(), obj);
                            score.add(amount);
                        }
                    }
                }
            }
        }
    }
}

