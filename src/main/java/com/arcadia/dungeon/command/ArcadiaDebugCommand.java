package com.arcadia.dungeon.command;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunResult;
import com.arcadia.dungeon.network.OpenDebugScreenPayload;
import com.arcadia.dungeon.network.OpenResultScreenPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Sous-commandes {@code /arcadia debug} (S2.6 + S7.1).
 *
 * <p>Requiert op level 2. Enregistré sous le nœud {@code /arcadia} existant.
 *
 * <ul>
 *   <li>{@code showscreen}       — ouvre le DebugScreen (S2.6)
 *   <li>{@code forcecomplete}    — termine la run en VICTORY (S7.1 AC1)
 *   <li>{@code forcedefeat}      — termine la run en DEFEAT (S7.1 AC2)
 *   <li>{@code killboss <runId>} — tue le boss in-world, déclenche la VICTORY via event (S7.1 AC3)
 *   <li>{@code showvictory}      — ouvre ResultScreen VICTORY sans run active (S7.1 AC4)
 * </ul>
 */
public final class ArcadiaDebugCommand {

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcadia")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("debug")
                .then(Commands.literal("showscreen")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player != null) player.connection.send(new OpenDebugScreenPayload());
                        return 1;
                    }))
                .then(Commands.literal("forcecomplete")
                    .then(Commands.argument("runId", StringArgumentType.word())
                        .executes(ctx -> forceEnd(ctx.getSource(),
                            StringArgumentType.getString(ctx, "runId"), RunResult.VICTORY))))
                .then(Commands.literal("forcedefeat")
                    .then(Commands.argument("runId", StringArgumentType.word())
                        .executes(ctx -> forceEnd(ctx.getSource(),
                            StringArgumentType.getString(ctx, "runId"), RunResult.DEFEAT))))
                .then(Commands.literal("killboss")
                    .then(Commands.argument("runId", StringArgumentType.word())
                        .executes(ctx -> killBoss(ctx.getSource(),
                            StringArgumentType.getString(ctx, "runId")))))
                .then(Commands.literal("showvictory")
                    .executes(ctx -> showVictory(ctx.getSource())))));
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int forceEnd(CommandSourceStack src, String runIdStr, RunResult result) {
        Run run = resolveRun(src, runIdStr);
        if (run == null) return 0;
        ArcadiaDungeon.runLifecycleService().completeRun(run, result);
        ArcadiaDungeon.rewardDistributionService().distribute(run, result);
        src.sendSuccess(() -> Component.literal("[Arcadia] Run " + runIdStr + " → " + result), true);
        return 1;
    }

    private static int killBoss(CommandSourceStack src, String runIdStr) {
        Run run = resolveRun(src, runIdStr);
        if (run == null) return 0;
        boolean killed = ArcadiaDungeon.bossPhaseService()
            .forceKillBoss(run.id(), src.getServer());
        if (killed) {
            src.sendSuccess(() -> Component.literal("[Arcadia] Boss killed for run " + runIdStr), true);
            return 1;
        }
        src.sendFailure(Component.literal("[Arcadia] No boss entity found for run " + runIdStr));
        return 0;
    }

    private static int showVictory(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[Arcadia] Player required (not console)"));
            return 0;
        }
        // Données fictives : 4m12s, 50 currency, nouveau PB
        player.connection.send(new OpenResultScreenPayload("VICTORY", 252L, 50L, true, 252L, 0, "arcadia_dungeon:demo",
            java.util.List.of("3x Diamond", "2x Gold Ingot", "1x Enchanted Book")));
        return 1;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static Run resolveRun(CommandSourceStack src, String runIdStr) {
        UUID uuid;
        try {
            uuid = UUID.fromString(runIdStr);
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("[Arcadia] UUID invalide : " + runIdStr));
            return null;
        }
        Run run = ArcadiaDungeon.runLifecycleService().findById(new RunId(uuid)).orElse(null);
        if (run == null) {
            src.sendFailure(Component.literal("[Arcadia] Aucune run active : " + runIdStr));
        }
        return run;
    }
}
