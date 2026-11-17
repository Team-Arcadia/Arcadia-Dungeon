package com.vyrriox.arcadiadungeon.dungeon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the items of {@code settings.lockedItems} on cooldown while their holder is in a run.
 *
 * <p>The chorus totem of Artifacts saves a dying player by throwing them sixteen to twenty blocks
 * away at random. It does so from a mixin on the totem check, before any death event exists, so a
 * run never hears of it: the player simply lands somewhere in the area, often inside a wall or
 * under the floor, off the course and out of reach. Artifacts skips a totem whose item is cooling
 * down, and so does the game for any item a player tries to use, so a cooldown blocks the totem
 * and the chorus fruit alike without depending on either of them.</p>
 *
 * <p>The cooldown is renewed every second and given back as soon as the player is out of the run,
 * so a player who leaves gets the item back at once.</p>
 *
 * @author vyrriox
 */
public final class RunItemLock {

    /** How often the lock is renewed, in ticks. */
    public static final int RENEW_TICKS = 20;
    /** Far longer than the renewal, so a slow tick never opens a gap, and short enough to fade on its own. */
    static final int LOCK_TICKS = 200;

    private static final Map<UUID, Set<Item>> LOCKED = new HashMap<>();

    private RunItemLock() {}

    /** Locks the items of every player in a run and gives them back to everyone who left one. */
    public static void tick(MinecraftServer server) {
        Set<UUID> inRun = new HashSet<>();
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            List<Item> items = resolve(instance.getConfig().settings.lockedItems);
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) continue;
                inRun.add(playerId);
                lock(player, items);
            }
        }

        for (Iterator<Map.Entry<UUID, Set<Item>>> it = LOCKED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Set<Item>> entry = it.next();
            if (inRun.contains(entry.getKey())) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) release(player, entry.getValue());
            it.remove();
        }
    }

    /** Puts these items on cooldown for this player, and remembers them to give them back. */
    public static void lock(ServerPlayer player, List<Item> items) {
        if (items.isEmpty()) return;
        for (Item item : items) {
            player.getCooldowns().addCooldown(item, LOCK_TICKS);
        }
        LOCKED.computeIfAbsent(player.getUUID(), id -> new HashSet<>()).addAll(items);
    }

    /** Ends the cooldown of everything this player had locked. */
    public static void release(ServerPlayer player) {
        Set<Item> items = LOCKED.remove(player.getUUID());
        if (items != null) release(player, items);
    }

    private static void release(ServerPlayer player, Set<Item> items) {
        for (Item item : items) {
            player.getCooldowns().removeCooldown(item);
        }
    }

    /** The items behind these ids. An id of a mod that is not installed is skipped. */
    public static List<Item> resolve(List<String> ids) {
        List<Item> items = new ArrayList<>();
        if (ids == null) return items;
        for (String id : ids) {
            ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id.trim());
            if (key == null) continue;
            BuiltInRegistries.ITEM.getOptional(key).ifPresent(items::add);
        }
        return items;
    }
}
