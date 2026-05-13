package com.arcadia.dungeon.command;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.event.DungeonAreaWandEventHandler;
import com.arcadia.dungeon.network.OpenAdminHubPayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commande {@code /arcadia admin} — ouvre le panneau d'administration (Story 8.1).
 *
 * <p>Requiert op level 2. Envoie {@link OpenAdminHubPayload} S2C au joueur
 * pour que le client ouvre {@code AdminHubScreen}.
 */
public final class ArcadiaAdminCommand {

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcadia")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("admin")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayer();
                    if (player == null) {
                        src.sendFailure(Component.literal("[Arcadia] Commande joueur uniquement (pas la console)."));
                        return 0;
                    }
                    player.connection.send(new OpenAdminHubPayload());
                    return 1;
                })
                .then(Commands.literal("wand")
                    .executes(ctx -> giveAreaWand(ctx.getSource())))
                .then(Commands.literal("wand_select")
                    .then(Commands.argument("dungeonId", StringArgumentType.word())
                        .executes(ctx -> selectWandDungeon(ctx.getSource(),
                            StringArgumentType.getString(ctx, "dungeonId")))))
                .then(Commands.literal("area_clear")
                    .then(Commands.argument("dungeonId", StringArgumentType.word())
                        .executes(ctx -> clearArea(ctx.getSource(),
                            StringArgumentType.getString(ctx, "dungeonId")))))));
    }

    private static int giveAreaWand(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[Arcadia] Commande joueur uniquement (pas la console)."));
            return 0;
        }

        String selected = ArcadiaDungeon.dungeonRegistry().dungeons().keySet().stream().findFirst().orElse(null);
        if (selected != null) {
            DungeonAreaWandEventHandler.beginSelection(player, selected);
        }
        src.sendSuccess(() -> Component.literal("[Arcadia Wand] Pelle recue. Donjon: "
            + (selected != null ? selected : "aucun")).withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("Clic gauche = Pos1 | Clic droit = Pos2 | /arcadia admin wand_select <dungeonId>")
            .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int selectWandDungeon(CommandSourceStack src, String dungeonId) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[Arcadia] Commande joueur uniquement (pas la console)."));
            return 0;
        }
        if (ArcadiaDungeon.dungeonRegistry().get(dungeonId).isEmpty()) {
            src.sendFailure(Component.literal("[Arcadia Wand] Donjon introuvable: " + dungeonId));
            return 0;
        }

        DungeonAreaWandEventHandler.beginSelection(player, dungeonId);
        src.sendSuccess(() -> Component.literal("[Arcadia Wand] Donjon selectionne: " + dungeonId)
            .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int clearArea(CommandSourceStack src, String dungeonId) {
        var config = ArcadiaDungeon.dungeonRegistry().get(dungeonId).orElse(null);
        if (config == null) {
            src.sendFailure(Component.literal("[Arcadia Wand] Donjon introuvable: " + dungeonId));
            return 0;
        }

        ArcadiaDungeon.dungeonRegistry().save(config.withArea(null, null));
        src.sendSuccess(() -> Component.literal("[Arcadia Wand] Zone effacee: " + dungeonId)
            .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
