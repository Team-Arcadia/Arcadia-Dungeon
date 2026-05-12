package com.arcadia.dungeon;

import com.arcadia.dungeon.command.ArcadiaDebugCommand;
import com.arcadia.dungeon.command.ArcadiaReloadCommand;
import com.arcadia.dungeon.command.ArcadiaSetupCommand;
import com.arcadia.dungeon.network.AbandonRunPayload;
import com.arcadia.dungeon.network.ClientPayloadHandler;
import com.arcadia.dungeon.network.DungeonListPayload;
import com.arcadia.dungeon.network.JoinRunPayload;
import com.arcadia.dungeon.network.OpenDebugScreenPayload;
import com.arcadia.dungeon.network.OpenResultScreenPayload;
import com.arcadia.dungeon.network.CreateDungeonPayload;
import com.arcadia.dungeon.network.ReloadRequestPayload;
import com.arcadia.dungeon.network.RequestDungeonListPayload;
import com.arcadia.dungeon.network.RequestRunResyncPayload;
import com.arcadia.dungeon.network.RunStatePayload;
import com.arcadia.dungeon.network.ServerPayloadHandler;
import com.arcadia.dungeon.network.StartRunPayload;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import com.arcadia.dungeon.persistence.PlacementRegistry;
import com.arcadia.dungeon.services.StructurePlacer;
import com.arcadia.dungeon.client.hud.RunOverlayHud;
import com.arcadia.dungeon.services.ArchetypeService;
import com.arcadia.dungeon.services.BossPhaseService;
import com.arcadia.dungeon.services.PlayerDeathService;
import com.arcadia.dungeon.services.PlayerProgressService;
import com.arcadia.dungeon.services.RewardDistributionService;
import com.arcadia.dungeon.services.RoomProgressionService;
import com.arcadia.dungeon.services.RunCleanupService;
import com.arcadia.dungeon.services.RunLifecycleService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point du mod Arcadia Dungeon v1.0.
 *
 * <p>v1.0 = première version sérieuse. Le PoC v0.0.1 est archivé dans {@code _legacy/},
 * pas un précédent officiel.
 *
 * <p>Voir {@code _bmad-output/planning-artifacts/} pour PRD, architecture, design system,
 * sprint backlog.
 */
@Mod(ArcadiaDungeon.MODID)
public class ArcadiaDungeon {

    public static final String MODID = "arcadia_dungeon";
    public static final Logger LOGGER = LoggerFactory.getLogger("ArcadiaDungeon");

    /**
     * Services initialisés lazy au {@code ServerStartingEvent} pour éviter
     * d'instancier des classes MC (FMLPaths, ServerLifecycleHooks) en contexte JUnit.
     * Ordre d'init : dungeonRegistry → runLifecycle → playerProgress → rewards →
     *               bossPhase → roomProgression.
     */
    private static volatile DungeonRegistry         dungeonRegistry;
    private static volatile RunLifecycleService     runLifecycleService;
    private static volatile PlayerProgressService   playerProgressService;
    private static volatile RewardDistributionService rewardDistributionService;
    private static volatile BossPhaseService        bossPhaseService;
    private static volatile RoomProgressionService  roomProgressionService;
    private static volatile PlayerDeathService      playerDeathService;
    private static volatile ArchetypeService        archetypeService;
    private static volatile RunCleanupService       runCleanupService;
    private static volatile PlacementRegistry       placementRegistry;
    private static volatile StructurePlacer         structurePlacer;

    public static DungeonRegistry dungeonRegistry() {
        DungeonRegistry r = dungeonRegistry;
        if (r == null) throw new IllegalStateException("DungeonRegistry not initialized — call after ServerStartingEvent");
        return r;
    }

    public static RunLifecycleService runLifecycleService() {
        RunLifecycleService s = runLifecycleService;
        if (s == null) throw new IllegalStateException("RunLifecycleService not initialized — call after ServerStartingEvent");
        return s;
    }

    public static PlayerProgressService playerProgressService() {
        PlayerProgressService s = playerProgressService;
        if (s == null) throw new IllegalStateException("PlayerProgressService not initialized — call after ServerStartingEvent");
        return s;
    }

