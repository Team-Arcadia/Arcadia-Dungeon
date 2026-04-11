package com.arcadia.dungeon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.arcadia.dungeon.config.ConfigManager;
import com.arcadia.dungeon.config.DungeonConfig;
import com.arcadia.dungeon.dungeon.CombatTuning;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

public class ArcadiaCommands {

    static final SuggestionProvider<CommandSourceStack> SUGGEST_DUNGEONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    ConfigManager.getInstance().getDungeonConfigs().keySet(), builder
            );

    static final SuggestionProvider<CommandSourceStack> SUGGEST_BOSS_IDS = (ctx, builder) -> {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config != null) {
            return SharedSuggestionProvider.suggest(
                    config.bosses.stream().map(b -> b.id), builder
            );
        }
        return builder.buildFuture();
    };

    static final SuggestionProvider<CommandSourceStack> SUGGEST_OPTIONAL_BOSS_IDS = (ctx, builder) -> {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config != null) {
            return SharedSuggestionProvider.suggest(
                    config.bosses.stream().filter(b -> b.optional).map(b -> b.id), builder
            );
        }
        return builder.buildFuture();
    };

    static final SuggestionProvider<CommandSourceStack> SUGGEST_BOSS_BAR_COLORS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    new String[]{"RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "WHITE", "PINK"}, builder
            );

    static final SuggestionProvider<CommandSourceStack> SUGGEST_REQUIRED_ACTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    new String[]{"NONE", "KILL_SUMMONS", "DESTROY_BLOCK", "INTERACT"}, builder
            );

    static final SuggestionProvider<CommandSourceStack> SUGGEST_EQUIP_SLOTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    new String[]{"mainhand", "offhand", "helmet", "chestplate", "leggings", "boots"}, builder
            );

    static final SuggestionProvider<CommandSourceStack> SUGGEST_ONLINE_PLAYERS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getName().getString()), builder
            );

    static final SuggestionProvider<CommandSourceStack> SUGGEST_ATTRIBUTES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    java.util.stream.Stream.concat(
                            net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.keySet().stream().map(net.minecraft.resources.ResourceLocation::toString),
                            CombatTuning.getSpecialKeys().stream()
                    ),
                    builder
            );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(ArcadiaAdminCommandTree.build());
        dispatcher.register(ArcadiaPlayerCommandTree.build());
        dispatcher.register(ArcadiaOpsCommandTree.build());
    }
}
