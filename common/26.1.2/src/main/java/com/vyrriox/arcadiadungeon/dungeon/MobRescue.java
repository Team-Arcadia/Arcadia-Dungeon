package com.vyrriox.arcadiadungeon.dungeon;

import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Brings back the mobs a run cannot end without and nobody can reach.
 *
 * <p>Players are held inside a dungeon's area, so a mob outside it can never be killed, and neither
 * can one stuck in a wall or hovering over a chasm. Each of those used to hold the run until its
 * timer ran out, and a boss whose summon was out of reach took no damage for all that time.</p>
 *
 * <p>This stays out of the way of a dungeon that works. A long walk to the next room is normal, so
 * nothing moves just because players are far: a mob is only moved when it is out of bounds, or after
 * minutes in which nobody hit anything. {@code rescueStuckMobs} turns all of it off.</p>
 *
 * @author vyrriox
 */
public final class MobRescue {
    /** A knockback or a hop over the edge is not a problem: it has to last this long. */
    static final long TROUBLE_GRACE_MS = 5_000L;
    private static final int AREA_MARGIN = 4;

    static final long IDLE_SEND_HOME_MS = 120_000L;
    static final long IDLE_TO_PLAYER_MS = 240_000L;
    private static final long RESCUE_COOLDOWN_MS = 60_000L;
    private static final double FAR_FROM_HOME = 8.0D;
    private static final double NEXT_TO_PLAYER = 6.0D;

    static final long SUMMON_GLOW_MS = 15_000L;
    static final long SUMMON_TO_BOSS_MS = 30_000L;
    static final long SUMMON_TO_PLAYER_MS = 60_000L;
    private static final long SUMMON_RESCUE_COOLDOWN_MS = 20_000L;
    private static final double SUMMON_LEASH = 24.0D;

    private MobRescue() {}

    public static void check(DungeonInstance run, MinecraftServer server, long now) {
        if (run.getState() == DungeonState.RECRUITING) return;

        List<BossInstance> bosses = List.copyOf(run.getActiveBosses().values());
        for (BossInstance boss : bosses) {
            boss.checkSummonLock(now);
        }
        if (!run.getConfig().settings.rescueStuckMobs) return;

        WaveInstance wave = run.getCurrentWave();
        if (wave != null && wave.isSpawned()) {
            for (TrackedMob mob : wave.getTrackedMobs()) {
                checkMob(run, server, mob, true, now);
            }
        }
        for (BossInstance boss : bosses) {
            if (boss.getTracked() != null) {
                // A hidden boss nobody has to fight stays hidden: only bounds apply to it.
                checkMob(run, server, boss.getTracked(), boss.getConfig().requiredKill, now);
            }
            for (TrackedMob summon : boss.getTrackedSummons()) {
                checkSummon(run, server, boss, summon, now);
            }
        }
    }

    private static void checkMob(DungeonInstance run, MinecraftServer server, TrackedMob mob, boolean mustDie, long now) {
        if (mob.follow(now) != TrackedMob.Presence.PRESENT) return;
        if (isOutOfBounds(run, mob, now)) return;
        if (!mustDie || now - mob.lastRescue() < RESCUE_COOLDOWN_MS) return;

        long idle = now - run.getLastProgressMillis();
        LivingEntity entity = mob.entity();
        if (idle >= IDLE_TO_PLAYER_MS) {
            ServerPlayer player = nearestPlayer(run, server, entity);
            if (player != null && entity.distanceTo(player) > NEXT_TO_PLAYER) {
                move(mob, player.position(), now, "nobody has hit anything for minutes");
            }
        } else if (idle >= IDLE_SEND_HOME_MS && entity.position().distanceTo(mob.home()) > FAR_FROM_HOME) {
            move(mob, mob.home(), now, "it wandered off and nobody has hit anything for minutes");
        }
    }

    private static void checkSummon(DungeonInstance run, MinecraftServer server, BossInstance boss, TrackedMob summon, long now) {
        if (summon.follow(now) != TrackedMob.Presence.PRESENT) return;
        if (isOutOfBounds(run, summon, now)) return;
        if (!boss.shouldBlockDamageWhileSummonsAlive()) return;

        long held = boss.summonLockHeldFor(now);
        LivingEntity entity = summon.entity();
        if (held >= SUMMON_GLOW_MS && !entity.hasGlowingTag()) entity.setGlowingTag(true);
        if (now - summon.lastRescue() < SUMMON_RESCUE_COOLDOWN_MS) return;

        if (held >= SUMMON_TO_PLAYER_MS && now - run.getLastProgressMillis() >= SUMMON_TO_BOSS_MS) {
            ServerPlayer player = nearestPlayer(run, server, entity);
            if (player != null && entity.distanceTo(player) > NEXT_TO_PLAYER) {
                move(summon, player.position(), now, "it protects its boss and nobody reached it");
            }
        } else if (held >= SUMMON_TO_BOSS_MS && boss.getBossEntity() != null
                && !boss.getBossEntity().isRemoved() && entity.distanceTo(boss.getBossEntity()) > SUMMON_LEASH) {
            move(summon, boss.getBossEntity().position(), now, "it strayed from the boss it protects");
        }
    }

    /** Sends a mob home once it has been outside the area or inside a wall for a while. */
    private static boolean isOutOfBounds(DungeonInstance run, TrackedMob mob, long now) {
        LivingEntity entity = mob.entity();
        if (!isOutsideArea(run.getConfig(), entity) && !entity.isInWall()) {
            mob.setTroubleSince(0L);
            return false;
        }
        if (mob.troubleSince() == 0L) {
            mob.setTroubleSince(now);
        } else if (now - mob.troubleSince() >= TROUBLE_GRACE_MS) {
            mob.setTroubleSince(0L);
            move(mob, mob.home(), now, "it was out of the area or inside a block");
        }
        return true;
    }

    private static boolean isOutsideArea(DungeonConfig config, LivingEntity entity) {
        if (!config.hasArea()) return false;
        // A few blocks of tolerance: spiders climb, and a mob pushed against the edge is still fighting.
        String dimension = entity.level().dimension().identifier().toString();
        return !config.isNearArea(dimension, entity.getX(), entity.getY(), entity.getZ(), AREA_MARGIN);
    }

    private static ServerPlayer nearestPlayer(DungeonInstance run, MinecraftServer server, LivingEntity entity) {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (UUID playerId : run.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive() || player.isSpectator() || player.level() != entity.level()) continue;
            double distance = player.distanceToSqr(entity);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static void move(TrackedMob mob, Vec3 target, long now, String why) {
        LivingEntity entity = mob.entity();
        Vec3 safe = SpawnSafety.findSafeSpawn(entity, target.x, target.y, target.z, 4, 4);
        entity.teleportTo(safe.x, safe.y, safe.z);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        if (entity instanceof Mob pathing) pathing.getNavigation().stop();
        mob.setLastRescue(now);
        ArcadiaDungeon.LOGGER.info("Moved dungeon mob {} to {}, {}, {}: {}",
                entity.getType(), (int) safe.x, (int) safe.y, (int) safe.z, why);
    }
}
