package com.rpgquestengine;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class TownSpawner {
    private static final int MIN_DISTANCE = 5000;
    private static final int MAX_REPEATS = 3;
    private static final List<String> TOWN_POOL = Arrays.asList(
            "villetyta1.schem",
            "karttrad.schem",
            "medievalhouses.schem"
    );

    public static class TownSavedData extends SavedData {
        public boolean spawnTownPlaced = false;
        public final List<int[]> placedCoords = new ArrayList<>();
        public final Map<String, Integer> counts = new HashMap<>();

        public static TownSavedData load(CompoundTag tag) {
            TownSavedData data = new TownSavedData();
            data.spawnTownPlaced = tag.getBoolean("SpawnTownPlaced");
            ListTag list = tag.getList("Placed", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                data.placedCoords.add(new int[]{c.getInt("x"), c.getInt("z")});
            }
            CompoundTag cntTag = tag.getCompound("Counts");
            for (String k : cntTag.getAllKeys()) {
                data.counts.put(k, cntTag.getInt(k));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("SpawnTownPlaced", spawnTownPlaced);
            ListTag list = new ListTag();
            for (int[] xz : placedCoords) {
                CompoundTag c = new CompoundTag();
                c.putInt("x", xz[0]);
                c.putInt("z", xz[1]);
                list.add(c);
            }
            tag.put("Placed", list);
            CompoundTag cntTag = new CompoundTag();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                cntTag.putInt(e.getKey(), e.getValue());
            }
            tag.put("Counts", cntTag);
            return tag;
        }

        public static TownSavedData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(TownSavedData::load, TownSavedData::new, "rpgquestengine_towns");
        }
    }

    public static void trySpawnInitialTown(ServerLevel overworld) {
        if (overworld == null || overworld.dimension() != Level.OVERWORLD) return;
        TownSavedData data = TownSavedData.get(overworld);
        if (data.spawnTownPlaced) return;

        File schem = new File(FMLPaths.CONFIGDIR.get().toFile(), "rpgquestengine/towns/Medieval spawn.schem");
        if (!schem.exists()) return;

        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        if (y < 60) y = 64;
        BlockPos origin = new BlockPos(-32, y - 2, -32);
        int[] dims = pasteSchematic(overworld, schem, origin);
        if (dims != null) {
            int centerX = origin.getX() + dims[0] / 2;
            int centerZ = origin.getZ() + dims[2] / 2;
            int spawnY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ);
            overworld.setDefaultSpawnPos(new BlockPos(centerX, Math.max(spawnY, y) + 1, centerZ), 0.0F);
            spawnTownVillagers(overworld, centerX, centerZ, 25);
            data.spawnTownPlaced = true;
            data.placedCoords.add(new int[]{centerX, centerZ});
            data.setDirty();
        }
    }

    public static void tickPlayerExploration(ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (sp.tickCount % 100 != 0) return;

        TownSavedData data = TownSavedData.get(level);
        if (!data.spawnTownPlaced) {
            trySpawnInitialTown(level);
        }

        int px = sp.getBlockX();
        int pz = sp.getBlockZ();
        for (int[] xz : data.placedCoords) {
            long dx = px - xz[0];
            long dz = pz - xz[1];
            if (dx * dx + dz * dz < (long) MIN_DISTANCE * MIN_DISTANCE) {
                return;
            }
        }

        List<String> available = new ArrayList<>();
        for (String name : TOWN_POOL) {
            if (data.counts.getOrDefault(name, 0) < MAX_REPEATS) {
                available.add(name);
            }
        }
        if (available.isEmpty()) return;

        String chosen = available.get(level.random.nextInt(available.size()));
        File schem = new File(FMLPaths.CONFIGDIR.get().toFile(), "rpgquestengine/towns/" + chosen);
        if (!schem.exists()) return;

        int targetX = px + 64;
        int targetZ = pz + 64;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
        if (y < 60) y = 63;

        int[] dims = pasteSchematic(level, schem, new BlockPos(targetX, y - 2, targetZ));
        if (dims != null) {
            int cx = targetX + dims[0] / 2;
            int cz = targetZ + dims[2] / 2;
            spawnTownVillagers(level, cx, cz, 25);
            data.placedCoords.add(new int[]{cx, cz});
            data.counts.put(chosen, data.counts.getOrDefault(chosen, 0) + 1);
            data.setDirty();
        }
    }

    private static void spawnTownVillagers(ServerLevel level, int centerX, int centerZ, int count) {
        for (int i = 0; i < count; i++) {
            int vx = centerX + level.random.nextInt(49) - 24;
            int vz = centerZ + level.random.nextInt(49) - 24;
            int vy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, vx, vz);
            var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            if (villager != null) {
                villager.moveTo(vx + 0.5, vy + 0.5, vz + 0.5, level.random.nextFloat() * 360F, 0.0F);
                villager.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(vx, vy, vz)),
                        net.minecraft.world.entity.MobSpawnType.STRUCTURE, null, null);
                level.addFreshEntity(villager);
            }
        }
    }

    private static int[] pasteSchematic(ServerLevel level, File file, BlockPos origin) {
        try (FileInputStream fis = new FileInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(fis);
            if (root.contains("Schematic")) root = root.getCompound("Schematic");

            int width = root.getShort("Width") & 0xFFFF;
            int height = root.getShort("Height") & 0xFFFF;
            int length = root.getShort("Length") & 0xFFFF;
            if (width <= 0 || height <= 0 || length <= 0) return null;

            CompoundTag paletteTag = root.getCompound("Palette");
            Map<Integer, BlockState> palette = new HashMap<>();
            var blockLookup = level.holderLookup(Registries.BLOCK);
            for (String key : paletteTag.getAllKeys()) {
                int id = paletteTag.getInt(key);
                try {
                    BlockState state = BlockStateParser.parseForBlock(blockLookup, key, false).blockState();
                    palette.put(id, state);
                } catch (Exception e) {
                    palette.put(id, Blocks.AIR.defaultBlockState());
                }
            }

            byte[] blockData = root.getByteArray("BlockData");
            int idx = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        if (idx >= blockData.length) break;
                        int value = 0;
                        int varintLen = 0;
                        while (idx < blockData.length) {
                            byte b = blockData[idx++];
                            value |= (b & 127) << (varintLen * 7);
                            varintLen++;
                            if ((b & 128) == 0) break;
                        }
                        BlockState state = palette.get(value);
                        if (state != null && !state.isAir()) {
                            level.setBlock(origin.offset(x, y, z), state, 2);
                        }
                    }
                }
            }
            return new int[]{width, height, length};
        } catch (Exception e) {
            System.err.println("[RPGQuestEngine] Error pasting schematic " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }
}

