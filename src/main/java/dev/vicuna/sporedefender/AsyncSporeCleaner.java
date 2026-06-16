package dev.vicuna.sporedefender;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;

final class AsyncSporeCleaner {
    private static final Deque<CleanJob> JOBS = new ArrayDeque<>();
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private AsyncSporeCleaner() {
    }

    static int enqueue(CommandSourceStack source, Operation operation, CleanRange range) {
        CleanJob job = new CleanJob(source, source.getLevel(), operation, range);
        JOBS.addLast(job);
        int queuePosition = JOBS.size();
        source.sendSuccess(() -> Component.translatable(
                "commands.sporedefender.job.queued",
                operation.displayName(),
                range.description(),
                range.chunkCount(),
                queuePosition
        ), true);
        return 1;
    }

    static void onServerTick(ServerTickEvent.Post event) {
        CleanJob job = JOBS.peekFirst();
        if (job == null || job.server() != event.getServer()) {
            return;
        }

        int processed = 0;
        int chunkBudget = SporeDefenderConfig.cleanChunksPerTick();
        long deadline = System.nanoTime() + (long) SporeDefenderConfig.cleanMaxMillisPerTick() * NANOS_PER_MILLI;
        while (!job.isComplete() && processed < chunkBudget && (processed == 0 || event.hasTime()) && (processed == 0 || System.nanoTime() < deadline)) {
            job.processNextChunk();
            processed++;
        }

        job.tick();
        if (job.isComplete()) {
            JOBS.removeFirst();
            job.sendComplete();
        } else if (job.shouldSendProgress()) {
            job.sendProgress();
        }
    }

    enum Operation {
        RESTORE(true, false, false, false, "commands.sporedefender.operation.restore"),
        PRUNE(false, true, false, false, "commands.sporedefender.operation.prune"),
        PURGE(false, false, true, false, "commands.sporedefender.operation.purge"),
        CURE(false, false, false, true, "commands.sporedefender.operation.cure"),
        CLEAN(true, true, true, true, "commands.sporedefender.operation.clean");

        private final boolean restoreBlocks;
        private final boolean removeSpreadBlocks;
        private final boolean purgeEntities;
        private final boolean cureEffects;
        private final String translationKey;

        Operation(boolean restoreBlocks, boolean removeSpreadBlocks, boolean purgeEntities, boolean cureEffects, String translationKey) {
            this.restoreBlocks = restoreBlocks;
            this.removeSpreadBlocks = removeSpreadBlocks;
            this.purgeEntities = purgeEntities;
            this.cureEffects = cureEffects;
            this.translationKey = translationKey;
        }

        Component displayName() {
            return Component.translatable(translationKey);
        }

        boolean cleansBlocks() {
            return restoreBlocks || removeSpreadBlocks;
        }
    }

    private static final class CleanJob {
        private final CommandSourceStack source;
        private final ServerLevel level;
        private final Operation operation;
        private final CleanRange range;
        private final MinecraftServer server;
        private int nextChunkX;
        private int nextChunkZ;
        private long checkedChunks;
        private long loadedChunks;
        private int restoredBlocks;
        private int removedSpreadBlocks;
        private int removedEntities;
        private int removedEffects;
        private int removedClouds;
        private int ticks;
        private int lastProgressTick;

        private CleanJob(CommandSourceStack source, ServerLevel level, Operation operation, CleanRange range) {
            this.source = source;
            this.level = level;
            this.operation = operation;
            this.range = range;
            this.server = source.getServer();
            this.nextChunkX = range.minChunkX();
            this.nextChunkZ = range.minChunkZ();
            prepareCaches(operation);
        }

        private MinecraftServer server() {
            return server;
        }

        private boolean isComplete() {
            return nextChunkZ > range.maxChunkZ();
        }

        private void processNextChunk() {
            int chunkX = nextChunkX;
            int chunkZ = nextChunkZ;
            advanceChunkCursor();
            checkedChunks++;

            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                return;
            }

            loadedChunks++;
            if (operation.cleansBlocks()) {
                SporeCleaner.BlockCleanResult blocks = SporeCleaner.cleanLoadedChunk(level, range, chunk, operation.restoreBlocks, operation.removeSpreadBlocks);
                restoredBlocks += blocks.restoredBlocks();
                removedSpreadBlocks += blocks.removedSpreadBlocks();
            }
            if (operation.purgeEntities) {
                removedEntities += SporeCleaner.purgeEntitiesInChunk(level, range, chunkX, chunkZ);
            }
            if (operation.cureEffects) {
                SporeCleaner.EffectCleanResult effects = SporeCleaner.cureEffectsInChunk(level, range, chunkX, chunkZ);
                removedEffects += effects.removedEffects();
                removedClouds += effects.removedClouds();
            }
        }

        private void advanceChunkCursor() {
            if (nextChunkX >= range.maxChunkX()) {
                nextChunkX = range.minChunkX();
                nextChunkZ++;
            } else {
                nextChunkX++;
            }
        }

        private void tick() {
            ticks++;
        }

        private boolean shouldSendProgress() {
            int interval = SporeDefenderConfig.cleanProgressIntervalTicks();
            if (ticks - lastProgressTick < interval) {
                return false;
            }
            lastProgressTick = ticks;
            return true;
        }

        private void sendProgress() {
            source.sendSuccess(() -> Component.translatable(
                    "commands.sporedefender.job.progress",
                    operation.displayName(),
                    checkedChunks,
                    range.chunkCount(),
                    loadedChunks,
                    restoredBlocks,
                    removedSpreadBlocks,
                    removedEntities,
                    removedEffects,
                    removedClouds
            ), false);
        }

        private void sendComplete() {
            source.sendSuccess(() -> Component.translatable(
                    "commands.sporedefender.job.complete",
                    operation.displayName(),
                    checkedChunks,
                    loadedChunks,
                    restoredBlocks,
                    removedSpreadBlocks,
                    removedEntities,
                    removedEffects,
                    removedClouds
            ), true);
        }

        private static void prepareCaches(Operation operation) {
            if (operation.restoreBlocks || operation.removeSpreadBlocks) {
                SporeCduRestorer.prepareForScan();
            }
            if (operation.removeSpreadBlocks) {
                SporeNaturalSpreadBlocks.prepareForScan();
            }
        }
    }
}
