package com.vyrriox.arcadiadungeon;

import com.mojang.logging.LogUtils;
import com.vyrriox.arcadiadungeon.command.ArcadiaCommands;
import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import com.vyrriox.arcadiadungeon.dungeon.DungeonStats;
import com.vyrriox.arcadiadungeon.event.DungeonEventHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import org.slf4j.Logger;

@Mod(ArcadiaDungeon.MODID)
public class ArcadiaDungeon {
    public static final String MODID = "arcadia_dungeon";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Permission node for bypassing anti-parasite system (compatible LuckPerms)
    public static final PermissionNode<Boolean> BYPASS_ANTIPARASITE = new PermissionNode<>(
            MODID, "bypass.antiparasite",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> false // default: no bypass
    );

    public static final PermissionNode<Boolean> BYPASS_ANTIFLY = new PermissionNode<>(
            MODID, "bypass.antifly",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> false
    );

    public ArcadiaDungeon(IEventBus modEventBus) {
        modEventBus.addListener(this::onCommonSetup);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new DungeonEventHandler());
        NeoForge.EVENT_BUS.register(DungeonStats.class);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Arcadia Dungeon initializing...");

        // The dungeon section of the Arcadia hub. This mod has no client half and is not
        // getting one: it registers a card and the action that opens it, and Arcadia Lib
        // sends both to whatever client connects. Nothing below touches a client class.
        com.arcadia.lib.ArcadiaModRegistry.registerCard(new com.arcadia.lib.client.ArcadiaModCard(
                com.vyrriox.arcadiadungeon.hub.DungeonsPanel.CARD_ID,
                "⚔",
                // Plain text, not translation keys: this mod is not installed on the client,
                // so its language file is not there either and a key would print raw. Which
                // also means this text can only ever be one language, and the hub it sits in
                // is English, so it is English. It read "Donjons / Lancez un donjon" next to
                // six English cards.
                "Dungeons",
                "Start a run",
                0xC0504A,
                1,      // sortOrder
                0,      // row
                -1,     // tabIndex: not a dashboard tab, it opens its own panel
                true,
                null,
                com.vyrriox.arcadiadungeon.hub.DungeonsPanel.OPEN_ACTION));

        com.arcadia.lib.ArcadiaModRegistry.registerServerAction(
                com.vyrriox.arcadiadungeon.hub.DungeonsPanel.OPEN_ACTION,
                com.vyrriox.arcadiadungeon.hub.DungeonsPanel::open);
    }

    @SubscribeEvent
    public void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(BYPASS_ANTIPARASITE);
        event.addNodes(BYPASS_ANTIFLY);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ConfigManager.getInstance().loadAll();
        DungeonManager.getInstance().setServer(event.getServer());
        com.vyrriox.arcadiadungeon.dungeon.WeeklyLeaderboard.getInstance().load();
        LOGGER.info("Arcadia Dungeon loaded {} dungeon(s)", ConfigManager.getInstance().getDungeonConfigs().size());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        DungeonManager.getInstance().stopAllDungeons();
        com.vyrriox.arcadiadungeon.dungeon.PlayerProgressManager.getInstance().flushDirty();
        DungeonManager.getInstance().setServer(null);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ArcadiaCommands.register(event.getDispatcher());
    }
}
