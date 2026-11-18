package com.rpgquestengine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class DailyQuestManager {
    public static class DailyTaskDef {
        public final String id;
        public final String type; // LOGIN, KILL_HOSTILE, MINE_BLOCK, CRAFT_ITEM, NPC_TURNIN
        public final String title;
        public final int target;

        public DailyTaskDef(String id, String type, String title, int target) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.target = target;
        }
    }

    private static final DailyTaskDef[] POOL = new DailyTaskDef[]{
            new DailyTaskDef("daily_login", "LOGIN", "Entra al mundo hoy", 1),
            new DailyTaskDef("daily_kill_10", "KILL_HOSTILE", "Derrota 10 monstruos hostiles", 10),
            new DailyTaskDef("daily_mine_20", "MINE_BLOCK", "Mina 20 bloques o menas", 20),
            new DailyTaskDef("daily_npc_1", "NPC_TURNIN", "Completa 1 misión de NPC", 1),
            new DailyTaskDef("daily_craft_6", "CRAFT_ITEM", "Fabrica 6 objetos en mesa", 6),
            new DailyTaskDef("daily_kill_15", "KILL_HOSTILE", "Abate 15 enemigos hostiles", 15),
            new DailyTaskDef("daily_mine_30", "MINE_BLOCK", "Extrae 30 bloques bajo tierra", 30),
    };

    public static List<DailyTaskDef> getTasksForDay(long dayEpoch) {
        List<DailyTaskDef> res = new ArrayList<>(4);
        res.add(POOL[0]); // Siempre incluir Login diario
        int offset = (int) (Math.abs(dayEpoch) % 3);
        res.add(POOL[1 + (offset % 2 == 0 ? 0 : 4)]); // Kill
        res.add(POOL[2 + (offset == 1 ? 4 : 0)]);     // Mine
        res.add(offset == 2 ? POOL[4] : POOL[3]);     // NPC o Craft
        return res;
    }

    public static long getCurrentDayEpoch() {
        return LocalDate.now().toEpochDay();
    }

    public static int getSecondsUntilReset() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT);
        return (int) Math.max(60, Duration.between(now, tomorrow).getSeconds());
    }

    private static CompoundTag getOrResetData(ServerPlayer sp) {
        CompoundTag root = sp.getPersistentData();
        CompoundTag daily = root.getCompound("rqe_daily");
        long today = getCurrentDayEpoch();
        if (daily.getLong("day") != today) {
            daily = new CompoundTag();
            daily.putLong("day", today);
            daily.putInt("points", 0);
            daily.putInt("milestones", 0);
            daily.putInt("claimedMask", 0);
            root.put("rqe_daily", daily);
        }
        // Asegurar progreso 1/1 para LOGIN
        daily.putInt("prog_daily_login", 1);
        root.put("rqe_daily", daily);
        return daily;
    }

    public static void onTaskProgress(ServerPlayer sp, String type, int amount) {
        if (sp == null || amount <= 0) return;
        CompoundTag daily = getOrResetData(sp);
        List<DailyTaskDef> tasks = getTasksForDay(daily.getLong("day"));
        boolean changed = false;
        for (DailyTaskDef def : tasks) {
            if (def.type.equals(type)) {
                String key = "prog_" + def.id;
                int cur = daily.getInt(key);
                if (cur < def.target) {
                    int next = Math.min(def.target, cur + amount);
                    daily.putInt(key, next);
                    changed = true;
                }
            }
        }
        if (changed) {
            syncToPlayer(sp);
        }
    }

    public static void claimTask(ServerPlayer sp, int taskIdx) {
        if (sp == null || taskIdx < 0 || taskIdx >= 4) return;
        CompoundTag daily = getOrResetData(sp);
        List<DailyTaskDef> tasks = getTasksForDay(daily.getLong("day"));
        DailyTaskDef def = tasks.get(taskIdx);

        int claimedMask = daily.getInt("claimedMask");
        if ((claimedMask & (1 << taskIdx)) != 0) return;
        int prog = daily.getInt("prog_" + def.id);
        if (prog < def.target) return;

        claimedMask |= (1 << taskIdx);
        daily.putInt("claimedMask", claimedMask);
        int newPoints = Math.min(400, daily.getInt("points") + 100);
        daily.putInt("points", newPoints);
        sp.sendSystemMessage(Component.literal("§a[Misión Diaria] §f" + def.title + " §e(+100 Pts)"));

        // Auto-desbloquear y entregar hitos alcanzados (100, 200, 300, 400)
        int msMask = daily.getInt("milestones");
        for (int m = 0; m < 4; m++) {
            int threshold = (m + 1) * 100;
            if (newPoints >= threshold && (msMask & (1 << m)) == 0) {
                msMask |= (1 << m);
                grantMilestoneReward(sp, m);
            }
        }
        daily.putInt("milestones", msMask);
        syncToPlayer(sp);
    }

    private static void grantMilestoneReward(ServerPlayer sp, int milestoneIdx) {
        switch (milestoneIdx) {
            case 0 -> {
                giveItem(sp, "cisco_mod:champion_coin", 1);
                sp.giveExperiencePoints(100);
                sp.sendSystemMessage(Component.literal("§6[Hito Diario 100] §fRecibiste: §e1x Moneda de Campeón + 100 XP"));
            }
            case 1 -> {
                giveItem(sp, "apotheosis:gem_dust", 2);
                giveItem(sp, "apotheosis:common_material", 1);
                sp.sendSystemMessage(Component.literal("§6[Hito Diario 200] §fRecibiste: §e2x Gem Dust + 1x Common Material"));
            }
            case 2 -> {
                giveItem(sp, "cisco_mod:champion_coin", 2);
                giveItem(sp, "apotheosis:uncommon_material", 2);
                sp.sendSystemMessage(Component.literal("§6[Hito Diario 300] §fRecibiste: §e2x Moneda de Campeón + 2x Uncommon Material"));
            }
            case 3 -> {
                giveItem(sp, "apotheosis:sigil_of_socketing", 1);
                sp.giveExperiencePoints(250);
                sp.sendSystemMessage(Component.literal("§6[Hito Diario 400] §fRecibiste: §d1x Sigil of Socketing + 250 XP"));
            }
        }
    }

    private static void giveItem(ServerPlayer sp, String itemId, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item != null) {
            ItemStack st = new ItemStack(item, count);
            if (!sp.getInventory().add(st)) {
                sp.drop(st, false);
            }
        }
    }

    public static void syncToPlayer(ServerPlayer sp) {
        if (sp == null) return;
        CompoundTag daily = getOrResetData(sp);
        long day = daily.getLong("day");
        List<DailyTaskDef> defs = getTasksForDay(day);
        int claimedMask = daily.getInt("claimedMask");

        List<SyncDailyQuestsPacket.DailyEntry> entries = new ArrayList<>(4);
        for (int i = 0; i < defs.size(); i++) {
            DailyTaskDef d = defs.get(i);
            int prog = daily.getInt("prog_" + d.id);
            boolean claimed = (claimedMask & (1 << i)) != 0;
            entries.add(new SyncDailyQuestsPacket.DailyEntry(d.id, d.title, prog, d.target, claimed));
        }

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new SyncDailyQuestsPacket(
                        daily.getInt("points"),
                        daily.getInt("milestones"),
                        getSecondsUntilReset(),
                        entries
                ));
    }
}
