package com.arcadia.dungeon.compat;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.util.ModCompat;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reflection-based compat for the Curios API (mod id: "curios").
 *
 * <p>Provides slot scanning and targeted restore for Apotheosis charm suppression.
 * No compile-time dependency on Curios — all calls go through reflection.
 * If Curios is not loaded, or if the reflection fails (API change), all methods are no-ops.
 *
 * <p>Composite slot key format used by {@link ApotheosisCompat}: {@code "C:{typeKey}:{slotIndex}"}
 * where {@code typeKey} is the Curios slot-type name (e.g. {@code "ring"}, {@code "necklace"}).
 */
final class CuriosCompat {

    private CuriosCompat() {}

    private static final String MOD_ID        = "curios";
    private static final String API_CLASS     = "top.theillusivec4.curios.api.CuriosApi";

    // -------------------------------------------------------------------------
    // Package-private API used by ApotheosisCompat
    // -------------------------------------------------------------------------

    /**
     * Scans all Curios inventory slots for charms that are currently enabled,
     * disables them, and appends {@code "C:{typeKey}:{idx}"} composite keys to {@code outKeys}.
     */
    static void suppressCharms(ServerPlayer player,
                                DataComponentType<Boolean> type,
                                List<String> outKeys) {
        if (!ModCompat.isLoaded(MOD_ID)) return;
        forEachSlot(player, (typeKey, idx, iih) -> {
            ItemStack stack = iih.getStackInSlot(idx);
            if (Boolean.TRUE.equals(stack.get(type))) {
                stack.set(type, false);
                outKeys.add("C:" + typeKey + ":" + idx);
                ArcadiaDungeon.LOGGER.debug(
                        "[Arcadia] Curios charm supprimé type={} slot={} joueur={}",
                        typeKey, idx, player.getGameProfile().getName());
            }
        });
    }

    /**
     * Sweeps Curios slots for newly equipped charms (not already in {@code alreadySuppressed}),
     * disables them, and appends their composite keys to {@code outKeys}.
     */
    static void sweepNewCharms(ServerPlayer player,
                                DataComponentType<Boolean> type,
                                List<String> alreadySuppressed,
                                List<String> outKeys) {
        if (!ModCompat.isLoaded(MOD_ID)) return;
        forEachSlot(player, (typeKey, idx, iih) -> {
            String key = "C:" + typeKey + ":" + idx;
            if (alreadySuppressed.contains(key)) return;
            ItemStack stack = iih.getStackInSlot(idx);
            if (Boolean.TRUE.equals(stack.get(type))) {
                stack.set(type, false);
                outKeys.add(key);
                ArcadiaDungeon.LOGGER.debug(
                        "[Arcadia] Curios nouveau charm supprimé type={} slot={} joueur={}",
                        typeKey, idx, player.getGameProfile().getName());
            }
        });
    }

    /**
     * Restores a charm that was suppressed at the given {@code typeKey}/{@code idx}.
     * Only re-enables if the slot still holds a stack that has the component.
     */
    static void restoreSlot(ServerPlayer player,
                             String typeKey, int idx,
                             DataComponentType<Boolean> type) {
        if (!ModCompat.isLoaded(MOD_ID)) return;
        IItemHandlerModifiable iih = getSlotHandler(player, typeKey);
        if (iih == null || idx >= iih.getSlots()) return;
        ItemStack stack = iih.getStackInSlot(idx);
        if (!stack.isEmpty() && stack.has(type)) {
            stack.set(type, true);
            ArcadiaDungeon.LOGGER.debug(
                    "[Arcadia] Curios charm restauré type={} slot={} joueur={}",
                    typeKey, idx, player.getGameProfile().getName());
        }
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    /** Iterates every (typeKey, slotIndex, IItemHandlerModifiable) triple in the player's Curios. */
    private static void forEachSlot(ServerPlayer player, SlotConsumer consumer) {
        try {
            Object handler = getCuriosHandler(player);
            if (handler == null) return;

            @SuppressWarnings("unchecked")
            Map<String, Object> curios =
                    (Map<String, Object>) handler.getClass().getMethod("getCurios").invoke(handler);

            for (Map.Entry<String, Object> entry : curios.entrySet()) {
                String typeKey = entry.getKey();
                IItemHandlerModifiable iih = extractItemHandler(entry.getValue());
                if (iih == null) continue;
                for (int i = 0; i < iih.getSlots(); i++) {
                    consumer.accept(typeKey, i, iih);
                }
            }
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.debug("[Arcadia] CuriosCompat.forEachSlot erreur: {}", e.getMessage());
        }
    }

    /** Navigates to the IItemHandlerModifiable for a specific Curios slot type. */
    private static IItemHandlerModifiable getSlotHandler(ServerPlayer player, String typeKey) {
        try {
            Object handler = getCuriosHandler(player);
            if (handler == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> curios =
                    (Map<String, Object>) handler.getClass().getMethod("getCurios").invoke(handler);

            Object stacksHandler = curios.get(typeKey);
            if (stacksHandler == null) return null;
            return extractItemHandler(stacksHandler);
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.debug("[Arcadia] CuriosCompat.getSlotHandler erreur: {}", e.getMessage());
            return null;
        }
    }

    /** Calls {@code CuriosApi.getCuriosInventory(player)} and unwraps the Optional. */
    private static Object getCuriosHandler(ServerPlayer player) throws Exception {
        Class<?> apiClass = Class.forName(API_CLASS);
        Method m = apiClass.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
        @SuppressWarnings("unchecked")
        Optional<Object> opt = (Optional<Object>) m.invoke(null, player);
        return opt.isEmpty() ? null : opt.get();
    }

    /**
     * Extracts an {@link IItemHandlerModifiable} from an {@code ICurioStacksHandler}.
     * Tries {@code getStacks()} first (Curios 1.21.x), then {@code getSlots()} as fallback.
     */
    private static IItemHandlerModifiable extractItemHandler(Object stacksHandler) {
        for (String methodName : new String[]{"getStacks", "getSlots"}) {
            try {
                Object result = stacksHandler.getClass().getMethod(methodName).invoke(stacksHandler);
                if (result instanceof IItemHandlerModifiable iih) return iih;
            } catch (Exception ignored) {}
        }
        ArcadiaDungeon.LOGGER.debug(
                "[Arcadia] CuriosCompat: impossible d'extraire IItemHandlerModifiable depuis {}",
                stacksHandler.getClass().getName());
        return null;
    }

    @FunctionalInterface
    private interface SlotConsumer {
        void accept(String typeKey, int idx, IItemHandlerModifiable handler);
    }
}
