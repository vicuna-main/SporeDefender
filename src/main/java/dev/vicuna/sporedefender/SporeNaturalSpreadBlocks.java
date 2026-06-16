package dev.vicuna.sporedefender;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class SporeNaturalSpreadBlocks {
    private static final Set<String> DEFAULT_NATURAL_SPREAD_IDS = Set.of(
            "minecraft:fire",
            "spore:acid",
            "spore:bile",
            "spore:crusted_bile",
            "spore:growths_big",
            "spore:growths_small",
            "spore:fungal_stem_sapling",
            "spore:blomfung",
            "spore:bloomfung2",
            "spore:fungal_stem",
            "spore:underwater_fungal_stem",
            "spore:hanging_fungal_stem",
            "spore:fungal_stem_top",
            "spore:underwater_fungal_stem_top",
            "spore:fungal_roots",
            "spore:acidic_sack",
            "spore:hand",
            "spore:lungs",
            "spore:glowshroom",
            "spore:growth_mycelium",
            "spore:wall_growths",
            "spore:wall_growths_big",
            "spore:wall_growths_fleshy",
            "spore:vocals",
            "spore:mycelium_veins",
            "spore:biomass_bulb",
            "spore:overgrown_spawner",
            "spore:brain_remnants",
            "spore:outpost_watcher",
            "spore:rooted_biomass",
            "spore:biomass_block",
            "spore:sicken_biomass_block",
            "spore:gastric_biomass_block",
            "spore:calcified_biomass_block",
            "spore:membrane_block",
            "spore:rooted_mycelium",
            "spore:fungal_shell",
            "spore:mycelium_block",
            "spore:mycelium_slab",
            "spore:remains",
            "spore:wall_remains",
            "spore:frozen_remains",
            "spore:freeze_burned_biomass",
            "spore:frozen_burned_biomass",
            "spore:cerebrum_block",
            "spore:innards_block",
            "spore:heart_block",
            "spore:braio_block",
            "spore:organite",
            "spore:drowned_lump",
            "spore:bile_lump",
            "spore:fang_lump",
            "spore:exploding_lump",
            "spore:poisoning_lump",
            "spore:biomass_lump",
            "spore:hive_spawn",
            "spore:fungal_clamp",
            "spore:rotten_log",
            "spore:rotten_planks",
            "spore:rotten_stair",
            "spore:rotten_slab",
            "spore:rotten_scraps",
            "spore:rotten_branch",
            "spore:rotten_bush",
            "spore:rotten_grass",
            "spore:rotten_fern",
            "spore:rotten_crops"
    );

    private static final Set<String> PROTECTED_PLAYER_BLOCK_IDS = Set.of(
            "spore:cdu",
            "spore:container",
            "spore:cabinet",
            "spore:zoaholic",
            "spore:incubator",
            "spore:surgery_table",
            "spore:laboratory_bed",
            "spore:lab_block",
            "spore:lab_block1",
            "spore:lab_block2",
            "spore:lab_block3",
            "spore:lab_slab",
            "spore:lab_slab1",
            "spore:lab_slab2",
            "spore:lab_slab3",
            "spore:lab_stair",
            "spore:iron_ladder",
            "spore:vent_plate",
            "spore:rusted_vent_plate",
            "spore:vent_door",
            "spore:reinforced_door",
            "spore:rusted_reinforced_door",
            "spore:frozen_reinforced_door",
            "spore:halogen_light",
            "spore:halogen_light_on",
            "spore:broken_halogen_light",
            "spore:broken_halogen_light_on",
            "spore:heart_pie",
            "spore:cooked_torso",
            "spore:skull_soup"
    );

    private static final ConcurrentMap<Block, Boolean> RESULTS = new ConcurrentHashMap<>();
    private static volatile Set<Block> removableBlocks;
    private static volatile Set<Block> protectedBlocks;

    private SporeNaturalSpreadBlocks() {
    }

    static void prepareForScan() {
        RESULTS.clear();
        removableBlocks = null;
        protectedBlocks = null;
    }

    static boolean canRemove(BlockState state) {
        if (SporeCduRestorer.canRestore(state)) {
            return false;
        }
        return RESULTS.computeIfAbsent(state.getBlock(), SporeNaturalSpreadBlocks::isRemovableBlock);
    }

    private static boolean isRemovableBlock(Block block) {
        if (protectedBlocks().contains(block)) {
            return false;
        }
        return removableBlocks().contains(block);
    }

    private static Set<Block> removableBlocks() {
        Set<Block> cached = removableBlocks;
        if (cached != null) {
            return cached;
        }

        Set<Block> blocks = new HashSet<>();
        addBlocks(blocks, DEFAULT_NATURAL_SPREAD_IDS);
        addBlocks(blocks, configuredInfectionTargets());
        addBlocks(blocks, SporeDefenderConfig.extraRemovableBlockIds());
        removableBlocks = Set.copyOf(blocks);
        return removableBlocks;
    }

    private static Set<Block> protectedBlocks() {
        Set<Block> cached = protectedBlocks;
        if (cached != null) {
            return cached;
        }

        Set<Block> blocks = new HashSet<>();
        addBlocks(blocks, PROTECTED_PLAYER_BLOCK_IDS);
        protectedBlocks = Set.copyOf(blocks);
        return protectedBlocks;
    }

    private static Set<String> configuredInfectionTargets() {
        Set<String> ids = new HashSet<>();
        for (Object entry : sporeConfiguredInfectionEntries()) {
            if (!(entry instanceof String text)) {
                continue;
            }

            String[] parts = text.split("\\|", 2);
            if (parts.length == 2) {
                ids.add(parts[1]);
            }
        }
        return ids;
    }

    private static List<?> sporeConfiguredInfectionEntries() {
        try {
            Class<?> configClass = Class.forName("com.Harbinger.Spore.core.SConfig");
            Object dataGen = configClass.getField("DATAGEN").get(null);
            Field infectionField = dataGen.getClass().getField("block_infection");
            Object configValue = infectionField.get(dataGen);
            Object value = configValue.getClass().getMethod("get").invoke(configValue);
            return value instanceof List<?> list ? list : List.of();
        } catch (ReflectiveOperationException | LinkageError exception) {
            return List.of();
        }
    }

    private static void addBlocks(Set<Block> blocks, Iterable<String> ids) {
        for (String id : ids) {
            block(id).ifPresent(blocks::add);
        }
    }

    private static Optional<Block> block(String id) {
        return SporeRegistries.block(id);
    }
}