    public static RewardDistributionService rewardDistributionService() {
        RewardDistributionService s = rewardDistributionService;
        if (s == null) throw new IllegalStateException("RewardDistributionService not initialized — call after ServerStartingEvent");
        return s;
    }

    public static BossPhaseService bossPhaseService() {
        BossPhaseService s = bossPhaseService;
        if (s == null) throw new IllegalStateException("BossPhaseService not initialized — call after ServerStartingEvent");
        return s;
    }

    public static RoomProgressionService roomProgressionService() {
        RoomProgressionService s = roomProgressionService;
        if (s == null) throw new IllegalStateException("RoomProgressionService not initialized — call after ServerStartingEvent");
        return s;
    }

    public static PlayerDeathService playerDeathService() {
        PlayerDeathService s = playerDeathService;
        if (s == null) throw new IllegalStateException("PlayerDeathService not initialized — call after ServerStartingEvent");
        return s;
    }

    public static ArchetypeService archetypeService() {
        ArchetypeService s = archetypeService;
        if (s == null) throw new IllegalStateException("ArchetypeService not initialized — call after ServerStartingEvent");
        return s;
    }

    public static PlacementRegistry placementRegistry() {
        PlacementRegistry r = placementRegistry;
        if (r == null) throw new IllegalStateException("PlacementRegistry not initialized — call after ServerStartingEvent");
        return r;
    }

    public static StructurePlacer structurePlacer() {
        StructurePlacer p = structurePlacer;
        if (p == null) throw new IllegalStateException("StructurePlacer not initialized — call after ServerStartingEvent");
        return p;
    }

