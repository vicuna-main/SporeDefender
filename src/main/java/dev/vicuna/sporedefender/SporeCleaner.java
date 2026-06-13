package dev.vicuna.sporedefender;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

final class SporeCleaner {
    private SporeCleaner() {
    }

    static BlockCleanResult restoreBlocks(ServerLevel level, CleanRange range) {
        SporeCduRestorer.prepareForScan();

        int restored = 0;
        int loadedChunks = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int chunkX = range.minChunkX(); chunkX <= range.maxChunkX(); chunkX++) {
            for (int chunkZ = range.minChunkZ(); chunkZ <= range.maxChunkZ(); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                loadedChunks++;
                restored += restoreChunk(level, range, chunk, mutable);
            }
        }

        return new BlockCleanResult(restored, loadedChunks);
    }

    static int purgeEntities(ServerLevel level, CleanRange range) {
        AABB area = new AABB(
                range.minX(),
                level.getMinBuildHeight(),
                range.minZ(),
                range.maxX() + 1.0D,
                level.getMaxBuildHeight(),
                range.maxZ() + 1.0D
        );
        int removed = 0;

        for (Entity entity : level.getEntities((Entity) null, area, entity -> isSporeEntity(entity) && isInRange(entity, range))) {
            entity.discard();
            removed++;
        }

        return removed;
    }

    private static int restoreChunk(ServerLevel level, CleanRange range, LevelChunk chunk, BlockPos.MutableBlockPos mutable) {
        int restored = 0;
        int minX = Math.max(range.minX(), chunk.getPos().getMinBlockX());
        int maxX = Math.min(range.maxX(), chunk.getPos().getMaxBlockX());
        int minZ = Math.max(range.minZ(), chunk.getPos().getMinBlockZ());
        int maxZ = Math.min(range.maxZ(), chunk.getPos().getMaxBlockZ());
        LevelChunkSection[] sections = chunk.getSections();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir() || !section.maybeHas(SporeCduRestorer::canRestore)) {
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
                        BlockState restoredState = SporeCduRestorer.restoreState(current);
                        if (restoredState != null && level.setBlock(mutable, restoredState, 3)) {
                            restored++;
                        }
                    }
                }
            }
        }

        return restored;
    }

    private static boolean isSporeEntity(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return "spore".equals(id.getNamespace());
    }

    private static boolean isInRange(Entity entity, CleanRange range) {
        return range.containsHorizontal(Mth.floor(entity.getX()), Mth.floor(entity.getZ()));
    }

    record BlockCleanResult(int restoredBlocks, int loadedChunks) {
    }
}
