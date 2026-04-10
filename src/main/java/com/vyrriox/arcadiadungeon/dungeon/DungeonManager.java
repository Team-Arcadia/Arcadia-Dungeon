package com.vyrriox.arcadiadungeon.dungeon;

import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.config.BossConfig;
import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.SpawnPointConfig;
import com.vyrriox.arcadiadungeon.util.MessageUtil;
import com.arcadia.core.message.LegacyColorFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DungeonManager {
    private static final DungeonManager INSTANCE = new DungeonManager();

    private MinecraftServer server;
    private final Map<String, DungeonInstance> activeInstances = new ConcurrentHashMap<>();
    // Per-dungeon cooldown: key = "uuid:dungeonId"
    private final Map<String, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, SpawnPointConfig> playerReturnPoints = new ConcurrentHashMap<>();
    private final Map<String, Long> dungeonAvailability = new ConcurrentHashMap<>();
    private final Set<String> availabilityAnnounced = new HashSet<>();
    private final Map<String, Long> nextAvailabilityAnnouncements = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> allowedBeneficialEffects = new ConcurrentHashMap<>();
    // Reverse lookup: player UUID -> dungeon ID for O(1) lookups
    private final Map<UUID, String> playerToDungeon = new ConcurrentHashMap<>();
    // Dungeons pending removal actions (to avoid reentrant modification)
    private final Set<String> pendingFails = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingCompletions = ConcurrentHashMap.newKeySet();

    private DungeonManager() {}

    public static DungeonManager getInstance() {
        return INSTANCE;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        if (server == null) {
            activeInstances.clear();
            playerCooldowns.clear();
            playerReturnPoints.clear();
            allowedBeneficialEffects.clear();
            playerToDungeon.clear();
        } else {
            PlayerProgressManager.getInstance().loadAll();
        }
    }

    public MinecraftServer getServer() {
        return server;
    }

    public DungeonInstance startDungeon(String dungeonId, ServerPlayer initiator) {
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            initiator.sendSystemMessage(Component.literal("[Arcadia] Donjon inconnu: " + dungeonId).withStyle(ChatFormatting.RED));
            return null;
        }

        if (!config.enabled) {
            initiator.sendSystemMessage(Component.literal("[Arcadia] Ce donjon est desactive.").withStyle(ChatFormatting.RED));
            return null;
        }

        if (activeInstances.containsKey(dungeonId)) {
            initiator.sendSystemMessage(Component.literal("[Arcadia] Ce donjon est deja en cours!").withStyle(ChatFormatting.RED));
            return null;
        }

        // Check progression requirement
        if (config.requiredDungeon != null && !config.requiredDungeon.isEmpty()) {
            if (!PlayerProgressManager.getInstance().hasCompleted(initiator.getUUID().toString(), config.requiredDungeon)) {
                DungeonConfig required = ConfigManager.getInstance().getDungeon(config.requiredDungeon);
                String requiredName = required != null ? required.name : config.requiredDungeon;
                initiator.sendSystemMessage(Component.literal(
                        "[Arcadia] Vous devez d'abord completer: " + requiredName
                ).withStyle(ChatFormatting.RED));
                return null;
            }
        }

        // Check per-dungeon cooldown
        String cooldownKey = initiator.getUUID() + ":" + dungeonId;
        Long lastRun = playerCooldowns.get(cooldownKey);
        if (lastRun == null) {
            PlayerProgress progress = PlayerProgressManager.getInstance().get(initiator.getUUID().toString());
            if (progress != null) {
                PlayerProgress.DungeonProgress dp = progress.completedDungeons.get(dungeonId);
                if (dp != null && dp.lastCompletionTimestamp > 0) {
                    lastRun = dp.lastCompletionTimestamp;
                    playerCooldowns.put(cooldownKey, lastRun);
                }
            }
        }
        if (lastRun != null && config.cooldownSeconds > 0) {
            long elapsed = (System.currentTimeMillis() - lastRun) / 1000;
            if (elapsed < config.cooldownSeconds) {
                long remaining = config.cooldownSeconds - elapsed;
                initiator.sendSystemMessage(Component.literal(
                        "[Arcadia] Cooldown: " + formatTime(remaining) + " restant(s)."
                ).withStyle(ChatFormatting.YELLOW));
                return null;
            }
        }

        // Check dungeon availability timer
        if (config.availableEverySeconds > 0) {
            Long lastAvailable = dungeonAvailability.get(dungeonId);
            if (lastAvailable != null) {
                long elapsed = (System.currentTimeMillis() - lastAvailable) / 1000;
                if (elapsed < config.availableEverySeconds) {
                    long remaining = config.availableEverySeconds - elapsed;
                    initiator.sendSystemMessage(Component.literal(
                            "[Arcadia] Ce donjon sera disponible dans " + formatTime(remaining) + "."
                    ).withStyle(ChatFormatting.YELLOW));
                    return null;
                }
            }
        }

        if (!canResolveSpawn(config.spawnPoint)) {
            ArcadiaDungeon.LOGGER.error("Cannot start dungeon {}: invalid spawn point {} in {}", dungeonId, config.spawnPoint == null ? "null" : config.spawnPoint.dimension, config.name);
            initiator.sendSystemMessage(Component.literal("[Arcadia] Spawn du donjon invalide. Demarrage annule.").withStyle(ChatFormatting.RED));
            return null;
        }

        // Save return point & create instance
        saveReturnPoint(initiator);
        DungeonInstance instance = new DungeonInstance(config, server);
        instance.addPlayer(initiator);
        playerToDungeon.put(initiator.getUUID(), dungeonId);
        activeInstances.put(dungeonId, instance);

        // Recruitment phase or immediate start
        if (config.recruitmentDurationSeconds > 0) {
            instance.startRecruitment();
            broadcastClickableJoin(config, initiator.getName().getString(), config.recruitmentDurationSeconds);
            MutableComponent recruitMsg = Component.literal("[Arcadia] Recrutement ouvert! Vos amis ont " + config.recruitmentDurationSeconds + "s pour rejoindre. ")
                    .withStyle(ChatFormatting.GREEN);
            MutableComponent leaveBtn2 = Component.literal("[QUITTER]").withStyle(style -> style
                    .withColor(ChatFormatting.RED)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arcadia_dungeon abandon"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Cliquez pour quitter le donjon").withStyle(ChatFormatting.GRAY)))
            );
            initiator.sendSystemMessage(recruitMsg.append(leaveBtn2));
        } else {
            teleportToSpawn(initiator, config.spawnPoint);
            instance.startDungeon();
            String msg = renderDungeonMessage(config.startMessage, initiator.getName().getString(), config);
            MessageUtil.broadcast(instance, msg);
            if (config.announceStart) {
                broadcastMessage(msg);
            }
        }

        ArcadiaDungeon.LOGGER.info("Dungeon {} started by {}", dungeonId, initiator.getName().getString());
        return instance;
    }

    public boolean joinDungeon(String dungeonId, ServerPlayer player) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Aucun donjon actif: " + dungeonId).withStyle(ChatFormatting.RED));
            return false;
        }

        DungeonConfig config = instance.getConfig();

        if (!config.enabled) {
            player.sendSystemMessage(Component.literal("[Arcadia] Ce donjon est desactive.").withStyle(ChatFormatting.RED));
            return false;
        }

        if (config.requiredDungeon != null && !config.requiredDungeon.isEmpty()) {
            if (!PlayerProgressManager.getInstance().hasCompleted(player.getUUID().toString(), config.requiredDungeon)) {
                DungeonConfig required = ConfigManager.getInstance().getDungeon(config.requiredDungeon);
                String requiredName = required != null ? required.name : config.requiredDungeon;
                player.sendSystemMessage(Component.literal(
                        "[Arcadia] Vous devez d'abord completer: " + requiredName
                ).withStyle(ChatFormatting.RED));
                return false;
            }
        }

        // Allow joining only during recruitment phase
        if (instance.getState() != DungeonState.RECRUITING) {
            player.sendSystemMessage(Component.literal("[Arcadia] Les portes de " + config.name + " sont fermees!").withStyle(ChatFormatting.RED));
            return false;
        }

        if (instance.getPlayers().size() >= config.settings.maxPlayers) {
            player.sendSystemMessage(Component.literal("[Arcadia] Donjon plein!").withStyle(ChatFormatting.RED));
            return false;
        }

        // Check if already in a dungeon
        if (getPlayerDungeon(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Vous etes deja dans un donjon!").withStyle(ChatFormatting.RED));
            return false;
        }

        // Anti-monopole: frequent players can only join after half the recruitment timer
        if (config.settings.antiMonopole && config.recruitmentDurationSeconds > 0) {
            int weeklyCount = WeeklyLeaderboard.getInstance().getData()
                    .playerCompletions.getOrDefault(player.getUUID().toString(), 0);

            if (weeklyCount >= config.settings.antiMonopoleThreshold) {
                long remaining = instance.getRecruitmentRemainingSeconds();
                long halfTime = config.recruitmentDurationSeconds / 2;
                if (remaining > halfTime) {
                    long waitSeconds = remaining - halfTime;
                    player.sendSystemMessage(Component.literal(
                            "[Arcadia] Vous avez deja fait " + weeklyCount + " donjons cette semaine. " +
                            "Priorite aux nouveaux joueurs! Revenez dans " + formatTime(waitSeconds) + ".")
                            .withStyle(ChatFormatting.YELLOW));
                    return false;
                }
            }
        }

        saveReturnPoint(player);
        instance.addPlayer(player);
        playerToDungeon.put(player.getUUID(), dungeonId);

        MutableComponent joinMsg = Component.literal("[Arcadia] Vous avez rejoint " + config.name + "! ").withStyle(ChatFormatting.GREEN);
        MutableComponent leaveBtn = Component.literal("[QUITTER]").withStyle(style -> style
                .withColor(ChatFormatting.RED)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arcadia_dungeon abandon"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Cliquez pour quitter le donjon").withStyle(ChatFormatting.GRAY)))
        );
        player.sendSystemMessage(joinMsg.append(leaveBtn));

        // Notify other players in dungeon
        for (UUID otherId : instance.getPlayers()) {
            if (!otherId.equals(player.getUUID())) {
                ServerPlayer other = server.getPlayerList().getPlayer(otherId);
                if (other != null) {
                    other.sendSystemMessage(Component.literal("[Arcadia] " + player.getName().getString() + " a rejoint le donjon!")
                            .withStyle(ChatFormatting.GREEN));
                }
            }
        }

        return true;
    }

    public void finishRecruitment(String dungeonId) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null || instance.getState() != DungeonState.RECRUITING) return;

        DungeonConfig config = instance.getConfig();

        if (instance.getPlayerCount() < config.settings.minPlayers) {
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    teleportBack(player);
                    player.sendSystemMessage(Component.literal("[Arcadia] Pas assez de joueurs (" + instance.getPlayerCount()
                            + "/" + config.settings.minPlayers + "). Donjon annule.").withStyle(ChatFormatting.RED));
                }
                playerToDungeon.remove(playerId);
            }
            instance.cleanup();
            activeInstances.remove(dungeonId);
            return;
        }

        if (!canResolveSpawn(config.spawnPoint)) {
            ArcadiaDungeon.LOGGER.error("Cannot finish recruitment for dungeon {}: invalid spawn point {}", dungeonId, config.spawnPoint == null ? "null" : config.spawnPoint.dimension);
            for (UUID playerId : new ArrayList<>(instance.getPlayers())) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    teleportBack(player);
                    player.sendSystemMessage(Component.literal("[Arcadia] Spawn du donjon invalide. Donjon annule.").withStyle(ChatFormatting.RED));
                }
                playerToDungeon.remove(playerId);
            }
            instance.cleanup();
            activeInstances.remove(dungeonId);
            return;
        }

        // Kick parasites from dungeon area before starting
        kickParasites(instance);

        // Start the actual dungeon
        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                teleportToSpawn(player, config.spawnPoint);
            }
        }
        instance.startDungeon();
        triggerScriptedWalls(dungeonId, "DUNGEON_START");

        // Spawn bosses configured to appear at dungeon start
        for (BossConfig bossConfig : config.bosses) {
            if (!bossConfig.spawnAtStart) continue;
            if (bossConfig.optional && bossConfig.spawnChance < 1.0) {
                double roll = server.overworld().getRandom().nextDouble();
                if (roll > bossConfig.spawnChance) continue;
            }
            ResourceLocation dimLoc = ResourceLocation.tryParse(bossConfig.spawnPoint.dimension);
            if (dimLoc == null) continue;
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) continue;
            BossInstance bossInstance = new BossInstance(bossConfig, level, instance.getPlayerCount());
            if (bossInstance.spawn()) {
                instance.addBossInstance(bossConfig.id, bossInstance);
                instance.setState(DungeonState.ACTIVE);
            }
        }

        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal("[Arcadia] Le donjon commence! " + instance.getPlayerCount() + " joueur(s)!")
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        String startMsg = renderDungeonMessage(config.startMessage, instance.getPlayerNames(server), config);
        MessageUtil.broadcast(instance, startMsg);

        if (config.announceStart) {
            String msg = "&6[Donjon] &7Les portes de &e" + config.name + "&7 se ferment! &f" + instance.getPlayerCount() + "&7 joueur(s) a l'interieur.";
            MessageUtil.broadcast(instance, msg);
            broadcastMessage(msg);
        }
    }

    public void completeDungeon(String dungeonId) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) return;

        DungeonConfig config = instance.getConfig();
        instance.setState(DungeonState.COMPLETED);

        long completionTime = instance.getElapsedSeconds();
        long arcadiaXpReward = calculateArcadiaXpReward(config);

        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                instance.giveRewards(player, config.completionRewards);

                LevelUpResult levelUpResult = PlayerProgressManager.getInstance().addXp(playerId.toString(), arcadiaXpReward);
                PlayerProgressManager.getInstance().recordCompletionAndSave(
                        playerId.toString(), player.getName().getString(), dungeonId, completionTime
                );
                WeeklyLeaderboard.getInstance().recordCompletion(
                        playerId.toString(), player.getName().getString(), dungeonId, completionTime
                );

                if (config.teleportBackOnComplete) {
                    teleportBack(player);
                }

                // Per-dungeon cooldown
                playerCooldowns.put(playerId + ":" + dungeonId, System.currentTimeMillis());

                player.sendSystemMessage(Component.literal("[Arcadia] Donjon termine! Felicitations! (Temps: " + formatTime(completionTime) + ")")
                        .withStyle(ChatFormatting.GOLD));
                sendArcadiaXpMessages(player, arcadiaXpReward, levelUpResult);
            }
        }

        String localCompletionMsg = renderDungeonMessage(config.completionMessage, instance.getPlayerNames(server), config);
        MessageUtil.broadcast(instance, localCompletionMsg);

        if (config.availableEverySeconds > 0) {
            dungeonAvailability.put(dungeonId, System.currentTimeMillis());
            availabilityAnnounced.remove(dungeonId);
        }

        if (config.announceCompletion && server != null) {
            String playerNames = instance.getPlayerNames(server);
            String msg = renderDungeonMessage(config.completionMessage, playerNames, config);
            broadcastMessage(msg, instance.getPlayers());
        }

        for (UUID pid : instance.getPlayers()) playerToDungeon.remove(pid);
        for (UUID pid : instance.getPlayers()) clearAllowedBeneficialEffects(pid);
        instance.cleanup();
        activeInstances.remove(dungeonId);
        ArcadiaDungeon.LOGGER.info("Dungeon {} completed", dungeonId);
    }

    private long calculateArcadiaXpReward(DungeonConfig config) {
        int baseXp = config.arcadiaXp > 0
                ? config.arcadiaXp
                : ConfigManager.getInstance().getProgressionConfig().defaultDungeonXp;
        double multiplier = Double.isFinite(config.difficultyMultiplier) && config.difficultyMultiplier >= 0
                ? config.difficultyMultiplier
                : 1.0;
        double reward = baseXp * multiplier;
        if (reward <= 0) {
            return 0;
        }
        if (reward >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, Math.round(reward));
    }

    private void sendArcadiaXpMessages(ServerPlayer player, long xpReward, LevelUpResult result) {
        if (xpReward > 0) {
            MessageUtil.send(player, "&6[Arcadia] &a+" + xpReward + " XP Arcadia");
        }
        if (result == null || !result.leveledUp) {
            return;
        }

        MessageUtil.send(player, "&6[Arcadia] &eNiveau Arcadia " + result.newLevel + " atteint!");
        if (result.rankChanged) {
            MessageUtil.send(player, "&6[Arcadia] &bNouveau rang: &f" + result.newRank);
        }
    }

    public void failDungeon(String dungeonId) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) return;

        DungeonConfig config = instance.getConfig();
        instance.setState(DungeonState.FAILED);

        // Capture player names BEFORE teleporting them back
        String playerNames = instance.getPlayerNames(server);

        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                if (config.teleportBackOnComplete) {
                    teleportBack(player);
                }
                player.sendSystemMessage(Component.literal("[Arcadia] Donjon echoue!").withStyle(ChatFormatting.RED));
            }
        }

        String localFailMsg = renderDungeonMessage(config.failMessage, playerNames, config);
        MessageUtil.broadcast(instance, localFailMsg);

        if (config.availableEverySeconds > 0) {
            dungeonAvailability.put(dungeonId, System.currentTimeMillis());
            availabilityAnnounced.remove(dungeonId);
        }

        if (config.announceCompletion && server != null && !playerNames.isEmpty()) {
            String msg = renderDungeonMessage(config.failMessage, playerNames, config);
            broadcastMessage(msg, instance.getPlayers());
        }

        for (UUID pid : instance.getPlayers()) playerToDungeon.remove(pid);
        for (UUID pid : instance.getPlayers()) clearAllowedBeneficialEffects(pid);
        instance.cleanup();
        activeInstances.remove(dungeonId);
    }

    public void stopDungeon(String dungeonId) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) return;

        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                teleportBack(player);
                player.sendSystemMessage(Component.literal("[Arcadia] Donjon arrete par un administrateur.").withStyle(ChatFormatting.YELLOW));
            }
        }

        for (UUID pid : instance.getPlayers()) playerToDungeon.remove(pid);
        for (UUID pid : instance.getPlayers()) clearAllowedBeneficialEffects(pid);
        instance.cleanup();
        activeInstances.remove(dungeonId);
    }

    public void stopAllDungeons() {
        for (String id : new ArrayList<>(activeInstances.keySet())) {
            stopDungeon(id);
        }
    }

    public void forceReset(String dungeonId) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) return;

        for (UUID playerId : new ArrayList<>(instance.getPlayers())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                teleportBack(player);
                player.sendSystemMessage(Component.literal("[Arcadia] Le donjon a ete reinitialise.").withStyle(ChatFormatting.RED));
            }
            playerToDungeon.remove(playerId);
        }

        instance.cleanup();
        for (UUID playerId : instance.getPlayers()) clearAllowedBeneficialEffects(playerId);
        activeInstances.remove(dungeonId);
        ArcadiaDungeon.LOGGER.info("Dungeon {} force reset", dungeonId);
    }

    public void scheduleRemoval(String dungeonId) {
        if (dungeonId == null || dungeonId.isBlank() || !activeInstances.containsKey(dungeonId)) {
            return;
        }

        pendingCompletions.remove(dungeonId);
        pendingFails.add(dungeonId);
    }

    public void scheduleFailDungeon(String dungeonId) {
        scheduleRemoval(dungeonId);
    }

    public void scheduleCompletion(String dungeonId) {
        if (dungeonId == null || dungeonId.isBlank() || !activeInstances.containsKey(dungeonId)) {
            return;
        }

        if (pendingFails.contains(dungeonId)) {
            return;
        }

        pendingCompletions.add(dungeonId);
    }

    private final Set<UUID> pendingPlayerRemovals = ConcurrentHashMap.newKeySet();

    public void schedulePlayerRemoval(UUID playerId) {
        pendingPlayerRemovals.add(playerId);
    }

    public boolean isPendingRemoval(UUID playerId) {
        return pendingPlayerRemovals.contains(playerId);
    }

    public void processPendingFails() {
        if (!pendingPlayerRemovals.isEmpty()) {
            List<UUID> removals = new ArrayList<>(pendingPlayerRemovals);
            pendingPlayerRemovals.clear();
            for (UUID id : removals) {
                removePlayerFromDungeon(id);
            }
        }
        if (!pendingCompletions.isEmpty()) {
            List<String> toProcess = new ArrayList<>(pendingCompletions);
            pendingCompletions.clear();
            for (String id : toProcess) {
                if (!pendingFails.contains(id)) {
                    completeDungeon(id);
                }
            }
        }
        if (!pendingFails.isEmpty()) {
            List<String> toProcess = new ArrayList<>(pendingFails);
            pendingFails.clear();
            for (String id : toProcess) {
                failDungeon(id);
            }
        }
    }

    public void removePlayerFromDungeon(UUID playerId) {
        String dungeonId = playerToDungeon.get(playerId);
        if (dungeonId == null) {
            return;
        }
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null || !instance.getPlayers().contains(playerId)) {
            playerToDungeon.remove(playerId);
            clearAllowedBeneficialEffects(playerId);
            return;
        }

        String playerName = "";
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            playerName = player.getName().getString();
            for (BossInstance boss : instance.getActiveBosses().values()) {
                boss.removePlayerFromBossBar(player);
            }
            teleportBack(player);
        }

        instance.removePlayer(playerId);
        playerToDungeon.remove(playerId);
        clearAllowedBeneficialEffects(playerId);

        if (!instance.getPlayers().isEmpty() && !playerName.isEmpty()) {
            String finalName = playerName;
            for (UUID otherId : instance.getPlayers()) {
                ServerPlayer other = server.getPlayerList().getPlayer(otherId);
                if (other != null) {
                    other.sendSystemMessage(Component.literal("[Arcadia] " + finalName + " a quitte le donjon! ("
                            + instance.getPlayerCount() + " joueur(s) restant(s))")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }

        if (instance.getPlayers().isEmpty()) {
            DungeonConfig cfg = instance.getConfig();
            if (cfg.announceCompletion && !playerName.isEmpty()) {
                String msg = cfg.failMessage
                        .replace("%player%", playerName)
                        .replace("%dungeon%", cfg.name);
                broadcastMessage(msg);
            }
            scheduleRemoval(cfg.id);
        }

        PlayerProgressManager.getInstance().saveNow(playerId.toString());
    }

    public void clearCooldown(UUID playerId) {
        playerCooldowns.entrySet().removeIf(e -> e.getKey().startsWith(playerId.toString()));
    }

    public void clearAllCooldowns() {
        playerCooldowns.clear();
    }

    public void allowBeneficialEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier, int durationTicks) {
        if (player == null || effect == null || !effect.value().isBeneficial()) return;
        String effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString() + "#" + amplifier;
        long expiresAt = System.currentTimeMillis() + ((long) durationTicks * 50L) + 3000L;
        allowedBeneficialEffects.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>()).put(effectId, expiresAt);
    }

    public void allowBeneficialEffect(ServerPlayer player, net.minecraft.core.Holder.Reference<net.minecraft.world.effect.MobEffect> effect, int amplifier, int durationTicks) {
        allowBeneficialEffect(player, (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) effect, amplifier, durationTicks);
    }

    public boolean isBeneficialEffectAllowed(ServerPlayer player, net.minecraft.world.effect.MobEffectInstance effectInstance) {
        if (player == null || effectInstance == null || !effectInstance.getEffect().value().isBeneficial()) return true;
        String effectId = BuiltInRegistries.MOB_EFFECT.getKey(effectInstance.getEffect().value()).toString() + "#" + effectInstance.getAmplifier();
        Map<String, Long> allowed = allowedBeneficialEffects.get(player.getUUID());
        if (allowed == null) return false;
        Long expiry = allowed.get(effectId);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) {
            allowed.remove(effectId);
            if (allowed.isEmpty()) {
                allowedBeneficialEffects.remove(player.getUUID());
            }
            return false;
        }
        return true;
    }

    public void clearAllowedBeneficialEffects(UUID playerId) {
        allowedBeneficialEffects.remove(playerId);
    }

    public DungeonInstance getInstance(String dungeonId) {
        return activeInstances.get(dungeonId);
    }

    public Map<String, DungeonInstance> getActiveInstances() {
        return Collections.unmodifiableMap(activeInstances);
    }

    public DungeonInstance getPlayerDungeon(UUID playerId) {
        String dungeonId = playerToDungeon.get(playerId);
        if (dungeonId != null) {
            DungeonInstance instance = activeInstances.get(dungeonId);
            if (instance != null && instance.getPlayers().contains(playerId)) {
                return instance;
            }
            // Stale entry
            playerToDungeon.remove(playerId);
        }
        return null;
    }

    public String findDungeonIdByBossEntity(net.minecraft.world.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        for (Map.Entry<String, DungeonInstance> entry : activeInstances.entrySet()) {
            for (BossInstance boss : entry.getValue().getActiveBosses().values()) {
                if (boss.getBossEntity() == entity) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public void saveReturnPoint(ServerPlayer player) {
        SpawnPointConfig returnPoint = new SpawnPointConfig(
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        );
        playerReturnPoints.put(player.getUUID(), returnPoint);
    }

    public void teleportBack(ServerPlayer player) {
        SpawnPointConfig returnPoint = playerReturnPoints.remove(player.getUUID());
        if (returnPoint != null) {
            teleportToSpawn(player, returnPoint);
        }
    }

    public void teleportToSpawn(ServerPlayer player, SpawnPointConfig spawn) {
        if (server == null || spawn == null) return;
        ResourceLocation dimLoc = ResourceLocation.tryParse(spawn.dimension);
        if (dimLoc == null) {
            ArcadiaDungeon.LOGGER.error("Invalid dimension: {}", spawn.dimension);
            return;
        }
        ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
        ServerLevel level = server.getLevel(dimKey);
        if (level != null) {
            player.teleportTo(level, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
        }
    }

    private boolean canResolveSpawn(SpawnPointConfig spawn) {
        if (server == null || spawn == null || spawn.dimension == null || spawn.dimension.isEmpty()) {
            return false;
        }
        ResourceLocation dimLoc = ResourceLocation.tryParse(spawn.dimension);
        if (dimLoc == null) {
            return false;
        }
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
        return server.getLevel(dimKey) != null;
    }

    private String renderDungeonMessage(String template, String playerValue, DungeonConfig config) {
        if (template == null || config == null) {
            return null;
        }

        return template
                .replace("%player%", playerValue == null ? "" : playerValue)
                .replace("%dungeon%", config.name == null ? "" : config.name)
                .replace("%id%", config.id == null ? "" : config.id);
    }

    public void checkAvailabilityAnnouncements() {
        if (server == null) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : dungeonAvailability.entrySet()) {
            String dungeonId = entry.getKey();
            if (availabilityAnnounced.contains(dungeonId)) continue;

            // Don't announce if this dungeon is currently active
            if (activeInstances.containsKey(dungeonId)) continue;

            DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
            if (config == null || !config.announceAvailability || config.availableEverySeconds <= 0) continue;

            long elapsed = (now - entry.getValue()) / 1000;
            // Minimum 10s delay before announcing to avoid spam right after fail/complete
            if (elapsed >= config.availableEverySeconds && elapsed >= 10) {
                availabilityAnnounced.add(dungeonId);
                broadcastAvailabilityAnnouncement(config);
            }
        }

        for (DungeonConfig config : ConfigManager.getInstance().getDungeonConfigs().values()) {
            if (config == null || config.id == null || config.id.isBlank()) {
                continue;
            }
            if (!config.enabled || !config.announceAvailability || config.announceIntervalMinutes <= 0) {
                nextAvailabilityAnnouncements.remove(config.id);
                continue;
            }
            if (activeInstances.containsKey(config.id)) {
                nextAvailabilityAnnouncements.remove(config.id);
                continue;
            }

            long intervalMs = config.announceIntervalMinutes * 60_000L;
            long nextAt = nextAvailabilityAnnouncements.computeIfAbsent(config.id, id -> now + intervalMs);
            if (now >= nextAt) {
                broadcastAvailabilityAnnouncement(config);
                nextAvailabilityAnnouncements.put(config.id, now + intervalMs);
            }
        }
    }

    private void broadcastAvailabilityAnnouncement(DungeonConfig config) {
        String msg = config.availabilityMessage
                .replace("%dungeon%", config.name)
                .replace("%id%", config.id);
        MutableComponent text = (MutableComponent) parseColorCodes(msg);
        MutableComponent button = Component.literal(" [LANCER]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arcadia_dungeon start " + config.id))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Cliquez pour lancer " + config.name).withStyle(ChatFormatting.YELLOW)))
                );
        Component full = text.append(button);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (getPlayerDungeon(player.getUUID()) != null) continue;
            player.sendSystemMessage(full);
        }
    }

    /**
     * Prune expired cooldowns and availability entries to prevent memory leaks.
     * Called periodically from tick handler.
     */
    public void pruneExpiredData() {
        long now = System.currentTimeMillis();

        // Prune player cooldowns older than max possible cooldown (24h safety cap)
        playerCooldowns.entrySet().removeIf(e -> (now - e.getValue()) / 1000 > 86400);
        allowedBeneficialEffects.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(effect -> effect.getValue() < now);
            return entry.getValue().isEmpty();
        });

        // Prune availability entries for dungeons that no longer exist or already announced
        dungeonAvailability.entrySet().removeIf(e -> {
            DungeonConfig config = ConfigManager.getInstance().getDungeon(e.getKey());
            if (config == null) return true;
            long elapsed = (now - e.getValue()) / 1000;
            return elapsed > config.availableEverySeconds + 3600; // 1h grace after expiry
        });
        nextAvailabilityAnnouncements.keySet().removeIf(id -> ConfigManager.getInstance().getDungeon(id) == null);

        // Prune return points for players not in any dungeon
        playerReturnPoints.entrySet().removeIf(e -> getPlayerDungeon(e.getKey()) == null);
    }

    public void broadcastMessage(String message) {
        broadcastMessage(message, Collections.emptySet());
    }

    public void broadcastMessage(String message, Set<UUID> excludedPlayers) {
        if (server == null) return;
        Component component = parseColorCodes(message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (excludedPlayers != null && excludedPlayers.contains(player.getUUID())) {
                continue;
            }
            player.sendSystemMessage(component);
        }
    }

    private void kickParasites(DungeonInstance instance) {
        if (server == null) return;
        DungeonConfig config = instance.getConfig();
        if (!config.hasArea()) return;

        Set<UUID> dungeonPlayers = instance.getPlayers();

        // Kick parasite players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (dungeonPlayers.contains(player.getUUID())) continue;
            if (player.isSpectator() || player.hasPermissions(2)) continue;
            try { if (net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(player, ArcadiaDungeon.BYPASS_ANTIPARASITE)) continue; } catch (Exception ignored) {}

            String playerDim = player.level().dimension().location().toString();
            if (config.isInArea(playerDim, player.getX(), player.getY(), player.getZ())) {
                ServerLevel overworld = server.overworld();
                net.minecraft.core.BlockPos spawnPos = overworld.getSharedSpawnPos();
                player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                player.sendSystemMessage(Component.literal("[Arcadia] Un donjon demarre dans cette zone! Vous avez ete teleporte au spawn.")
                        .withStyle(ChatFormatting.RED));
                ArcadiaDungeon.LOGGER.info("Kicked parasite {} from dungeon area {}", player.getName().getString(), config.id);
            }
        }

        // Clear all existing mobs in the dungeon area
        clearMobsInArea(config);
    }

    public void triggerScriptedWalls(String dungeonId, String activationCondition) {
        if (server == null || dungeonId == null || activationCondition == null || activationCondition.isBlank()) {
            return;
        }

        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) {
            return;
        }

        DungeonConfig config = instance.getConfig();
        if (config.scriptedWalls == null || config.scriptedWalls.isEmpty()) {
            return;
        }

        String normalized = activationCondition.trim().toUpperCase(Locale.ROOT);
        for (DungeonConfig.ScriptedWallConfig wall : config.scriptedWalls) {
            if (wall == null || wall.blocks == null || wall.blocks.isEmpty()) {
                continue;
            }
            if (wall.activationCondition == null || wall.activationCondition.isBlank()) {
                continue;
            }

            String wallCondition = wall.activationCondition.trim().toUpperCase(Locale.ROOT);
            if (!wallCondition.equals(normalized)) {
                continue;
            }

            String triggerKey = wall.id + "@" + wallCondition;
            if (!instance.markWallConditionTriggered(triggerKey)) {
                continue;
            }

            applyScriptedWall(config, wall);
        }
    }

    private void applyScriptedWall(DungeonConfig config, DungeonConfig.ScriptedWallConfig wall) {
        String action = wall.action == null ? "TOGGLE" : wall.action.trim().toUpperCase(Locale.ROOT);
        boolean placeBlocks = !"REMOVE".equals(action);

        for (DungeonConfig.AreaPos blockPos : wall.blocks) {
            ResourceLocation dimLoc = ResourceLocation.tryParse(blockPos.dimension);
            if (dimLoc == null) {
                continue;
            }

            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) {
                continue;
            }

            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(blockPos.x, blockPos.y, blockPos.z);
            if (placeBlocks) {
                level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState());
            } else {
                level.removeBlock(pos, false);
            }
        }

        ArcadiaDungeon.LOGGER.info("Applied scripted wall {} for dungeon {} with action {}", wall.id, config.id, action);
    }

    private void clearMobsInArea(DungeonConfig config) {
        if (server == null || !config.hasArea()) return;

        ResourceLocation dimLoc = ResourceLocation.tryParse(config.areaPos1.dimension);
        if (dimLoc == null) return;
        ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) return;

        int minX = Math.min(config.areaPos1.x, config.areaPos2.x);
        int maxX = Math.max(config.areaPos1.x, config.areaPos2.x);
        int minY = Math.min(config.areaPos1.y, config.areaPos2.y);
        int maxY = Math.max(config.areaPos1.y, config.areaPos2.y);
        int minZ = Math.min(config.areaPos1.z, config.areaPos2.z);
        int maxZ = Math.max(config.areaPos1.z, config.areaPos2.z);

        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        List<net.minecraft.world.entity.Mob> mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area);

        int cleared = 0;
        for (net.minecraft.world.entity.Mob mob : mobs) {
            mob.discard();
            cleared++;
        }

        if (cleared > 0) {
            ArcadiaDungeon.LOGGER.info("Cleared {} mobs from dungeon area {}", cleared, config.id);
        }
    }

    public void broadcastClickableJoin(DungeonConfig config, String playerName, long secondsLeft) {
        if (server == null) return;
        String cmd = "/arcadia_dungeon join " + config.id;

        net.minecraft.network.chat.MutableComponent msg = Component.empty()
                .append(Component.literal("[Donjon] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(playerName).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" lance ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(config.name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("! ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[REJOINDRE (" + secondsLeft + "s)]")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Cliquez pour rejoindre " + config.name + "!\n" + cmd)
                                                .withStyle(ChatFormatting.GREEN)))
                        )
                );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(msg);
        }
    }

    public void broadcastClickableReminder(DungeonConfig config, long secondsLeft) {
        if (server == null) return;
        String cmd = "/arcadia_dungeon join " + config.id;

        net.minecraft.network.chat.MutableComponent msg = Component.empty()
                .append(Component.literal("[Donjon] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(config.name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" - encore ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("! ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[REJOINDRE]")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Cliquez pour rejoindre!\n" + cmd)
                                                .withStyle(ChatFormatting.GREEN)))
                        )
                );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(msg);
        }
    }

    public static Component parseColorCodes(String message) {
        return LegacyColorFormatter.parse(message);
    }

    public static String formatTime(long seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        } else if (seconds >= 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }
}
