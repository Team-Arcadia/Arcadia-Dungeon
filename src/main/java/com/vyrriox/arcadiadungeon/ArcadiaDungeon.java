package com.vyrriox.arcadiadungeon;

import com.mojang.logging.LogUtils;
import com.arcadia.core.profiling.ProfilerUtil;
import com.vyrriox.arcadiadungeon.command.ArcadiaCommands;
import com.vyrriox.arcadiadungeon.util.ModCompat;
import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
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
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Arcadia Dungeon initializing...");
        LOGGER.info("ModCompat: HAS_LUCKPERMS={}, HAS_SPARK={}", ModCompat.HAS_LUCKPERMS, ModCompat.HAS_SPARK);
    }

    @SubscribeEvent
    public void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(BYPASS_ANTIPARASITE);
        event.addNodes(BYPASS_ANTIFLY);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ProfilerUtil.setServer(event.getServer());
        ConfigManager configManager = ConfigManager.getInstance();
        configManager.loadAll();
        DungeonManager.getInstance().setServer(event.getServer());
        com.vyrriox.arcadiadungeon.dungeon.WeeklyLeaderboard.getInstance().load();
        LOGGER.info("Arcadia Dungeon loaded {} dungeon(s)", configManager.getDungeonConfigs().size());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        DungeonManager.getInstance().stopAllDungeons();
        com.vyrriox.arcadiadungeon.dungeon.PlayerProgressManager.getInstance().flushDirty();
        DungeonManager.getInstance().setServer(null);
        ProfilerUtil.clearServer();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ArcadiaCommands.register(event.getDispatcher());
    }
}
