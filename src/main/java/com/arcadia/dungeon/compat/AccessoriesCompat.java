package com.arcadia.dungeon.compat;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.util.ModCompat;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reflection-based compat for the Accessories mod by wisp-forest (mod id: "accessories").
 *
 * <p>Navigation path (all via reflection):
 * <ol>
 *   <li>{@code AccessoriesCapability.get(player)} → {@code AccessoriesCapability}</li>
 *   <li>{@code capability.getContainers()} → {@code Map<String, AccessoriesContainer>}</li>
 *   <li>{@code container.getAccessories()} → {@code ExpandedSimpleContainer} (extends {@code SimpleContainer})</li>
 *   <li>{@code simpleContainer.getItem(idx)} / {@code setItem(idx, stack)}</li>
 * </ol>
 *
 * <p>Composite slot key format used by {@link ApotheosisCompat}: {@code "A:{slotType}:{slotIndex}"}
 */
final class AccessoriesCompat {

    private AccessoriesCompat() {}

    private static final String MOD_ID         = "accessories";
    private static final String CAPABILITY_CLASS = "io.wispforest.accessories.api.AccessoriesCapability";

    // -------------------------------------------------------------------------
    // Package-private API used by ApotheosisCompat
    // -------------------------------------------------------------------------

    static void suppressCharms(ServerPlayer player,
                                DataComponentType<Boolean> type,
                                List<String> outKeys) {
        if (!ModCompat.isLoaded(MOD_ID)) return;
        forEachSlot(player, (slotType, idx, container) -> {
            try {
                ItemStack stack = getItem(container, idx);
                if (!stack.isEmpty() && Boolean.TRUE.equals(stack.get(type))) {
                    stack.set(type, false);
                    outKeys.add("A:" + slotType + ":" + idx);
                    ArcadiaDungeon.LOGGER.debug(
                            "[Arcadia] Se ha eliminado el amuleto de accesorio tipo={} ranura={} jugador={}",
                            slotType, idx, player.getGameProfile().getName());
                }
            } catch (Exception e) {
                ArcadiaDungeon.LOGGER.debug("[Arcadia] Accesorios: Compatibilidad, supresión, error de amuletos: {}", e.getMessage());
            }
        });
    }

    static void sweepNewCharms(ServerPlayer player,
                                DataComponentType<Boolean> type,
                                List<String> alreadySuppressed,
                                List<String> outKeys) {
        if (!ModCompat.isLoaded(MOD_ID)) return;
        forEachSlot(player, (slotType, idx, container) -> {
            String key = "A:" + slotType + ":" + idx;
            if (alreadySuppressed.contains(key)) return;
            try {
                ItemStack stack = getItem(container, idx);
                if (!stack.isEmpty() && Boolean.TRUE.equals(stack.get(type))) {
                    stack.set(type, false);
                    outKeys.add(key);
                    ArcadiaDungeon.LOGGER.debug(
                            "[Arcadia] Accesorios: nuevo charm eliminado tipo={} ranura={} jugador={}",
                            slotType, idx, player.getGameProfile().getName());
                }
            } catch (Exception e) {
                ArcadiaDungeon.LOGGER.debug("[Arcadia]  AccesoriosCompat.sweepNewCharms error: {}", e.getMessage());
            }
        });
    }

    static void restoreSlot(ServerPlayer player,
                             String slotType, int idx,
                             DataComponentType<Boolean> type) {
        if (!ModCompat.isLoaded(MOD_ID)) return;
        Object container = resolveContainer(player, slotType);
        if (container == null) return;
        try {
            int size = containerSize(container);
            if (idx >= size) return;
            ItemStack stack = getItem(container, idx);
            if (!stack.isEmpty() && stack.has(type)) {
                stack.set(type, true);
                ArcadiaDungeon.LOGGER.debug(
                        "[Arcadia] Accesorios: charm restaurado tipo={} ranura={} jugador={}",
                        slotType, idx, player.getGameProfile().getName());
            }
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.debug("[Arcadia] Error en la ranura de restauración de accesorios: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    private static void forEachSlot(ServerPlayer player, SlotConsumer consumer) {
        try {
            Object capability = getCapability(player);
            if (capability == null) return;

            @SuppressWarnings("unchecked")
            Map<String, Object> containers =
                    (Map<String, Object>) capability.getClass().getMethod("getContainers").invoke(capability);

            for (Map.Entry<String, Object> entry : containers.entrySet()) {
                String slotType = entry.getKey();
                Object container = extractAccessoriesContainer(entry.getValue());
                if (container == null) continue;
                int size = containerSize(container);
                for (int i = 0; i < size; i++) {
                    consumer.accept(slotType, i, container);
                }
            }
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.debug("[Arcadia] Error en «AccessoriesCompat.forEachSlot»: {}", e.getMessage());
        }
    }

    private static Object resolveContainer(ServerPlayer player, String slotType) {
        try {
            Object capability = getCapability(player);
            if (capability == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> containers =
                    (Map<String, Object>) capability.getClass().getMethod("getContainers").invoke(capability);

            Object accessoriesContainer = containers.get(slotType);
            if (accessoriesContainer == null) return null;
            return extractAccessoriesContainer(accessoriesContainer);
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.debug("[Arcadia] Error de resolución de compatibilidad de accesorios: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calls {@code AccessoriesCapability.get(player)}.
     * Falls back to {@code getOptionally(player)} if {@code get()} is not found.
     */
    private static Object getCapability(ServerPlayer player) throws Exception {
        Class<?> capClass = Class.forName(CAPABILITY_CLASS);

        // Try getOptionally() first
        try {
            Method m = capClass.getMethod("getOptionally", net.minecraft.world.entity.LivingEntity.class);
            @SuppressWarnings("unchecked")
            Optional<Object> opt = (Optional<Object>) m.invoke(null, player);
            return opt != null && opt.isPresent() ? opt.get() : null;
        } catch (NoSuchMethodException ignored) {}

        // Fallback: get() returning nullable
        Method m = capClass.getMethod("get", net.minecraft.world.entity.LivingEntity.class);
        return m.invoke(null, player);
    }

    /**
     * Extracts the functional accessories {@code ExpandedSimpleContainer} from an
     * {@code AccessoriesContainer} by calling {@code getAccessories()}.
     */
    private static Object extractAccessoriesContainer(Object accessoriesContainer) {
        try {
            return accessoriesContainer.getClass().getMethod("getAccessories").invoke(accessoriesContainer);
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.debug(
                    "[Arcadia] AccessoriesCompat: getAccessories() introuvable sur {}",
                    accessoriesContainer.getClass().getName());
            return null;
        }
    }

    /** Returns the number of slots in a {@code Container}-like object. */
    private static int containerSize(Object container) {
        for (String m : new String[]{"getContainerSize", "getSlotCount", "getMaxStackSize"}) {
            try {
                return (int) container.getClass().getMethod(m).invoke(container);
            } catch (Exception ignored) {}
        }
        return 0;
    }

    /** Reads an ItemStack by index from a {@code Container}-like object. */
    private static ItemStack getItem(Object container, int idx) throws Exception {
        return (ItemStack) container.getClass().getMethod("getItem", int.class).invoke(container, idx);
    }

    @FunctionalInterface
    private interface SlotConsumer {
        void accept(String slotType, int idx, Object container);
    }
}
