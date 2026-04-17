package com.arcadia.dungeon.event;

import com.arcadia.dungeon.compat.ApotheosisCompat;
import com.arcadia.dungeon.dungeon.DungeonInstance;
import com.arcadia.dungeon.dungeon.DungeonManager;
import com.arcadia.dungeon.gui.ArcadiaPendingInputManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Handles player lifecycle events: login/logout, respawn, and command blocking inside dungeons.
 *
 * <p>Beneficial effect blocking (charms) is handled via {@link ApotheosisCompat} —
 * charms are disabled at the DataComponent level on dungeon entry and restored on exit,
 * which is cleaner than intercepting {@code MobEffectEvent.Applicable} (which blocked
 * the effect but not the {@code hurtAndBreak} call, causing a durability drain loop).
 */
public class DungeonPlayerEventHandler {

    // === CHARM RE-ENABLE BLOCKING ===

    /**
     * Prevents players from re-enabling Apotheosis charms while inside a dungeon.
     * Apotheosis toggles CHARM_ENABLED via Item#use (right-click), so we cancel
     * the interaction before it reaches the item's use() method.
     */
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (DungeonManager.getInstance().getPlayerDungeon(player.getUUID()) == null) return;
        if (!ApotheosisCompat.isCharmItem(event.getItemStack())) return;

        event.setCanceled(true);
        player.sendSystemMessage(
                Component.literal("[Arcadia] Les charms sont désactivés pendant le donjon.")
                        .withStyle(ChatFormatting.RED));
    }

    // === COMMAND BLOCKING ===

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        try {
            var source = event.getParseResults().getContext().getSource();
            if (!(source.getEntity() instanceof ServerPlayer player)) return;
            DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
            if (instance == null || !instance.getConfig().settings.blockTeleportCommands) return;

            String input = event.getParseResults().getReader().getString().trim();
            if (input.startsWith("/")) input = input.substring(1);
            String rootCmd = input.split(" ")[0].toLowerCase();
            String cmdNoPrefix = rootCmd.contains(":") ? rootCmd.substring(rootCmd.indexOf(':') + 1) : rootCmd;

            for (String blocked : instance.getConfig().settings.blockedCommands) {
                if (rootCmd.equals(blocked) || cmdNoPrefix.equals(blocked)) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("[Arcadia] Cette commande est bloquee pendant le donjon!").withStyle(ChatFormatting.RED));
                    return;
                }
            }
        } catch (RuntimeException e) {
            DungeonEventUtil.logHandlerError("onCommand", e);
        }
    }

    // === PLAYER LIFECYCLE ===

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(uuid);
            if (instance != null) {
                DungeonManager.getInstance().removePlayerFromDungeon(uuid);
            }
            // Restore Apotheosis charms that were suspended for this dungeon
            ApotheosisCompat.restoreCharms(player);
            ArcadiaPendingInputManager.clear(player);
            // Cleanup wand data
            DungeonWandEventHandler.wandDungeon.remove(uuid);
            DungeonWandEventHandler.wandPos1.remove(uuid);
            DungeonWandEventHandler.wandPos2.remove(uuid);
            DungeonWandEventHandler.wandWall.remove(uuid);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Fix #16: skip respawn TP if player is pending removal (max deaths reached)
        if (DungeonManager.getInstance().isPendingRemoval(player.getUUID())) return;

        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null) return;

        DungeonManager.getInstance().teleportToSpawn(player, instance.getConfig().spawnPoint);
        player.sendSystemMessage(Component.literal("[Arcadia] Respawn au debut du donjon. ("
                + instance.getRemainingLives(player.getUUID()) + " vie(s) restante(s))")
                .withStyle(ChatFormatting.YELLOW));
    }
}
