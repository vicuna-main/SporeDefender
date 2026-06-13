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
        return normalizedIds(EXTRA_REMOVABLE_BLOCKS.get());
    }

    static boolean isExtraRemovableEntity(ResourceLocation id) {
        return extraRemovableEntityIds().contains(id.toString());
    }

    static Set<String> extraRemovableEntityIds() {
        return normalizedIds(EXTRA_REMOVABLE_ENTITIES.get());
    }

    static boolean cleanSporeItemDrops() {
        return CLEAN_SPORE_ITEM_DROPS.get();
    }

    static boolean preventSporeHostileMobsInClaims() {
        return PREVENT_SPORE_HOSTILE_MOBS_IN_CLAIMS.get();
    }

    private static Set<String> normalizedIds(List<? extends String> values) {
        return values.stream()
                .map(SporeDefenderConfig::normalizeId)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<String> normalizeId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? Optional.empty() : Optional.of(id.toString());
    }

    private static boolean isResourceLocationString(Object value) {
        return value instanceof String text && ResourceLocation.tryParse(text) != null;
    }
}
