package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.InvocationTargetException;

/**
 * Common-side S2C dispatcher.
 *
 * <p>Payload registration runs on dedicated servers too, so this class must not
 * directly reference Minecraft client classes. The real handler is loaded only
 * on the physical client.
 */
public final class ClientPayloadDispatcher {

    private static final String CLIENT_HANDLER = "com.arcadia.dungeon.network.ClientPayloadHandler";

    private ClientPayloadDispatcher() {}

    public static void handleToast(ArcadiaToastPayload payload, IPayloadContext context) {
        dispatch("handleToast", ArcadiaToastPayload.class, payload, context);
    }

    public static void handleRunState(RunStatePayload payload, IPayloadContext context) {
        dispatch("handleRunState", RunStatePayload.class, payload, context);
    }

    public static void handleOpenAdminHub(OpenAdminHubPayload payload, IPayloadContext context) {
        dispatch("handleOpenAdminHub", OpenAdminHubPayload.class, payload, context);
    }

    public static void handleOpenDebugScreen(OpenDebugScreenPayload payload, IPayloadContext context) {
        dispatch("handleOpenDebugScreen", OpenDebugScreenPayload.class, payload, context);
    }

    public static void handleDungeonList(DungeonListPayload payload, IPayloadContext context) {
        dispatch("handleDungeonList", DungeonListPayload.class, payload, context);
    }

    public static void handlePlayerProgress(PlayerProgressPayload payload, IPayloadContext context) {
        dispatch("handlePlayerProgress", PlayerProgressPayload.class, payload, context);
    }

    public static void handleMonitorData(MonitorDataPayload payload, IPayloadContext context) {
        dispatch("handleMonitorData", MonitorDataPayload.class, payload, context);
    }

    public static void handleDungeonEditData(DungeonEditDataPayload payload, IPayloadContext context) {
        dispatch("handleDungeonEditData", DungeonEditDataPayload.class, payload, context);
    }

    public static void handleAreaWandStatus(AreaWandStatusPayload payload, IPayloadContext context) {
        dispatch("handleAreaWandStatus", AreaWandStatusPayload.class, payload, context);
    }

    public static void handleStructurePlacementStatus(StructurePlacementStatusPayload payload, IPayloadContext context) {
        dispatch("handleStructurePlacementStatus", StructurePlacementStatusPayload.class, payload, context);
    }

    public static void handleOpenResultScreen(OpenResultScreenPayload payload, IPayloadContext context) {
        dispatch("handleOpenResultScreen", OpenResultScreenPayload.class, payload, context);
    }

    private static <T> void dispatch(String methodName, Class<T> payloadType, T payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> handler = Class.forName(CLIENT_HANDLER);
            handler.getMethod(methodName, payloadType, IPayloadContext.class).invoke(null, payload, context);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][NET] Unable to dispatch client payload {}", methodName, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            ArcadiaDungeon.LOGGER.error("[Arcadia][NET] Client payload handler failed {}", methodName, cause);
        }
    }
}
