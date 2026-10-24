package com.vyrriox.arcadiadungeon.hub;

import com.arcadia.lib.ui.PanelButton;
import com.arcadia.lib.ui.PanelButtonStyle;
import com.arcadia.lib.ui.PanelEntry;
import com.arcadia.lib.ui.PanelProvider;
import com.arcadia.lib.ui.PanelService;
import com.arcadia.lib.ui.PanelSpec;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.dungeon.DungeonInstance;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import com.vyrriox.arcadiadungeon.dungeon.DungeonState;
import com.vyrriox.arcadiadungeon.dungeon.PlayerProgressManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The dungeon list in the Arcadia hub.
 *
 * <p><b>The point of this class.</b> Arcadia Dungeon has no client half and is not getting one:
 * it ships no screen, no renderer, no key binding, and nothing here imports a client type. What
 * it does is describe a panel, which the connected client draws with whatever Arcadia Lib it
 * has. The rules stay where they belong: this builds a description and routes a click, and
 * {@link DungeonManager#startDungeon} and {@link DungeonManager#joinDungeon} decide what
 * actually happens and tell the player when they refuse.</p>
 *
 * <p>The listing therefore never enforces anything. It greys out a row the manager is going to
 * turn down so a player is not invited to click for nothing, and that is a courtesy, not a
 * check: clicking a stale row reaches the manager and the manager says no.</p>
 *
 * @author vyrriox
 */
public final class DungeonsPanel implements PanelProvider {

    /** Card id and server action id the hub knows this panel by. */
    public static final String CARD_ID = "dungeon";
    public static final String OPEN_ACTION = "dungeon.open";

    private static final int BUTTON_CLOSE = -1;

    /** Dungeon ids in the order the last build emitted them; an entry ref indexes this. */
    private final List<String> shown = new ArrayList<>();

    /** Opens the dungeon list for this player. */
    public static void open(ServerPlayer player) {
        PanelService.open(player, new DungeonsPanel());
    }

    @Override
    public PanelSpec build(ServerPlayer player) {
        shown.clear();
        DungeonManager manager = DungeonManager.getInstance();
        PlayerProgressManager progress = PlayerProgressManager.getInstance();
        String uuid = player.getUUID().toString();

        List<DungeonConfig> configs = new ArrayList<>(
                ConfigManager.getInstance().getDungeonConfigs().values());
        configs.removeIf(config -> !config.enabled);
        configs.sort(Comparator.comparingInt((DungeonConfig c) -> c.order)
                .thenComparing(c -> c.name == null ? "" : c.name));

        int cleared = 0;
        for (DungeonConfig config : configs) {
            if (progress.hasCompleted(uuid, config.id)) cleared++;
        }

        // English, like every other panel in the hub. This module ships no language file, so
        // its text can only ever be one language, and sitting in an English hub it was the one
        // panel in French with the accents stripped out of it.
        PanelSpec.Builder builder = PanelSpec
                .builder(Component.literal("Dungeons").withStyle(ChatFormatting.GOLD))
                .subtitle(Component.literal(cleared + " / " + configs.size() + " cleared")
                        .withStyle(ChatFormatting.GRAY));

        for (DungeonConfig config : configs) {
            int ref = shown.size();
            shown.add(config.id);
            builder.entry(entryFor(ref, config, player, manager, progress, uuid));
        }

        // The library's own key rather than a literal: arcadia-lib is on the client whatever
        // this module is, so this one string does follow the player's language.
        builder.button(PanelButton.of(BUTTON_CLOSE,
                Component.translatable("arcadia_lib.panel.close"), PanelButtonStyle.GHOST));
        return builder.build();
    }

    private PanelEntry entryFor(int ref, DungeonConfig config, ServerPlayer player,
                                DungeonManager manager, PlayerProgressManager progress,
                                String uuid) {
        DungeonInstance running = manager.getInstance(config.id);
        boolean completed = progress.hasCompleted(uuid, config.id);
        List<Component> lore = new ArrayList<>(3);
        boolean clickable = true;
        int accent;

        if (running != null) {
            DungeonState state = running.getState();
            if (state == DungeonState.RECRUITING) {
                lore.add(Component.literal("Recruiting: " + running.getPlayerCount()
                                + " player(s), " + running.getRecruitmentRemainingSeconds() + "s")
                        .withStyle(ChatFormatting.GRAY));
                lore.add(Component.literal("Click to join").withStyle(ChatFormatting.GOLD));
                accent = 0xFFD4A847;
            } else {
                lore.add(Component.literal("Running for "
                                + DungeonManager.formatTime(running.getElapsedSeconds()))
                        .withStyle(ChatFormatting.RED));
                clickable = false;
                accent = 0xFFC04040;
            }
        } else {
            long cooldown = manager.getCooldownRemainingSeconds(player.getUUID(), config.id);
            long availability = manager.getAvailabilityRemainingSeconds(config.id);
            String locked = lockedBy(config, progress, uuid);

            if (locked != null) {
                lore.add(Component.literal("Clear " + locked + " first")
                        .withStyle(ChatFormatting.RED));
                clickable = false;
                accent = 0xFF6A5A4A;
            } else if (availability > 0) {
                lore.add(Component.literal("Available in "
                                + DungeonManager.formatTime(availability))
                        .withStyle(ChatFormatting.GRAY));
                clickable = false;
                accent = 0xFF6A5A4A;
            } else if (cooldown > 0) {
                lore.add(Component.literal("Cooldown: "
                                + DungeonManager.formatTime(cooldown))
                        .withStyle(ChatFormatting.GRAY));
                clickable = false;
                accent = 0xFF6A5A4A;
            } else {
                // Gold rather than green. Green is the brightest thing vanilla's palette has and
                // it was the loudest colour on a panel whose whole family is brass; the one line
                // that is a call to action can be the warm accent instead.
                lore.add(Component.literal("Click to start").withStyle(ChatFormatting.GOLD));
                accent = completed ? 0xFF4ECCA3 : 0xFFB87333;
            }
        }

        if (completed) {
            lore.add(Component.literal("Already cleared").withStyle(ChatFormatting.DARK_GRAY));
        }

        PanelEntry entry = new PanelEntry(ref, iconFor(config, running, completed),
                Component.literal(config.name == null || config.name.isEmpty()
                        ? config.id : config.name).withStyle(ChatFormatting.WHITE),
                List.copyOf(lore), Component.empty(), accent, clickable);
        return entry;
    }

    /** The dungeon this one is gated behind, by name, or null when it is open. */
    private static String lockedBy(DungeonConfig config, PlayerProgressManager progress, String uuid) {
        if (config.requiredDungeon == null || config.requiredDungeon.isEmpty()) return null;
        if (progress.hasCompleted(uuid, config.requiredDungeon)) return null;
        DungeonConfig required = ConfigManager.getInstance().getDungeon(config.requiredDungeon);
        return required != null && required.name != null && !required.name.isEmpty()
                ? required.name : config.requiredDungeon;
    }

    private static ItemStack iconFor(DungeonConfig config, DungeonInstance running, boolean completed) {
        if (running != null) return new ItemStack(Items.NETHER_STAR);
        if (completed) return new ItemStack(Items.FILLED_MAP);
        return new ItemStack(Items.IRON_SWORD);
    }

    @Override
    public void onEntry(ServerPlayer player, int ref, int button, Runnable refresh) {
        if (ref < 0 || ref >= shown.size()) return;
        String dungeonId = shown.get(ref);
        DungeonManager manager = DungeonManager.getInstance();
        DungeonInstance running = manager.getInstance(dungeonId);

        // The manager owns every rule and messages the player itself when it refuses, so this
        // does not re-check anything: it picks join or start and lets the manager answer.
        if (running != null && running.getState() == DungeonState.RECRUITING) {
            if (manager.joinDungeon(dungeonId, player)) {
                PanelService.close(player);
                return;
            }
        } else if (running == null) {
            if (manager.startDungeon(dungeonId, player) != null) {
                PanelService.close(player);
                return;
            }
        }
        // Refused, and the player has been told why: show them the list as it is now.
        refresh.run();
    }

    @Override
    public void onButton(ServerPlayer player, int ref, int button, Runnable refresh) {
        if (ref == BUTTON_CLOSE) PanelService.close(player);
    }
}
