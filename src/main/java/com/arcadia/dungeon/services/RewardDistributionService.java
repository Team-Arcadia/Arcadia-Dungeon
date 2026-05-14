package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.player.PlayerProgress;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunResult;
import com.arcadia.dungeon.network.OpenResultScreenPayload;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Service distribution des récompenses à la fin d'une run (Story S4.3).
 *
 * <p>VICTORY → 100 % currency + loot. DEFEAT → 25 % currency, pas de loot.
 * ABANDONED / SERVER_SHUTDOWN → rien distribué.
 * Doit être appelé sur le SGT (via LivingDeathEvent ou enqueueWork).
 */
public final class RewardDistributionService {

    private static final double DEFEAT_MULTIPLIER = 0.25;

    private final PlayerProgressService playerProgressService;
    private final DungeonRegistry dungeonRegistry;
    private final Random random = new Random();

    public RewardDistributionService(PlayerProgressService playerProgressService,
                                     DungeonRegistry dungeonRegistry) {
        this.playerProgressService = playerProgressService;
        this.dungeonRegistry = dungeonRegistry;
    }

    /**
     * Distribue currency + loot à tous les joueurs connectés de la run.
     */
    public void distribute(Run run, RunResult result) {
        if (result == RunResult.ABANDONED || result == RunResult.SERVER_SHUTDOWN) return;

        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config == null || config.rewards() == null) return;

        DungeonConfig.Rewards rewards = config.rewards();
        double multiplier = result == RunResult.VICTORY ? 1.0 : DEFEAT_MULTIPLIER;
        long currency = Math.round(rewards.currency() * multiplier);

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long elapsedSeconds = run.elapsedSeconds();

        for (UUID playerId : run.playerIds()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            String playerName = player.getGameProfile().getName();

            if (currency > 0) {
                playerProgressService.addCurrency(playerId, playerName, currency);
            }

            boolean newPb = false;
            long bestTime = 0L;
            List<String> lootLines = new ArrayList<>();

            if (result == RunResult.VICTORY) {
                // S5.1 — PB tracking
                newPb = playerProgressService.recordRunCompletion(
                    playerId, playerName, run.dungeonId(), elapsedSeconds);
                if (newPb) {
                    ArcadiaDungeon.LOGGER.info(
                        "[Arcadia][RUN] event=new_pb playerId={} dungeonId={} duration={}s",
                        playerId, run.dungeonId(), elapsedSeconds);
                }

                PlayerProgress prog = playerProgressService.get(playerId).orElse(null);
                if (prog != null) {
                    PlayerProgress.DungeonProgress dp = prog.dungeons().get(run.dungeonId());
                    if (dp != null) bestTime = dp.bestTimeSeconds;
                }

                if (rewards.loot() != null) {
                    for (DungeonConfig.LootEntry entry : rewards.loot()) {
                        int count = giveItem(player, entry);
                        if (count > 0) {
                            ResourceLocation rl = ResourceLocation.tryParse(entry.item());
                            if (rl != null) lootLines.add(count + "x " + formatItemName(rl));
                        }
                    }
                }
            }

            // S6.3 — ouvrir ResultScreen côté client
            player.connection.send(new OpenResultScreenPayload(
                result.name(), elapsedSeconds, currency, newPb, bestTime, 0, run.dungeonId(), lootLines));
            com.arcadia.dungeon.network.ServerPayloadHandler.sendPlayerProgress(player);
        }

        ArcadiaDungeon.LOGGER.info(
            "[Arcadia][RUN] event=rewards_distributed runId={} result={} currency={} multiplier={}",
            run.id(), result, currency, multiplier);
    }

    public List<String> distributeBossRewards(Run run, DungeonConfig.BossDefinition boss) {
        if (boss == null || boss.rewardsOrEmpty().isEmpty()) return List.of();

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return List.of();

        List<String> distributed = new ArrayList<>();
        for (UUID playerId : run.playerIds()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;

            for (DungeonConfig.BossReward reward : boss.rewardsOrEmpty()) {
                if (random.nextDouble() > clamp01(reward.chance())) continue;
                int count = giveItem(player, reward.item(), reward.min(), reward.max());
                if (count > 0) {
                    ResourceLocation rl = ResourceLocation.tryParse(reward.item());
                    if (rl != null) distributed.add(count + "x " + formatItemName(rl));
                }
            }
        }

        if (!distributed.isEmpty()) {
            ArcadiaDungeon.LOGGER.info(
                "[Arcadia][BOSS] event=boss_rewards_distributed runId={} bossId={} rewards={}",
                run.id(), boss.id(), distributed.size());
        }
        return distributed;
    }

    private int giveItem(ServerPlayer player, DungeonConfig.LootEntry entry) {
        return giveItem(player, entry.item(), entry.min(), entry.max());
    }

    private int giveItem(ServerPlayer player, String itemId, int minValue, int maxValue) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][RUN] loot item invalide: {}", itemId);
            return 0;
        }
        var item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        if (item == null) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][RUN] item inconnu: {}", itemId);
            return 0;
        }
        int min = Math.min(minValue, maxValue);
        int max = Math.max(minValue, maxValue);
        int count = min >= max ? max : min + random.nextInt(max - min + 1);
        if (count <= 0) return 0;

        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        return count;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String formatItemName(ResourceLocation rl) {
        String[] parts = rl.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
