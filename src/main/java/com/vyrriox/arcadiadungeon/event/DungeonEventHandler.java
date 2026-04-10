package com.vyrriox.arcadiadungeon.event;

import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.util.MessageUtil;
import com.vyrriox.arcadiadungeon.util.SparkUtil;
import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.boss.BossManager;
import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.MobSpawnConfig;
import com.vyrriox.arcadiadungeon.config.WaveConfig;
import com.vyrriox.arcadiadungeon.config.PhaseConfig;
import com.vyrriox.arcadiadungeon.dungeon.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

public class DungeonEventHandler {

    private final Set<Integer> announcedRecruitmentMilestones = new HashSet<>(); // Fix #9: track milestones
    private final Set<Integer> announcedTimerMilestones = new HashSet<>();
    private long nextWaveCheckAt = 0L;
    private long nextRecruitmentCheckAt = 0L;
    private long nextDungeonTimerCheckAt = 0L;
    private long nextRestrictedEffectsCheckAt = 0L;
    private long nextAntiFlyCheckAt = 0L;
    private long nextParasiteCheckAt = 0L;
    private long nextContainmentCheckAt = 0L;
    private long nextRecruitFreezeAt = 0L;
    private long nextAvailabilityCheckAt = 0L;
    private long nextProgressFlushAt = 0L;
    private long nextWeeklyTickAt = 0L;
    private long nextPruneAt = 0L;
    private long nextManagedCombatCheckAt = 0L;

    // Wand: track selected dungeon per player
    public static final Map<UUID, String> wandDungeon = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<UUID, net.minecraft.core.BlockPos> wandPos1 = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<UUID, net.minecraft.core.BlockPos> wandPos2 = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<UUID, String> wandWall = new java.util.concurrent.ConcurrentHashMap<>();

    public static final String AREA_WAND_TAG = "arcadia_area_wand";
    public static final String WALL_WAND_TAG = "arcadia_wall_wand";

    private static boolean isDebugEnabled(DungeonConfig config) {
        return config != null && config.debugMode && ArcadiaDungeon.LOGGER.isDebugEnabled();
    }

    private static void logHandlerError(String handlerName, Exception e) {
        ArcadiaDungeon.LOGGER.error("Arcadia: erreur inattendue dans {}", handlerName, e);
    }

    private static void logHandlerError(String handlerName, String context, Exception e) {
        ArcadiaDungeon.LOGGER.error("Arcadia: erreur inattendue dans {} [{}]", handlerName, context, e);
    }

    @SubscribeEvent
    public void onPlayerInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String toolTag = getToolTag(player);
        if (toolTag == null) return;
        if (!ensureWandSelection(player, toolTag)) return;

