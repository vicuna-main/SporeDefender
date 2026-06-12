package dev.vicuna.sporedefender;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SporeDefenderCommands {
    private static final int MAX_RADIUS = 128;

    private SporeDefenderCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sporedefender")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("restore")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                                .executes(context -> restore(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("purge")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                                .executes(context -> purge(context.getSource(), IntegerArgumentType.getInteger(context, "radius"))))));
    }

    private static int restore(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        int removed = 0;
        int radiusSqr = radius * radius;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = Math.max(level.getMinBuildHeight(), center.getY() - radius); y <= Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius); y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    int dx = x - center.getX();
                    int dy = y - center.getY();
                    int dz = z - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > radiusSqr) {
                        continue;
                    }

                    mutable.set(x, y, z);
                    if (!level.hasChunkAt(mutable)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(mutable);
                    if (isSporeSpreadBlock(state)) {
                        level.setBlock(mutable, Blocks.AIR.defaultBlockState(), 3);
                        removed++;
                    }
                }
            }
        }

        int finalRemoved = removed;
        source.sendSuccess(() -> Component.literal("Removed " + finalRemoved + " Spore spread blocks within radius " + radius + "."), true);
        return removed;
    }

    private static int purge(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        AABB area = new AABB(BlockPos.containing(source.getPosition())).inflate(radius);
        int removed = 0;

        for (Entity entity : level.getEntities((Entity) null, area, SporeDefenderCommands::isSporeHostileEntity)) {
            entity.discard();
            removed++;
        }

        int finalRemoved = removed;
        source.sendSuccess(() -> Component.literal("Removed " + finalRemoved + " hostile Spore entities within radius " + radius + "."), true);
        return removed;
    }

    private static boolean isSporeHostileEntity(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return "spore".equals(id.getNamespace()) && (entity instanceof Enemy || entity instanceof Mob);
    }

    private static boolean isSporeSpreadBlock(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!"spore".equals(id.getNamespace())) {
            return false;
        }

        String path = id.getPath();
        return path.startsWith("infested_")
                || path.contains("fungal")
                || path.contains("growth")
                || path.contains("mycelium")
                || path.contains("biomass")
                || path.contains("remains")
                || path.contains("rotten")
                || path.contains("lump")
                || path.contains("bile")
                || path.contains("acid")
                || path.contains("membrane")
                || path.contains("roots")
                || path.equals("hand")
                || path.equals("lungs")
                || path.equals("vocals")
                || path.equals("overgrown_spawner");
    }
}
