package com.vyrriox.arcadiadungeon;

import com.mojang.logging.LogUtils;
import com.vyrriox.arcadiadungeon.command.ArcadiaCommands;
import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import com.vyrriox.arcadiadungeon.dungeon.DungeonStats;
import com.vyrriox.arcadiadungeon.event.DungeonEventHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
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

    public ArcadiaDungeon() {
        // Forge 1.20.1 constructs a mod class with no arguments and does not hand it the mod
        // event bus: taking one in the constructor, the way NeoForge 1.21 does, compiles
        // perfectly well here and then fails at load with no suitable constructor. The bus
        // is asked for instead.
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get()
                .getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new DungeonEventHandler());
        MinecraftForge.EVENT_BUS.register(DungeonStats.class);
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
                // so its language file is not there either and a key would print raw.
                "Donjons",
                "Lancez un donjon",
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
