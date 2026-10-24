package com.vyrriox.arcadiadungeon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.boss.BossManager;
import com.vyrriox.arcadiadungeon.config.*;
import com.vyrriox.arcadiadungeon.dungeon.CombatTuning;
import com.vyrriox.arcadiadungeon.dungeon.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ArcadiaCommands {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_DUNGEONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    ConfigManager.getInstance().getDungeonConfigs().keySet(), builder
            );

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BOSS_IDS = (ctx, builder) -> {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config != null) {
            return SharedSuggestionProvider.suggest(
                    config.bosses.stream().map(b -> b.id), builder
            );
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_OPTIONAL_BOSS_IDS = (ctx, builder) -> {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config != null) {
            return SharedSuggestionProvider.suggest(
                    config.bosses.stream().filter(b -> b.optional).map(b -> b.id), builder
            );
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BOSS_BAR_COLORS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    new String[]{"RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "WHITE", "PINK"}, builder
            );

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_REQUIRED_ACTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    new String[]{"NONE", "KILL_SUMMONS", "DESTROY_BLOCK", "INTERACT"}, builder
            );

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_EQUIP_SLOTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    new String[]{"mainhand", "offhand", "helmet", "chestplate", "leggings", "boots"}, builder
            );

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ONLINE_PLAYERS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    ctx.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getName().getString()), builder
            );

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ATTRIBUTES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    java.util.stream.Stream.concat(
                            net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.keySet().stream().map(net.minecraft.resources.ResourceLocation::toString),
                            CombatTuning.getSpecialKeys().stream()
                    ),
                    builder
            );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcadia_dungeon")
                .then(Commands.literal("admin")
                .requires(source -> source.hasPermission(2))

                // === CREATE ===
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ArcadiaCommands::createDungeon)
                                )
                        )
                )

                // === DELETE ===
                .then(Commands.literal("delete")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ArcadiaCommands::deleteDungeon)
                        )
                )

                // === LIST ===
                .then(Commands.literal("list")
                        .executes(ArcadiaCommands::listDungeons)
                )

                // === INFO ===
                .then(Commands.literal("info")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ArcadiaCommands::dungeonInfo)
                        )
                )

                // === SETSPAWN ===
                .then(Commands.literal("setspawn")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ArcadiaCommands::setSpawn)
                        )
                )



                // === SETDUNGEONAREA ===
                .then(Commands.literal("setdungeonarea")
                        .then(Commands.literal("pos1")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .executes(ctx -> setDungeonArea(ctx, 1))
                                )
                        )
                        .then(Commands.literal("pos2")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .executes(ctx -> setDungeonArea(ctx, 2))
                                )
                        )
                        .then(Commands.literal("clear")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .executes(ArcadiaCommands::clearDungeonArea)
                                )
                        )
                )

                // === COOLDOWN ===
                .then(Commands.literal("cooldown")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                        .executes(ArcadiaCommands::setCooldown)
                                )
                        )
                )

                // === AVAILABILITY ===
                .then(Commands.literal("availability")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                        .executes(ArcadiaCommands::setAvailability)
                                )
                        )
                )

                // === ANNOUNCE ===
                .then(Commands.literal("announce")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.literal("start")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> setAnnounce(ctx, "start"))
                                        )
                                )
                                .then(Commands.literal("completion")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> setAnnounce(ctx, "completion"))
                                        )
                                )
                                .then(Commands.literal("availability")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> setAnnounce(ctx, "availability"))
                                        )
                                )
                                .then(Commands.literal("availabilitymessage")
                                        .then(Commands.argument("msg", StringArgumentType.greedyString())
                                                .executes(ArcadiaCommands::setAvailabilityMessage)
                                        )
                                )
                        )
                )

                // === SETTINGS ===
                .then(Commands.literal("settings")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.literal("maxplayers")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> setSetting(ctx, "maxplayers"))
                                        )
                                )
                                .then(Commands.literal("minplayers")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> setSetting(ctx, "minplayers"))
                                        )
                                )
                                .then(Commands.literal("recruitment")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setSetting(ctx, "recruitment"))
                                        )
                                )
                                .then(Commands.literal("timelimit")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setSetting(ctx, "timelimit"))
                                        )
                                )
                                .then(Commands.literal("pvp")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setSetting(ctx, "pvp"))
                                        )
                                )
                                .then(Commands.literal("maxdeaths")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setSetting(ctx, "maxdeaths"))
                                        )
                                )
                                .then(Commands.literal("teleportback")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setSetting(ctx, "teleportback"))
                                        )
                                )
                                .then(Commands.literal("wavehealthmultiplier")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> setSettingDouble(ctx, "wavehealthmultiplier"))
                                        )
                                )
                                .then(Commands.literal("wavedamagemultiplier")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> setSettingDouble(ctx, "wavedamagemultiplier"))
                                        )
                                )
                                .then(Commands.literal("wavecountmultiplier")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> setSettingDouble(ctx, "wavecountmultiplier"))
                                        )
                                )
                                .then(Commands.literal("difficultyscaling")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setSetting(ctx, "difficultyscaling"))
                                        )
                                )
                                .then(Commands.literal("antimonopole")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setSetting(ctx, "antimonopole"))
                                        )
                                )
                                .then(Commands.literal("antimonopolethreshold")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                                .executes(ctx -> setSetting(ctx, "antimonopolethreshold"))
                                        )
                                )
                                .then(Commands.literal("blockteleport")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setSetting(ctx, "blockteleport"))
                                        )
                                )
                        )
                )

                // === MESSAGES ===
                .then(Commands.literal("setmessage")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.literal("start")
                                        .then(Commands.argument("msg", StringArgumentType.greedyString())
                                                .executes(ctx -> setDungeonMessage(ctx, "start"))
                                        )
                                )
                                .then(Commands.literal("completion")
                                        .then(Commands.argument("msg", StringArgumentType.greedyString())
                                                .executes(ctx -> setDungeonMessage(ctx, "completion"))
                                        )
                                )
                                .then(Commands.literal("fail")
                                        .then(Commands.argument("msg", StringArgumentType.greedyString())
                                                .executes(ctx -> setDungeonMessage(ctx, "fail"))
                                        )
                                )
                                .then(Commands.literal("recruitment")
                                        .then(Commands.argument("msg", StringArgumentType.greedyString())
                                                .executes(ctx -> setDungeonMessage(ctx, "recruitment"))
                                        )
                                )
                        )
                )

                // === RENAME ===
                .then(Commands.literal("rename")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ArcadiaCommands::renameDungeon)
                                )
                        )
                )

                // === BOSS ===
                .then(Commands.literal("boss")
                        .then(Commands.literal("add")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .then(Commands.argument("entityType", ResourceLocationArgument.id())
                                                        .then(Commands.argument("health", DoubleArgumentType.doubleArg(1))
                                                                .then(Commands.argument("damage", DoubleArgumentType.doubleArg(0))
                                                                        .executes(ArcadiaCommands::addBoss)
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .executes(ArcadiaCommands::removeBoss)
                                        )
                                )
                        )
                        .then(Commands.literal("setname")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ArcadiaCommands::setBossName)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("setspawn")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .executes(ArcadiaCommands::setBossSpawn)
                                        )
                                )
                        )
                        .then(Commands.literal("adaptive")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .then(Commands.argument("healthMult", DoubleArgumentType.doubleArg(0))
                                                                .then(Commands.argument("damageMult", DoubleArgumentType.doubleArg(0))
                                                                        .executes(ArcadiaCommands::setBossAdaptive)
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("bossbar")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("color", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_BAR_COLORS)
                                                        .executes(ArcadiaCommands::setBossBar)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("spawnafterwave")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(0))
                                                        .executes(ArcadiaCommands::setBossSpawnAfterWave)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("optional")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(ArcadiaCommands::setBossOptional)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("spawnchance")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_OPTIONAL_BOSS_IDS)
                                                .then(Commands.argument("chance", DoubleArgumentType.doubleArg(0, 1))
                                                        .executes(ArcadiaCommands::setBossSpawnChance)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("spawnmessage")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("msg", StringArgumentType.greedyString())
                                                        .executes(ArcadiaCommands::setBossSpawnMessage)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("skipmessage")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_OPTIONAL_BOSS_IDS)
                                                .then(Commands.argument("msg", StringArgumentType.greedyString())
                                                        .executes(ArcadiaCommands::setBossSkipMessage)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("spawnatstart")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(ArcadiaCommands::setBossSpawnAtStart)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("requiredkill")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(ArcadiaCommands::setBossRequiredKill)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("sethealth")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("health", DoubleArgumentType.doubleArg(1))
                                                        .executes(ArcadiaCommands::setBossHealth)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("setdamage")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("damage", DoubleArgumentType.doubleArg(0))
                                                        .executes(ArcadiaCommands::setBossDamage)
                                                )
                                        )
                                )
                        )

                        // === BOSS EQUIP ===
                        .then(Commands.literal("equip")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("slot", StringArgumentType.word())
                                                        .suggests(SUGGEST_EQUIP_SLOTS)
                                                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                                                .executes(ArcadiaCommands::setBossEquip)
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("copyequip")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .executes(ArcadiaCommands::copyEquipToBoss)
                                        )
                                )
                        )

                        // === BOSS ATTRIBUTE ===
                        .then(Commands.literal("attribute")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("attribute", ResourceLocationArgument.id())
                                                                .suggests(SUGGEST_ATTRIBUTES)
                                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                                        .executes(ArcadiaCommands::setBossAttribute)
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("attribute", ResourceLocationArgument.id())
                                                                .suggests(SUGGEST_ATTRIBUTES)
                                                                .executes(ArcadiaCommands::removeBossAttribute)
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("list")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .executes(ArcadiaCommands::listBossAttributes)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("combat")
                                .then(Commands.literal("attackrange")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                                .executes(ctx -> setBossCombatValue(ctx, CombatTuning.KEY_ATTACK_RANGE))
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("attackcooldown")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> setBossCombatValue(ctx, CombatTuning.KEY_ATTACK_COOLDOWN_MS))
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("aggrorange")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                                .executes(ctx -> setBossCombatValue(ctx, CombatTuning.KEY_AGGRO_RANGE))
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("projectilecooldown")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> setBossCombatValue(ctx, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS))
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("dodgechance")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1))
                                                                .executes(ctx -> setBossCombatValue(ctx, CombatTuning.KEY_DODGE_CHANCE))
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("dodgecooldown")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> setBossCombatValue(ctx, CombatTuning.KEY_DODGE_COOLDOWN_MS))
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("dodgeprojectilesonly")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                                .executes(ctx -> setBossCombatBool(ctx, CombatTuning.KEY_DODGE_PROJECTILES_ONLY))
                                                        )
                                                )
                                        )
                                )
                        )

                        // === BOSS PHASE ===
                        .then(Commands.literal("phase")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("healthThreshold", DoubleArgumentType.doubleArg(0, 1))
                                                                        .executes(ArcadiaCommands::addPhase)
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .executes(ArcadiaCommands::removePhase)
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("set")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.literal("damage")
                                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                                                .executes(ctx -> setPhaseProperty(ctx, "damage"))
                                                                        )
                                                                )
                                                                .then(Commands.literal("speed")
                                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                                                .executes(ctx -> setPhaseProperty(ctx, "speed"))
                                                                        )
                                                                )
                                                                .then(Commands.literal("action")
                                                                        .then(Commands.argument("action", StringArgumentType.word())
                                                                                .suggests(SUGGEST_REQUIRED_ACTIONS)
                                                                                .executes(ctx -> setPhaseProperty(ctx, "action"))
                                                                        )
                                                                )
                                                                .then(Commands.literal("message")
                                                                        .then(Commands.argument("msg", StringArgumentType.greedyString())
                                                                                .executes(ctx -> setPhaseProperty(ctx, "message"))
                                                                        )
                                                                )
                                                                .then(Commands.literal("invulnerable")
                                                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                                                                .executes(ctx -> setPhaseProperty(ctx, "invulnerable"))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("summon")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("entityType", ResourceLocationArgument.id())
                                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                                                .executes(ArcadiaCommands::addPhaseSummon)
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("summonequip")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                        .then(Commands.argument("slot", StringArgumentType.word())
                                                                                .suggests(SUGGEST_EQUIP_SLOTS)
                                                                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                                                                        .executes(ArcadiaCommands::setSummonEquip)
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        .then(Commands.literal("summonattribute")
                                .then(Commands.literal("set")
                                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                                        .suggests(SUGGEST_DUNGEONS)
                                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                                .suggests(SUGGEST_BOSS_IDS)
                                                                .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                        .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                                .then(Commands.argument("attribute", ResourceLocationArgument.id())
                                                                                        .suggests(SUGGEST_ATTRIBUTES)
                                                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                                                                .executes(ArcadiaCommands::setSummonAttribute)
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                                        .suggests(SUGGEST_DUNGEONS)
                                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                                .suggests(SUGGEST_BOSS_IDS)
                                                                .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                        .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                                .then(Commands.argument("attribute", ResourceLocationArgument.id())
                                                                                        .suggests(SUGGEST_ATTRIBUTES)
                                                                                        .executes(ArcadiaCommands::removeSummonAttribute)
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("list")
                                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                                        .suggests(SUGGEST_DUNGEONS)
                                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                                .suggests(SUGGEST_BOSS_IDS)
                                                                .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                        .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                                .executes(ArcadiaCommands::listSummonAttributes)
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("combat")
                                .then(Commands.literal("attackrange")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                                                .executes(ctx -> setSummonCombatValue(ctx, CombatTuning.KEY_ATTACK_RANGE))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("attackcooldown")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                                                .executes(ctx -> setSummonCombatValue(ctx, CombatTuning.KEY_ATTACK_COOLDOWN_MS))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("aggrorange")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                                                .executes(ctx -> setSummonCombatValue(ctx, CombatTuning.KEY_AGGRO_RANGE))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("projectilecooldown")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                                                .executes(ctx -> setSummonCombatValue(ctx, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("dodgechance")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1))
                                                                                .executes(ctx -> setSummonCombatValue(ctx, CombatTuning.KEY_DODGE_CHANCE))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("dodgecooldown")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                                                .executes(ctx -> setSummonCombatValue(ctx, CombatTuning.KEY_DODGE_COOLDOWN_MS))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("dodgeprojectilesonly")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("bossId", StringArgumentType.word())
                                                        .suggests(SUGGEST_BOSS_IDS)
                                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("summonIndex", IntegerArgumentType.integer(0))
                                                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                                                .executes(ctx -> setSummonCombatBool(ctx, CombatTuning.KEY_DODGE_PROJECTILES_ONLY))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )

                // === PHASE EFFECT + COMMAND ===
                .then(Commands.literal("phaseeffect")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.argument("bossId", StringArgumentType.word())
                                        .suggests(SUGGEST_BOSS_IDS)
                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("effect", ResourceLocationArgument.id())
                                                        .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("amplifier", IntegerArgumentType.integer(0))
                                                                        .executes(ArcadiaCommands::addPhaseEffect)
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("phasecommand")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.argument("bossId", StringArgumentType.word())
                                        .suggests(SUGGEST_BOSS_IDS)
                                        .then(Commands.argument("phaseNum", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("cmd", StringArgumentType.greedyString())
                                                        .executes(ArcadiaCommands::addPhaseCommand)
                                                )
                                        )
                                )
                        )
                )

                // === REWARD ===
                .then(Commands.literal("reward")
                        .then(Commands.literal("add")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("chance", DoubleArgumentType.doubleArg(0, 1))
                                                                        .executes(ArcadiaCommands::addReward)
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("completion")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("chance", DoubleArgumentType.doubleArg(0, 1))
                                                                .executes(ArcadiaCommands::addCompletionReward)
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("clear")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("bossId", StringArgumentType.word())
                                                .suggests(SUGGEST_BOSS_IDS)
                                                .executes(ArcadiaCommands::clearRewards)
                                        )
                                )
                        )
                )

                // === STOP ===
                .then(Commands.literal("stop")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ArcadiaCommands::stopDungeon)
                        )
                )

                // === TP ===
                .then(Commands.literal("tp")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ArcadiaCommands::teleportToDungeon)
                        )
                )

                // === FORCERESET ===
                .then(Commands.literal("forcereset")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ArcadiaCommands::forceReset)
                        )
                )

                // === PROGRESS MANAGEMENT ===
                .then(Commands.literal("progress")
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(SUGGEST_ONLINE_PLAYERS)
                                        .executes(ArcadiaCommands::resetPlayerProgress)
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .executes(ArcadiaCommands::resetPlayerDungeonProgress)
                                        )
                                )
                        )
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(SUGGEST_ONLINE_PLAYERS)
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("completions", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("bestTime", IntegerArgumentType.integer(0))
                                                                .executes(ArcadiaCommands::setPlayerProgress)
                                                        )
                                                )
                                        )
                                )
                        )
                )

                // === WAND ===
                .then(Commands.literal("wand")
                        .executes(ArcadiaCommands::giveAreaWand)
                )
                .then(Commands.literal("wallwand")
                        .executes(ArcadiaCommands::giveWallWand)
                )
                .then(Commands.literal("wand_select")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ArcadiaCommands::wandSelect)
                        )
                )
                .then(Commands.literal("wall_select")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.argument("wallId", StringArgumentType.word())
                                        .executes(ArcadiaCommands::wallSelect)
                                )
                        )
                )

                // === WEEKLY ===
                .then(Commands.literal("weekly")
                        .then(Commands.literal("reward")
                                .then(Commands.argument("top", IntegerArgumentType.integer(1, 3))
                                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                        .executes(ArcadiaCommands::setWeeklyReward)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("resetday")
                                .then(Commands.argument("day", StringArgumentType.word())
                                        .executes(ArcadiaCommands::setWeeklyResetDay)
                                )
                        )
                        .then(Commands.literal("hour")
                                .then(Commands.argument("hour", IntegerArgumentType.integer(0, 23))
                                        .executes(ArcadiaCommands::setWeeklyHour)
                                )
                        )
                        .then(Commands.literal("info")
                                .executes(ArcadiaCommands::weeklyInfo)
                        )
                        .then(Commands.literal("reset")
                                .executes(ArcadiaCommands::weeklyForceReset)
                        )
                )

                // === TIMER WARNINGS ===
                .then(Commands.literal("timerwarnings")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.literal("add")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                                .executes(ArcadiaCommands::addTimerWarning)
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                                .executes(ArcadiaCommands::removeTimerWarning)
                                        )
                                )
                                .then(Commands.literal("list")
                                        .executes(ArcadiaCommands::listTimerWarnings)
                                )
                        )
                )

                // === DEBUG ===
                .then(Commands.literal("debug")
                        .then(Commands.literal("reload")
                                .executes(ArcadiaCommands::debugReload)
                        )
                        .then(Commands.literal("info")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .executes(ArcadiaCommands::debugInfo)
                                )
                        )
                        .then(Commands.literal("active")
                                .executes(ArcadiaCommands::debugActive)
                        )
                        .then(Commands.literal("spawnboss")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .executes(ArcadiaCommands::debugSpawnBoss)
                                )
                        )
                        .then(Commands.literal("skipboss")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .executes(ArcadiaCommands::debugSkipBoss)
                                )
                        )
                        .then(Commands.literal("complete")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .executes(ArcadiaCommands::debugComplete)
                                )
                        )
                        .then(Commands.literal("resetcooldown")
                                .executes(ArcadiaCommands::debugResetCooldown)
                        )
                )

                // === ENABLE / DISABLE ===
                .then(Commands.literal("enable")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ctx -> toggleDungeon(ctx, true))
                        )
                )
                .then(Commands.literal("disable")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ctx -> toggleDungeon(ctx, false))
                        )
                )

                // === PROGRESSION ===
                .then(Commands.literal("progression")
                        .executes(ArcadiaCommands::showProgression)
                )

                // === TOP ===
                .then(Commands.literal("top")
                        .executes(ctx -> showTop(ctx, null))
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ctx -> showTop(ctx, StringArgumentType.getString(ctx, "dungeon")))
                        )
                )

                // === STATS ===
                .then(Commands.literal("stats")
                        .executes(ctx -> showStats(ctx, null))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(SUGGEST_ONLINE_PLAYERS)
                                .executes(ctx -> showStats(ctx, StringArgumentType.getString(ctx, "player")))
                        )
                )

                // === ORDER ===
                .then(Commands.literal("order")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(ArcadiaCommands::setOrder)
                                )
                        )
                )

                // === REQUIRE ===
                .then(Commands.literal("require")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .then(Commands.literal("none")
                                        .executes(ArcadiaCommands::clearRequirement)
                                )
                                .then(Commands.argument("required", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .executes(ArcadiaCommands::setRequirement)
                                )
                        )
                )

                // === WAVE ===
                .then(Commands.literal("wave")
                        .then(Commands.literal("add")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                .executes(ArcadiaCommands::addWave)
                                        )
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                .executes(ArcadiaCommands::removeWave)
                                        )
                                )
                        )
                        .then(Commands.literal("mob")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("entityType", ResourceLocationArgument.id())
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                                .executes(ArcadiaCommands::addWaveMob)
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("setmobpos")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                        .executes(ArcadiaCommands::setWaveMobPos)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("delay")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                                        .executes(ArcadiaCommands::setWaveDelay)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("glowing")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .then(Commands.argument("delaySeconds", IntegerArgumentType.integer(0))
                                                                .executes(ArcadiaCommands::setWaveGlowing)
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("setmob")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                        .then(Commands.literal("health")
                                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1))
                                                                        .executes(ctx -> setWaveMobProp(ctx, "health"))
                                                                )
                                                        )
                                                        .then(Commands.literal("damage")
                                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                                        .executes(ctx -> setWaveMobProp(ctx, "damage"))
                                                                )
                                                        )
                                                        .then(Commands.literal("speed")
                                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                                        .executes(ctx -> setWaveMobProp(ctx, "speed"))
                                                                )
                                                        )
                                                        .then(Commands.literal("count")
                                                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                                                        .executes(ctx -> setWaveMobProp(ctx, "count"))
                                                                )
                                                        )
                                                        .then(Commands.literal("name")
                                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                        .executes(ctx -> setWaveMobProp(ctx, "name"))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("message")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("msg", StringArgumentType.greedyString())
                                                        .executes(ArcadiaCommands::setWaveMessage)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("list")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .executes(ArcadiaCommands::listWaves)
                                )
                        )
                        .then(Commands.literal("equip")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("slot", StringArgumentType.word())
                                                                .suggests(SUGGEST_EQUIP_SLOTS)
                                                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                                                        .executes(ArcadiaCommands::setWaveMobEquip)
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("copyequip")
                                .then(Commands.argument("dungeon", StringArgumentType.word())
                                        .suggests(SUGGEST_DUNGEONS)
                                        .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                        .executes(ArcadiaCommands::copyEquipToWaveMob)
                                                )
                                        )
                                )
                        )
                        // === WAVE MOB ATTRIBUTE ===
                        .then(Commands.literal("attribute")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("attribute", ResourceLocationArgument.id())
                                                                        .suggests(SUGGEST_ATTRIBUTES)
                                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                                                .executes(ArcadiaCommands::setWaveMobAttribute)
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("attribute", ResourceLocationArgument.id())
                                                                        .suggests(SUGGEST_ATTRIBUTES)
                                                                        .executes(ArcadiaCommands::removeWaveMobAttribute)
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("list")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                                .executes(ArcadiaCommands::listWaveMobAttributes)
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("combat")
                                .then(Commands.literal("attackrange")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                                        .executes(ctx -> setWaveCombatValue(ctx, CombatTuning.KEY_ATTACK_RANGE))
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("attackcooldown")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                                        .executes(ctx -> setWaveCombatValue(ctx, CombatTuning.KEY_ATTACK_COOLDOWN_MS))
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("aggrorange")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                                                        .executes(ctx -> setWaveCombatValue(ctx, CombatTuning.KEY_AGGRO_RANGE))
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("projectilecooldown")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                                        .executes(ctx -> setWaveCombatValue(ctx, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS))
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("dodgechance")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1))
                                                                        .executes(ctx -> setWaveCombatValue(ctx, CombatTuning.KEY_DODGE_CHANCE))
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("dodgecooldown")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                                        .executes(ctx -> setWaveCombatValue(ctx, CombatTuning.KEY_DODGE_COOLDOWN_MS))
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("dodgeprojectilesonly")
                                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                                .suggests(SUGGEST_DUNGEONS)
                                                .then(Commands.argument("waveNum", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("mobIndex", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                                        .executes(ctx -> setWaveCombatBool(ctx, CombatTuning.KEY_DODGE_PROJECTILES_ONLY))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        ));

        // === PLAYER COMMANDS (no permission required) ===
        dispatcher.register(Commands.literal("arcadia_dungeon")
                .then(Commands.literal("start")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ArcadiaCommands::startDungeon)
                        )
                )
                .then(Commands.literal("join")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ArcadiaCommands::joinDungeon)
                        )
                )
                .then(Commands.literal("leave")
                        .executes(ArcadiaCommands::leaveDungeon)
                )
                .then(Commands.literal("abandon")
                        .executes(ArcadiaCommands::abandonDungeon)
                )
                .then(Commands.literal("progression")
                        .executes(ArcadiaCommands::showProgression)
                )
                .then(Commands.literal("top")
                        .executes(ctx -> showTop(ctx, null))
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests(SUGGEST_DUNGEONS)
                                .executes(ctx -> showTop(ctx, StringArgumentType.getString(ctx, "dungeon")))
                        )
                )
                .then(Commands.literal("stats")
                        .executes(ctx -> showStats(ctx, null))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(SUGGEST_ONLINE_PLAYERS)
                                .executes(ctx -> showStats(ctx, StringArgumentType.getString(ctx, "player")))
                        )
                )
                .then(Commands.literal("status")
                        .executes(ArcadiaCommands::showStatus)
                )
        );
    }

    // === COMMAND IMPLEMENTATIONS ===

    private static int createDungeon(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        String name = StringArgumentType.getString(ctx, "name");

        if (ConfigManager.getInstance().getDungeon(id) != null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Un donjon avec l'id '" + id + "' existe deja!"));
            return 0;
        }

        DungeonConfig config = new DungeonConfig(id, name);

        // Set spawn to player position if available
        if (ctx.getSource().getPlayer() != null) {
            ServerPlayer player = ctx.getSource().getPlayer();
            config.spawnPoint = new SpawnPointConfig(
                    player.level().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );
        }

        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon '" + name + "' cree avec succes! (id: " + id + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int deleteDungeon(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        if (ConfigManager.getInstance().deleteDungeon(id)) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon '" + id + "' supprime.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
        return 0;
    }

    private static int listDungeons(CommandContext<CommandSourceStack> ctx) {
        Map<String, DungeonConfig> configs = ConfigManager.getInstance().getDungeonConfigs();
        if (configs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Aucun donjon configure.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== Donjons Arcadia ===").withStyle(ChatFormatting.GOLD), false);
        for (DungeonConfig config : configs.values()) {
            String status = config.enabled ? "&aActif" : "&cDesactive";
            DungeonInstance active = DungeonManager.getInstance().getInstance(config.id);
            String running = active != null ? " &e[EN COURS]" : "";
            ctx.getSource().sendSuccess(() -> DungeonManager.parseColorCodes(
                    " &7- &f" + config.name + " &7(id: " + config.id + ") " + status + running
            ), false);
        }
        return 1;
    }

    private static int dungeonInfo(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== " + config.name + " ===").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("ID: " + config.id).withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Cooldown: " + config.cooldownSeconds + "s").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Disponible toutes les: " + (config.availableEverySeconds > 0 ? config.availableEverySeconds + "s" : "toujours")).withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("TP retour: " + (config.teleportBackOnComplete ? "Oui" : "Non")).withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Annonce debut: " + (config.announceStart ? "Oui" : "Non")).withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Annonce fin: " + (config.announceCompletion ? "Oui" : "Non")).withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Joueurs max: " + config.settings.maxPlayers).withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Temps limite: " + config.settings.timeLimitSeconds + "s").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawn: " + config.spawnPoint.dimension + " [" +
                (int) config.spawnPoint.x + ", " + (int) config.spawnPoint.y + ", " + (int) config.spawnPoint.z + "]").withStyle(ChatFormatting.GRAY), false);

        ctx.getSource().sendSuccess(() -> Component.literal("Boss (" + config.bosses.size() + "):").withStyle(ChatFormatting.YELLOW), false);
        for (BossConfig boss : config.bosses) {
            ctx.getSource().sendSuccess(() -> Component.literal("  - " + boss.customName + " (" + boss.entityType + ") HP:" + boss.baseHealth + " DMG:" + boss.baseDamage +
                    " Phases:" + boss.phases.size() + " Rewards:" + boss.rewards.size()).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int setSpawn(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        config.spawnPoint = new SpawnPointConfig(
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        );
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Spawn du donjon defini a votre position!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }



    private static int setCooldown(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.cooldownSeconds = seconds;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Cooldown defini a " + seconds + "s.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setAvailability(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.availableEverySeconds = seconds;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Disponibilite definie a toutes les " + seconds + "s (0 = toujours).")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setAnnounce(CommandContext<CommandSourceStack> ctx, String type) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        if ("start".equals(type)) {
            config.announceStart = enabled;
        } else if ("availability".equals(type)) {
            config.announceAvailability = enabled;
        } else {
            config.announceCompletion = enabled;
        }
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Annonce " + type + " " + (enabled ? "activee" : "desactivee") + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setAvailabilityMessage(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        String msg = StringArgumentType.getString(ctx, "msg");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.availabilityMessage = msg;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Message de disponibilite mis a jour.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setSetting(CommandContext<CommandSourceStack> ctx, String setting) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }

        switch (setting) {
            case "maxplayers" -> config.settings.maxPlayers = IntegerArgumentType.getInteger(ctx, "value");
            case "minplayers" -> config.settings.minPlayers = IntegerArgumentType.getInteger(ctx, "value");
            case "recruitment" -> config.recruitmentDurationSeconds = IntegerArgumentType.getInteger(ctx, "value");
            case "timelimit" -> config.settings.timeLimitSeconds = IntegerArgumentType.getInteger(ctx, "value");
            case "pvp" -> config.settings.pvp = BoolArgumentType.getBool(ctx, "value");
            case "maxdeaths" -> config.settings.maxDeaths = IntegerArgumentType.getInteger(ctx, "value");
            case "teleportback" -> config.teleportBackOnComplete = BoolArgumentType.getBool(ctx, "value");
            case "difficultyscaling" -> config.settings.difficultyScaling = BoolArgumentType.getBool(ctx, "value");
            case "antimonopole" -> config.settings.antiMonopole = BoolArgumentType.getBool(ctx, "value");
            case "antimonopolethreshold" -> config.settings.antiMonopoleThreshold = IntegerArgumentType.getInteger(ctx, "value");
            case "blockteleport" -> config.settings.blockTeleportCommands = BoolArgumentType.getBool(ctx, "value");
        }

        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Parametre " + setting + " mis a jour.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setSettingDouble(CommandContext<CommandSourceStack> ctx, String setting) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        double value = DoubleArgumentType.getDouble(ctx, "value");

        switch (setting) {
            case "wavehealthmultiplier" -> config.settings.waveHealthMultiplierPerPlayer = value;
            case "wavedamagemultiplier" -> config.settings.waveDamageMultiplierPerPlayer = value;
            case "wavecountmultiplier" -> config.settings.waveCountMultiplierPerPlayer = value;
        }

        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Parametre " + setting + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === BOSS COMMANDS ===

    private static int addBoss(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String entityType = ResourceLocationArgument.getId(ctx, "entityType").toString();
        double health = DoubleArgumentType.getDouble(ctx, "health");
        double damage = DoubleArgumentType.getDouble(ctx, "damage");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        for (BossConfig existing : config.bosses) {
            if (existing.id.equals(bossId)) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Boss '" + bossId + "' existe deja!"));
                return 0;
            }
        }

        BossConfig boss = new BossConfig(bossId, entityType, health, damage);
        boss.customName = bossId;

        if (ctx.getSource().getPlayer() != null) {
            ServerPlayer player = ctx.getSource().getPlayer();
            boss.spawnPoint = new SpawnPointConfig(
                    player.level().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );
        }

        // Add default phase 1
        PhaseConfig phase1 = new PhaseConfig(1, 1.0);
        phase1.description = "Phase initiale";
        boss.phases.add(phase1);

        config.bosses.add(boss);
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss '" + bossId + "' ajoute! (" + entityType + " HP:" + health + " DMG:" + damage + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeBoss(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        boolean removed = config.bosses.removeIf(b -> b.id.equals(bossId));
        if (removed) {
            ConfigManager.getInstance().saveDungeon(config);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss '" + bossId + "' supprime.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("[Arcadia] Boss introuvable: " + bossId));
        return 0;
    }

    private static int setBossName(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String name = StringArgumentType.getString(ctx, "name");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.customName = name;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Nom du boss defini: " + name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossSpawn(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        boss.spawnPoint = new SpawnPointConfig(
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        );
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Spawn du boss defini a votre position!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossAdaptive(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        double healthMult = DoubleArgumentType.getDouble(ctx, "healthMult");
        double damageMult = DoubleArgumentType.getDouble(ctx, "damageMult");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.adaptivePower = enabled;
        boss.healthMultiplierPerPlayer = healthMult;
        boss.damageMultiplierPerPlayer = damageMult;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Puissance adaptive: " + (enabled ? "Oui" : "Non") +
                " (HP x" + healthMult + "/joueur, DMG x" + damageMult + "/joueur)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossBar(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String color = StringArgumentType.getString(ctx, "color");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.showBossBar = true;
        boss.bossBarColor = color.toUpperCase();
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Barre de boss couleur: " + color)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossSpawnAfterWave(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.spawnAfterWave = waveNum;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        if (waveNum == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : spawn apres toutes les vagues (defaut)")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : spawn apres la vague " + waveNum)
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return 1;
    }

    private static int setBossOptional(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.optional = enabled;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " optionnel: " + (enabled ? "Oui" : "Non"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossSpawnAtStart(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.spawnAtStart = enabled;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " spawn au debut: " + (enabled ? "Oui" : "Non"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossRequiredKill(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.requiredKill = enabled;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " kill obligatoire: " + (enabled ? "Oui" : "Non"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossSpawnMessage(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String msg = StringArgumentType.getString(ctx, "msg");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.spawnMessage = msg;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : message de spawn mis a jour")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossSkipMessage(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String msg = StringArgumentType.getString(ctx, "msg");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.skipMessage = msg;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : message de skip mis a jour")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossSpawnChance(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        double chance = DoubleArgumentType.getDouble(ctx, "chance");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.spawnChance = chance;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " chance de spawn: " + (int)(chance * 100) + "%")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossHealth(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        double health = DoubleArgumentType.getDouble(ctx, "health");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.baseHealth = health;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : vie de base = " + health)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossDamage(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        double damage = DoubleArgumentType.getDouble(ctx, "damage");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.baseDamage = damage;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : degats de base = " + damage)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === DUNGEON MESSAGES ===

    private static int setDungeonMessage(CommandContext<CommandSourceStack> ctx, String type) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        String msg = StringArgumentType.getString(ctx, "msg");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        switch (type) {
            case "start" -> config.startMessage = msg;
            case "completion" -> config.completionMessage = msg;
            case "fail" -> config.failMessage = msg;
            case "recruitment" -> config.recruitmentMessage = msg;
        }
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Message " + type + " mis a jour.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int renameDungeon(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        String name = StringArgumentType.getString(ctx, "name");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.name = name;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon renomme: " + name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === WAVE CONFIG COMMANDS ===

    private static int setWaveDelay(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId)); return 0; }
        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum)); return 0; }

        wave.delayBeforeSeconds = seconds;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vague " + waveNum + " : delai = " + seconds + "s")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setWaveGlowing(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        int delay = IntegerArgumentType.getInteger(ctx, "delaySeconds");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId)); return 0; }
        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum)); return 0; }

        wave.glowingAfterDelay = enabled;
        wave.glowingDelaySeconds = delay;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vague " + waveNum + " : glowing " + (enabled ? "actif apres " + delay + "s" : "desactive"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setWaveMobProp(CommandContext<CommandSourceStack> ctx, String prop) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId)); return 0; }
        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum)); return 0; }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex)); return 0; }

        MobSpawnConfig mob = wave.mobs.get(mobIndex);
        String display;
        switch (prop) {
            case "health" -> { mob.health = DoubleArgumentType.getDouble(ctx, "value"); display = "vie = " + mob.health; }
            case "damage" -> { mob.damage = DoubleArgumentType.getDouble(ctx, "value"); display = "degats = " + mob.damage; }
            case "speed" -> { mob.speed = DoubleArgumentType.getDouble(ctx, "value"); display = "vitesse = " + mob.speed; }
            case "count" -> { mob.count = IntegerArgumentType.getInteger(ctx, "value"); display = "nombre = " + mob.count; }
            case "name" -> { mob.customName = StringArgumentType.getString(ctx, "name"); display = "nom = " + mob.customName; }
            default -> { return 0; }
        }

        ConfigManager.getInstance().saveDungeon(config);
        final String d = display;
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : " + d)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === PHASE COMMANDS ===

    private static int addPhase(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        double healthThreshold = DoubleArgumentType.getDouble(ctx, "healthThreshold");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = new PhaseConfig(phaseNum, healthThreshold);
        phase.description = "Phase " + phaseNum;
        boss.phases.add(phase);
        boss.phases.sort((a, b) -> Double.compare(b.healthThreshold, a.healthThreshold));

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Phase " + phaseNum + " ajoutee (seuil: " + (healthThreshold * 100) + "% HP)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removePhase(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boolean removed = boss.phases.removeIf(p -> p.phase == phaseNum);
        if (removed) {
            ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Phase " + phaseNum + " supprimee.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
        return 0;
    }

    private static int setPhaseProperty(CommandContext<CommandSourceStack> ctx, String property) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }

        switch (property) {
            case "damage" -> phase.damageMultiplier = DoubleArgumentType.getDouble(ctx, "value");
            case "speed" -> phase.speedMultiplier = DoubleArgumentType.getDouble(ctx, "value");
            case "action" -> phase.requiredAction = StringArgumentType.getString(ctx, "action");
            case "message" -> phase.phaseStartMessage = StringArgumentType.getString(ctx, "msg");
            case "invulnerable" -> {
                int seconds = IntegerArgumentType.getInteger(ctx, "ticks");
                phase.invulnerableDuringTransition = seconds > 0;
                phase.transitionDurationSeconds = seconds;
            }
        }

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Phase " + phaseNum + " " + property + " mis a jour.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int addPhaseSummon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        String entityType = ResourceLocationArgument.getId(ctx, "entityType").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }

        phase.summonMobs.add(new SummonConfig(entityType, count));
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Invocation ajoutee: " + count + "x " + entityType + " en phase " + phaseNum)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === REWARD COMMANDS ===

    private static int addReward(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String item = ResourceLocationArgument.getId(ctx, "item").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        double chance = DoubleArgumentType.getDouble(ctx, "chance");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.rewards.add(new RewardConfig(item, count, chance));
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Recompense ajoutee: " + count + "x " + item + " (" + (chance * 100) + "%)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int addCompletionReward(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String item = ResourceLocationArgument.getId(ctx, "item").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        double chance = DoubleArgumentType.getDouble(ctx, "chance");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        config.completionRewards.add(new RewardConfig(item, count, chance));
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Recompense de completion ajoutee: " + count + "x " + item + " (" + (chance * 100) + "%)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearRewards(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.rewards.clear();
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Recompenses du boss " + bossId + " effacees.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === DUNGEON ACTIONS ===

    private static int startDungeon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        DungeonInstance instance = DungeonManager.getInstance().startDungeon(dungeonId, player);
        if (instance != null) {
            // If recruiting, waves/bosses start after recruitment ends (event handler)
            if (instance.getState() != DungeonState.RECRUITING) {
                if (!instance.hasWaves()) {
                    BossManager.getInstance().spawnNextBoss(instance);
                }
                // Waves are started automatically by the event handler
            }
            return 1;
        }
        return 0;
    }

    private static int joinDungeon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        return DungeonManager.getInstance().joinDungeon(dungeonId, player) ? 1 : 0;
    }

    private static int stopDungeon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonManager.getInstance().stopDungeon(dungeonId);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon '" + dungeonId + "' arrete.")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int teleportToDungeon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        DungeonManager.getInstance().teleportToSpawn(player, config.spawnPoint);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Teleporte au donjon " + config.name + "!")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int leaveDungeon(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vous n'etes dans aucun donjon!"));
            return 0;
        }

        DungeonManager.getInstance().removePlayerFromDungeon(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vous avez quitte le donjon.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int toggleDungeon(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.enabled = enabled;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon " + (enabled ? "active" : "desactive") + ": " + config.name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === ABANDON (player, no permission) ===

    private static int abandonDungeon(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vous n'etes dans aucun donjon!"));
            return 0;
        }

        String dungeonName = instance.getConfig().name;
        DungeonManager.getInstance().removePlayerFromDungeon(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vous avez abandonne " + dungeonName + ".")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    // === STATUS (player) ===

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        String uuid = player.getUUID().toString();
        PlayerProgress progress = PlayerProgressManager.getInstance()
                .getOrCreate(uuid, player.getName().getString());

        ctx.getSource().sendSuccess(() -> Component.literal("========= Statut Arcadia =========").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        // Current dungeon
        DungeonInstance currentInstance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (currentInstance != null) {
            var cfg = currentInstance.getConfig();
            String state = switch (currentInstance.getState()) {
                case RECRUITING -> "Recrutement (" + currentInstance.getRecruitmentRemainingSeconds() + "s)";
                case ACTIVE -> "En cours";
                case BOSS_FIGHT -> "Combat de boss";
                case WAITING -> "Attente";
                default -> currentInstance.getState().toString();
            };
            int lives = currentInstance.getRemainingLives(player.getUUID());
            long elapsed = currentInstance.getElapsedSeconds();
            ctx.getSource().sendSuccess(() -> Component.literal(" Donjon actuel: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(cfg.name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), false);
            ctx.getSource().sendSuccess(() -> Component.literal("   Etat: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(state).withStyle(ChatFormatting.WHITE)), false);
            ctx.getSource().sendSuccess(() -> Component.literal("   Temps: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(DungeonManager.formatTime(elapsed)).withStyle(ChatFormatting.WHITE))
                    .append(cfg.settings.timeLimitSeconds > 0 ?
                            Component.literal(" / " + DungeonManager.formatTime(cfg.settings.timeLimitSeconds)).withStyle(ChatFormatting.GRAY) :
                            Component.literal(" (illimite)").withStyle(ChatFormatting.GRAY)), false);
            ctx.getSource().sendSuccess(() -> Component.literal("   Vies: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(cfg.settings.maxDeaths > 0 ? lives + "/" + cfg.settings.maxDeaths : "illimitees")
                            .withStyle(lives <= 1 && cfg.settings.maxDeaths > 0 ? ChatFormatting.RED : ChatFormatting.GREEN)), false);
            ctx.getSource().sendSuccess(() -> Component.literal("   Joueurs: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(currentInstance.getPlayerCount() + "/" + cfg.settings.maxPlayers).withStyle(ChatFormatting.WHITE)), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(" Donjon actuel: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Aucun").withStyle(ChatFormatting.WHITE)), false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.RESET), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" Donjons:").withStyle(ChatFormatting.YELLOW), false);

        for (var entry : ConfigManager.getInstance().getDungeonConfigs().entrySet()) {
            DungeonConfig cfg = entry.getValue();
            if (!cfg.enabled) continue;

            boolean unlocked = cfg.requiredDungeon == null || cfg.requiredDungeon.isEmpty()
                    || progress.hasCompleted(cfg.requiredDungeon);
            boolean completed = progress.hasCompleted(cfg.id);

            DungeonInstance active = DungeonManager.getInstance().getInstance(cfg.id);

            String status;
            ChatFormatting color;

            if (!unlocked) {
                DungeonConfig req = ConfigManager.getInstance().getDungeon(cfg.requiredDungeon);
                status = "Verrouille (Requis: " + (req != null ? req.name : cfg.requiredDungeon) + ")";
                color = ChatFormatting.RED;
            } else if (active != null) {
                status = active.getState() == DungeonState.RECRUITING ?
                        "Recrutement (" + active.getRecruitmentRemainingSeconds() + "s)" :
                        "En cours (" + active.getPlayerCount() + " joueurs)";
                color = ChatFormatting.AQUA;
            } else {
                status = "Disponible";
                color = ChatFormatting.GREEN;
                if (completed) {
                    PlayerProgress.DungeonProgress dp = progress.completedDungeons.get(cfg.id);
                    if (dp != null) {
                        status = dp.completions + "x | Record: " + formatTime(dp.bestTimeSeconds);
                        color = ChatFormatting.GREEN;
                    }
                }
            }

            final String fStatus = status;
            final ChatFormatting fColor = color;
            ctx.getSource().sendSuccess(() -> Component.literal("   " + cfg.name + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(fStatus).withStyle(fColor)), false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.RESET), false);
        int totalCompletions = progress.getTotalCompletions();
        int dungeonsCompleted = progress.completedDungeons.size();
        ctx.getSource().sendSuccess(() -> Component.literal(" Total: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(totalCompletions + " completions").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + dungeonsCompleted + " donjons)").withStyle(ChatFormatting.GRAY)), false);

        var weeklyData = WeeklyLeaderboard.getInstance().getData();
        int weeklyCount = weeklyData.playerCompletions.getOrDefault(uuid, 0);
        ctx.getSource().sendSuccess(() -> Component.literal(" Cette semaine: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(weeklyCount + " completions").withStyle(ChatFormatting.YELLOW)), false);

        ctx.getSource().sendSuccess(() -> Component.literal("===================================").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    // === FORCERESET ===

    private static int forceReset(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Aucune instance active: " + dungeonId));
            return 0;
        }
        DungeonManager.getInstance().forceReset(dungeonId);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon " + dungeonId + " reinitialise. Joueurs expulses, mobs supprimes.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === PROGRESS MANAGEMENT ===

    private static int resetPlayerProgress(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerName);
        if (progress == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
            return 0;
        }
        PlayerProgressManager.getInstance().resetPlayer(progress.uuid);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Progression de " + playerName + " entierement reinitialisee.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetPlayerDungeonProgress(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerName);
        if (progress == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
            return 0;
        }
        PlayerProgressManager.getInstance().resetPlayerDungeon(progress.uuid, dungeonId);
        DungeonConfig dc = ConfigManager.getInstance().getDungeon(dungeonId);
        String dName = dc != null ? dc.name : dungeonId;
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Progression de " + playerName + " pour " + dName + " reinitialisee.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setPlayerProgress(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int completions = IntegerArgumentType.getInteger(ctx, "completions");
        int bestTime = IntegerArgumentType.getInteger(ctx, "bestTime");

        // Find or create player progress
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerName);
        String uuid;
        if (progress != null) {
            uuid = progress.uuid;
        } else {
            // Try to find online player
            if (ctx.getSource().getServer() != null) {
                ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
                if (target != null) {
                    uuid = target.getUUID().toString();
                } else {
                    ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
                    return 0;
                }
            } else {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
                return 0;
            }
        }

        PlayerProgressManager.getInstance().setPlayerDungeon(uuid, playerName, dungeonId, completions, bestTime);
        DungeonConfig dc = ConfigManager.getInstance().getDungeon(dungeonId);
        String dName = dc != null ? dc.name : dungeonId;
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] " + playerName + " -> " + dName + ": " + completions + " completions, record " + formatTime(bestTime) + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === DEBUG COMMANDS ===

    private static int debugReload(CommandContext<CommandSourceStack> ctx) {
        ConfigManager.getInstance().loadAll();
        PlayerProgressManager.getInstance().loadAll();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Configs rechargees! " +
                ConfigManager.getInstance().getDungeonConfigs().size() + " donjon(s), " +
                PlayerProgressManager.getInstance().getAll().size() + " joueur(s).")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int debugInfo(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);

        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia Debug] Aucune instance active pour: " + dungeonId));
            return 0;
        }

        // Snapshot values to avoid lambda issues
        String state = instance.getState().toString();
        int playerCount = instance.getPlayerCount();
        long elapsed = instance.getElapsedSeconds();
        int bossCount = instance.getActiveBosses().size();
        int bossIndex = instance.getCurrentBossIndex();
        int totalBosses = instance.getConfig().bosses.size();
        boolean wavesComplete = instance.areWavesCompleted();
        int waveIndex = instance.getCurrentWaveIndex();
        int totalWaves = instance.getWaveInstances().size();

        ctx.getSource().sendSuccess(() -> Component.literal("=== Debug " + dungeonId + " ===").withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Etat: " + state).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Joueurs: " + playerCount).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Temps ecoule: " + formatTime(elapsed)).withStyle(ChatFormatting.WHITE), false);

        // Wave info
        if (totalWaves > 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("  Vagues: " + (wavesComplete ? "TERMINEES" : (waveIndex + 1) + "/" + totalWaves))
                    .withStyle(wavesComplete ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
            if (!wavesComplete) {
                WaveInstance currentWave = instance.getCurrentWave();
                if (currentWave != null) {
                    int remaining = currentWave.getRemainingMobs();
                    ctx.getSource().sendSuccess(() -> Component.literal("    Vague actuelle: " + remaining + " mob(s) restant(s)")
                            .withStyle(ChatFormatting.GRAY), false);
                }
            }
        }

        // Boss info
        ctx.getSource().sendSuccess(() -> Component.literal("  Boss: " + bossIndex + "/" + totalBosses + " (actifs: " + bossCount + ")")
                .withStyle(ChatFormatting.WHITE), false);

        for (Map.Entry<String, BossInstance> entry : new ArrayList<>(instance.getActiveBosses().entrySet())) {
            BossInstance boss = entry.getValue();
            String bossId = entry.getKey();
            boolean alive = boss.isBossAlive();
            int phase = boss.getCurrentPhase() + 1;
            String hp = "";
            if (boss.getBossEntity() != null) {
                hp = " HP: " + String.format("%.1f", boss.getBossEntity().getHealth()) + "/" + String.format("%.1f", boss.getBossEntity().getMaxHealth());
            }
            boolean trans = boss.isTransitioning();
            boolean needKill = boss.requiresKillSummons();
            String finalHp = hp;
            ctx.getSource().sendSuccess(() -> Component.literal("    " + bossId + ": " + (alive ? "Vivant" : "Mort") + " Phase:" + phase + finalHp
                    + (trans ? " [TRANSITION]" : "") + (needKill ? " [KILL SUMMONS]" : ""))
                    .withStyle(alive ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        }

        return 1;
    }

    private static int debugActive(CommandContext<CommandSourceStack> ctx) {
        Map<String, DungeonInstance> active = DungeonManager.getInstance().getActiveInstances();
        if (active.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Aucun donjon actif.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== Donjons Actifs ===").withStyle(ChatFormatting.AQUA), false);
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(active.entrySet())) {
            String key = entry.getKey();
            DungeonInstance inst = entry.getValue();
            String st = inst.getState().toString();
            int pc = inst.getPlayerCount();
            long el = inst.getElapsedSeconds();
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + key + " | Etat: " + st +
                    " | Joueurs: " + pc + " | Temps: " + formatTime(el))
                    .withStyle(ChatFormatting.WHITE), false);
        }
        return 1;
    }

    private static int debugSpawnBoss(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia Debug] Aucune instance active: " + dungeonId));
            return 0;
        }
        boolean spawned = BossManager.getInstance().spawnNextBoss(instance);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss spawn: " + (spawned ? "Oui" : "Non (plus de boss)"))
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int debugSkipBoss(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia Debug] Aucune instance active: " + dungeonId));
            return 0;
        }

        if (instance.getActiveBosses().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia Debug] Aucun boss actif a skip!"));
            return 0;
        }

        boolean skippedInterWaveBoss = instance.isWaitingForInterWaveBoss();

        for (Map.Entry<String, BossInstance> entry : new ArrayList<>(instance.getActiveBosses().entrySet())) {
            BossInstance boss = entry.getValue();
            String bossId = entry.getKey();

            // Give boss rewards to all players
            for (java.util.UUID playerId : instance.getPlayers()) {
                ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    instance.giveRewards(player, boss.getConfig().rewards);
                    player.sendSystemMessage(Component.literal("[Arcadia Debug] Boss " + boss.getConfig().customName + " skip!")
                            .withStyle(ChatFormatting.AQUA));
                }
            }

            boss.cleanup();
            instance.removeBossInstance(bossId);
        }

        if (skippedInterWaveBoss) {
            instance.setWaitingForInterWaveBoss(false);
            instance.setState(DungeonState.ACTIVE);
            if (instance.areWavesCompleted()) {
                if (instance.hasNextBoss()) {
                    BossManager.getInstance().spawnNextBoss(instance);
                    ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss inter-vague skip, progression reprise.")
                            .withStyle(ChatFormatting.AQUA), false);
                } else if (instance.allRequiredBossesDefeated()) {
                    DungeonManager.getInstance().completeDungeon(dungeonId);
                    ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss inter-vague skip, donjon termine!")
                            .withStyle(ChatFormatting.AQUA), false);
                }
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss inter-vague skip, vague suivante reprise.")
                        .withStyle(ChatFormatting.AQUA), false);
            }
        } else if (instance.hasNextBoss()) {
            BossManager.getInstance().spawnNextBoss(instance);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss skip, prochain boss spawn!")
                    .withStyle(ChatFormatting.AQUA), false);
        } else if (instance.allRequiredBossesDefeated() && (!instance.hasWaves() || instance.areWavesCompleted())) {
            DungeonManager.getInstance().completeDungeon(dungeonId);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss skip, donjon termine!")
                    .withStyle(ChatFormatting.AQUA), false);
        } else {
            instance.setState(DungeonState.ACTIVE);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss skip, progression du donjon reprise.")
                    .withStyle(ChatFormatting.AQUA), false);
        }

        return 1;
    }

    private static int debugComplete(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia Debug] Aucune instance active: " + dungeonId));
            return 0;
        }
        DungeonManager.getInstance().completeDungeon(dungeonId);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Donjon " + dungeonId + " force complete! Mobs nettoyes, joueurs TP.")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int debugResetCooldown(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        DungeonManager.getInstance().clearCooldown(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Tous vos cooldowns ont ete reset!")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    // === PROGRESSION COMMANDS ===

    private static int showProgression(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        PlayerProgress progress = PlayerProgressManager.getInstance()
                .getOrCreate(player.getUUID().toString(), player.getName().getString());

        List<DungeonConfig> dungeons = new ArrayList<>(ConfigManager.getInstance().getDungeonConfigs().values());
        dungeons.sort(Comparator.comparingInt(d -> d.order));

        ctx.getSource().sendSuccess(() -> Component.literal("").append(
                Component.literal("========= Donjons Arcadia =========").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        ), false);

        for (DungeonConfig dungeon : dungeons) {
            if (!dungeon.enabled) continue;

            boolean completed = progress.hasCompleted(dungeon.id);
            boolean unlocked = dungeon.requiredDungeon == null || dungeon.requiredDungeon.isEmpty()
                    || progress.hasCompleted(dungeon.requiredDungeon);

            PlayerProgress.DungeonProgress dp = progress.completedDungeons.get(dungeon.id);

            // Build progress bar
            String bar;
            ChatFormatting color;
            String status;
            String details;

            if (completed) {
                bar = "##########";
                color = ChatFormatting.GREEN;
                status = "COMPLETE";
                details = dp.completions + "x | Record: " + formatTime(dp.bestTimeSeconds);
            } else if (unlocked) {
                bar = ">>--------";
                color = ChatFormatting.YELLOW;
                status = "DISPONIBLE";
                details = "Cliquez pour entrer!";
            } else {
                bar = "----------";
                color = ChatFormatting.RED;
                status = "VERROUILLE";
                DungeonConfig req = ConfigManager.getInstance().getDungeon(dungeon.requiredDungeon);
                details = "Requis: " + (req != null ? req.name : dungeon.requiredDungeon);
            }

            MutableComponent line = Component.literal(" ");
            line.append(Component.literal("[" + bar + "] ").withStyle(color));
            line.append(Component.literal(dungeon.order + ". ").withStyle(ChatFormatting.WHITE));
            line.append(Component.literal(dungeon.name).withStyle(color, ChatFormatting.BOLD));
            line.append(Component.literal(" ").withStyle(ChatFormatting.RESET));
            line.append(Component.literal("[" + status + "]").withStyle(color));

            // Add click event if unlocked
            if (unlocked && !completed) {
                line.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arcadia_dungeon start " + dungeon.id))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Cliquez pour lancer " + dungeon.name)))
                );
            } else if (completed) {
                line.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arcadia_dungeon start " + dungeon.id))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Relancer " + dungeon.name + "\n" + details)))
                );
            } else {
                line.withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(details)))
                );
            }

            ctx.getSource().sendSuccess(() -> line, false);

            // Details line
            MutableComponent detailLine = Component.literal("   ");
            detailLine.append(Component.literal(details).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            ctx.getSource().sendSuccess(() -> detailLine, false);
        }

        // Total stats
        int total = progress.getTotalCompletions();
        int completed = (int) dungeons.stream().filter(d -> d.enabled && progress.hasCompleted(d.id)).count();
        int totalEnabled = (int) dungeons.stream().filter(d -> d.enabled).count();

        ctx.getSource().sendSuccess(() -> Component.literal("====================================").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" Progression: " + completed + "/" + totalEnabled + " donjons | " + total + " completions totales")
                .withStyle(ChatFormatting.AQUA), false);

        return 1;
    }

    private static int showTop(CommandContext<CommandSourceStack> ctx, String dungeonId) {
        if (dungeonId != null) {
            // Top for specific dungeon
            DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
            if (config == null) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
                return 0;
            }

            List<Map.Entry<PlayerProgress, PlayerProgress.DungeonProgress>> top =
                    PlayerProgressManager.getInstance().getTopForDungeon(dungeonId, 10);

            ctx.getSource().sendSuccess(() -> Component.literal("=== Top " + config.name + " ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

            if (top.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("Aucune completion enregistree.").withStyle(ChatFormatting.GRAY), false);
                return 1;
            }

            for (int i = 0; i < top.size(); i++) {
                final int rank = i + 1;
                Map.Entry<PlayerProgress, PlayerProgress.DungeonProgress> entry = top.get(i);
                PlayerProgress pp = entry.getKey();
                PlayerProgress.DungeonProgress dp = entry.getValue();

                ChatFormatting rankColor = rank <= 3 ? (rank == 1 ? ChatFormatting.GOLD : rank == 2 ? ChatFormatting.GRAY : ChatFormatting.RED) : ChatFormatting.WHITE;

                ctx.getSource().sendSuccess(() -> Component.literal(" " + rank + ". ")
                        .withStyle(rankColor, ChatFormatting.BOLD)
                        .append(Component.literal(pp.playerName).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(formatTime(dp.bestTimeSeconds)).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" (" + dp.completions + "x)").withStyle(ChatFormatting.GRAY))
                , false);
            }
        } else {
            // Global top
            List<PlayerProgress> top = PlayerProgressManager.getInstance().getTopPlayers(10);

            ctx.getSource().sendSuccess(() -> Component.literal("=== Top Joueurs Arcadia ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

            if (top.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("Aucune completion enregistree.").withStyle(ChatFormatting.GRAY), false);
                return 1;
            }

            for (int i = 0; i < top.size(); i++) {
                final int rank = i + 1;
                PlayerProgress pp = top.get(i);

                ChatFormatting rankColor = rank <= 3 ? (rank == 1 ? ChatFormatting.GOLD : rank == 2 ? ChatFormatting.GRAY : ChatFormatting.RED) : ChatFormatting.WHITE;

                ctx.getSource().sendSuccess(() -> Component.literal(" " + rank + ". ")
                        .withStyle(rankColor, ChatFormatting.BOLD)
                        .append(Component.literal(pp.playerName).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(pp.getTotalCompletions() + " completions").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" (" + pp.completedDungeons.size() + " donjons)").withStyle(ChatFormatting.GRAY))
                , false);
            }
        }
        return 1;
    }

    private static int showStats(CommandContext<CommandSourceStack> ctx, String playerName) {
        PlayerProgress progress;

        if (playerName != null) {
            progress = PlayerProgressManager.getInstance().findByName(playerName);
            if (progress == null) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
                return 0;
            }
        } else {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
                return 0;
            }
            progress = PlayerProgressManager.getInstance()
                    .getOrCreate(player.getUUID().toString(), player.getName().getString());
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== Stats: " + progress.playerName + " ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Completions totales: " + progress.getTotalCompletions()).withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Donjons completes: " + progress.completedDungeons.size()).withStyle(ChatFormatting.AQUA), false);

        if (!progress.completedDungeons.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Details:").withStyle(ChatFormatting.YELLOW), false);
            for (Map.Entry<String, PlayerProgress.DungeonProgress> entry : progress.completedDungeons.entrySet()) {
                String dId = entry.getKey();
                PlayerProgress.DungeonProgress dp = entry.getValue();
                DungeonConfig dc = ConfigManager.getInstance().getDungeon(dId);
                String dName = dc != null ? dc.name : dId;

                ctx.getSource().sendSuccess(() -> Component.literal("  - " + dName + ": ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(dp.completions + "x").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" | Record: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(formatTime(dp.bestTimeSeconds)).withStyle(ChatFormatting.GREEN))
                , false);
            }
        }

        return 1;
    }

    private static int setOrder(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        int order = IntegerArgumentType.getInteger(ctx, "number");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.order = order;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Ordre du donjon defini a " + order + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setRequirement(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        String required = StringArgumentType.getString(ctx, "required");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        if (ConfigManager.getInstance().getDungeon(required) == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon requis introuvable: " + required));
            return 0;
        }

        config.requiredDungeon = required;
        ConfigManager.getInstance().saveDungeon(config);
        DungeonConfig req = ConfigManager.getInstance().getDungeon(required);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] " + config.name + " necessite maintenant: " + req.name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clearRequirement(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.requiredDungeon = "";
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Prerequis supprime pour " + config.name + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === WAVE COMMANDS ===

    private static int addWave(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        for (WaveConfig wave : config.waves) {
            if (wave.waveNumber == waveNum) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague " + waveNum + " existe deja!"));
                return 0;
            }
        }

        WaveConfig wave = new WaveConfig(waveNum);
        config.waves.add(wave);
        config.waves.sort(Comparator.comparingInt(w -> w.waveNumber));
        ConfigManager.getInstance().saveDungeon(config);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vague " + waveNum + " ajoutee au donjon " + config.name + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeWave(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        boolean removed = config.waves.removeIf(w -> w.waveNumber == waveNum);
        if (removed) {
            ConfigManager.getInstance().saveDungeon(config);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vague " + waveNum + " supprimee.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
        return 0;
    }

    private static int addWaveMob(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        String entityType = ResourceLocationArgument.getId(ctx, "entityType").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }

        MobSpawnConfig mob = new MobSpawnConfig();
        mob.entityType = entityType;
        mob.count = count;

        // Use player position as spawn point
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            mob.spawnPoint = new SpawnPointConfig(
                    player.level().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );
        }

        wave.mobs.add(mob);
        ConfigManager.getInstance().saveDungeon(config);

        int mobIndex = wave.mobs.size() - 1;
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] " + count + "x " + entityType + " ajoute a la vague " + waveNum + " (index: " + mobIndex + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setWaveMobPos(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }

        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index de mob invalide: " + mobIndex + " (max: " + (wave.mobs.size() - 1) + ")"));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        MobSpawnConfig mob = wave.mobs.get(mobIndex);
        mob.spawnPoint = new SpawnPointConfig(
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        );
        ConfigManager.getInstance().saveDungeon(config);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Position du mob " + mobIndex + " (vague " + waveNum + ") definie a votre position!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setWaveMessage(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        String message = StringArgumentType.getString(ctx, "msg");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }

        wave.startMessage = message;
        ConfigManager.getInstance().saveDungeon(config);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Message de la vague " + waveNum + " defini.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listWaves(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        if (config.waves.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Aucune vague configuree pour " + config.name + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== Vagues de " + config.name + " ===").withStyle(ChatFormatting.GOLD), false);
        for (WaveConfig wave : config.waves) {
            int totalMobs = wave.mobs.stream().mapToInt(m -> m.count).sum();
            ctx.getSource().sendSuccess(() -> Component.literal("  Vague " + wave.waveNumber + ": " + wave.mobs.size() + " type(s), " + totalMobs + " mobs total")
                    .withStyle(ChatFormatting.YELLOW), false);
            for (int i = 0; i < wave.mobs.size(); i++) {
                MobSpawnConfig mob = wave.mobs.get(i);
                final int idx = i;
                ctx.getSource().sendSuccess(() -> Component.literal("    [" + idx + "] " + mob.count + "x " + mob.entityType + " @ " +
                        (int) mob.spawnPoint.x + "," + (int) mob.spawnPoint.y + "," + (int) mob.spawnPoint.z)
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    // === EQUIP COMMANDS ===

    private static int setWaveMobEquip(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");
        String slot = StringArgumentType.getString(ctx, "slot").toLowerCase();
        String item = ResourceLocationArgument.getId(ctx, "item").toString();

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }

        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        MobSpawnConfig mob = wave.mobs.get(mobIndex);
        applyEquipSlot(mob, slot, item);
        ConfigManager.getInstance().saveDungeon(config);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vague " + waveNum + " mob " + mobIndex + " : " + slot + " = " + item)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setSummonEquip(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");
        String slot = StringArgumentType.getString(ctx, "slot").toLowerCase();
        String item = ResourceLocationArgument.getId(ctx, "item").toString();

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }

        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex + " (max: " + (phase.summonMobs.size() - 1) + ")"));
            return 0;
        }

        SummonConfig summon = phase.summonMobs.get(summonIndex);
        switch (slot) {
            case "mainhand" -> summon.mainHand = item;
            case "offhand" -> summon.offHand = item;
            case "helmet" -> summon.helmet = item;
            case "chestplate" -> summon.chestplate = item;
            case "leggings" -> summon.leggings = item;
            case "boots" -> summon.boots = item;
            default -> {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Slot invalide: " + slot + " (mainhand/offhand/helmet/chestplate/leggings/boots)"));
                return 0;
            }
        }

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : " + slot + " = " + item)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static void applyEquipSlot(MobSpawnConfig mob, String slot, String item) {
        switch (slot) {
            case "mainhand" -> mob.mainHand = item;
            case "offhand" -> mob.offHand = item;
            case "helmet" -> mob.helmet = item;
            case "chestplate" -> mob.chestplate = item;
            case "leggings" -> mob.leggings = item;
            case "boots" -> mob.boots = item;
        }
    }

    // === BOSS EQUIP ===

    private static int setBossEquip(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String slot = StringArgumentType.getString(ctx, "slot").toLowerCase();
        String item = ResourceLocationArgument.getId(ctx, "item").toString();

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        switch (slot) {
            case "mainhand" -> boss.mainHand = item;
            case "offhand" -> boss.offHand = item;
            case "helmet" -> boss.helmet = item;
            case "chestplate" -> boss.chestplate = item;
            case "leggings" -> boss.leggings = item;
            case "boots" -> boss.boots = item;
            default -> {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Slot invalide: " + slot));
                return 0;
            }
        }

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : " + slot + " = " + item)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int copyEquipToBoss(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        boss.mainHand = getItemId(player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        boss.offHand = getItemId(player, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        boss.helmet = getItemId(player, net.minecraft.world.entity.EquipmentSlot.HEAD);
        boss.chestplate = getItemId(player, net.minecraft.world.entity.EquipmentSlot.CHEST);
        boss.leggings = getItemId(player, net.minecraft.world.entity.EquipmentSlot.LEGS);
        boss.boots = getItemId(player, net.minecraft.world.entity.EquipmentSlot.FEET);

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Equipement copie sur le boss " + bossId + "!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int copyEquipToWaveMob(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        MobSpawnConfig mob = wave.mobs.get(mobIndex);
        mob.mainHand = getItemId(player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        mob.offHand = getItemId(player, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        mob.helmet = getItemId(player, net.minecraft.world.entity.EquipmentSlot.HEAD);
        mob.chestplate = getItemId(player, net.minecraft.world.entity.EquipmentSlot.CHEST);
        mob.leggings = getItemId(player, net.minecraft.world.entity.EquipmentSlot.LEGS);
        mob.boots = getItemId(player, net.minecraft.world.entity.EquipmentSlot.FEET);

        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Equipement copie sur le mob " + mobIndex + " (vague " + waveNum + ")!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static String getItemId(ServerPlayer player, net.minecraft.world.entity.EquipmentSlot slot) {
        net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
        if (stack.isEmpty()) return "";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // === BOSS ATTRIBUTE ===

    private static int setBossAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.customAttributes.put(attribute, value);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : " + attribute + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeBossAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        if (boss.customAttributes.remove(attribute) == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Attribut non defini: " + attribute));
            return 0;
        }

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : attribut " + attribute + " supprime")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listBossAttributes(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        if (boss.customAttributes.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : aucun attribut personnalise")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Attributs du boss " + bossId + " :")
                .withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, Double> entry : boss.customAttributes.entrySet()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + entry.getKey() + " = " + entry.getValue())
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    private static int setBossCombatValue(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        double value = readNumericCombatValue(ctx);

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.customAttributes.put(key, value);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setBossCombatBool(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        boolean value = BoolArgumentType.getBool(ctx, "value");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.customAttributes.put(key, value ? 1.0D : 0.0D);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === WAVE MOB ATTRIBUTE ===

    private static int setWaveMobAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        wave.mobs.get(mobIndex).customAttributes.put(attribute, value);
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : " + attribute + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeWaveMobAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        if (wave.mobs.get(mobIndex).customAttributes.remove(attribute) == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Attribut non defini: " + attribute));
            return 0;
        }

        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : attribut " + attribute + " supprime")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listWaveMobAttributes(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        Map<String, Double> attrs = wave.mobs.get(mobIndex).customAttributes;
        if (attrs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : aucun attribut personnalise")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Attributs du mob " + mobIndex + " (vague " + waveNum + ") :")
                .withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, Double> entry : attrs.entrySet()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + entry.getKey() + " = " + entry.getValue())
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    private static int setWaveCombatValue(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");
        double value = readNumericCombatValue(ctx);

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        wave.mobs.get(mobIndex).customAttributes.put(key, value);
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setWaveCombatBool(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");
        boolean value = BoolArgumentType.getBool(ctx, "value");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        wave.mobs.get(mobIndex).customAttributes.put(key, value ? 1.0D : 0.0D);
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === SUMMON ATTRIBUTE ===

    private static int setSummonAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }
        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex));
            return 0;
        }

        phase.summonMobs.get(summonIndex).customAttributes.put(attribute, value);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : " + attribute + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeSummonAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }
        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex));
            return 0;
        }

        if (phase.summonMobs.get(summonIndex).customAttributes.remove(attribute) == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Attribut non defini: " + attribute));
            return 0;
        }

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : attribut " + attribute + " supprime")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listSummonAttributes(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }
        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex));
            return 0;
        }

        Map<String, Double> attrs = phase.summonMobs.get(summonIndex).customAttributes;
        if (attrs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : aucun attribut personnalise")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Attributs du sbire " + summonIndex + " (phase " + phaseNum + ") :")
                .withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, Double> entry : attrs.entrySet()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + entry.getKey() + " = " + entry.getValue())
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    private static int setSummonCombatValue(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");
        double value = readNumericCombatValue(ctx);

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }
        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex));
            return 0;
        }

        phase.summonMobs.get(summonIndex).customAttributes.put(key, value);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setSummonCombatBool(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");
        boolean value = BoolArgumentType.getBool(ctx, "value");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }
        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex));
            return 0;
        }

        phase.summonMobs.get(summonIndex).customAttributes.put(key, value ? 1.0D : 0.0D);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static double readNumericCombatValue(CommandContext<CommandSourceStack> ctx) {
        try {
            return DoubleArgumentType.getDouble(ctx, "value");
        } catch (IllegalArgumentException ignored) {
            return IntegerArgumentType.getInteger(ctx, "value");
        }
    }

    // === WAND ===

    private static int giveAreaWand(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        net.minecraft.world.item.ItemStack wand = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_SHOVEL);
        wand.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(com.vyrriox.arcadiadungeon.event.DungeonEventHandler.AREA_WAND_TAG + " - Arcadia Dungeon Area Wand").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Ask which dungeon
        Map<String, DungeonConfig> dungeons = ConfigManager.getInstance().getDungeonConfigs();
        if (dungeons.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Aucun donjon configure!"));
            return 0;
        }

        // Give the wand
        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }

        // Default to first dungeon, show selection
        String firstId = dungeons.keySet().iterator().next();
        com.vyrriox.arcadiadungeon.event.DungeonEventHandler.wandDungeon.put(player.getUUID(), firstId);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Wand recue! Donjon: " + firstId).withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Clic gauche = Pos1 | Clic droit = Pos2").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /arcadia_dungeon admin wand_select <dungeon> pour la zone du donjon").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  La pelle en or sert uniquement a la zone globale du donjon").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Les zones de boss/vagues ont ete retirees pour ne plus casser l'IA").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Changer de donjon:").withStyle(ChatFormatting.GRAY), false);

        for (DungeonConfig cfg : dungeons.values()) {
            MutableComponent line = Component.literal("    [" + cfg.name + "]").withStyle(ChatFormatting.YELLOW)
                    .withStyle(style -> style
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                    "/arcadia_dungeon admin wand_select " + cfg.id))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Cliquer pour selectionner " + cfg.name))));
            ctx.getSource().sendSuccess(() -> line, false);
        }

        return 1;
    }

    // === WAND SELECT ===

    private static int wandSelect(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!")); return 0; }

        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId)); return 0; }

        com.vyrriox.arcadiadungeon.event.DungeonEventHandler.wandDungeon.put(player.getUUID(), dungeonId);
        com.vyrriox.arcadiadungeon.event.DungeonEventHandler.wandWall.remove(player.getUUID());
        com.vyrriox.arcadiadungeon.event.DungeonEventHandler.wandPos1.remove(player.getUUID());
        com.vyrriox.arcadiadungeon.event.DungeonEventHandler.wandPos2.remove(player.getUUID());

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Wand] Donjon selectionne: " + config.name)
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int giveWallWand(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        net.minecraft.world.item.ItemStack wand = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_HOE);
        wand.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(com.vyrriox.arcadiadungeon.event.DungeonEventHandler.WALL_WAND_TAG + " - Arcadia Scripted Wall Wand").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Hoe recue! Selectionnez un mur avec /arcadia_dungeon admin wall_select <dungeon> <wallId>, puis cliquez sur les blocs.")
                .withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] TODO: systeme de conditions/activation des murs scriptes a venir.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int wallSelect(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!")); return 0; }

        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String wallId = StringArgumentType.getString(ctx, "wallId");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId)); return 0; }

        var wall = config.scriptedWalls.stream().filter(w -> w.id.equals(wallId)).findFirst().orElse(null);
        if (wall == null) {
            wall = new DungeonConfig.ScriptedWallConfig();
            wall.id = wallId;
            config.scriptedWalls.add(wall);
            ConfigManager.getInstance().saveDungeon(config);
        }

        com.vyrriox.arcadiadungeon.event.DungeonEventHandler.wandDungeon.put(player.getUUID(), dungeonId);
        com.vyrriox.arcadiadungeon.event.DungeonEventHandler.wandWall.put(player.getUUID(), wallId);
        com.vyrriox.arcadiadungeon.event.DungeonEventHandler.wandPos1.remove(player.getUUID());
        com.vyrriox.arcadiadungeon.event.DungeonEventHandler.wandPos2.remove(player.getUUID());

        int count = wall.blocks == null ? 0 : wall.blocks.size();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Wall] Mur scripté selectionne: " + wallId + " (" + count + " bloc(s))")
                .withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Wall] Cliquez sur les blocs avec la hoe en or pour les ajouter/retirer.")
                .withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Wall] TODO: conditions d'activation et logique runtime.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    // === PHASE EFFECT + COMMAND ===

    private static int addPhaseEffect(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        String effect = ResourceLocationArgument.getId(ctx, "effect").toString();
        int duration = IntegerArgumentType.getInteger(ctx, "duration");
        int amplifier = IntegerArgumentType.getInteger(ctx, "amplifier");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum)); return 0; }

        phase.playerEffects.add(new PhaseConfig.PhaseEffect(effect, duration, amplifier));
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Effet " + effect + " (lvl " + (amplifier + 1) + ", " + duration + "s) ajoute a la phase " + phaseNum)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int addPhaseCommand(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        String cmd = StringArgumentType.getString(ctx, "cmd");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum)); return 0; }

        phase.phaseCommands.add(cmd);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Commande ajoutee a la phase " + phaseNum + ": " + cmd)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === WEEKLY COMMANDS ===

    private static int setWeeklyReward(CommandContext<CommandSourceStack> ctx) {
        int top = IntegerArgumentType.getInteger(ctx, "top");
        String item = ResourceLocationArgument.getId(ctx, "item").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");

        WeeklyLeaderboard.getInstance().setReward(top, item, count);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Recompense top " + top + " definie: " + count + "x " + item)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setWeeklyResetDay(CommandContext<CommandSourceStack> ctx) {
        String dayStr = StringArgumentType.getString(ctx, "day").toUpperCase();
        try {
            java.time.DayOfWeek day = java.time.DayOfWeek.valueOf(dayStr);
            WeeklyLeaderboard.getInstance().setResetDay(day);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Jour de reset: " + dayStr).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Jour invalide! (MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)"));
            return 0;
        }
    }

    private static int setWeeklyHour(CommandContext<CommandSourceStack> ctx) {
        int hour = IntegerArgumentType.getInteger(ctx, "hour");
        WeeklyLeaderboard.getInstance().setAnnounceHour(hour);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Heure d'annonce: " + hour + "h").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int weeklyInfo(CommandContext<CommandSourceStack> ctx) {
        var config = WeeklyLeaderboard.getInstance().getConfig();
        var data = WeeklyLeaderboard.getInstance().getData();

        ctx.getSource().sendSuccess(() -> Component.literal("=== Leaderboard Hebdo ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Semaine: " + data.weekId).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Reset: " + config.resetDay + " a " + config.announceHour + "h").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Joueurs: " + data.playerCompletions.size()).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Recompenses:").withStyle(ChatFormatting.YELLOW), false);

        for (int i = 1; i <= 3; i++) {
            List<com.vyrriox.arcadiadungeon.config.RewardConfig> rewards = switch (i) {
                case 1 -> config.top1Rewards;
                case 2 -> config.top2Rewards;
                case 3 -> config.top3Rewards;
                default -> List.of();
            };
            final int rank = i;
            if (!rewards.isEmpty()) {
                var r = rewards.get(0);
                ctx.getSource().sendSuccess(() -> Component.literal("    Top " + rank + ": " + r.count + "x " + r.item).withStyle(ChatFormatting.GRAY), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("    Top " + rank + ": aucune").withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    private static int weeklyForceReset(CommandContext<CommandSourceStack> ctx) {
        WeeklyLeaderboard.getInstance().forceReset(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Leaderboard hebdo reset! Annonce envoyee et recompenses distribuees.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === TIMER WARNINGS ===

    private static int addTimerWarning(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id)); return 0; }

        if (!config.settings.timerWarnings.contains(seconds)) {
            config.settings.timerWarnings.add(seconds);
            config.settings.timerWarnings.sort(java.util.Collections.reverseOrder());
            ConfigManager.getInstance().saveDungeon(config);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Avertissement a " + seconds + "s ajoute.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeTimerWarning(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id)); return 0; }

        config.settings.timerWarnings.remove(Integer.valueOf(seconds));
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Avertissement a " + seconds + "s retire.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listTimerWarnings(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id)); return 0; }

        String warnings = config.settings.timerWarnings.stream().map(s -> s + "s").reduce((a, b) -> a + ", " + b).orElse("aucun");
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Avertissements pour " + config.name + ": " + warnings).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    // === DUNGEON AREA ===

    private static int setDungeonArea(CommandContext<CommandSourceStack> ctx, int posNumber) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        DungeonConfig.AreaPos pos = new DungeonConfig.AreaPos(
                player.level().dimension().location().toString(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ()
        );

        if (posNumber == 1) {
            config.areaPos1 = pos;
        } else {
            config.areaPos2 = pos;
        }

        ConfigManager.getInstance().saveDungeon(config);

        String coordStr = pos.x + ", " + pos.y + ", " + pos.z;
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Zone du donjon pos" + posNumber + " definie: " + coordStr)
                .withStyle(ChatFormatting.GREEN), true);

        if (config.hasArea()) {
            int minX = Math.min(config.areaPos1.x, config.areaPos2.x);
            int maxX = Math.max(config.areaPos1.x, config.areaPos2.x);
            int minY = Math.min(config.areaPos1.y, config.areaPos2.y);
            int maxY = Math.max(config.areaPos1.y, config.areaPos2.y);
            int minZ = Math.min(config.areaPos1.z, config.areaPos2.z);
            int maxZ = Math.max(config.areaPos1.z, config.areaPos2.z);
            int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Zone complete! " +
                    (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1) + " (" + volume + " blocs)")
                    .withStyle(ChatFormatting.AQUA), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Definissez maintenant pos" + (posNumber == 1 ? "2" : "1") + " pour completer la zone.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        return 1;
    }

    private static int clearDungeonArea(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.areaPos1 = null;
        config.areaPos2 = null;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Zone du donjon " + config.name + " supprimee.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === HELPERS ===

    private static BossConfig findBoss(CommandContext<CommandSourceStack> ctx, String dungeonId, String bossId) {
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return null;
        }
        for (BossConfig boss : config.bosses) {
            if (boss.id.equals(bossId)) {
                return boss;
            }
        }
        ctx.getSource().sendFailure(Component.literal("[Arcadia] Boss introuvable: " + bossId));
        return null;
    }

    private static String formatTime(long seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m " + (seconds % 60) + "s";
        } else if (seconds >= 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }
}
