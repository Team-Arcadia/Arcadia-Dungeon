package com.arcadia.dungeon.command;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Commande {@code /arcadia reload} — hot-reload des configs donjons.
 *
 * <p>Story S1.4. Requiert op level 2 (admin).
 */
public final class ArcadiaReloadCommand {

    private final DungeonRegistry registry;

    public ArcadiaReloadCommand(DungeonRegistry registry) {
        this.registry = registry;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arcadia")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("reload").executes(this::executeReload));
        dispatcher.register(root);
    }

    private int executeReload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        // TODO[DEBT] : refuser reload si une run active référence un donjon en cours de modif.
        // Raison MVP : RunRegistry pas encore implémenté (S2.2). Implémentation MVP = reload brutal.
        // Sortie : Story S2.2 quand RunRegistry existera. Si MVP shippé sans, ajout en v1.0.1.
        // Date : 2026-05-07

        Map<String, DungeonConfig> reloaded = registry.reload();
        ArcadiaDungeon.LOGGER.info("[Arcadia][CONFIG] event=reload count={} fallback={}",
            reloaded.size(), registry.isFallbackActive());

        String message = registry.isFallbackActive()
            ? "§e⚠ Aucun donjon valide chargé — fallback sur l'exemple JAR. Vérifiez vos JSON."
            : "§a✓ " + reloaded.size() + " donjon(s) rechargé(s).";
        ctx.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }
}
