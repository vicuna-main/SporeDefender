package dev.vicuna.sporedefender;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class SporeProtection {
    private static final StackWalker STACK_WALKER = StackWalker.getInstance();
    private static final String SPORE_PACKAGE = "com.Harbinger.Spore.";

    private SporeProtection() {
    }

    public static boolean shouldBlockSet(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return calledFromSpore() && GriefDefenderBridge.isProtected(serverLevel, pos);
    }

    public static boolean shouldBlockDestruction(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return calledFromSpore() && GriefDefenderBridge.isProtected(serverLevel, pos);
    }

    private static boolean calledFromSpore() {
        return STACK_WALKER.walk(frames -> frames
                .limit(80)
                .map(StackWalker.StackFrame::getClassName)
                .anyMatch(name -> name.startsWith(SPORE_PACKAGE)));
    }
}
