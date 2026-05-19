package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunPhase;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Legacy-style global dungeon area protection. */
public final class DungeonZoneProtectionService {

    public static final String MANAGED_ENTITY_TAG = "arcadia_managed";
    private static final Set<String> BLOCKED_RUN_COMMANDS = Set.of(
        "home", "homes", "sethome", "back", "spawn", "warp", "rtp", "tpa", "tpaccept");

    private final DungeonRegistry dungeonRegistry;
    private final RunLifecycleService runLifecycleService;
    private final RoomProgressionService roomProgressionService;
    private int tickCounter;

    public DungeonZoneProtectionService(DungeonRegistry dungeonRegistry,
                                        RunLifecycleService runLifecycleService,
                                        RoomProgressionService roomProgressionService) {
        this.dungeonRegistry = dungeonRegistry;
        this.runLifecycleService = runLifecycleService;
        this.roomProgressionService = roomProgressionService;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) return;
        if (!(entity instanceof LivingEntity living)) return;
        if (living.getTags().contains(MANAGED_ENTITY_TAG)) return;

        String dim = living.level().dimension().location().toString();
        for (Run run : runLifecycleService.activeRuns().values()) {
            if (run.phase() != RunPhase.IN_PROGRESS) continue;
            DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
            if (config != null && config.hasArea() && config.isInArea(dim, living.getX(), living.getY(), living.getZ())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        if (runLifecycleService.findActiveRunForPlayer(player.getUUID()).isEmpty()) return;

        String raw = event.getParseResults().getReader().getString().trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        String root = raw.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        int namespace = root.indexOf(':');
        if (namespace >= 0) root = root.substring(namespace + 1);
        if (!BLOCKED_RUN_COMMANDS.contains(root)) return;

        event.setCanceled(true);
        player.sendSystemMessage(Component.translatable("arcadia.server.run.command_blocked", root)
            .withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 20 != 0) return;
        MinecraftServer server = event.getServer();
        Set<UUID> activePlayers = new HashSet<>();

        for (Run run : runLifecycleService.activeRuns().values()) {
            if (run.phase() != RunPhase.IN_PROGRESS) continue;
            DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
            if (config == null || !config.hasArea()) continue;

            for (UUID playerId : run.playerIds()) {
                activePlayers.add(playerId);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || player.isSpectator()) continue;
                String dim = player.level().dimension().location().toString();
                if (!config.isInArea(dim, player.getX(), player.getY(), player.getZ())) {
                    teleportBackToRun(player, run);
                    player.sendSystemMessage(Component.translatable("arcadia.server.zone.cannot_leave")
                        .withStyle(ChatFormatting.RED));
                }
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (activePlayers.contains(player.getUUID()) || player.isSpectator()) continue;
            String dim = player.level().dimension().location().toString();
            for (Run run : runLifecycleService.activeRuns().values()) {
                if (run.phase() != RunPhase.IN_PROGRESS) continue;
                DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
                if (config != null && config.hasArea() && config.isInArea(dim, player.getX(), player.getY(), player.getZ())) {
                    ejectParasite(server, player);
                    return;
                }
            }
        }
    }

    private void teleportBackToRun(ServerPlayer player, Run run) {
        Vec3 spawn = roomProgressionService.getSpawnPosition(run.id()).orElse(player.position());
        player.teleportTo(player.serverLevel(), spawn.x, spawn.y, spawn.z, player.getYRot(), player.getXRot());
    }

    private static void ejectParasite(MinecraftServer server, ServerPlayer player) {
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        player.sendSystemMessage(Component.translatable("arcadia.server.zone.entry_denied")
            .withStyle(ChatFormatting.RED));
    }
}
