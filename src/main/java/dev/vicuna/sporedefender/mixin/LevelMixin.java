package dev.vicuna.sporedefender.mixin;

import dev.vicuna.sporedefender.SporeProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class LevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporedefender$setBlock(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        if (SporeProtection.shouldBlockSet((Level) (Object) this, pos, state)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporedefender$setBlockWithDepth(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (SporeProtection.shouldBlockSet((Level) (Object) this, pos, state)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporedefender$setBlockAndUpdate(BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (SporeProtection.shouldBlockSet((Level) (Object) this, pos, state)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporedefender$removeBlock(BlockPos pos, boolean isMoving, CallbackInfoReturnable<Boolean> cir) {
        if (SporeProtection.shouldBlockDestruction((Level) (Object) this, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporedefender$destroyBlock(BlockPos pos, boolean dropBlock, CallbackInfoReturnable<Boolean> cir) {
        if (SporeProtection.shouldBlockDestruction((Level) (Object) this, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporedefender$destroyBlockWithEntity(BlockPos pos, boolean dropBlock, @Nullable Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SporeProtection.shouldBlockDestruction((Level) (Object) this, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporedefender$destroyBlockWithEntityAndDepth(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (SporeProtection.shouldBlockDestruction((Level) (Object) this, pos)) {
            cir.setReturnValue(false);
        }
    }
}