        net.minecraft.core.BlockPos pos = event.getPos();
        if (WALL_WAND_TAG.equals(toolTag)) {
            toggleScriptedWallBlock(player, pos);
        } else {
            wandPos1.put(player.getUUID(), pos);
            player.sendSystemMessage(Component.literal("[Arcadia Wand] Pos1 definie: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.GREEN));
            trySaveWandArea(player);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerInteractRight(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String toolTag = getToolTag(player);
        if (toolTag == null) return;
        if (!ensureWandSelection(player, toolTag)) return;

        net.minecraft.core.BlockPos pos = event.getPos();
        if (WALL_WAND_TAG.equals(toolTag)) {
            toggleScriptedWallBlock(player, pos);
        } else {
            wandPos2.put(player.getUUID(), pos);
            player.sendSystemMessage(Component.literal("[Arcadia Wand] Pos2 definie: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.AQUA));
            trySaveWandArea(player);
        }
        event.setCanceled(true);
    }

    private String getToolTag(ServerPlayer player) {
        String name = player.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.empty()).getString();
        if (name.contains(AREA_WAND_TAG)) return AREA_WAND_TAG;
        if (name.contains(WALL_WAND_TAG)) return WALL_WAND_TAG;
        return null;
    }

    private boolean ensureWandSelection(ServerPlayer player, String toolTag) {
        if (!wandDungeon.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[Arcadia] Aucun donjon selectionne pour cet outil.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (WALL_WAND_TAG.equals(toolTag) && !wandWall.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[Arcadia] Selectionnez un mur scripté avec /arcadia_dungeon admin wall_select <dungeon> <wallId>.").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private void trySaveWandArea(ServerPlayer player) {
        net.minecraft.core.BlockPos p1 = wandPos1.get(player.getUUID());
        net.minecraft.core.BlockPos p2 = wandPos2.get(player.getUUID());
        if (p1 == null || p2 == null) return;

        String dungeonId = wandDungeon.get(player.getUUID());
        var config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) return;

        String dim = player.level().dimension().location().toString();
        DungeonConfig.AreaPos pos1 = new DungeonConfig.AreaPos(dim, p1.getX(), p1.getY(), p1.getZ());
        DungeonConfig.AreaPos pos2 = new DungeonConfig.AreaPos(dim, p2.getX(), p2.getY(), p2.getZ());

        config.areaPos1 = pos1;
        config.areaPos2 = pos2;
        String label = config.name;
        ConfigManager.getInstance().saveDungeon(config);

        int sx = Math.abs(p2.getX() - p1.getX()) + 1;
        int sy = Math.abs(p2.getY() - p1.getY()) + 1;
        int sz = Math.abs(p2.getZ() - p1.getZ()) + 1;
        player.sendSystemMessage(Component.literal("[Arcadia Wand] Zone sauvegardee pour " + label + "! " + sx + "x" + sy + "x" + sz + " (" + (sx*sy*sz) + " blocs)")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        wandPos1.remove(player.getUUID());
        wandPos2.remove(player.getUUID());
    }

    private void toggleScriptedWallBlock(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        String dungeonId = wandDungeon.get(player.getUUID());
        String wallId = wandWall.get(player.getUUID());
        if (dungeonId == null || wallId == null) return;

        var config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) return;

        var wall = config.scriptedWalls.stream().filter(w -> w.id.equals(wallId)).findFirst().orElse(null);
        if (wall == null) return;

        String dim = player.level().dimension().location().toString();
        var existing = wall.blocks.stream().filter(b -> b.dimension.equals(dim) && b.x == pos.getX() && b.y == pos.getY() && b.z == pos.getZ()).findFirst().orElse(null);
        if (existing != null) {
            wall.blocks.remove(existing);
            player.sendSystemMessage(Component.literal("[Arcadia Wall] Bloc retire: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.YELLOW));
        } else {
            wall.blocks.add(new DungeonConfig.AreaPos(dim, pos.getX(), pos.getY(), pos.getZ()));
            player.sendSystemMessage(Component.literal("[Arcadia Wall] Bloc ajoute: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.GREEN));
        }
        ConfigManager.getInstance().saveDungeon(config);
    }

    // === CHARM/EXTERNAL EFFECT BLOCKING ===
    @SubscribeEvent
    public void onEffectApplied(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
            if (instance == null) return;

            var effectInstance = event.getEffectInstance();
            if (effectInstance == null) return;

            if (effectInstance.getEffect().value().isBeneficial()
                    && !DungeonManager.getInstance().isBeneficialEffectAllowed(player, effectInstance)) {
                event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        } catch (Exception e) {
            logHandlerError("onEffectApplied", e);
        }
    }

    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (DungeonManager.getInstance().getPlayerDungeon(player.getUUID()) == null) return;

            var item = event.getItem().getItem();
            if (item == net.minecraft.world.item.Items.GOLDEN_APPLE) {
                DungeonManager.getInstance().allowBeneficialEffect(player, net.minecraft.world.effect.MobEffects.REGENERATION, 1, 100);
                DungeonManager.getInstance().allowBeneficialEffect(player, net.minecraft.world.effect.MobEffects.ABSORPTION, 0, 2400);
            } else if (item == net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE) {
                DungeonManager.getInstance().allowBeneficialEffect(player, net.minecraft.world.effect.MobEffects.REGENERATION, 1, 400);
                DungeonManager.getInstance().allowBeneficialEffect(player, net.minecraft.world.effect.MobEffects.ABSORPTION, 3, 2400);
                DungeonManager.getInstance().allowBeneficialEffect(player, net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 0, 6000);
                DungeonManager.getInstance().allowBeneficialEffect(player, net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 0, 6000);
            }
        } catch (Exception e) {
            logHandlerError("onItemUseFinish", e);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        try {
            long now = System.currentTimeMillis();
            Entity entity = event.getEntity();
            if (entity instanceof Player) return;

            if (entity instanceof Projectile projectile && CombatTuning.shouldCancelProjectileSpawn(projectile, now)) {
                event.setCanceled(true);
                return;
            }

            if (entity instanceof Vex vex && isArcadiaManaged(vex.getOwner())) {
                event.setCanceled(true);
                return;
            }

            if (!(entity instanceof LivingEntity living)) return;
            if (isArcadiaManaged(living)) return;

            for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
                var config = instance.getConfig();
                if (!config.hasArea()) continue;
                String dimension = living.level().dimension().location().toString();
                if (!config.isInArea(dimension, living.getX(), living.getY(), living.getZ())) continue;

                event.setCanceled(true);
                if (isDebugEnabled(config)) {
                    ArcadiaDungeon.LOGGER.debug("Blocked unmanaged entity {} inside active dungeon {}", entity.getType(), config.id);
                }
                return;
            }
        } catch (Exception e) {
            logHandlerError("onEntityJoinLevel", "entity=" + event.getEntity().getType(), e);
        }
    }

    // === COMMAND BLOCKING ===
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        try {
            var source = event.getParseResults().getContext().getSource();
            if (!(source.getEntity() instanceof ServerPlayer player)) return;
            DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
            if (instance == null || !instance.getConfig().settings.blockTeleportCommands) return;

            String input = event.getParseResults().getReader().getString().trim();
            if (input.startsWith("/")) input = input.substring(1);
            String rootCmd = input.split(" ")[0].toLowerCase();
            String cmdNoPrefix = rootCmd.contains(":") ? rootCmd.substring(rootCmd.indexOf(':') + 1) : rootCmd;

            for (String blocked : instance.getConfig().settings.blockedCommands) {
                if (rootCmd.equals(blocked) || cmdNoPrefix.equals(blocked)) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("[Arcadia] Cette commande est bloquee pendant le donjon!").withStyle(ChatFormatting.RED));
                    return;
                }
            }
        } catch (Exception e) {
            logHandlerError("onCommand", e);
        }
    }

    // === TICK ===
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        boolean sparkSectionStarted = SparkUtil.startSection("tick");
        try {
            try {
                long now = System.currentTimeMillis();
                DungeonManager.getInstance().processPendingFails();
                BossManager.getInstance().tickAllBosses(); // Fix #19: early-exit is inside BossManager now

                if (now >= nextWaveCheckAt) {
                    nextWaveCheckAt = now + 500L;
                    checkWaveStates();
                }
                if (now >= nextRecruitmentCheckAt) {
                    nextRecruitmentCheckAt = now + 1000L;
                    checkRecruitment();
                }
                if (now >= nextDungeonTimerCheckAt) {
                    nextDungeonTimerCheckAt = now + 1000L;
                    checkDungeonTimers();
                }
                if (now >= nextRestrictedEffectsCheckAt) {
                    nextRestrictedEffectsCheckAt = now + 5000L;
                    checkRestrictedEffects();
                }
                if (now >= nextAntiFlyCheckAt) {
                    nextAntiFlyCheckAt = now + 2000L;
                    checkAntiFly();
                }
                if (now >= nextParasiteCheckAt) {
                    nextParasiteCheckAt = now + 5000L;
                    checkParasites();
                }
                if (now >= nextContainmentCheckAt) {
                    nextContainmentCheckAt = now + 5000L;
                    checkPlayerContainment();
                }
                if (now >= nextRecruitFreezeAt) {
                    nextRecruitFreezeAt = now + 5000L;
                    freezeRecruitingPlayers();
                }
                if (now >= nextAvailabilityCheckAt) {
                    nextAvailabilityCheckAt = now + 10000L;
                    DungeonManager.getInstance().checkAvailabilityAnnouncements();
                }
                if (now >= nextProgressFlushAt) {
                    nextProgressFlushAt = now + 30000L;
                    PlayerProgressManager.getInstance().flushDirty();
                }
                if (now >= nextWeeklyTickAt) {
                    nextWeeklyTickAt = now + 60000L;
                    var server = DungeonManager.getInstance().getServer();
                    if (server != null) WeeklyLeaderboard.getInstance().tick(server);
                }
                if (now >= nextPruneAt) {
                    nextPruneAt = now + 300000L;
                    DungeonManager.getInstance().pruneExpiredData();
                }
                if (now >= nextManagedCombatCheckAt) {
                    nextManagedCombatCheckAt = now + 100L;
                    checkManagedCombat(now);
                }
            } catch (Exception e) {
                logHandlerError("onServerTick", e);
            }
        } finally {
            if (sparkSectionStarted) {
                SparkUtil.endSection(sparkSectionStarted);
            }
        }
    }

    // === ENTITY EVENTS ===
    @SubscribeEvent
    public void onEntityDeath(LivingDeathEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity.getTags().contains("arcadia_boss")) { handleBossDeath(entity); return; }
            if (entity.getTags().contains("arcadia_wave_mob")) return;
            if (entity instanceof ServerPlayer player) handlePlayerDeath(player, event);
        } catch (Exception e) {
            logHandlerError("onEntityDeath", "entity=" + event.getEntity().getType(), e);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityDamage(LivingIncomingDamageEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            long now = System.currentTimeMillis();

            // Anti-PVP
            if (entity instanceof ServerPlayer targetPlayer) {
                DungeonInstance targetDungeon = DungeonManager.getInstance().getPlayerDungeon(targetPlayer.getUUID());
                if (targetDungeon != null && !targetDungeon.getConfig().settings.pvp) {
                    if (event.getSource().getEntity() instanceof Player attacker) {
                        if (targetDungeon.getPlayers().contains(attacker.getUUID())) {
                            event.setCanceled(true);
                            return;
                        }
                    }
                }
            }

            boolean sparkSectionStarted = SparkUtil.startSection("combat");
            try {
                if (entity.getTags().contains("arcadia_managed") && CombatTuning.shouldDodge(entity, event.getSource().getDirectEntity(), now)) {
                    sendDodgeMessage(entity, event.getSource().getEntity());
                    event.setCanceled(true);
                    return;
                }

                if (event.getSource().getEntity() instanceof LivingEntity attacker
                        && attacker.getTags().contains("arcadia_managed")
                        && CombatTuning.shouldCancelDirectMeleeForCooldown(attacker, event.getSource().getDirectEntity(), now)) {
                    event.setCanceled(true);
                    return;
                }
            } finally {
                if (sparkSectionStarted) {
                    SparkUtil.endSection(sparkSectionStarted);
                }
            }

            if (!entity.getTags().contains("arcadia_boss")) return;

            // Fix #7: direct lookup instead of O(D*B) scan
            for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
                for (BossInstance boss : instance.getActiveBosses().values()) {
                    if (boss.getBossEntity() != entity) continue;

                    if (boss.isTransitioning()) { event.setCanceled(true); return; }
                    if (boss.shouldBlockDamageWhileSummonsAlive()) {
                        event.setCanceled(true);
                        boss.sendSummonWarning(instance.getPlayers());
                        return;
                    }

                    // Reveal boss bar on first hit (optional bosses)
                    boss.revealBossBar();

                    // Anti-phase-skip
                    float currentHp = boss.getBossEntity().getHealth();
                    float maxHp = boss.getBossEntity().getMaxHealth();
                    float resultHp = currentHp - event.getAmount();
                    float floor = getHealthFloorForNextPhase(boss, maxHp);
                    if (floor > 0 && resultHp < floor) {
                        // Force the boss slightly below the threshold so the next tick can
                        // reliably advance the phase instead of pinning health exactly on it.
                        float targetHp = Math.max(Math.nextDown(floor), 0.5f);
                        float capped = currentHp - targetHp;
                        if (capped > 0) event.setAmount(capped); else event.setCanceled(true);
                        resultHp = currentHp - event.getAmount();
                    }

                    if (!event.isCanceled() && maxHp > 0) {
                        boss.checkPhaseTransition(resultHp / maxHp, instance.getPlayers());
                    }
                    return;
                }
            }
        } catch (Exception e) {
            logHandlerError("onEntityDamage", "entity=" + event.getEntity().getType(), e);
        }
    }

    private float getHealthFloorForNextPhase(BossInstance boss, float maxHp) {
        var phases = boss.getConfig().phases;
        for (int i = boss.getCurrentPhase() + 1; i < phases.size(); i++) {
            return (float) (phases.get(i).healthThreshold * maxHp);
        }
        return 0;
    }

    private void sendDodgeMessage(LivingEntity entity, Entity attacker) {
        String dodgeMessage = CombatTuning.getDodgeMessage(entity);
        if (dodgeMessage == null || dodgeMessage.isEmpty()) {
            return;
        }

        if (attacker instanceof ServerPlayer player) {
            MessageUtil.send(player, dodgeMessage);
            return;
        }

        if (entity instanceof ServerPlayer player) {
            MessageUtil.send(player, dodgeMessage);
        }
    }

    private void checkManagedCombat(long now) {
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            for (BossInstance boss : instance.getActiveBosses().values()) {
                LivingEntity bossEntity = boss.getBossEntity();
                if (bossEntity != null && bossEntity.isAlive()) {
                    CombatTuning.tryExtendedMeleeAttack(bossEntity, now);
                }
                for (LivingEntity summon : boss.getSummonedMobs()) {
                    if (summon.isAlive()) {
                        CombatTuning.tryExtendedMeleeAttack(summon, now);
                    }
                }
            }

            for (WaveInstance wave : instance.getWaveInstances()) {
                for (LivingEntity mob : wave.getAliveMobs()) {
                    if (mob.isAlive()) {
                        CombatTuning.tryExtendedMeleeAttack(mob, now);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().getTags().contains("arcadia_no_loot")) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains("arcadia_no_loot")) event.setCanceled(true);
    }

    // === PLAYER EVENTS ===
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(uuid);
            if (instance != null) {
                DungeonManager.getInstance().removePlayerFromDungeon(uuid);
            }
            // Cleanup wand data
            wandDungeon.remove(uuid);
            wandPos1.remove(uuid);
            wandPos2.remove(uuid);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Fix #16: skip respawn TP if player is pending removal (max deaths reached)
        if (DungeonManager.getInstance().isPendingRemoval(player.getUUID())) return;

        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null) return;

        DungeonManager.getInstance().teleportToSpawn(player, instance.getConfig().spawnPoint);
        player.sendSystemMessage(Component.literal("[Arcadia] Respawn au debut du donjon. ("
                + instance.getRemainingLives(player.getUUID()) + " vie(s) restante(s))")
                .withStyle(ChatFormatting.YELLOW));
    }

