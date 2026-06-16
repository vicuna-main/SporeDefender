package dev.vicuna.sporedefender;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SporeDefenderCommands {
    private static final int MAX_BLOCK_RADIUS = 16_384;
    private static final int MAX_CHUNK_RADIUS = 1_024;

    private SporeDefenderCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sporedefender")
                .requires(source -> source.hasPermission(2))
                .then(rangeCommand("restore", AsyncSporeCleaner.Operation.RESTORE, true))
                .then(rangeCommand("prune", AsyncSporeCleaner.Operation.PRUNE, false))
                .then(rangeCommand("purge", AsyncSporeCleaner.Operation.PURGE, true))
                .then(rangeCommand("cure", AsyncSporeCleaner.Operation.CURE, false))
                .then(rangeCommand("clean", AsyncSporeCleaner.Operation.CLEAN, false)));
    }

    private static BlockPos center(CommandSourceStack source) {
        return BlockPos.containing(source.getPosition());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rangeCommand(String name, AsyncSporeCleaner.Operation operation, boolean legacyRadius) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(name)
                .then(Commands.literal("radius")
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(1, MAX_BLOCK_RADIUS))
                                .executes(context -> enqueue(context.getSource(), operation, blockRadius(context, "blocks")))))
                .then(Commands.literal("chunks")
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(0, MAX_CHUNK_RADIUS))
                                .executes(context -> enqueue(context.getSource(), operation, chunkRadius(context)))));

        if (legacyRadius) {
            command.then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_BLOCK_RADIUS))
                    .executes(context -> enqueue(context.getSource(), operation, blockRadius(context, "radius"))));
        }

        return command;
    }

    private static int enqueue(CommandSourceStack source, AsyncSporeCleaner.Operation operation, CleanRange range) {
        return AsyncSporeCleaner.enqueue(source, operation, range);
    }

    private static CleanRange blockRadius(CommandContext<CommandSourceStack> context, String argumentName) {
        CommandSourceStack source = context.getSource();
        return CleanRange.blockRadius(center(source), IntegerArgumentType.getInteger(context, argumentName));
    }

    private static CleanRange chunkRadius(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return CleanRange.chunkRadius(center(source), IntegerArgumentType.getInteger(context, "chunks"));
    }

}
