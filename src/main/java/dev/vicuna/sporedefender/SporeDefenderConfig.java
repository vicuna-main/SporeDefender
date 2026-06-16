package dev.vicuna.sporedefender;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class SporeDefenderConfig {
    static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_REMOVABLE_BLOCKS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_REMOVABLE_ENTITIES;
    private static final ModConfigSpec.BooleanValue CLEAN_SPORE_ITEM_DROPS;
    private static final ModConfigSpec.BooleanValue PREVENT_SPORE_HOSTILE_MOBS_IN_CLAIMS;
    private static final ModConfigSpec.IntValue CLEAN_CHUNKS_PER_TICK;
    private static final ModConfigSpec.IntValue CLEAN_MAX_MILLIS_PER_TICK;
    private static final ModConfigSpec.IntValue CLEAN_PROGRESS_INTERVAL_TICKS;
    private static volatile NormalizedIds extraRemovableBlockIds = NormalizedIds.empty();
    private static volatile NormalizedIds extraRemovableEntityIds = NormalizedIds.empty();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("cleanup");
        EXTRA_REMOVABLE_BLOCKS = builder
                .comment("Additional block registry ids that /sporedefender prune and clean may remove. Invalid or missing ids are ignored.")
                .defineList(List.of("extraRemovableBlocks"), List::of, () -> "", SporeDefenderConfig::isResourceLocationString, ModConfigSpec.Range.of(0, Integer.MAX_VALUE));
        EXTRA_REMOVABLE_ENTITIES = builder
                .comment("Additional entity type registry ids that /sporedefender purge and clean may remove. Invalid or missing ids are ignored.")
                .defineList(List.of("extraRemovableEntities"), List::of, () -> "", SporeDefenderConfig::isResourceLocationString, ModConfigSpec.Range.of(0, Integer.MAX_VALUE));
        CLEAN_SPORE_ITEM_DROPS = builder
                .comment("When enabled, /sporedefender purge and clean also remove dropped item entities whose item registry namespace is spore.")
                .define("cleanSporeItemDrops", true);
        CLEAN_CHUNKS_PER_TICK = builder
                .comment("Maximum loaded or unloaded chunks an async cleanup command may check each server tick.")
                .defineInRange("cleanChunksPerTick", 64, 1, 4096);
        CLEAN_MAX_MILLIS_PER_TICK = builder
                .comment("Soft time budget in milliseconds for async cleanup work per server tick. One chunk may still finish even if it exceeds this budget.")
                .defineInRange("cleanMaxMillisPerTick", 10, 1, 50);
        CLEAN_PROGRESS_INTERVAL_TICKS = builder
                .comment("How often async cleanup commands send progress messages, in server ticks.")
                .defineInRange("cleanProgressIntervalTicks", 100, 20, 1200);
        builder.pop();

        builder.push("protection");
        PREVENT_SPORE_HOSTILE_MOBS_IN_CLAIMS = builder
                .comment("When enabled, new hostile Spore mobs are prevented from spawning inside non-wilderness GriefDefender claims.")
                .define("preventSporeHostileMobsInClaims", true);
        builder.pop();

        SPEC = builder.build();
    }

    private SporeDefenderConfig() {
    }

    static Set<String> extraRemovableBlockIds() {
        return cachedNormalizedIds(EXTRA_REMOVABLE_BLOCKS.get(), extraRemovableBlockIds, true);
    }

    static Set<String> extraRemovableEntityIds() {
        return cachedNormalizedIds(EXTRA_REMOVABLE_ENTITIES.get(), extraRemovableEntityIds, false);
    }

    static boolean cleanSporeItemDrops() {
        return CLEAN_SPORE_ITEM_DROPS.get();
    }

    static boolean preventSporeHostileMobsInClaims() {
        return PREVENT_SPORE_HOSTILE_MOBS_IN_CLAIMS.get();
    }

    static int cleanChunksPerTick() {
        return CLEAN_CHUNKS_PER_TICK.get();
    }

    static int cleanMaxMillisPerTick() {
        return CLEAN_MAX_MILLIS_PER_TICK.get();
    }

    static int cleanProgressIntervalTicks() {
        return CLEAN_PROGRESS_INTERVAL_TICKS.get();
    }

    private static Set<String> normalizedIds(List<? extends String> values) {
        return values.stream()
                .map(SporeDefenderConfig::normalizeId)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> cachedNormalizedIds(List<? extends String> values, NormalizedIds cached, boolean blockIds) {
        List<String> snapshot = List.copyOf(values);
        if (cached.values().equals(snapshot)) {
            return cached.ids();
        }

        NormalizedIds updated = new NormalizedIds(snapshot, normalizedIds(snapshot));
        if (blockIds) {
            extraRemovableBlockIds = updated;
        } else {
            extraRemovableEntityIds = updated;
        }
        return updated.ids();
    }

    private static Optional<String> normalizeId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? Optional.empty() : Optional.of(id.toString());
    }

    private static boolean isResourceLocationString(Object value) {
        return value instanceof String text && ResourceLocation.tryParse(text) != null;
    }

    private record NormalizedIds(List<String> values, Set<String> ids) {
        private static NormalizedIds empty() {
            return new NormalizedIds(List.of(), Set.of());
        }
    }
}
