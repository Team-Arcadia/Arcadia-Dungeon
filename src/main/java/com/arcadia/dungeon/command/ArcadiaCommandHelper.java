package com.arcadia.dungeon.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.arcadia.dungeon.config.ArcadiaProgressionConfig;
import com.arcadia.dungeon.config.BossConfig;
import com.arcadia.dungeon.config.ConfigManager;
import com.arcadia.dungeon.config.DungeonConfig;
import com.arcadia.dungeon.dungeon.CombatTuning;
import com.arcadia.dungeon.dungeon.PlayerProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class ArcadiaCommandHelper {

    private ArcadiaCommandHelper() {
    }

    public static BossConfig findBoss(CommandContext<CommandSourceStack> ctx, String dungeonId, String bossId) {
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] No se encuentra la mazmorra: " + dungeonId));
            return null;
        }
        for (BossConfig boss : config.bosses) {
            if (boss.id.equals(bossId)) {
                return boss;
            }
        }
        ctx.getSource().sendFailure(Component.literal("[Arcadia] No se encuentra el jefe: " + bossId));
        return null;
    }

    public static DungeonConfig requireDungeon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] No se encuentra la mazmorra: " + dungeonId));
            return null;
        }
        return config;
    }

    public static ArcadiaProgressionConfig.MilestoneReward requireMilestone(CommandContext<CommandSourceStack> ctx, int level) {
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.normalize();
        ArcadiaProgressionConfig.MilestoneReward milestone = config.milestoneRewards.stream()
                .filter(entry -> entry != null && entry.level == level)
                .findFirst()
                .orElse(null);
        if (milestone == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Aucun milestone pour le niveau " + level));
            return null;
        }
        return milestone;
    }

    public static boolean isDungeonUnlocked(PlayerProgress progress, DungeonConfig dungeon) {
        if (progress == null || dungeon == null) {
            return false;
        }
        progress.normalize();
        boolean dungeonRequirementMet = dungeon.requiredDungeon == null || dungeon.requiredDungeon.isEmpty()
                || progress.hasCompleted(dungeon.requiredDungeon);
        boolean levelRequirementMet = dungeon.requiredArcadiaLevel <= 0
                || progress.arcadiaProgress.arcadiaLevel >= dungeon.requiredArcadiaLevel;
        return dungeonRequirementMet && levelRequirementMet;
    }

    public static String formatTime(long seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m " + (seconds % 60) + "s";
        } else if (seconds >= 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }

    public static ChatFormatting parseLegacyColor(String color) {
        if (color == null || color.length() < 2 || color.charAt(0) != '&') {
            return ChatFormatting.GRAY;
        }
        return switch (Character.toLowerCase(color.charAt(1))) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            default -> ChatFormatting.GRAY;
        };
    }

    public static MutableComponent commandButton(String label, String command, boolean run, String hoverText) {
        ClickEvent.Action action = run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND;
        return Component.literal(label).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withBold(true)
                .withClickEvent(new ClickEvent(action, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(hoverText).withStyle(ChatFormatting.GRAY))));
    }

    public static String normalizeLegacyColor(String color) {
        if (color == null || color.isBlank()) {
            return "&7";
        }
        String trimmed = color.trim().toLowerCase();
        if (trimmed.length() == 1) {
            trimmed = "&" + trimmed;
        }
        if (trimmed.length() == 2 && trimmed.charAt(0) == '&') {
            return trimmed;
        }
        return "&7";
    }

    public static Entity findEntityById(MinecraftServer server, int entityId) {
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    public static Double clampSpecialValue(String key, double value) {
        return switch (key) {
            case CombatTuning.KEY_ATTACK_RANGE, CombatTuning.KEY_AGGRO_RANGE -> Math.max(0.0D, value);
            case CombatTuning.KEY_ATTACK_COOLDOWN_MS, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS, CombatTuning.KEY_DODGE_COOLDOWN_MS -> Math.max(0.0D, Math.round(value));
            case CombatTuning.KEY_DODGE_CHANCE -> Math.max(0.0D, Math.min(1.0D, value));
            case CombatTuning.KEY_DODGE_PROJECTILES_ONLY -> value >= 0.5D ? 1.0D : 0.0D;
            default -> null;
        };
    }

    public static void warnIfClamped(String key, double requested, double applied) {
        if (Double.compare(requested, applied) != 0) {
            com.arcadia.dungeon.ArcadiaDungeon.LOGGER.warn("Valor de ajuste de combate fijado en {}: requested={}, applied={}", key, requested, applied);
        }
    }
}
