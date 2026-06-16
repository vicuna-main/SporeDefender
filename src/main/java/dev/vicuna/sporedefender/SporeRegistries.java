package dev.vicuna.sporedefender;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

final class SporeRegistries {
    static final String SPORE_NAMESPACE = "spore";
    static final ResourceLocation SPORE_CDU = ResourceLocation.fromNamespaceAndPath(SPORE_NAMESPACE, "cdu");

    private SporeRegistries() {
    }

    static boolean isSpore(ResourceLocation id) {
        return id != null && SPORE_NAMESPACE.equals(id.getNamespace());
    }

    static ResourceLocation entityId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    static ResourceLocation itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    static ResourceLocation blockId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    static ResourceLocation effectId(MobEffect effect) {
        return BuiltInRegistries.MOB_EFFECT.getKey(effect);
    }

    static Optional<Block> block(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.BLOCK.getOptional(location);
    }
}
