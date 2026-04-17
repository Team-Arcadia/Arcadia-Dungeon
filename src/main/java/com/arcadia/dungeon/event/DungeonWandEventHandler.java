package com.arcadia.dungeon.event;

import com.arcadia.dungeon.config.ConfigManager;
import com.arcadia.dungeon.config.DungeonConfig;
import com.arcadia.dungeon.dungeon.DungeonManager;
import com.arcadia.dungeon.gui.ArcadiaPendingInputManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles admin wand interactions (area selection and scripted wall editing)
 * and GUI pending-input chat capture.
 */
public class DungeonWandEventHandler {

    // Wand: track selected dungeon per player — accessed externally by GUI and command classes
    public static final Map<UUID, String> wandDungeon = new ConcurrentHashMap<>();
    public static final Map<UUID, BlockPos> wandPos1 = new ConcurrentHashMap<>();
    public static final Map<UUID, BlockPos> wandPos2 = new ConcurrentHashMap<>();
    public static final Map<UUID, String> wandWall = new ConcurrentHashMap<>();

    public static final String AREA_WAND_TAG = "arcadia_area_wand";
    public static final String WALL_WAND_TAG = "arcadia_wall_wand";

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            ArcadiaPendingInputManager.PendingInput pending = ArcadiaPendingInputManager.get(player);
            if (pending == null) return;

            event.setCanceled(true);
            String input = event.getRawText() == null ? "" : event.getRawText().trim();
            if (input.equalsIgnoreCase("cancel")) {
                ArcadiaPendingInputManager.clear(player);
                player.sendSystemMessage(Component.literal("[Arcadia] Saisie annulee.").withStyle(ChatFormatting.YELLOW));
                reopenPendingMenu(player, pending);
                return;
            }

            boolean applied = pending.applyAction().apply(player, input);
            if (applied) {
                ArcadiaPendingInputManager.clear(player);
                if (pending.reopenOnSuccess()) {
                    reopenPendingMenu(player, pending);
                }
            }
        } catch (RuntimeException e) {
            DungeonEventUtil.logHandlerError("onServerChat", e);
        }
    }

    private void reopenPendingMenu(ServerPlayer player, ArcadiaPendingInputManager.PendingInput pending) {
        if (pending.reopenAction() == null) return;
        pending.reopenAction().accept(player);
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String toolTag = getToolTag(player);
        if (toolTag == null) return;
        if (!ensureWandSelection(player, toolTag)) return;

        BlockPos pos = event.getPos();
        if (WALL_WAND_TAG.equals(toolTag)) {
            toggleScriptedWallBlock(player, pos);
        } else {
            wandPos1.put(player.getUUID(), pos);
            player.sendSystemMessage(Component.literal("[Arcadia Wand] Pos1 definie: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.GREEN));
            trySaveWandArea(player);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerInteractRight(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String toolTag = getToolTag(player);
        if (toolTag == null) return;
        if (!ensureWandSelection(player, toolTag)) return;

        BlockPos pos = event.getPos();
        if (WALL_WAND_TAG.equals(toolTag)) {
            toggleScriptedWallBlock(player, pos);
        } else {
            wandPos2.put(player.getUUID(), pos);
            player.sendSystemMessage(Component.literal("[Arcadia Wand] Pos2 definie: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.AQUA));
            trySaveWandArea(player);
        }
        event.setCanceled(true);
    }

    private String getToolTag(ServerPlayer player) {
        String name = player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_NAME, Component.empty()).getString();
        if (name.contains(AREA_WAND_TAG)) return AREA_WAND_TAG;
        if (name.contains(WALL_WAND_TAG)) return WALL_WAND_TAG;
        return null;
    }

    private boolean ensureWandSelection(ServerPlayer player, String toolTag) {
        if (!wandDungeon.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[Arcadia] Aucun donjon selectionne pour cet outil.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (WALL_WAND_TAG.equals(toolTag) && !wandWall.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[Arcadia] Selectionnez un mur scripté avec /arcadia_dungeon admin wall_select <dungeon> <wallId>.").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private void trySaveWandArea(ServerPlayer player) {
        BlockPos p1 = wandPos1.get(player.getUUID());
        BlockPos p2 = wandPos2.get(player.getUUID());
        if (p1 == null || p2 == null) return;

        String dungeonId = wandDungeon.get(player.getUUID());
        var config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) return;

        String dim = player.level().dimension().location().toString();
        DungeonConfig.AreaPos pos1 = new DungeonConfig.AreaPos(dim, p1.getX(), p1.getY(), p1.getZ());
        DungeonConfig.AreaPos pos2 = new DungeonConfig.AreaPos(dim, p2.getX(), p2.getY(), p2.getZ());

        config.areaPos1 = pos1;
        config.areaPos2 = pos2;
        String label = config.name;
        ConfigManager.getInstance().saveDungeon(config);

        int sx = Math.abs(p2.getX() - p1.getX()) + 1;
        int sy = Math.abs(p2.getY() - p1.getY()) + 1;
        int sz = Math.abs(p2.getZ() - p1.getZ()) + 1;
        player.sendSystemMessage(Component.literal("[Arcadia Wand] Zone sauvegardee pour " + label + "! " + sx + "x" + sy + "x" + sz + " (" + (sx * sy * sz) + " blocs)")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        wandPos1.remove(player.getUUID());
        wandPos2.remove(player.getUUID());
    }

    private void toggleScriptedWallBlock(ServerPlayer player, BlockPos pos) {
        String dungeonId = wandDungeon.get(player.getUUID());
        String wallId = wandWall.get(player.getUUID());
        if (dungeonId == null || wallId == null) return;

        var config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) return;

        var wall = config.scriptedWalls.stream().filter(w -> w.id.equals(wallId)).findFirst().orElse(null);
        if (wall == null) return;

        String dim = player.level().dimension().location().toString();
        var existing = wall.blocks.stream()
                .filter(b -> b.dimension.equals(dim) && b.x == pos.getX() && b.y == pos.getY() && b.z == pos.getZ())
                .findFirst().orElse(null);
        if (existing != null) {
            wall.blocks.remove(existing);
            player.sendSystemMessage(Component.literal("[Arcadia Wall] Bloc retire: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.YELLOW));
        } else {
            wall.blocks.add(new DungeonConfig.AreaPos(dim, pos.getX(), pos.getY(), pos.getZ()));
            player.sendSystemMessage(Component.literal("[Arcadia Wall] Bloc ajoute: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.GREEN));
        }
        ConfigManager.getInstance().saveDungeon(config);
    }
}
