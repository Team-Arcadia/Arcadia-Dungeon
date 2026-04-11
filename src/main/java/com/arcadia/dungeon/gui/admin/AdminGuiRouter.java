package com.arcadia.dungeon.gui.admin;

import com.arcadia.dungeon.config.ConfigManager;
import com.arcadia.dungeon.config.DungeonConfig;
import com.arcadia.dungeon.config.SpawnPointConfig;
import com.arcadia.dungeon.gui.ArcadiaChestMenu;
import com.arcadia.dungeon.gui.ArcadiaPendingInputManager;
import com.arcadia.dungeon.gui.admin.dungeon.AdminDungeonGuiMenus;
import com.arcadia.dungeon.gui.admin.entity.AdminEntityGuiMenus;
import com.arcadia.dungeon.gui.admin.progression.AdminProgressionGuiMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AdminGuiRouter {

    private AdminGuiRouter() {
    }

    public static void openPlayerArcadiaGui(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(10, guiItem(Items.PLAYER_HEAD, ChatFormatting.GOLD + "Profil Arcadia", "Voir ton profil"), sp -> {
                sp.closeContainer();
                sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "arcadia_dungeon profile");
            });
            menu.setButton(12, guiItem(Items.GOLD_INGOT, ChatFormatting.YELLOW + "Top Arcadia", "Voir le classement"), sp -> {
                sp.closeContainer();
                sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "arcadia_dungeon top");
            });
            menu.setButton(14, guiItem(Items.MAP, ChatFormatting.AQUA + "Progression", "Voir les donjons"), sp -> {
                sp.closeContainer();
                sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "arcadia_dungeon progression");
            });

            List<DungeonConfig> dungeons = new ArrayList<>(ConfigManager.getInstance().getDungeonConfigs().values());
            dungeons.sort(Comparator.comparingInt(d -> d.order));
            int slot = 18;
            for (DungeonConfig dungeon : dungeons) {
                if (!dungeon.enabled || slot >= 27) continue;
                menu.setButton(slot++, guiItem(Items.END_PORTAL_FRAME, ChatFormatting.GREEN + dungeon.name, "Lancer " + dungeon.id), sp -> {
                    sp.closeContainer();
                    sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "arcadia_dungeon start " + dungeon.id);
                });
            }
            return menu;
        }, Component.literal("Arcadia")));
    }

    public static void openAdminHubGui(ServerPlayer player, int page) {
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 5);
            menu.setButton(10, guiItem(Items.CRAFTING_TABLE, ChatFormatting.GOLD + "Creer un donjon", "Format chat: <id> <nom>"), sp ->
                    ArcadiaPendingInputManager.begin(
                            sp,
                            "Entre '<id> <nom>' pour creer un donjon",
                            (playerInput, input) -> {
                                String[] parts = input.trim().split("\\s+", 2);
                                if (parts.length < 2) {
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <id> <nom>").withStyle(ChatFormatting.RED));
                                    return false;
                                }
                                if (ConfigManager.getInstance().getDungeon(parts[0]) != null) {
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Cet id existe deja.").withStyle(ChatFormatting.RED));
                                    return false;
                                }
                                DungeonConfig config = new DungeonConfig(parts[0], parts[1]);
                                config.spawnPoint = new SpawnPointConfig(
                                        playerInput.level().dimension().location().toString(),
                                        playerInput.getX(), playerInput.getY(), playerInput.getZ(),
                                        playerInput.getYRot(), playerInput.getXRot()
                                );
                                ConfigManager.getInstance().saveDungeon(config);
                                playerInput.sendSystemMessage(Component.literal("[Arcadia] Donjon cree: " + parts[1]).withStyle(ChatFormatting.GREEN));
                                return true;
                            },
                            reopen -> openAdminHubGui(reopen, page)
                    ));
            menu.setButton(12, guiItem(Items.BOOK, ChatFormatting.AQUA + "Liste Donjons", "Selectionner un donjon"), null);
            menu.setButton(14, guiItem(Items.DIAMOND, ChatFormatting.LIGHT_PURPLE + "Arcadia", "Ouvrir les reglages Arcadia"), AdminGuiRouter::openArcadiaAdminGui);

            List<DungeonConfig> dungeons = new ArrayList<>(ConfigManager.getInstance().getDungeonConfigs().values().stream()
                    .sorted(Comparator.comparingInt(d -> d.order))
                    .toList());
            int pageSize = 18;
            int totalPages = Math.max(1, (dungeons.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(dungeons.size(), start + pageSize);
            int slot = 18;
            for (DungeonConfig dungeon : dungeons.subList(start, end)) {
                menu.setButton(slot++, guiItem(
                        dungeon.enabled ? Items.CHEST : Items.BARRIER,
                        (dungeon.enabled ? ChatFormatting.GREEN : ChatFormatting.RED) + dungeon.name,
                        "ID: " + dungeon.id,
                        "Cliquer pour ouvrir"), sp -> openDungeonAdminHubGui(sp, dungeon.id));
            }
            if (safePage > 0) {
                menu.setButton(36, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Page precedente", "Page " + safePage), sp -> openAdminHubGui(sp, safePage - 1));
            }
            menu.setButton(40, guiItem(Items.PAPER, ChatFormatting.WHITE + "Page " + (safePage + 1) + "/" + totalPages, "Navigation"), null);
            if (safePage + 1 < totalPages) {
                menu.setButton(44, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Page suivante", "Page " + (safePage + 2)), sp -> openAdminHubGui(sp, safePage + 1));
            }
            return menu;
        }, Component.literal("Admin Donjons")));
    }

    public static void openDungeonAdminHubGui(ServerPlayer player, String dungeonId) { AdminDungeonGuiMenus.openDungeonAdminHubGui(player, dungeonId); }
    public static void openDungeonCoreGui(ServerPlayer player, String dungeonId) { AdminDungeonGuiMenus.openDungeonCoreGui(player, dungeonId); }
    public static void openDungeonMessagesGui(ServerPlayer player, String dungeonId) { AdminDungeonGuiMenus.openDungeonMessagesGui(player, dungeonId); }
    public static void openDungeonRewardsGui(ServerPlayer player, String dungeonId) { AdminDungeonGuiMenus.openDungeonRewardsGui(player, dungeonId, 0); }
    public static void openDungeonRewardsGui(ServerPlayer player, String dungeonId, int page) { AdminDungeonGuiMenus.openDungeonRewardsGui(player, dungeonId, page); }
    public static void openDungeonAreaGui(ServerPlayer player, String dungeonId) { AdminDungeonGuiMenus.openDungeonAreaGui(player, dungeonId, 0); }
    public static void openDungeonAreaGui(ServerPlayer player, String dungeonId, int page) { AdminDungeonGuiMenus.openDungeonAreaGui(player, dungeonId, page); }
    public static void openWallDetailGui(ServerPlayer player, String dungeonId, String wallId) { AdminDungeonGuiMenus.openWallDetailGui(player, dungeonId, wallId); }
    public static void openBossListGui(ServerPlayer player, String dungeonId, int page) { AdminEntityGuiMenus.openBossListGui(player, dungeonId, page); }
    public static void openBossDetailGui(ServerPlayer player, String dungeonId, String bossId) { AdminEntityGuiMenus.openBossDetailGui(player, dungeonId, bossId); }
    public static void openWaveListGui(ServerPlayer player, String dungeonId, int page) { AdminEntityGuiMenus.openWaveListGui(player, dungeonId, page); }
    public static void openWaveDetailGui(ServerPlayer player, String dungeonId, int waveNumber, int page) { AdminEntityGuiMenus.openWaveDetailGui(player, dungeonId, waveNumber, page); }
    public static void openWaveMobDetailGui(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex) { AdminEntityGuiMenus.openWaveMobDetailGui(player, dungeonId, waveNumber, mobIndex); }
    public static void openArcadiaAdminGui(ServerPlayer player) { AdminProgressionGuiMenus.openArcadiaAdminGui(player); }
    public static void openArcadiaAdminGui(ServerPlayer player, int page) { AdminProgressionGuiMenus.openArcadiaAdminGui(player, page); }
    public static void openDungeonArcadiaAdminGui(ServerPlayer player, String dungeonId) { AdminProgressionGuiMenus.openDungeonArcadiaAdminGui(player, dungeonId); }
    public static void openArcadiaRanksGui(ServerPlayer player) { AdminProgressionGuiMenus.openArcadiaRanksGui(player, 0); }
    public static void openArcadiaRanksGui(ServerPlayer player, int page) { AdminProgressionGuiMenus.openArcadiaRanksGui(player, page); }
    public static void openArcadiaMilestonesGui(ServerPlayer player) { AdminProgressionGuiMenus.openArcadiaMilestonesGui(player, 0); }
    public static void openArcadiaMilestonesGui(ServerPlayer player, int page) { AdminProgressionGuiMenus.openArcadiaMilestonesGui(player, page); }
    public static void openArcadiaMilestoneDetailGui(ServerPlayer player, int level) { AdminProgressionGuiMenus.openArcadiaMilestoneDetailGui(player, level, 0); }
    public static void openArcadiaMilestoneDetailGui(ServerPlayer player, int level, int page) { AdminProgressionGuiMenus.openArcadiaMilestoneDetailGui(player, level, page); }
    public static void openArcadiaStreaksGui(ServerPlayer player) { AdminProgressionGuiMenus.openArcadiaStreaksGui(player, 0); }
    public static void openArcadiaStreaksGui(ServerPlayer player, int page) { AdminProgressionGuiMenus.openArcadiaStreaksGui(player, page); }
    public static void openArcadiaStreakDetailGui(ServerPlayer player, int weeks) { AdminProgressionGuiMenus.openArcadiaStreakDetailGui(player, weeks); }

    public static ItemStack guiItem(net.minecraft.world.item.Item item, String name, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        stack.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lore));
        return stack;
    }

    public static String getItemId(ServerPlayer player, net.minecraft.world.entity.EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        if (stack.isEmpty()) {
            return "";
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
