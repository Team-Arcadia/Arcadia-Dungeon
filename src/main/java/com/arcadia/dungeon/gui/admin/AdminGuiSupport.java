package com.arcadia.dungeon.gui.admin;

import com.arcadia.dungeon.gui.ArcadiaChestMenu;
import com.arcadia.dungeon.gui.ArcadiaPendingInputManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class AdminGuiSupport {
    private AdminGuiSupport() {}

    public static void beginPrompt(ServerPlayer player, String prompt,
                                   BiFunction<ServerPlayer, String, Boolean> apply,
                                   Consumer<ServerPlayer> reopen) {
        ArcadiaPendingInputManager.begin(player, prompt, apply, reopen);
    }

    public static void beginPromptNoReopen(ServerPlayer player, String prompt,
                                           BiFunction<ServerPlayer, String, Boolean> apply,
                                           Consumer<ServerPlayer> reopenOnCancel) {
        ArcadiaPendingInputManager.begin(player, prompt, apply, reopenOnCancel, false);
    }

    public static void beginIntPrompt(ServerPlayer player, String prompt,
                                      BiFunction<ServerPlayer, Integer, Boolean> apply,
                                      Consumer<ServerPlayer> reopen) {
        ArcadiaPendingInputManager.begin(player, prompt, (playerInput, input) -> {
            try {
                return apply.apply(playerInput, Integer.parseInt(input.trim()));
            } catch (NumberFormatException e) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeur invalide.").withStyle(ChatFormatting.RED));
                return false;
            }
        }, reopen);
    }

    public static void beginDoublePrompt(ServerPlayer player, String prompt,
                                         BiFunction<ServerPlayer, Double, Boolean> apply,
                                         Consumer<ServerPlayer> reopen) {
        ArcadiaPendingInputManager.begin(player, prompt, (playerInput, input) -> {
            try {
                return apply.apply(playerInput, Double.parseDouble(input.trim()));
            } catch (NumberFormatException e) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeur invalide.").withStyle(ChatFormatting.RED));
                return false;
            }
        }, reopen);
    }

    public static void addPagination(ArcadiaChestMenu menu, int prevSlot, int infoSlot, int nextSlot,
                                     int safePage, int totalPages, BiConsumer<ServerPlayer, Integer> openPage) {
        if (safePage > 0) {
            menu.setButton(prevSlot, navItem(ChatFormatting.YELLOW + "Page precedente", "Page " + safePage),
                    sp -> openPage.accept(sp, safePage - 1));
        }
        menu.setButton(infoSlot, navItem(ChatFormatting.WHITE + "Page " + (safePage + 1) + "/" + totalPages, "Navigation"), null);
        if (safePage + 1 < totalPages) {
            menu.setButton(nextSlot, navItem(ChatFormatting.YELLOW + "Page suivante", "Page " + (safePage + 2)),
                    sp -> openPage.accept(sp, safePage + 1));
        }
    }

    public static void openConfirmationGui(ServerPlayer player, String title, String description,
                                           Consumer<ServerPlayer> onConfirm, Consumer<ServerPlayer> onCancel) {
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(11, guiItem(Items.LIME_DYE, ChatFormatting.GREEN + "Confirmer", description), onConfirm);
            menu.setButton(15, guiItem(Items.BARRIER, ChatFormatting.RED + "Annuler", "Revenir sans rien changer"), onCancel);
            return menu;
        }, Component.literal(title)));
    }

    private static ItemStack guiItem(net.minecraft.world.item.Item item, String name, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        for (String line : loreLines) {
            lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        return stack;
    }

    private static ItemStack navItem(String name, String lore) {
        ItemStack stack = new ItemStack(name.contains("Page ") && name.contains("/") ? Items.PAPER : Items.ARROW);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.List.of(Component.literal(lore).withStyle(ChatFormatting.GRAY))));
        return stack;
    }
}
