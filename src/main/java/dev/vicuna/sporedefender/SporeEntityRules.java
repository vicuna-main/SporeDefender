package dev.vicuna.sporedefender;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.Set;

final class SporeEntityRules {
    private static final String SPORE_NAMESPACE = "spore";

    private SporeEntityRules() {
    }

    static boolean shouldPurge(Entity entity, Set<String> extraRemovableEntityIds, boolean includeSporeItemDrops) {
        return isSporeType(entity)
                || isConfiguredRemovableEntity(entity, extraRemovableEntityIds)
                || (includeSporeItemDrops && isSporeItemDrop(entity));
    }

    static boolean isSporeHostileMob(Entity entity) {
        return entity instanceof Enemy && isSporeType(entity);
    }

    private static boolean isSporeType(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return SPORE_NAMESPACE.equals(id.getNamespace());
    }

    private static boolean isConfiguredRemovableEntity(Entity entity, Set<String> extraRemovableEntityIds) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return extraRemovableEntityIds.contains(id.toString());
    }

    private static boolean isSporeItemDrop(Entity entity) {
        if (!(entity instanceof ItemEntity itemEntity)) {
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
        return SPORE_NAMESPACE.equals(itemId.getNamespace());
    }
}
