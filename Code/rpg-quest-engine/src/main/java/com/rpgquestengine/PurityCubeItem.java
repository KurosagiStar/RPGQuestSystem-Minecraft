package com.rpgquestengine;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = RPGQuestEngine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PurityCubeItem extends Item {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RPGQuestEngine.MODID);

    public static final RegistryObject<Item> PURITY_CUBE = ITEMS.register("purity_cube",
            () -> new PurityCubeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    private static boolean curiosResolved = false;
    private static Method getCuriosInventoryMethod;
    private static Method getEquippedCuriosMethod;

    public PurityCubeItem(Properties properties) {
        super(properties);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(PurityCubeItem::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES
                || event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(PURITY_CUBE);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7Al equiparse en §dSpellstone§7:"));
        tooltip.add(Component.literal("§b• Inmunidad total a efectos negativos"));
        tooltip.add(Component.literal("§8Geometría arcana purificada sin maldiciones."));
    }

    public static boolean isEquippedInCurios(Player player) {
        if (player == null) return false;
        if (!curiosResolved) {
            curiosResolved = true;
            try {
                Class<?> apiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                getCuriosInventoryMethod = apiClass.getMethod("getCuriosInventory", LivingEntity.class);
                Class<?> handlerClass = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                getEquippedCuriosMethod = handlerClass.getMethod("getEquippedCurios");
            } catch (Throwable ignored) {
                getCuriosInventoryMethod = null;
                getEquippedCuriosMethod = null;
            }
        }
        if (getCuriosInventoryMethod == null || getEquippedCuriosMethod == null) return false;
        try {
            Object lazyOptObj = getCuriosInventoryMethod.invoke(null, player);
            if (lazyOptObj instanceof LazyOptional<?> lazyOpt && lazyOpt.isPresent()) {
                Object curiosHandler = lazyOpt.orElse(null);
                if (curiosHandler != null) {
                    Object itemHandlerObj = getEquippedCuriosMethod.invoke(curiosHandler);
                    if (itemHandlerObj instanceof IItemHandler handler) {
                        Item target = PURITY_CUBE.get();
                        int slots = handler.getSlots();
                        for (int i = 0; i < slots; i++) {
                            ItemStack st = handler.getStackInSlot(i);
                            if (!st.isEmpty() && st.getItem() == target) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player) {
            MobEffectInstance inst = event.getEffectInstance();
            if (inst != null && inst.getEffect() != null
                    && inst.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                if (isEquippedInCurios(player)) {
                    event.setResult(Event.Result.DENY);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player == null || player.level().isClientSide()) return;
        if (player.tickCount % 10 != 0) return;

        if (!player.getActiveEffects().isEmpty() && isEquippedInCurios(player)) {
            List<MobEffect> toRemove = null;
            for (MobEffectInstance inst : player.getActiveEffects()) {
                MobEffect eff = inst.getEffect();
                if (eff != null && eff.getCategory() == MobEffectCategory.HARMFUL) {
                    if (toRemove == null) toRemove = new ArrayList<>();
                    toRemove.add(eff);
                }
            }
            if (toRemove != null) {
                for (MobEffect eff : toRemove) {
                    player.removeEffect(eff);
                }
            }
        }
    }
}
