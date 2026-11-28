package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public class RerollReforgePacket {
    private static final String APOTH_MENU = "dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu";
    private static final String ESOTERIC_MENU = "com.mira.esotericreforging.screen.EsotericReforgingMenu";
    private static final String REFORGE_SEED_KEY = "apoth_reforge_seed";

    public final boolean fromServer;
    public final int syncedSeed;

    public RerollReforgePacket() {
        this.fromServer = false;
        this.syncedSeed = 0;
    }

    public RerollReforgePacket(int syncedSeed) {
        this.fromServer = true;
        this.syncedSeed = syncedSeed;
    }

    public static void encode(RerollReforgePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.fromServer);
        if (msg.fromServer) {
            buf.writeInt(msg.syncedSeed);
        }
    }

    public static RerollReforgePacket decode(FriendlyByteBuf buf) {
        boolean fromServer = buf.readBoolean();
        int seed = fromServer ? buf.readInt() : 0;
        return fromServer ? new RerollReforgePacket(seed) : new RerollReforgePacket();
    }

    public static void handle(RerollReforgePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.fromServer) {
                ReforgingRerollClientHandler.onServerSyncSeed(msg.syncedSeed);
                return;
            }

            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            AbstractContainerMenu menu = sp.containerMenu;
            if (menu == null) return;

            String clsName = menu.getClass().getName();
            if (!APOTH_MENU.equals(clsName) && !ESOTERIC_MENU.equals(clsName)) {
                return;
            }

            if (menu.slots.size() < 6 || !menu.getSlot(0).hasItem() || !menu.getSlot(1).hasItem()) {
                sp.displayClientMessage(Component.literal("§cColoca un objeto y un material de rareza primero."), true);
                return;
            }

            try {
                Method getRarity = findMethod(menu.getClass(), "getRarity");
                if (getRarity != null && getRarity.invoke(menu) == null) {
                    sp.displayClientMessage(Component.literal("§cMaterial de rareza inválido."), true);
                    return;
                }
            } catch (Exception ignored) {
            }

            boolean creative = sp.getAbilities().instabuild;
            if (!creative && sp.experienceLevel < 1) {
                sp.displayClientMessage(Component.literal("§cNecesitas al menos 1 nivel de XP para cambiar opciones."), true);
                return;
            }

            if (!creative) {
                sp.giveExperienceLevels(-1);
            }

            int newSeed = sp.getRandom().nextInt();
            if (newSeed == 0) newSeed = 1;

            sp.getPersistentData().putInt(REFORGE_SEED_KEY, newSeed);

            try {
                Method updateSeed = findMethod(menu.getClass(), "updateSeed");
                if (updateSeed != null) {
                    updateSeed.invoke(menu);
                } else {
                    Field seedField = findField(menu.getClass(), "seed");
                    if (seedField != null) {
                        seedField.setInt(menu, newSeed);
                    }
                }
            } catch (Exception ignored) {
            }

            menu.slotsChanged(null);
            menu.broadcastChanges();

            sp.serverLevel().playSound(
                    null,
                    sp.blockPosition(),
                    SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.BLOCKS,
                    0.75F,
                    1.2F + sp.getRandom().nextFloat() * 0.2F
            );

            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new RerollReforgePacket(newSeed));
        });
        ctx.get().setPacketHandled(true);
    }

    static Field findField(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    static Method findMethod(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Method m = cur.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}
