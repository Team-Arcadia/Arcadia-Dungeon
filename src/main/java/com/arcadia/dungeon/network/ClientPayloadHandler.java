package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.client.screen.AdminHubScreen;
import com.arcadia.dungeon.client.screen.AdminDungeonZoneScreen;
import com.arcadia.dungeon.client.screen.ArcadiaNavigator;
import com.arcadia.dungeon.client.screen.DebugRunScreen;
import com.arcadia.dungeon.client.screen.ResultScreen;
import com.arcadia.dungeon.client.state.ActiveRunsClient;
import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.client.state.DungeonListClient;
import com.arcadia.dungeon.client.state.PlayerProgressClient;
import com.arcadia.dungeon.client.state.RunStateClient;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handlers client pour les payloads S2C (Stories S2.4, S2.6, S3.4).
 *
 * <p>Méthodes appelées uniquement sur le logical client — pas de {@code @OnlyIn}
 * nécessaire car {@code playToClient} handlers ne s'exécutent jamais sur
 * un serveur dédié.
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {}

    public static void handleRunState(RunStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            long delta = System.currentTimeMillis() - payload.serverTimestampMs();
            if (delta > 200) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia][SYNC] high_latency delta={}ms runId={}",
                    delta, payload.runId());
            }

            // S3.4 — Détection désync : runId différent du state courant
            RunStatePayload current = RunStateClient.getState().orElse(null);
            if (current != null && !current.runId().equals(payload.runId())) {
                ArcadiaDungeon.LOGGER.warn(
                    "[Arcadia][SYNC] desync_detected cached_runId={} received_runId={} — requesting resync",
                    current.runId(), payload.runId());
                PacketDistributor.sendToServer(new RequestRunResyncPayload());
            }

            RunStateClient.update(payload);
        });
    }

    public static void handleOpenAdminHub(OpenAdminHubPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ArcadiaNavigator.reset();
            Minecraft.getInstance().setScreen(new AdminHubScreen());
        });
    }

    public static void handleOpenDebugScreen(OpenDebugScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new DebugRunScreen()));
    }

    public static void handleDungeonList(DungeonListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DungeonListClient.update(payload.dungeons(), payload.globalClasses()));
    }

    public static void handlePlayerProgress(PlayerProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PlayerProgressClient.update(payload));
    }

    public static void handleMonitorData(MonitorDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ActiveRunsClient.update(payload));
    }

    public static void handleDungeonEditData(DungeonEditDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DungeonEditClient.update(
            payload.dungeonId(), payload.configJson(),
            payload.spawnX(), payload.spawnY(), payload.spawnZ(),
            payload.spawnDim(), payload.spawnSet()));
    }

    public static void handleAreaWandStatus(AreaWandStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            DungeonEditClient.updateAreaWand(payload);
            Minecraft mc = Minecraft.getInstance();
            if (payload.selecting()) {
                if (mc.screen instanceof AdminDungeonZoneScreen) {
                    mc.setScreen(null);
                }
                return;
            }
            if (payload.areaSet() && mc.screen == null) {
                mc.setScreen(new AdminDungeonZoneScreen(
                    payload.dungeonId(),
                    DungeonEditClient.getString("nameKey", payload.dungeonId())));
            }
        });
    }

    public static void handleOpenResultScreen(OpenResultScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ResultScreen(
            payload.result(), payload.elapsedSeconds(), payload.currencyEarned(),
            payload.newPb(), payload.bestTimeSeconds(),
            payload.respawnSeconds(), payload.dungeonId(), payload.lootLines())));
    }
}
