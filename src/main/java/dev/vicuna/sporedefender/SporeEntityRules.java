package dev.vicuna.sporedefender;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.Set;

final class SporeEntityRules {
    private SporeEntityRules() {
    }

    static boolean shouldPurge(Entity entity, Set<String> extraRemovableEntityIds, boolean includeSporeItemDrops) {
        ResourceLocation entityId = SporeRegistries.entityId(entity);
        return SporeRegistries.isSpore(entityId)
                || extraRemovableEntityIds.contains(entityId.toString())
                || (includeSporeItemDrops && isSporeItemDrop(entity));
    }

    static boolean isSporeHostileMob(Entity entity) {
        return entity instanceof Enemy && SporeRegistries.isSpore(SporeRegistries.entityId(entity));
    }

    private static boolean isSporeItemDrop(Entity entity) {
        if (!(entity instanceof ItemEntity itemEntity)) {
            return false;
        }

        ResourceLocation itemId = SporeRegistries.itemId(itemEntity.getItem().getItem());
        return SporeRegistries.isSpore(itemId);
    }
}
