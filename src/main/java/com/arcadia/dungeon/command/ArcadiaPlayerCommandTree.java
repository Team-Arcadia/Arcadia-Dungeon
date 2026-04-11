package com.arcadia.dungeon.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

final class ArcadiaPlayerCommandTree {

    private ArcadiaPlayerCommandTree() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("arcadia_dungeon")
                .then(Commands.literal("start")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(ArcadiaCommands.SUGGEST_DUNGEONS)
                                .executes(ArcadiaRuntimeCommandActions::startDungeon)
                        )
                )
                .then(Commands.literal("join")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(ArcadiaCommands.SUGGEST_DUNGEONS)
                                .executes(ArcadiaRuntimeCommandActions::joinDungeon)
                        )
                )
                .then(Commands.literal("leave")
                        .executes(ArcadiaRuntimeCommandActions::leaveDungeon)
                )
                .then(Commands.literal("abandon")
                        .executes(ArcadiaRuntimeCommandActions::abandonDungeon)
                )
                .then(Commands.literal("progression")
                        .executes(ArcadiaProgressionCommandActions::showProgression)
                )
                .then(Commands.literal("profile")
                        .executes(ctx -> ArcadiaProgressionCommandActions.showArcadiaProfile(ctx, null))
                )
                .then(Commands.literal("top")
                        .executes(ctx -> ArcadiaProgressionCommandActions.showTop(ctx, null))
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(ArcadiaCommands.SUGGEST_DUNGEONS)
                                .executes(ctx -> ArcadiaProgressionCommandActions.showTop(ctx, StringArgumentType.getString(ctx, "dungeon")))
                        )
                )
                .then(Commands.literal("stats")
                        .executes(ctx -> ArcadiaProgressionCommandActions.showStats(ctx, null))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(ArcadiaCommands.SUGGEST_ONLINE_PLAYERS)
                                .executes(ctx -> ArcadiaProgressionCommandActions.showStats(ctx, StringArgumentType.getString(ctx, "player")))
                        )
                )
                .then(Commands.literal("status")
                        .executes(ArcadiaRuntimeCommandActions::showStatus)
                )
                .then(Commands.literal("menu")
                        .executes(ArcadiaProgressionCommandActions::showPlayerMenu)
                );
    }
}

