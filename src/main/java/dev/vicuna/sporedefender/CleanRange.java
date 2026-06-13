package dev.vicuna.sporedefender;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

record CleanRange(
        BlockPos center,
        int minX,
        int maxX,
        int minZ,
        int maxZ,
        int minChunkX,
        int maxChunkX,
        int minChunkZ,
        int maxChunkZ,
        int horizontalRadius,
        Component description
) {
    static CleanRange blockRadius(BlockPos center, int radius) {
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        return new CleanRange(
                center,
                minX,
                maxX,
                minZ,
                maxZ,
                minX >> 4,
                maxX >> 4,
                minZ >> 4,
                maxZ >> 4,
                radius,
                Component.translatable("commands.sporedefender.range.radius", radius)
        );
    }

    static CleanRange chunkRadius(BlockPos center, int chunks) {
        ChunkPos centerChunk = new ChunkPos(center);
        int minChunkX = centerChunk.x - chunks;
        int maxChunkX = centerChunk.x + chunks;
        int minChunkZ = centerChunk.z - chunks;
        int maxChunkZ = centerChunk.z + chunks;
        return new CleanRange(
                center,
                minChunkX << 4,
                (maxChunkX << 4) + 15,
                minChunkZ << 4,
                (maxChunkZ << 4) + 15,
                minChunkX,
                maxChunkX,
                minChunkZ,
                maxChunkZ,
                -1,
                Component.translatable("commands.sporedefender.range.chunks", chunks)
        );
    }

    boolean containsHorizontal(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }
        if (horizontalRadius < 0) {
            return true;
        }

        long dx = (long) x - center.getX();
        long dz = (long) z - center.getZ();
        long radius = horizontalRadius;
        return dx * dx + dz * dz <= radius * radius;
    }
}
