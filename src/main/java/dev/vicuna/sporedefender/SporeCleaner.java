package dev.vicuna.sporedefender;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class SporeCleaner {
    private static volatile Field areaEffectCloudPotionContentsField;
    private static volatile boolean areaEffectCloudPotionContentsLookupAttempted;

    private SporeCleaner() {
    }

    static BlockCleanResult restoreBlocks(ServerLevel level, CleanRange range) {
        return cleanBlocks(level, range, false);
    }

    static BlockCleanResult cleanseBlocks(ServerLevel level, CleanRange range) {
        return cleanBlocks(level, range, true);
    }

    static BlockCleanResult removeNaturalSpreadBlocks(ServerLevel level, CleanRange range) {
        SporeCduRestorer.prepareForScan();
        SporeNaturalSpreadBlocks.prepareForScan();

        int removed = 0;
        int loadedChunks = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int chunkX = range.minChunkX(); chunkX <= range.maxChunkX(); chunkX++) {
            for (int chunkZ = range.minChunkZ(); chunkZ <= range.maxChunkZ(); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                loadedChunks++;
                removed += cleanChunk(level, range, chunk, mutable, false, true).removedSpreadBlocks();
            }
        }

        return new BlockCleanResult(0, removed, loadedChunks);
    }

    private static BlockCleanResult cleanBlocks(ServerLevel level, CleanRange range, boolean removeNaturalSpread) {
        SporeCduRestorer.prepareForScan();
        if (removeNaturalSpread) {
            SporeNaturalSpreadBlocks.prepareForScan();
        }

        int restored = 0;
        int removed = 0;
        int loadedChunks = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int chunkX = range.minChunkX(); chunkX <= range.maxChunkX(); chunkX++) {
            for (int chunkZ = range.minChunkZ(); chunkZ <= range.maxChunkZ(); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                loadedChunks++;
                BlockCleanResult result = cleanChunk(level, range, chunk, mutable, true, removeNaturalSpread);
                restored += result.restoredBlocks();
                removed += result.removedSpreadBlocks();
            }
        }

        return new BlockCleanResult(restored, removed, loadedChunks);
    }

    static BlockCleanResult cleanLoadedChunk(ServerLevel level, CleanRange range, LevelChunk chunk, boolean restoreInfected, boolean removeNaturalSpread) {
        return cleanChunk(level, range, chunk, new BlockPos.MutableBlockPos(), restoreInfected, removeNaturalSpread);
    }

    static int purgeEntities(ServerLevel level, CleanRange range) {
        Set<String> extraRemovableEntityIds = SporeDefenderConfig.extraRemovableEntityIds();
        boolean includeSporeItemDrops = SporeDefenderConfig.cleanSporeItemDrops();
        int removed = 0;
        for (Entity entity : level.getEntities((Entity) null, wholeHeightArea(level, range), entity -> SporeEntityRules.shouldPurge(entity, extraRemovableEntityIds, includeSporeItemDrops) && isInRange(entity, range))) {
            entity.discard();
            removed++;
        }

        return removed;
    }

    static int purgeEntitiesInChunk(ServerLevel level, CleanRange range, int chunkX, int chunkZ) {
        Set<String> extraRemovableEntityIds = SporeDefenderConfig.extraRemovableEntityIds();
        boolean includeSporeItemDrops = SporeDefenderConfig.cleanSporeItemDrops();
        ChunkSlice slice = chunkSlice(level, range, chunkX, chunkZ);
        int removed = 0;
        for (Entity entity : level.getEntities((Entity) null, slice.area(), entity -> slice.contains(entity) && SporeEntityRules.shouldPurge(entity, extraRemovableEntityIds, includeSporeItemDrops))) {
            entity.discard();
            removed++;
        }

        return removed;
    }

    static EffectCleanResult cureEffects(ServerLevel level, CleanRange range) {
        AABB area = wholeHeightArea(level, range);
        int removedEffects = 0;
        int removedClouds = 0;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, entity -> isInRange(entity, range))) {
            removedEffects += removeSporeEffects(entity);
        }

        for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, area, cloud -> isInRange(cloud, range) && hasSporeEffect(cloud))) {
            cloud.discard();
            removedClouds++;
        }

        return new EffectCleanResult(removedEffects, removedClouds);
    }

    static EffectCleanResult cureEffectsInChunk(ServerLevel level, CleanRange range, int chunkX, int chunkZ) {
        ChunkSlice slice = chunkSlice(level, range, chunkX, chunkZ);
        int removedEffects = 0;
        int removedClouds = 0;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, slice.area(), slice::contains)) {
            removedEffects += removeSporeEffects(entity);
        }

        for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, slice.area(), cloud -> slice.contains(cloud) && hasSporeEffect(cloud))) {
            cloud.discard();
            removedClouds++;
        }

        return new EffectCleanResult(removedEffects, removedClouds);
    }

    private static AABB wholeHeightArea(ServerLevel level, CleanRange range) {
        return new AABB(
                range.minX(),
                level.getMinBuildHeight(),
                range.minZ(),
                range.maxX() + 1.0D,
                level.getMaxBuildHeight(),
                range.maxZ() + 1.0D
        );
    }

    private static ChunkSlice chunkSlice(ServerLevel level, CleanRange range, int chunkX, int chunkZ) {
        int minX = Math.max(range.minX(), chunkX << 4);
        int maxX = Math.min(range.maxX(), (chunkX << 4) + 15);
        int minZ = Math.max(range.minZ(), chunkZ << 4);
        int maxZ = Math.min(range.maxZ(), (chunkZ << 4) + 15);
        return new ChunkSlice(
                minX,
                maxX,
                minZ,
                maxZ,
                range,
                new AABB(
                        minX,
                        level.getMinBuildHeight(),
                        minZ,
                        maxX + 1.0D,
                        level.getMaxBuildHeight(),
                        maxZ + 1.0D
                )
        );
    }

    private static BlockCleanResult cleanChunk(ServerLevel level, CleanRange range, LevelChunk chunk, BlockPos.MutableBlockPos mutable, boolean restoreInfected, boolean removeNaturalSpread) {
        int restored = 0;
        int removed = 0;
        int minX = Math.max(range.minX(), chunk.getPos().getMinBlockX());
        int maxX = Math.min(range.maxX(), chunk.getPos().getMaxBlockX());
        int minZ = Math.max(range.minZ(), chunk.getPos().getMinBlockZ());
        int maxZ = Math.min(range.maxZ(), chunk.getPos().getMaxBlockZ());
        LevelChunkSection[] sections = chunk.getSections();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir() || !sectionMayContainCleanableBlocks(section, restoreInfected, removeNaturalSpread)) {
                continue;
            }

            int sectionY = level.getSectionYFromSectionIndex(sectionIndex);
            int minY = Math.max(level.getMinBuildHeight(), SectionPos.sectionToBlockCoord(sectionY));
            int maxY = Math.min(level.getMaxBuildHeight() - 1, minY + 15);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!range.containsHorizontal(x, z)) {
                        continue;
                    }

                    for (int y = minY; y <= maxY; y++) {
                        mutable.set(x, y, z);
                        BlockState current = chunk.getBlockState(mutable);
                        BlockState replacement = replacementState(current, restoreInfected, removeNaturalSpread);
                        if (replacement == null) {
                            continue;
                        }

                        if (level.setBlock(mutable, replacement, 3)) {
                            if (replacement.isAir()) {
                                removed++;
                            } else {
                                restored++;
                            }
                        }
                    }
                }
            }
        }

        return new BlockCleanResult(restored, removed, 0);
    }

    private static boolean sectionMayContainCleanableBlocks(LevelChunkSection section, boolean restoreInfected, boolean removeNaturalSpread) {
        return section.maybeHas(state ->
                (restoreInfected && SporeCduRestorer.canRestore(state))
                        || (removeNaturalSpread && SporeNaturalSpreadBlocks.canRemove(state)));
    }

    private static BlockState replacementState(BlockState current, boolean restoreInfected, boolean removeNaturalSpread) {
        if (restoreInfected) {
            BlockState restoredState = SporeCduRestorer.restoreState(current);
            if (restoredState != null) {
                return restoredState;
            }
        }

        if (removeNaturalSpread && SporeNaturalSpreadBlocks.canRemove(current)) {
            return Blocks.AIR.defaultBlockState();
        }

        return null;
    }

    private static int removeSporeEffects(LivingEntity entity) {
        int removed = 0;
        List<MobEffectInstance> effects = new ArrayList<>(entity.getActiveEffects());
        for (MobEffectInstance effect : effects) {
            if (isSporeEffect(effect)) {
                if (entity.removeEffect(effect.getEffect())) {
                    removed++;
                }
            }
        }
        return removed;
    }

    private static boolean hasSporeEffect(AreaEffectCloud cloud) {
        PotionContents contents = areaEffectCloudPotionContents(cloud);
        if (contents == null || !contents.hasEffects()) {
            return false;
        }

        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (isSporeEffect(effect)) {
                return true;
            }
        }
        return false;
    }

    private static PotionContents areaEffectCloudPotionContents(AreaEffectCloud cloud) {
        Field field = areaEffectCloudPotionContentsField();
        if (field == null) {
            return null;
        }

        try {
            Object value = field.get(cloud);
            return value instanceof PotionContents contents ? contents : null;
        } catch (IllegalAccessException exception) {
            return null;
        }
    }

    private static Field areaEffectCloudPotionContentsField() {
        if (areaEffectCloudPotionContentsLookupAttempted) {
            return areaEffectCloudPotionContentsField;
        }

        try {
            Field field = AreaEffectCloud.class.getDeclaredField("potionContents");
            field.setAccessible(true);
            areaEffectCloudPotionContentsField = field;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            areaEffectCloudPotionContentsField = null;
        } finally {
            areaEffectCloudPotionContentsLookupAttempted = true;
        }
        return areaEffectCloudPotionContentsField;
    }

    private static boolean isSporeEffect(MobEffectInstance effect) {
        return isSporeEffect(effect.getEffect().value());
    }

    private static boolean isSporeEffect(MobEffect effect) {
        return SporeRegistries.isSpore(SporeRegistries.effectId(effect));
    }

    private static boolean isInRange(Entity entity, CleanRange range) {
        return range.containsHorizontal(Mth.floor(entity.getX()), Mth.floor(entity.getZ()));
    }

    record BlockCleanResult(int restoredBlocks, int removedSpreadBlocks, int loadedChunks) {
    }

    record EffectCleanResult(int removedEffects, int removedClouds) {
    }

    private record ChunkSlice(int minX, int maxX, int minZ, int maxZ, CleanRange range, AABB area) {
        boolean contains(Entity entity) {
            int x = Mth.floor(entity.getX());
            int z = Mth.floor(entity.getZ());
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ && range.containsHorizontal(x, z);
        }
    }
}
