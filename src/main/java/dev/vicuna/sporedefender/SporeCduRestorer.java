package dev.vicuna.sporedefender;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class SporeCduRestorer {
    private static final long JSON_LOOKUP_RETRY_DELAY_MILLIS = 5_000L;
    private static final String[] DEFAULT_CDU_CLEANING = {
            "spore:infested_stone|minecraft:stone",
            "minecraft:mycelium|minecraft:dirt",
            "spore:infested_dirt|minecraft:dirt",
            "spore:infested_deepslate|minecraft:deepslate",
            "spore:infested_sand|minecraft:sand",
            "spore:infested_gravel|minecraft:gravel",
            "spore:infested_netherrack|minecraft:netherrack",
            "spore:infested_end_stone|minecraft:end_stone",
            "spore:infested_soul_sand|minecraft:soul_sand",
            "spore:infested_red_sand|minecraft:red_sand",
            "spore:infested_clay|minecraft:clay",
            "spore:infested_cobblestone|minecraft:cobblestone",
            "spore:infested_cobbled_deepslate|minecraft:cobbled_deepslate",
            "spore:infested_laboratory_block|spore:lab_block",
            "spore:infested_laboratory_block1|spore:lab_block1",
            "spore:infested_laboratory_block2|spore:lab_block2",
            "spore:infested_laboratory_block3|spore:lab_block3",
            "spore:infested_stone_bricks|minecraft:stone_bricks",
            "spore:infested_bricks|minecraft:bricks"
    };

    private static final ConcurrentMap<Block, Optional<Block>> RESULTS = new ConcurrentHashMap<>();
    private static volatile Map<Block, Block> configuredCleaning;
    private static volatile Method cduJsonResultMethod;
    private static volatile long nextJsonLookupAttemptMillis;

    private SporeCduRestorer() {
    }

    static void prepareForScan() {
        RESULTS.clear();
        configuredCleaning = null;
    }

    static boolean canRestore(BlockState state) {
        return cduUnwrappedState(state) != null || resultBlock(state.getBlock()) != null;
    }

    static BlockState restoreState(BlockState state) {
        BlockState unwrappedCdu = cduUnwrappedState(state);
        if (unwrappedCdu != null) {
            return unwrappedCdu;
        }

        Block result = resultBlock(state.getBlock());
        if (result == null || result == state.getBlock()) {
            return null;
        }

        return copyMatchingProperties(state, result.defaultBlockState());
    }

    private static BlockState cduUnwrappedState(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!ResourceLocation.fromNamespaceAndPath("spore", "cdu").equals(id)) {
            return null;
        }

        Property<?> property = state.getBlock().getStateDefinition().getProperty("lit");
        if (property == null || property.getValueClass() != Boolean.class) {
            return null;
        }

        Object value = state.getValue(property);
        if (!Boolean.TRUE.equals(value)) {
            return null;
        }

        return setBoolean(state, property, false);
    }

    private static Block resultBlock(Block source) {
        return RESULTS.computeIfAbsent(source, block -> Optional.ofNullable(lookupResultBlock(block))).orElse(null);
    }

    private static Block lookupResultBlock(Block source) {
        Block configured = configuredCleaning().get(source);
        if (configured != null) {
            return configured;
        }

        return cduJsonResult(source);
    }

    private static Map<Block, Block> configuredCleaning() {
        Map<Block, Block> cached = configuredCleaning;
        if (cached != null) {
            return cached;
        }

        Map<Block, Block> mappings = new HashMap<>();
        addCleaningEntries(mappings, DEFAULT_CDU_CLEANING);
        addCleaningEntries(mappings, sporeConfiguredCleaningEntries());
        configuredCleaning = Map.copyOf(mappings);
        return configuredCleaning;
    }

    private static List<?> sporeConfiguredCleaningEntries() {
        try {
            Class<?> configClass = Class.forName("com.Harbinger.Spore.core.SConfig");
            Object dataGen = configClass.getField("DATAGEN").get(null);
            Field cleaningField = dataGen.getClass().getField("block_cleaning");
            Object configValue = cleaningField.get(dataGen);
            Object value = configValue.getClass().getMethod("get").invoke(configValue);
            return value instanceof List<?> list ? list : List.of();
        } catch (ReflectiveOperationException | LinkageError exception) {
            return List.of();
        }
    }

    private static void addCleaningEntries(Map<Block, Block> mappings, Object entries) {
        if (!(entries instanceof Iterable<?> iterable)) {
            return;
        }

        for (Object entry : iterable) {
            if (!(entry instanceof String text)) {
                continue;
            }

            String[] parts = text.split("\\|", 2);
            if (parts.length != 2) {
                continue;
            }

            Block source = block(parts[0]);
            Block target = block(parts[1]);
            if (source != null && target != null) {
                mappings.put(source, target);
            }
        }
    }

    private static Block cduJsonResult(Block source) {
        Method method = cduJsonResultMethod();
        if (method == null) {
            return null;
        }

        try {
            Object result = method.invoke(null, source);
            return result instanceof Block block ? block : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private static Method cduJsonResultMethod() {
        if (cduJsonResultMethod != null) {
            return cduJsonResultMethod;
        }

        long now = System.currentTimeMillis();
        if (now < nextJsonLookupAttemptMillis) {
            return null;
        }

        try {
            Class<?> dataClass = Class.forName("com.Harbinger.Spore.ExtremelySusThings.CustomJsonReader.SporeCduConversionData");
            cduJsonResultMethod = dataClass.getMethod("getResult", Block.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            cduJsonResultMethod = null;
            nextJsonLookupAttemptMillis = now + JSON_LOOKUP_RETRY_DELAY_MILLIS;
        }
        return cduJsonResultMethod;
    }

    private static Block block(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
    }

    private static BlockState copyMatchingProperties(BlockState source, BlockState target) {
        BlockState result = target;
        for (Map.Entry<Property<?>, Comparable<?>> entry : source.getValues().entrySet()) {
            Property<?> targetProperty = result.getBlock().getStateDefinition().getProperty(entry.getKey().getName());
            if (targetProperty != null) {
                result = copyProperty(result, targetProperty, entry.getValue());
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState state, Property<T> property, Comparable<?> value) {
        try {
            return state.setValue(property, property.getValueClass().cast(value));
        } catch (IllegalArgumentException exception) {
            return state;
        }
    }

    private static <T extends Comparable<T>> BlockState setBoolean(BlockState state, Property<T> property, boolean value) {
        try {
            return state.setValue(property, property.getValueClass().cast(value));
        } catch (IllegalArgumentException exception) {
            return state;
        }
    }
}
