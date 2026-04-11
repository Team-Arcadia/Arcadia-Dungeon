package com.arcadia.dungeon.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

final class ArcadiaOpsCommandTree {

    private ArcadiaOpsCommandTree() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("arcadia")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(ArcadiaRuntimeCommandActions::showAdminStatus)
                )
                .then(Commands.literal("stop")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(ArcadiaCommands.SUGGEST_DUNGEONS)
                                .executes(ArcadiaRuntimeCommandActions::stopDungeon)
                        )
                )
                .then(Commands.literal("cuboid")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(ArcadiaCommands.SUGGEST_DUNGEONS)
                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                        .then(Commands.argument("y1", IntegerArgumentType.integer())
                                                .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                        .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                                .then(Commands.argument("y2", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                                .executes(ArcadiaRuntimeCommandActions::setDungeonCuboid)
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("tuning")
                        .then(Commands.argument("entityId", IntegerArgumentType.integer(0))
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(ArcadiaCommands.SUGGEST_ATTRIBUTES)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(ArcadiaRuntimeCommandActions::applyRuntimeTuning)
                                        )
                                )
                        )
                )
                .then(Commands.literal("reload")
                        .executes(ArcadiaRuntimeCommandActions::reloadConfigs)
                );
    }
}

