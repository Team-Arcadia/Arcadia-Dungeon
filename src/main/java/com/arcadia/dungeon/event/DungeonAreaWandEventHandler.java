package com.arcadia.dungeon.event;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.network.AreaWandStatusPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Admin wand for selecting the global dungeon area, matching the legacy flow. */
public final class DungeonAreaWandEventHandler {

    public static final String AREA_WAND_TAG = "arcadia_area_wand";

    private static final Map<UUID, String> SELECTED_DUNGEON = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> POS_1 = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> POS_2 = new ConcurrentHashMap<>();

    public static void selectDungeon(ServerPlayer player, String dungeonId) {
        SELECTED_DUNGEON.put(player.getUUID(), dungeonId);
        POS_1.remove(player.getUUID());
        POS_2.remove(player.getUUID());
    }

    public static String selectedDungeon(ServerPlayer player) {
        return SELECTED_DUNGEON.get(player.getUUID());
    }

    public static void beginSelection(ServerPlayer player, String dungeonId) {
        selectDungeon(player, dungeonId);
        removeAreaWand(player);

        ItemStack wand = new ItemStack(Items.GOLDEN_SHOVEL);
        wand.set(DataComponents.CUSTOM_NAME,
            Component.literal(AREA_WAND_TAG + " - Arcadia Dungeon Area Wand")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }

        player.sendSystemMessage(Component.literal("[Arcadia Wand] Selection de zone: " + dungeonId
            + ". Clic gauche = Pos1, clic droit = Pos2.").withStyle(ChatFormatting.GOLD));
        sendStatus(player, dungeonId, true, false);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isAreaWand(player)) {
            setPosition(player, event.getPos(), true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isAreaWand(player)) {
            setPosition(player, event.getPos(), false);
            event.setCanceled(true);
        }
    }

    private static void setPosition(ServerPlayer player, BlockPos pos, boolean first) {
        if (!player.hasPermissions(2)) return;
        String dungeonId = SELECTED_DUNGEON.get(player.getUUID());
        if (dungeonId == null || dungeonId.isBlank()) {
            player.sendSystemMessage(Component.literal("[Arcadia Wand] Selectionne un donjon avec /arcadia admin wand_select <dungeonId>.")
                .withStyle(ChatFormatting.RED));
            return;
        }

        if (first) {
            POS_1.put(player.getUUID(), pos);
            player.sendSystemMessage(Component.literal("[Arcadia Wand] Pos1: " + format(pos)).withStyle(ChatFormatting.GREEN));
        } else {
            POS_2.put(player.getUUID(), pos);
            player.sendSystemMessage(Component.literal("[Arcadia Wand] Pos2: " + format(pos)).withStyle(ChatFormatting.AQUA));
        }

        sendStatus(player, dungeonId, true, false);
        saveIfComplete(player, dungeonId);
    }

    private static void saveIfComplete(ServerPlayer player, String dungeonId) {
        BlockPos p1 = POS_1.get(player.getUUID());
        BlockPos p2 = POS_2.get(player.getUUID());
        if (p1 == null || p2 == null) return;

        DungeonConfig config = ArcadiaDungeon.dungeonRegistry().get(dungeonId).orElse(null);
        if (config == null) {
            player.sendSystemMessage(Component.literal("[Arcadia Wand] Donjon introuvable: " + dungeonId)
                .withStyle(ChatFormatting.RED));
            return;
        }

        String dim = player.level().dimension().location().toString();
        DungeonConfig.AreaPos area1 = new DungeonConfig.AreaPos(dim, p1.getX(), p1.getY(), p1.getZ());
        DungeonConfig.AreaPos area2 = new DungeonConfig.AreaPos(dim, p2.getX(), p2.getY(), p2.getZ());
        ArcadiaDungeon.dungeonRegistry().save(config.withArea(area1, area2));

        int sx = Math.abs(p2.getX() - p1.getX()) + 1;
        int sy = Math.abs(p2.getY() - p1.getY()) + 1;
        int sz = Math.abs(p2.getZ() - p1.getZ()) + 1;
        player.sendSystemMessage(Component.literal("[Arcadia Wand] Zone sauvegardee pour " + dungeonId
            + " (" + sx + "x" + sy + "x" + sz + ", " + (sx * sy * sz) + " blocs)")
            .withStyle(ChatFormatting.GOLD));

        removeAreaWand(player);
        sendStatus(player, dungeonId, false, true);
        POS_1.remove(player.getUUID());
        POS_2.remove(player.getUUID());
    }

    private static boolean isAreaWand(ServerPlayer player) {
        return isAreaWand(player.getMainHandItem());
    }

    private static boolean isAreaWand(ItemStack stack) {
        Component name = stack.getOrDefault(DataComponents.CUSTOM_NAME, Component.empty());
        return name.getString().contains(AREA_WAND_TAG);
    }

    private static void removeAreaWand(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isAreaWand(inventory.getItem(i))) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static void sendStatus(ServerPlayer player, String dungeonId, boolean selecting, boolean areaSet) {
        BlockPos p1 = POS_1.get(player.getUUID());
        BlockPos p2 = POS_2.get(player.getUUID());
        String dim = player.level().dimension().location().toString();
        PacketDistributor.sendToPlayer(player, new AreaWandStatusPayload(
            dungeonId,
            selecting,
            p1 != null,
            p2 != null,
            areaSet,
            dim,
            p1 != null ? p1.getX() : 0,
            p1 != null ? p1.getY() : 0,
            p1 != null ? p1.getZ() : 0,
            p2 != null ? p2.getX() : 0,
            p2 != null ? p2.getY() : 0,
            p2 != null ? p2.getZ() : 0
        ));
    }

    private static String format(BlockPos pos) {
        return pos.getX() + " / " + pos.getY() + " / " + pos.getZ();
    }
}
