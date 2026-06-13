package dev.vicuna.sporedefender;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

final class SporeDefenderEvents {
    private SporeDefenderEvents() {
    }

    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !SporeDefenderConfig.preventSporeHostileMobsInClaims()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Entity entity = event.getEntity();
        if (SporeEntityRules.isSporeHostileMob(entity) && GriefDefenderBridge.isProtected(level, entity.blockPosition())) {
            event.setCanceled(true);
        }
    }
}
