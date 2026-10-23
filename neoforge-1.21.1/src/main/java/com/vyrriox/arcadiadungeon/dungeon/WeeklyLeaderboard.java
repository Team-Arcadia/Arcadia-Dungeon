package com.vyrriox.arcadiadungeon.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.config.RewardConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WeeklyLeaderboard {
    private static final WeeklyLeaderboard INSTANCE = new WeeklyLeaderboard();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path dataFile;
    private WeeklyData data = new WeeklyData();
    private boolean announcedThisWeek = false;
    private volatile boolean dirty = false;

    public static class WeeklyData {
        public String weekId = "";
        public Map<String, Integer> playerCompletions = new ConcurrentHashMap<>();
        public Map<String, String> playerNames = new ConcurrentHashMap<>();
        public boolean rewarded = false;
    }

    public static class WeeklyConfig {
        public boolean enabled = true;
        public DayOfWeek resetDay = DayOfWeek.MONDAY;
        public int announceHour = 18;
        public List<RewardConfig> top1Rewards = new ArrayList<>();
        public List<RewardConfig> top2Rewards = new ArrayList<>();
        public List<RewardConfig> top3Rewards = new ArrayList<>();
    }

    private WeeklyConfig config = new WeeklyConfig();

    private WeeklyLeaderboard() {
        this.dataFile = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon").resolve("weekly_leaderboard.json");
    }

    public static WeeklyLeaderboard getInstance() {
        return INSTANCE;
    }

    public void load() {
        // Load weekly data
        try {
            if (Files.exists(dataFile)) {
                String json = Files.readString(dataFile);
                data = GSON.fromJson(json, WeeklyData.class);
                if (data == null) data = new WeeklyData();
            }
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.error("Failed to load weekly leaderboard", e);
            data = new WeeklyData();
        }

        // Load config
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon").resolve("weekly_config.json");
        try {
            if (Files.exists(configFile)) {
                String json = Files.readString(configFile);
                config = GSON.fromJson(json, WeeklyConfig.class);
                if (config == null) config = new WeeklyConfig();
            } else {
                // Create default config with example rewards
                config = new WeeklyConfig();
                config.top1Rewards.add(new RewardConfig("minecraft:netherite_ingot", 3, 1.0));
                config.top2Rewards.add(new RewardConfig("minecraft:diamond", 10, 1.0));
                config.top3Rewards.add(new RewardConfig("minecraft:gold_ingot", 16, 1.0));
                Files.createDirectories(configFile.getParent());
                Files.writeString(configFile, GSON.toJson(config));
            }
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.error("Failed to load weekly config", e);
        }

        // Check if we need a new week
        String currentWeek = getCurrentWeekId();
        if (!currentWeek.equals(data.weekId)) {
            data = new WeeklyData();
            data.weekId = currentWeek;
            announcedThisWeek = false;
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            Files.writeString(dataFile, GSON.toJson(data));
            dirty = false;
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to save weekly leaderboard", e);
        }
    }

    public void recordCompletion(String uuid, String playerName) {
        data.playerCompletions.merge(uuid, 1, Integer::sum);
        data.playerNames.put(uuid, playerName);
        dirty = true;
    }

    public void tick(MinecraftServer server) {
        if (server == null || !config.enabled) return;

        LocalDateTime now = LocalDateTime.now();
        String currentWeek = getCurrentWeekId();

        // Check for weekly reset
        if (!currentWeek.equals(data.weekId)) {
            // New week - announce results and give rewards
            if (!data.rewarded && !data.playerCompletions.isEmpty()) {
                announceAndReward(server);
            }
            data = new WeeklyData();
            data.weekId = currentWeek;
            announcedThisWeek = false;
            dirty = true;
            return;
        }

        // Check for announcement at configured hour (range-based: within 2 minutes to avoid missing on lag)
        if (!announcedThisWeek && now.getHour() == config.announceHour && now.getMinute() <= 2) {
            if (now.getDayOfWeek() == config.resetDay && !data.rewarded) {
                announceAndReward(server);
                announcedThisWeek = true;
            }
        }
        if (dirty) {
            save();
        }
    }

    private void announceAndReward(MinecraftServer server) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(data.playerCompletions.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // Announce
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal("").withStyle(ChatFormatting.RESET));
            player.sendSystemMessage(Component.literal("======= Top Joueurs de la Semaine =======").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                Map.Entry<String, Integer> entry = sorted.get(i);
                String name = data.playerNames.getOrDefault(entry.getKey(), "???");
                int completions = entry.getValue();
                ChatFormatting color = i == 0 ? ChatFormatting.GOLD : i == 1 ? ChatFormatting.GRAY : ChatFormatting.RED;
                String medal = i == 0 ? "1er" : i == 1 ? "2eme" : "3eme";

                player.sendSystemMessage(Component.literal("  " + medal + " - ")
                        .withStyle(color, ChatFormatting.BOLD)
                        .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" (" + completions + " completions)").withStyle(ChatFormatting.GRAY)));
            }

            if (sorted.isEmpty()) {
                player.sendSystemMessage(Component.literal("  Aucune completion cette semaine!").withStyle(ChatFormatting.GRAY));
            }

            player.sendSystemMessage(Component.literal("==========================================").withStyle(ChatFormatting.GOLD));
        }

        // Give rewards to top 3
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            String uuid = sorted.get(i).getKey();
            ServerPlayer player = null;
            try {
                player = server.getPlayerList().getPlayer(UUID.fromString(uuid));
            } catch (Exception ignored) {}

            if (player != null) {
                List<RewardConfig> rewards = switch (i) {
                    case 0 -> config.top1Rewards;
                    case 1 -> config.top2Rewards;
                    case 2 -> config.top3Rewards;
                    default -> List.of();
                };

                // Use DungeonInstance reward system - create a temporary helper
                for (RewardConfig reward : rewards) {
                    if (reward.item != null && !reward.item.isEmpty()) {
                        net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(reward.item);
                        if (loc != null) {
                            Optional<net.minecraft.world.item.Item> itemOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(loc);
                            if (itemOpt.isPresent()) {
                                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(itemOpt.get(), reward.count);
                                if (!player.getInventory().add(stack)) {
                                    player.drop(stack, false);
                                }
                            }
                        }
                    }
                    if (reward.experience > 0) {
                        player.giveExperiencePoints(reward.experience);
                    }
                }

                String medal = i == 0 ? "1er" : i == 1 ? "2eme" : "3eme";
                player.sendSystemMessage(Component.literal("[Arcadia] Felicitations! Vous etes " + medal + " cette semaine! Recompenses recues!")
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        data.rewarded = true;
        dirty = true;
        ArcadiaDungeon.LOGGER.info("Weekly leaderboard rewards distributed");
    }

    public List<Map.Entry<String, Integer>> getTop(int limit) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(data.playerCompletions.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public String getPlayerName(String uuid) {
        return data.playerNames.getOrDefault(uuid, "???");
    }

    public WeeklyData getData() {
        return data;
    }

    public WeeklyConfig getConfig() {
        return config;
    }

    public void setReward(int top, String item, int count) {
        RewardConfig reward = new RewardConfig(item, count, 1.0);
        List<RewardConfig> list = switch (top) {
            case 1 -> config.top1Rewards;
            case 2 -> config.top2Rewards;
            case 3 -> config.top3Rewards;
            default -> null;
        };
        if (list != null) {
            list.clear();
            list.add(reward);
            saveConfig();
        }
    }

    public void setResetDay(DayOfWeek day) {
        config.resetDay = day;
        saveConfig();
    }

    public void setAnnounceHour(int hour) {
        config.announceHour = hour;
        saveConfig();
    }

    public void forceReset(MinecraftServer server) {
        if (!data.playerCompletions.isEmpty()) {
            announceAndReward(server);
        }
        data = new WeeklyData();
        data.weekId = getCurrentWeekId();
        dirty = true;
        save();
    }

    private void saveConfig() {
        try {
            Path configFile = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon").resolve("weekly_config.json");
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(config));
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to save weekly config", e);
        }
    }

    private String getCurrentWeekId() {
        LocalDate now = LocalDate.now();
        int week = now.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        return now.getYear() + "-W" + String.format("%02d", week);
    }
}
