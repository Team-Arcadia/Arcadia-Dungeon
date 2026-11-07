package com.vyrriox.arcadiadungeon.dungeon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A mob a run is waiting on, followed by its id rather than by a Java reference.
 *
 * <p><b>Why this exists.</b> {@code isAlive()} answers false for a mob that is only unloaded, because
 * unloading removes the object, and when the mob comes back from disk it is a new object. A run that
 * held the reference read an unloaded wave as a cleared wave, and an unloaded boss as a boss nobody
 * could ever reach again: its hits no longer counted, killing it ended nothing, and if it went to
 * disk during its spawn protection it came back invulnerable for good.</p>
 *
 * @author vyrriox
 */
public final class TrackedMob {

    public enum Presence {
        /** In the world, loaded, alive. */
        PRESENT,
        /** Not loaded right now. It exists on disk and is on its way back. */
        AWAY,
        /** Dead. */
        KILLED,
        /** Removed for good by something else, or lost. It will not come back by itself. */
        REMOVED
    }

    /** How long a mob may stay unloaded, while its chunk is being held, before it counts as lost. */
    private static final long AWAY_LIMIT_MS = 15_000L;

    private final UUID id;
    private LivingEntity entity;
    private ServerLevel level;
    private ChunkPos lastChunk;
    private final Vec3 home;
    private long awaySince = 0L;

    // Bookkeeping of MobRescue.
    private long troubleSince = 0L;
    private long lastRescue = 0L;

    public TrackedMob(LivingEntity entity, ServerLevel level, Vec3 home) {
        this.id = entity.getUUID();
        this.entity = entity;
        this.level = level;
        this.lastChunk = entity.chunkPosition();
        this.home = home;
    }

    /** Looks the mob up again if its object is gone, and says where it stands. */
    public Presence follow(long now) {
        if (entity.isRemoved()) {
            Entity.RemovalReason reason = entity.getRemovalReason();
            if (reason == Entity.RemovalReason.KILLED) return Presence.KILLED;
            // Some modded bosses take themselves out at zero health instead of dying.
            if (reason == Entity.RemovalReason.DISCARDED) {
                return entity.isDeadOrDying() ? Presence.KILLED : Presence.REMOVED;
            }

            LivingEntity found = findAgain();
            if (found == null) {
                ManagedEntities.hold(level, lastChunk);
                if (awaySince == 0L) awaySince = now;
                return now - awaySince > AWAY_LIMIT_MS ? Presence.REMOVED : Presence.AWAY;
            }
            entity = found;
        }
        awaySince = 0L;
        lastChunk = entity.chunkPosition();
        if (entity.level() instanceof ServerLevel current) level = current;
        return entity.isDeadOrDying() ? Presence.KILLED : Presence.PRESENT;
    }

    /** Renews the hold on the mob's chunk. Called every second, from ticks and not the wall clock. */
    public void holdChunk() {
        if (!entity.isRemoved()) lastChunk = entity.chunkPosition();
        ManagedEntities.hold(level, lastChunk);
    }

    private LivingEntity findAgain() {
        Entity found = level.getEntity(id);
        if (found == null) {
            for (ServerLevel other : level.getServer().getAllLevels()) {
                if (other == level) continue;
                found = other.getEntity(id);
                if (found != null) break;
            }
        }
        return found instanceof LivingEntity living && !living.isRemoved() ? living : null;
    }

    /** Removes the mob from the world, whichever object it is by now. */
    public void discard() {
        if (entity.isRemoved()) {
            LivingEntity found = findAgain();
            if (found == null) return;
            entity = found;
        }
        if (entity.isAlive()) entity.discard();
    }

    public UUID id() {
        return id;
    }

    /** The mob's object as of the last {@link #follow}. It may be stale when the mob is not present. */
    public LivingEntity entity() {
        return entity;
    }

    public Vec3 home() {
        return home;
    }

    long troubleSince() {
        return troubleSince;
    }

    void setTroubleSince(long now) {
        troubleSince = now;
    }

    long lastRescue() {
        return lastRescue;
    }

    void setLastRescue(long now) {
        lastRescue = now;
    }
}
