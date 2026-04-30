package com.arcadia.dungeon.compat;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.util.ModCompat;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compat layer for Apotheosis Potion Charms.
 *
 * <h3>Strategy</h3>
 * <p>Instead of fighting the charm's inventory tick (which unconditionally calls
 * {@code hurtAndBreak} after {@code addEffect} regardless of whether the effect
 * was actually applied), we disable the charm entirely by toggling its
 * {@code apotheosis:charm_enabled} DataComponent to {@code false} when a player
 * enters an active dungeon instance, and restore it when they leave.
 *
 * <h3>Scope</h3>
 * <p>The scan covers three inventory spaces:
 * <ul>
 *   <li><b>Vanilla</b> — {@code player.getInventory()} (hotbar, main, armour, offhand)</li>
 *   <li><b>Curios</b> — all slot types via {@link CuriosCompat} (reflection, mod id: {@code "curios"})</li>
 *   <li><b>Accessories</b> — all slot types via {@link AccessoriesCompat} (reflection, mod id: {@code "accessories"})</li>
 * </ul>
 * <p>If Apotheosis is not loaded, all methods are no-ops. If Curios or Accessories are
 * not loaded, those scans are silently skipped.
 *
 * <h3>Slot key format</h3>
 * <p>Suppressed slots are tracked with composite string keys:
 * <ul>
 *   <li>{@code "V:{index}"} — vanilla inventory slot</li>
 *   <li>{@code "C:{typeKey}:{index}"} — Curios slot (e.g. {@code "C:ring:0"})</li>
 *   <li>{@code "A:{slotType}:{index}"} — Accessories slot (e.g. {@code "A:ring:0"})</li>
 * </ul>
 */
public final class ApotheosisCompat {

    private ApotheosisCompat() {}

    private static final ResourceLocation CHARM_ENABLED_RL =
            ResourceLocation.fromNamespaceAndPath("apotheosis", "charm_enabled");

    /**
     * Per-player list of composite slot keys whose charm was enabled before suppression.
     * Thread-safe: written from server tick thread, read from event handlers.
     */
    private static final Map<UUID, List<String>> suspendedCharmSlots = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Disables all enabled Apotheosis charms across the player's entire inventory
     * (vanilla + Curios + Accessories). Records which slots were affected so they
     * can be restored on exit.
     *
     * <p>Call when a player transitions from RECRUITING → ACTIVE (post-TP).
     */
    public static void suppressCharms(ServerPlayer player) {
        DataComponentType<Boolean> type = getCharmEnabledType();
        if (type == null) return;

        List<String> suppressed = new ArrayList<>();

        // --- Vanilla inventory ---
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (Boolean.TRUE.equals(stack.get(type))) {
                stack.set(type, false);
                suppressed.add("V:" + i);
                ArcadiaDungeon.LOGGER.debug(
                        "[Arcadia] Se ha eliminado el amuleto de vainilla: ranura={} jugador={}", i, player.getGameProfile().getName());
            }
        }

        // --- Curios slots ---
        CuriosCompat.suppressCharms(player, type, suppressed);

        // --- Accessories slots ---
        AccessoriesCompat.suppressCharms(player, type, suppressed);

        if (!suppressed.isEmpty()) {
            suspendedCharmSlots.merge(player.getUUID(), suppressed, (existing, added) -> {
                existing.addAll(added);
                return existing;
            });
        }
    }

    /**
     * Re-enables any charms that were suppressed for this player, across all inventory spaces.
     * Call when a player exits a dungeon (logout, death-ejection, dungeon end).
     */
    public static void restoreCharms(ServerPlayer player) {
        DataComponentType<Boolean> type = getCharmEnabledType();
        if (type == null) return;

        List<String> slots = suspendedCharmSlots.remove(player.getUUID());
        if (slots == null || slots.isEmpty()) return;

        for (String key : slots) {
            try {
                if (key.startsWith("V:")) {
                    int idx = Integer.parseInt(key.substring(2));
                    ItemStack stack = player.getInventory().getItem(idx);
                    if (!stack.isEmpty() && stack.has(type)) {
                        stack.set(type, true);
                        ArcadiaDungeon.LOGGER.debug(
                                "[Arcadia] Amuleto de vainilla restaurado slot={} jugador={}", idx, player.getGameProfile().getName());
                    }
                } else if (key.startsWith("C:")) {
                    String[] parts = key.split(":", 3);
                    if (parts.length == 3) {
                        CuriosCompat.restoreSlot(player, parts[1], Integer.parseInt(parts[2]), type);
                    }
                } else if (key.startsWith("A:")) {
                    String[] parts = key.split(":", 3);
                    if (parts.length == 3) {
                        AccessoriesCompat.restoreSlot(player, parts[1], Integer.parseInt(parts[2]), type);
                    }
                }
            } catch (Exception e) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia] Error al restaurar el charm key={}: {}", key, e.getMessage());
            }
        }
    }

    /**
     * Sweeps the player's entire inventory (vanilla + Curios + Accessories) for newly
     * equipped charms that weren't present at dungeon entry. Suppresses them if found.
     *
     * <p>Call periodically (every ~5s) for all active dungeon players.
     */
    public static void sweepNewCharms(ServerPlayer player) {
        DataComponentType<Boolean> type = getCharmEnabledType();
        if (type == null) return;

        List<String> alreadySuppressed =
                suspendedCharmSlots.getOrDefault(player.getUUID(), List.of());
        List<String> newSlots = new ArrayList<>();

        // --- Vanilla inventory ---
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            String key = "V:" + i;
            if (alreadySuppressed.contains(key)) continue;
            ItemStack stack = inventory.getItem(i);
            if (Boolean.TRUE.equals(stack.get(type))) {
                stack.set(type, false);
                newSlots.add(key);
                ArcadiaDungeon.LOGGER.debug(
                        "[Arcadia] Se ha eliminado el nuevo charm de Vanilla: slot={} jugador={}", i, player.getGameProfile().getName());
            }
        }

        // --- Curios slots ---
        CuriosCompat.sweepNewCharms(player, type, alreadySuppressed, newSlots);

        // --- Accessories slots ---
        AccessoriesCompat.sweepNewCharms(player, type, alreadySuppressed, newSlots);

        if (!newSlots.isEmpty()) {
            suspendedCharmSlots.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).addAll(newSlots);
        }
    }

    /**
     * Removes tracking data for a player without restoring their charms.
     * Use only on crash recovery / orphaned state cleanup at server boot.
     */
    public static void clearTracking(UUID uuid) {
        suspendedCharmSlots.remove(uuid);
    }

    /**
     * Returns {@code true} if this ItemStack is an Apotheosis charm
     * (i.e. it carries the {@code apotheosis:charm_enabled} DataComponent).
     */
    public static boolean isCharmItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        DataComponentType<Boolean> type = getCharmEnabledType();
        return type != null && stack.has(type);
    }

    /** Returns {@code true} if this player has suppressed charms tracked. */
    public static boolean hasSuppressedCharms(UUID uuid) {
        List<String> slots = suspendedCharmSlots.get(uuid);
        return slots != null && !slots.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static DataComponentType<Boolean> getCharmEnabledType() {
        if (!ModCompat.isLoaded("apotheosis")) return null;
        return (DataComponentType<Boolean>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(CHARM_ENABLED_RL);
    }
}
