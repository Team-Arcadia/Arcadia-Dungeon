package com.arcadia.dungeon.command;

import com.arcadia.dungeon.network.OpenAdminHubPayload;
import com.mojang.brigadier.CommandDispatcher;
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
                })));
    }
}