    // === HANDLERS ===
    private void handleBossDeath(LivingEntity entity) {
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            String dungeonId = entry.getKey();

            for (Map.Entry<String, BossInstance> bossEntry : new ArrayList<>(instance.getActiveBosses().entrySet())) {
                BossInstance boss = bossEntry.getValue();
                if (boss.getBossEntity() != entity) continue;

                String bossId = bossEntry.getKey();
                if (isDebugEnabled(instance.getConfig())) {
                    ArcadiaDungeon.LOGGER.debug("Boss death handled for dungeon={} boss={}", dungeonId, bossId);
                }

                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        instance.giveRewards(player, boss.getConfig().rewards);
                        String bossLabel = (boss.getConfig().customName == null || boss.getConfig().customName.isEmpty())
                                ? bossId
                                : boss.getConfig().customName;
                        Component bossName = DungeonManager.parseColorCodes(bossLabel);
                        player.sendSystemMessage(Component.literal("[Arcadia] Boss ").withStyle(ChatFormatting.GOLD)
                                .append(bossName)
                                .append(Component.literal(" vaincu!").withStyle(ChatFormatting.GOLD)));
                    }
                }

                // Fix #2: remove from map BEFORE cleanup to prevent re-entry issues
                instance.removeBossInstance(bossId);
                boss.cleanup();