    public ArcadiaDungeon(IEventBus modEventBus) {
        LOGGER.info("[Arcadia][BOOT] Mod loading — v1.0 fresh start");
        modEventBus.addListener(this::registerPayloads);
        // HUD overlay (S6.5) — enregistré sur le mod event bus, ne s'exécute que côté client
        modEventBus.addListener(ArcadiaDungeon::onRegisterGuiLayers);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "run_hud"),
            RunOverlayHud.INSTANCE
        );
        LOGGER.info("[Arcadia][BOOT] HUD layer registered: run_hud");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID);
        registrar.playToClient(RunStatePayload.TYPE, RunStatePayload.CODEC,
            ClientPayloadHandler::handleRunState);
        registrar.playToServer(StartRunPayload.TYPE, StartRunPayload.CODEC,
            ServerPayloadHandler::handleStartRun);
        registrar.playToServer(AbandonRunPayload.TYPE, AbandonRunPayload.CODEC,
            ServerPayloadHandler::handleAbandonRun);
        registrar.playToClient(OpenDebugScreenPayload.TYPE, OpenDebugScreenPayload.CODEC,
            ClientPayloadHandler::handleOpenDebugScreen);
        registrar.playToServer(JoinRunPayload.TYPE, JoinRunPayload.CODEC,
            ServerPayloadHandler::handleJoinRun);
        registrar.playToServer(RequestRunResyncPayload.TYPE, RequestRunResyncPayload.CODEC,
            ServerPayloadHandler::handleRequestResync);
        registrar.playToClient(DungeonListPayload.TYPE, DungeonListPayload.CODEC,
            ClientPayloadHandler::handleDungeonList);
        registrar.playToClient(OpenResultScreenPayload.TYPE, OpenResultScreenPayload.CODEC,
            ClientPayloadHandler::handleOpenResultScreen);
        registrar.playToServer(RequestDungeonListPayload.TYPE, RequestDungeonListPayload.CODEC,
            ServerPayloadHandler::handleRequestDungeonList);
        registrar.playToServer(ReloadRequestPayload.TYPE, ReloadRequestPayload.CODEC,
            ServerPayloadHandler::handleReloadRequest);
        registrar.playToServer(CreateDungeonPayload.TYPE, CreateDungeonPayload.CODEC,
            ServerPayloadHandler::handleCreateDungeon);
        LOGGER.info("[Arcadia][BOOT] Payloads registered (RunState/DungeonList/OpenResultScreen S2C, StartRun/AbandonRun/JoinRun/RequestResync/RequestDungeonList/ReloadRequest/CreateDungeon C2S, OpenDebugScreen S2C)");
    }

    /** Listeners serveur : boot + commandes + shutdown. */
    @EventBusSubscriber(modid = MODID)
    public static final class ServerEvents {

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            LOGGER.info("[Arcadia][BOOT] Server starting — bootstrapping dungeon registry");
            if (dungeonRegistry == null) dungeonRegistry = new DungeonRegistry();
            dungeonRegistry.bootstrap();

            if (runLifecycleService == null)
                runLifecycleService = new RunLifecycleService(dungeonRegistry);

            if (playerProgressService == null)
                playerProgressService = new PlayerProgressService();

            if (rewardDistributionService == null)
                rewardDistributionService = new RewardDistributionService(playerProgressService, dungeonRegistry);

            if (bossPhaseService == null) {
                bossPhaseService = new BossPhaseService(runLifecycleService, rewardDistributionService, dungeonRegistry);
                NeoForge.EVENT_BUS.register(bossPhaseService);
            }

            if (roomProgressionService == null) {
                roomProgressionService = new RoomProgressionService(dungeonRegistry, runLifecycleService, bossPhaseService);
                NeoForge.EVENT_BUS.register(roomProgressionService);
            }

            if (playerDeathService == null) {
                playerDeathService = new PlayerDeathService(runLifecycleService, roomProgressionService, rewardDistributionService);
                NeoForge.EVENT_BUS.register(playerDeathService);
            }

            if (archetypeService == null) {
                archetypeService = new ArchetypeService(dungeonRegistry);
                runLifecycleService.setArchetypeService(archetypeService);
            }

            if (runCleanupService == null) {
                runCleanupService = new RunCleanupService(runLifecycleService, roomProgressionService);
                runCleanupService.start();
            }

            if (placementRegistry == null) placementRegistry = new PlacementRegistry();
            placementRegistry.load(); // toujours recharger — onRegisterCommands peut créer le registry sans loader

            if (structurePlacer == null) {
                structurePlacer = new StructurePlacer();
            }

            playerProgressService.load();

            LOGGER.info("[Arcadia][BOOT] Services initialized (RunLifecycle, PlayerProgress, Rewards, BossPhase, RoomProgression, PlayerDeath)");
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            // Shutdown + null systématique : garantit que onServerStarting recrée tout proprement
            // (évite RejectedExecutionException sur executor terminé lors du prochain boot)
            if (runCleanupService != null)    { runCleanupService.shutdown(); runCleanupService = null; }
            if (playerDeathService != null)   { playerDeathService.shutdown(); NeoForge.EVENT_BUS.unregister(playerDeathService); playerDeathService = null; }
            if (bossPhaseService != null)     { NeoForge.EVENT_BUS.unregister(bossPhaseService); bossPhaseService = null; }
            if (roomProgressionService != null) { NeoForge.EVENT_BUS.unregister(roomProgressionService); roomProgressionService = null; }
            if (playerProgressService != null) { playerProgressService.save(); playerProgressService.shutdown(); playerProgressService = null; }
            if (runLifecycleService != null)  { runLifecycleService.shutdownAll(); runLifecycleService = null; }
            dungeonRegistry        = null;
            placementRegistry      = null;
            structurePlacer        = null;
            archetypeService       = null;
            rewardDistributionService = null;
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            if (dungeonRegistry == null) dungeonRegistry = new DungeonRegistry();
            new ArcadiaReloadCommand(dungeonRegistry).register(event.getDispatcher());
            new ArcadiaDebugCommand().register(event.getDispatcher());
            if (placementRegistry == null) placementRegistry = new PlacementRegistry();
            if (structurePlacer == null) structurePlacer = new StructurePlacer();
            new ArcadiaSetupCommand(structurePlacer, placementRegistry).register(event.getDispatcher());
            LOGGER.info("[Arcadia][BOOT] Commands registered (/arcadia reload, /arcadia setup, /arcadia debug *)");
        }
    }
}
