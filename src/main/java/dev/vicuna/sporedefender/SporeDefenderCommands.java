package dev.vicuna.sporedefender;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SporeDefenderCommands {
    private static final int MAX_BLOCK_RADIUS = 128;
    private static final int MAX_CHUNK_RADIUS = 8;

    private SporeDefenderCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sporedefender")
                .requires(source -> source.hasPermission(2))
                .then(rangeCommand("restore", SporeDefenderCommands::restore, true))
                .then(rangeCommand("purge", SporeDefenderCommands::purge, true))
                .then(rangeCommand("clean", SporeDefenderCommands::clean, false)));
    }

    private static BlockPos center(CommandSourceStack source) {
        return BlockPos.containing(source.getPosition());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rangeCommand(String name, CleanAction action, boolean legacyRadius) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(name)
                .then(Commands.literal("radius")
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(1, MAX_BLOCK_RADIUS))
                                .executes(context -> action.run(context.getSource(), blockRadius(context, "blocks")))))
                .then(Commands.literal("chunks")
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(0, MAX_CHUNK_RADIUS))
                                .executes(context -> action.run(context.getSource(), chunkRadius(context)))));

        if (legacyRadius) {
            command.then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_BLOCK_RADIUS))
                    .executes(context -> action.run(context.getSource(), blockRadius(context, "radius"))));
        }

        return command;
    }

    private static CleanRange blockRadius(CommandContext<CommandSourceStack> context, String argumentName) {
        CommandSourceStack source = context.getSource();
        return CleanRange.blockRadius(center(source), IntegerArgumentType.getInteger(context, argumentName));
    }

    private static CleanRange chunkRadius(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return CleanRange.chunkRadius(center(source), IntegerArgumentType.getInteger(context, "chunks"));
    }

    private static int restore(CommandSourceStack source, CleanRange range) {
        SporeCleaner.BlockCleanResult result = SporeCleaner.restoreBlocks(source.getLevel(), range);
        source.sendSuccess(() -> Component.literal("Restored " + result.restoredBlocks() + " Spore-infected blocks in " + result.loadedChunks() + " loaded chunks using " + range.description() + "."), true);
        return result.restoredBlocks();
    }

    private static int purge(CommandSourceStack source, CleanRange range) {
        int removed = SporeCleaner.purgeEntities(source.getLevel(), range);
        source.sendSuccess(() -> Component.literal("Removed " + removed + " Spore entities using " + range.description() + "."), true);
        return removed;
    }

    private static int clean(CommandSourceStack source, CleanRange range) {
        SporeCleaner.BlockCleanResult blocks = SporeCleaner.restoreBlocks(source.getLevel(), range);
        int entities = SporeCleaner.purgeEntities(source.getLevel(), range);
        source.sendSuccess(() -> Component.literal("Restored " + blocks.restoredBlocks() + " blocks and removed " + entities + " Spore entities near " + range.center().toShortString() + " using " + range.description() + "."), true);
        return blocks.restoredBlocks() + entities;
    }

    @FunctionalInterface
    private interface CleanAction {
        int run(CommandSourceStack source, CleanRange range);
    }
}