                // Inter-wave boss: let checkWaveStates handle resume when required bosses dead
                if (instance.isWaitingForInterWaveBoss()) {
                    // checkWaveStates will detect allRequiredBossesDefeated and resume
                } else if (instance.hasNextBoss()) {
                    boolean spawned = BossManager.getInstance().spawnNextBoss(instance);
                    if (spawned) {
                        for (UUID playerId : instance.getPlayers()) {
                            ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                            if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Prochain boss en approche...").withStyle(ChatFormatting.YELLOW));
                        }
                    } else if (instance.allRequiredBossesDefeated()) {
                        if (!instance.hasWaves() || instance.areWavesCompleted()) {
                            for (BossInstance remaining : new ArrayList<>(instance.getActiveBosses().values())) {
                                remaining.cleanup();
                            }
                            instance.getActiveBosses().clear();
                            DungeonManager.getInstance().scheduleCompletion(dungeonId);
                        }
                    }
                } else if (instance.allRequiredBossesDefeated() && !instance.isWaitingForInterWaveBoss()) {
                    // All required bosses defeated — check if waves done too
                    if (!instance.hasWaves() || instance.areWavesCompleted()) {
                        // Cleanup non-required bosses still alive
                        for (BossInstance remaining : new ArrayList<>(instance.getActiveBosses().values())) {
                            remaining.cleanup();
                        }
                        instance.getActiveBosses().clear();
                        DungeonManager.getInstance().scheduleCompletion(dungeonId);
                    }
                }
                return;
            }
        }
    }

    private void handlePlayerDeath(ServerPlayer player, LivingDeathEvent event) {
        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null) return;

        int deaths = instance.addDeath(player.getUUID());
        int maxDeaths = instance.getConfig().settings.maxDeaths;
        int remaining = instance.getRemainingLives(player.getUUID());
        if (isDebugEnabled(instance.getConfig())) {
            ArcadiaDungeon.LOGGER.debug("Player death in dungeon={} player={} deaths={} remaining={}",
                    instance.getConfig().id, player.getGameProfile().getName(), deaths, remaining);
        }

        if (maxDeaths > 0 && deaths >= maxDeaths) {
            player.sendSystemMessage(Component.literal("[Arcadia] Maximum de morts (" + maxDeaths + ") atteint! Vous etes exclu du donjon.").withStyle(ChatFormatting.RED));
            for (UUID otherId : instance.getPlayers()) {
                if (!otherId.equals(player.getUUID())) {
                    ServerPlayer other = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(otherId);
                    if (other != null) other.sendSystemMessage(Component.literal("[Arcadia] " + player.getName().getString() + " a ete exclu (trop de morts)!").withStyle(ChatFormatting.RED));
                }
            }
            DungeonManager.getInstance().schedulePlayerRemoval(player.getUUID());
        } else {
            player.sendSystemMessage(Component.literal("[Arcadia] Mort! " + remaining + " vie(s) restante(s).").withStyle(ChatFormatting.RED));
        }
    }

    // === RECRUITMENT ===
    private void freezeRecruitingPlayers() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            if (instance.getState() != DungeonState.RECRUITING) continue;
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    // Fix #18: longer duration (120 ticks = 6s), applied every 5s
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0, false, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 255, false, false, false));
                }
            }
        }
    }

    private void checkRestrictedEffects() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) continue;

                for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
                    if (!effect.getEffect().value().isBeneficial()) continue;
                    if (DungeonManager.getInstance().isBeneficialEffectAllowed(player, effect)) continue;
                    player.removeEffect(effect.getEffect());
                }
            }
        }
    }

    private void checkRecruitment() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            String dungeonId = entry.getKey();
            if (instance.getState() != DungeonState.RECRUITING) continue;

            long remaining = instance.getRecruitmentRemainingSeconds();

            // Fix #9: range-based milestone checks (won't miss on lag)
            int[] milestones = {60, 30, 10, 5};
            for (int m : milestones) {
                int key = Objects.hash(dungeonId, m);
                if (remaining <= m && remaining > m - 2 && !announcedRecruitmentMilestones.contains(key)) {
                    announcedRecruitmentMilestones.add(key);
                    for (UUID playerId : instance.getPlayers()) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Recrutement: " + m + "s restantes!").withStyle(ChatFormatting.YELLOW));
                    }
                    DungeonManager.getInstance().broadcastClickableReminder(instance.getConfig(), m);
                }
            }

            if (instance.isRecruitmentOver()) {
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        player.removeEffect(MobEffects.BLINDNESS);
                        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    }
                }
                clearRecruitmentMilestones(dungeonId);
                DungeonManager.getInstance().finishRecruitment(dungeonId);
            }
        }
    }

    // === WAVES ===
    private void checkWaveStates() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();

            // Handle inter-wave boss fight: wait for required inter-wave bosses to die, then resume waves
            if (instance.isWaitingForInterWaveBoss()) {
                if (instance.allRequiredBossesDefeated()) {
                    instance.setWaitingForInterWaveBoss(false);
                    instance.setState(DungeonState.ACTIVE);
                    if (instance.areWavesCompleted()) {
                        onWavesCompleted(instance);
                    }
                }
                continue;
            }

            // Allow wave processing if state is ACTIVE, or BOSS_FIGHT with only optional bosses alive
            if (instance.areWavesCompleted() || !instance.hasWaves()) continue;
            if (instance.getState() != DungeonState.ACTIVE && instance.getState() != DungeonState.BOSS_FIGHT) continue;
            if (instance.getState() == DungeonState.BOSS_FIGHT && !instance.allRequiredBossesDefeated()) continue;

            WaveInstance currentWave = instance.getCurrentWave();
            if (currentWave == null) { instance.setWavesCompleted(true); onWavesCompleted(instance); continue; }
            if (!currentWave.isSpawned() && !currentWave.tickDelay()) continue;

            if (!currentWave.isSpawned()) {
                currentWave.spawn(server, instance.getPlayerCount(), instance.getConfig().settings);
                String msg = currentWave.getConfig().startMessage;
                if (msg == null || msg.isEmpty()) msg = "&e[Donjon] &7Vague " + currentWave.getConfig().waveNumber + " !";
                MessageUtil.broadcast(instance, msg);
                DungeonManager.getInstance().triggerScriptedWalls(instance.getConfig().id, "WAVE_START:" + currentWave.getConfig().waveNumber);
                if (isDebugEnabled(instance.getConfig())) {
                    ArcadiaDungeon.LOGGER.debug("Wave spawned dungeon={} wave={} playerCount={}",
                            instance.getConfig().id, currentWave.getConfig().waveNumber, instance.getPlayerCount());
                }
            }

            currentWave.checkGlowing();

            if (currentWave.isCleared()) {
                int clearedWaveNumber = currentWave.getConfig().waveNumber;
                DungeonManager.getInstance().triggerScriptedWalls(instance.getConfig().id, "WAVE_COMPLETE:" + clearedWaveNumber);
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) player.sendSystemMessage(Component.literal("[Donjon] Vague " + clearedWaveNumber + " terminee!").withStyle(ChatFormatting.GREEN));
                }

                // Check for inter-wave boss after this wave
                boolean bossSpawned = BossManager.getInstance().spawnBossesForWave(instance, clearedWaveNumber);
                if (bossSpawned) {
                    instance.setWaitingForInterWaveBoss(true);
                    // Advance wave index now so waves resume after boss dies
                    if (!instance.advanceWave()) {
                        instance.setWavesCompleted(true);
                    }
                    for (UUID playerId : instance.getPlayers()) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) player.sendSystemMessage(Component.literal("[Donjon] Un boss approche!").withStyle(ChatFormatting.GOLD));
                    }
                } else {
                    if (!instance.advanceWave()) onWavesCompleted(instance);
                }
            }
        }
    }

    private void onWavesCompleted(DungeonInstance instance) {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        // Try to spawn remaining end-of-waves bosses
        boolean bossSpawned = false;
        if (instance.hasNextBoss()) {
            bossSpawned = BossManager.getInstance().spawnNextBoss(instance);
        }

        if (bossSpawned) {
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) player.sendSystemMessage(Component.literal("[Donjon] Toutes les vagues eliminees! Le boss approche...").withStyle(ChatFormatting.GOLD));
            }
            if (isDebugEnabled(instance.getConfig())) {
                ArcadiaDungeon.LOGGER.debug("Boss spawn scheduled after waves dungeon={}", instance.getConfig().id);
            }
        } else if (instance.allRequiredBossesDefeated()) {
            // No more bosses to spawn and all required are dead — complete
            for (BossInstance remaining : new ArrayList<>(instance.getActiveBosses().values())) {
                remaining.cleanup();
            }
            instance.getActiveBosses().clear();
            DungeonManager.getInstance().scheduleCompletion(instance.getConfig().id);
        }
        // else: required spawnAtStart bosses still alive — wait for them to die
    }

    private void checkPlayerContainment() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            var config = instance.getConfig();
            if (!config.hasArea() || instance.getState() == DungeonState.RECRUITING) continue;

            // Contain dungeon players inside the area
            for (UUID playerId : new ArrayList<>(instance.getPlayers())) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || !player.isAlive() || player.isSpectator()) continue;
                if (!config.isInArea(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ())) {
                    DungeonManager.getInstance().teleportToSpawn(player, config.spawnPoint);
                    player.sendSystemMessage(Component.literal("[Arcadia] Vous ne pouvez pas quitter la zone du donjon!").withStyle(ChatFormatting.RED));
                }
            }

        }
    }

    private void checkParasites() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            if (instance.getPlayers().isEmpty()) continue;
            var config = instance.getConfig();
            if (!config.hasArea() || instance.getState() == DungeonState.RECRUITING) continue;

            Set<UUID> dungeonPlayers = instance.getPlayers();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (dungeonPlayers.contains(player.getUUID())) continue;
                if (player.isSpectator()) continue;
                if (player.hasPermissions(2)) continue;
                try {
                    if (net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(player, ArcadiaDungeon.BYPASS_ANTIPARASITE)) {
                        continue;
                    }
                } catch (Exception ignored) {}

                String dim = player.level().dimension().location().toString();
                if (!config.isInArea(dim, player.getX(), player.getY(), player.getZ())) continue;

                net.minecraft.server.level.ServerLevel overworld = server.overworld();
                net.minecraft.core.BlockPos spawnPos = overworld.getSharedSpawnPos();
                player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                player.sendSystemMessage(Component.literal("[Arcadia] Un donjon est en cours dans cette zone! Vous avez ete teleporte au spawn.")
                        .withStyle(ChatFormatting.RED));
            }

            ResourceLocation dimLoc = ResourceLocation.tryParse(config.areaPos1.dimension);
            if (dimLoc == null) continue;
            var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc));
            if (level == null) continue;

            int minX = Math.min(config.areaPos1.x, config.areaPos2.x);
            int maxX = Math.max(config.areaPos1.x, config.areaPos2.x);
            int minY = Math.min(config.areaPos1.y, config.areaPos2.y);
            int maxY = Math.max(config.areaPos1.y, config.areaPos2.y);
            int minZ = Math.min(config.areaPos1.z, config.areaPos2.z);
            int maxZ = Math.max(config.areaPos1.z, config.areaPos2.z);
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);

            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (entity instanceof ServerPlayer) continue;
                if (entity.getTags().contains("arcadia_managed")) continue;
                entity.discard();
            }
        }
    }

    private final Map<UUID, Boolean> suspendedFlight = new HashMap<>();

    private void checkAntiFly() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        Set<UUID> dungeonPlayers = new HashSet<>();
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            if (instance.getState() == DungeonState.RECRUITING) continue;

            for (UUID playerId : instance.getPlayers()) {
                dungeonPlayers.add(playerId);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || !player.isAlive() || player.isSpectator()) continue;
                if (player.hasPermissions(2)) continue;
                try {
                    if (net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(player, ArcadiaDungeon.BYPASS_ANTIFLY)) {
                        restoreFlight(player);
                        continue;
                    }
                } catch (Exception ignored) {}

                if (player.getAbilities().mayfly && !suspendedFlight.containsKey(playerId)) {
                    suspendedFlight.put(playerId, true);
                }

                boolean changed = false;
                if (player.getAbilities().flying) {
                    player.getAbilities().flying = false;
                    changed = true;
                }
                if (player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = false;
                    changed = true;
                }
                if (player.isFallFlying()) {
                    player.stopFallFlying();
                }
                if (changed) {
                    player.onUpdateAbilities();
                    player.sendSystemMessage(Component.literal("[Arcadia] Le fly est desactive dans ce donjon.").withStyle(ChatFormatting.RED));
                }
            }
        }

        for (UUID playerId : new ArrayList<>(suspendedFlight.keySet())) {
            if (dungeonPlayers.contains(playerId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                restoreFlight(player);
            } else {
                suspendedFlight.remove(playerId);
            }
        }
    }

    private void restoreFlight(ServerPlayer player) {
        if (!Boolean.TRUE.equals(suspendedFlight.remove(player.getUUID()))) return;
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
    }

    // === TIMERS ===
    private void checkDungeonTimers() {
        clearOrphanedMilestones();
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            String dungeonId = entry.getKey();
            if (instance.getState() == DungeonState.RECRUITING) continue;

            if (instance.isTimedOut()) {
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Temps ecoule!").withStyle(ChatFormatting.RED));
                }
                clearDungeonMilestones(dungeonId, instance);
                DungeonManager.getInstance().scheduleRemoval(dungeonId);
                continue;
            }

            int timeLimit = instance.getConfig().settings.timeLimitSeconds;
            if (timeLimit > 0) {
                long remaining = timeLimit - instance.getElapsedSeconds();
                // Fix #9: range-based checks
                List<Integer> milestones = instance.getConfig().settings.timerWarnings;
                for (int m : milestones) {
                    int key = Objects.hash(dungeonId, "timer", m);
                    if (remaining <= m && remaining > m - 2 && !announcedTimerMilestones.contains(key)) {
                        announcedTimerMilestones.add(key);
                        for (UUID playerId : instance.getPlayers()) {
                            ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                            if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Temps restant: " + DungeonManager.formatTime(m) + "!").withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }
            }
        }
    }

    private void clearRecruitmentMilestones(String dungeonId) {
        int[] milestones = {60, 30, 10, 5};
        for (int m : milestones) {
            announcedRecruitmentMilestones.remove(Objects.hash(dungeonId, m));
        }
    }

    private void clearTimerMilestones(String dungeonId, DungeonInstance instance) {
        if (instance == null) {
            return;
        }
        for (int m : instance.getConfig().settings.timerWarnings) {
            announcedTimerMilestones.remove(Objects.hash(dungeonId, "timer", m));
        }
    }

    private void clearDungeonMilestones(String dungeonId, DungeonInstance instance) {
        clearRecruitmentMilestones(dungeonId);
        clearTimerMilestones(dungeonId, instance);
    }

    private void clearOrphanedMilestones() {
        Set<String> activeDungeonIds = DungeonManager.getInstance().getActiveInstances().keySet();
        announcedRecruitmentMilestones.removeIf(key -> !belongsToActiveDungeon(key, activeDungeonIds, false));
        announcedTimerMilestones.removeIf(key -> !belongsToActiveDungeon(key, activeDungeonIds, true));
    }

    private boolean belongsToActiveDungeon(int storedKey, Set<String> activeDungeonIds, boolean timerKey) {
        for (String dungeonId : activeDungeonIds) {
            if (timerKey) {
                DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
                if (instance == null) {
                    continue;
                }
                for (int milestone : instance.getConfig().settings.timerWarnings) {
                    if (storedKey == Objects.hash(dungeonId, "timer", milestone)) {
                        return true;
                    }
                }
            } else {
                int[] milestones = {60, 30, 10, 5};
                for (int milestone : milestones) {
                    if (storedKey == Objects.hash(dungeonId, milestone)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isArcadiaManaged(Entity entity) {
        return entity != null && entity.getTags().contains("arcadia_managed");
    }
}
