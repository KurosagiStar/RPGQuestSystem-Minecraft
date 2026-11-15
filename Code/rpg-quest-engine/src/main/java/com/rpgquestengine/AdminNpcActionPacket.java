package com.rpgquestengine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.function.Supplier;

public class AdminNpcActionPacket {
    private final String action;
    private final String npcId;

    public AdminNpcActionPacket(String action, String npcId) {
        this.action = action;
        this.npcId = npcId;
    }

    public static void encode(AdminNpcActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action);
        buf.writeUtf(msg.npcId);
    }

    public static AdminNpcActionPacket decode(FriendlyByteBuf buf) {
        return new AdminNpcActionPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(AdminNpcActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !sp.hasPermissions(2)) return;

            double x = sp.getX();
            double y = sp.getY();
            double z = sp.getZ();
            float yRot = sp.getYRot();
            float xRot = sp.getXRot();

            if ("SPAWN".equals(msg.action)) {
                String cmd = "easy_npc preset import custom easy_npc:preset/villager/" + msg.npcId + ".npc.snbt";
                sp.getServer().getCommands().performPrefixedCommand(
                    sp.createCommandSourceStack().withPermission(4), cmd);
                QuestConfig.setNpcLocation(msg.npcId, x, y, z);
                sp.sendSystemMessage(Component.literal("§a[RPGQuestEngine] NPC " + msg.npcId + " spawneado y guardado."));
            } else if ("RESPAWN_ALL".equals(msg.action)) {
                sp.getServer().getCommands().performPrefixedCommand(
                    sp.createCommandSourceStack().withPermission(2), "rpgquest respawn_saved");
            } else if ("MOVE".equals(msg.action)) {
                double[] oldPos = QuestConfig.NPC_LOCATIONS.get(msg.npcId);
                if (oldPos != null && oldPos.length >= 3) {
                    sp.serverLevel().getChunk(((int) Math.floor(oldPos[0])) >> 4, ((int) Math.floor(oldPos[2])) >> 4);
                }
                int moved = 0;
                for (ServerLevel level : sp.getServer().getAllLevels()) {
                    for (Entity ent : level.getAllEntities()) {
                        if (ent.getTags().contains(msg.npcId)) {
                            ent.teleportTo(sp.serverLevel(), x, y, z, java.util.Set.of(), yRot, xRot);
                            moved++;
                        }
                    }
                }
                QuestConfig.setNpcLocation(msg.npcId, x, y, z);
                if (moved > 0) {
                    sp.sendSystemMessage(Component.literal("§a[RPGQuestEngine] Movido " + msg.npcId + " (" + moved + ") a tu posición."));
                } else {
                    String cmd = "easy_npc preset import custom easy_npc:preset/villager/" + msg.npcId + ".npc.snbt";
                    sp.getServer().getCommands().performPrefixedCommand(
                        sp.createCommandSourceStack().withPermission(4), cmd);
                    sp.sendSystemMessage(Component.literal("§a[RPGQuestEngine] Entidad no cargada; spawneado " + msg.npcId + " en tu posición."));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

