package com.arcadia.dungeon.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ArcadiaChestMenu extends AbstractContainerMenu {
    private final SimpleContainer container;
    private final int menuRows;
    private final Map<Integer, Consumer<ServerPlayer>> clickActions = new HashMap<>();

    public ArcadiaChestMenu(int containerId, Inventory playerInventory, int rows) {
        super(resolveType(rows), containerId);
        this.menuRows = rows;
        this.container = new SimpleContainer(rows * 9);

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                int slotIndex = column + row * 9;
                addSlot(new Slot(container, slotIndex, 8 + column * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPickup(Player player) {
                        return false;
                    }

                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }

        int inventoryY = 18 + rows * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, inventoryY + row * 18));
            }
        }

        int hotbarY = inventoryY + 58;
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, hotbarY));
        }
    }

    public void setButton(int slot, ItemStack stack, Consumer<ServerPlayer> action) {
        if (slot < 0 || slot >= container.getContainerSize()) return;
        container.setItem(slot, stack);
        if (action != null) {
            clickActions.put(slot, action);
        } else {
            clickActions.remove(slot);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < container.getContainerSize()) {
            if (player instanceof ServerPlayer serverPlayer) {
                Consumer<ServerPlayer> action = clickActions.get(slotId);
                if (action != null) {
                    action.accept(serverPlayer);
                }
            }
            broadcastChanges();
            return;
        }
        return;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static MenuType<?> resolveType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }
}
