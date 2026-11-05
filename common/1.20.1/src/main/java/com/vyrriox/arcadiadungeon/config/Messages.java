package com.vyrriox.arcadiadungeon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every line of text the mod shows a player.
 *
 * <p>The mod is server side only, so a client has no language file of it to look a translation key
 * up in. The text is chosen here and sent as plain text. It lives in
 * {@code config/arcadia/dungeon/lang/<language>.json}, written on first start from the files in the
 * jar, so a server owner can reword any line or add a language by dropping in a new file.</p>
 *
 * <p>{@code config/arcadia/dungeon/language.json} picks whose language is used: {@code "auto"}
 * follows each player's client, and a language code such as {@code "es_es"} gives everyone that
 * file. A line a language does not have comes from {@code en_us}.</p>
 *
 * @author vyrriox
 */
public final class Messages {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type TEXT_MAP = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
    private static final String FALLBACK_LANGUAGE = "en_us";
    private static final String AUTO = "auto";
    private static final String[] BUNDLED_LANGUAGES = {FALLBACK_LANGUAGE, "fr_fr"};

    private static volatile Map<String, Map<String, String>> languages = bundledLanguages();
    private static volatile String serverLanguage = AUTO;

    private Messages() {
    }

    /** Called with every config load, so the dungeon reload command picks up an edited file too. */
    public static void load(Path configDir) {
        Path langDir = configDir.resolve("lang");
        Map<String, Map<String, String>> loaded = bundledLanguages();
        try {
            Files.createDirectories(langDir);
            for (String language : BUNDLED_LANGUAGES) {
                writeMissingLines(langDir.resolve(language + ".json"), bundled(language));
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(langDir, "*.json")) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    String language = name.substring(0, name.length() - ".json".length()).toLowerCase(Locale.ROOT);
                    Map<String, String> lines = read(file);
                    if (lines == null) continue;
                    loaded.computeIfAbsent(language, l -> new HashMap<>()).putAll(lines);
                }
            }
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to read the dungeon language files, using the built-in text", e);
        }
        languages = loaded;
        serverLanguage = readServerLanguage(configDir.resolve("language.json"));
    }

    /** A line for this player with its {@code %name%} placeholders filled, colour codes included. */
    public static String text(ServerPlayer player, String key, Object... args) {
        return textIn(languageOf(player), key, args);
    }

    /** A line for this player as chat text. A null player, the console, gets the server language. */
    public static MutableComponent get(ServerPlayer player, String key, Object... args) {
        return DungeonManager.parseColorCodes(text(player, key, args));
    }

    /** {@code configured} when a dungeon's config sets that text, otherwise the language file's line. */
    public static MutableComponent getOr(ServerPlayer player, String configured, String key, Object... args) {
        return configured == null || configured.isBlank()
                ? get(player, key, args)
                : DungeonManager.parseColorCodes(fill(configured, args));
    }

    /** The line for a language as a client reports it, such as {@code es_mx}. */
    public static String textIn(String clientLanguage, String key, Object... args) {
        Map<String, Map<String, String>> all = languages;
        String line = all.getOrDefault(resolveLanguage(clientLanguage), Map.of()).get(key);
        if (line == null) line = all.getOrDefault(FALLBACK_LANGUAGE, Map.of()).getOrDefault(key, key);
        return fill(line, args);
    }

    /** The lines shipped in the jar for a language, empty when there is no such file. */
    public static Map<String, String> bundled(String language) {
        String path = "/arcadia_dungeon/lang/" + language + ".json";
        try (InputStream in = Messages.class.getResourceAsStream(path)) {
            if (in == null) return new LinkedHashMap<>();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Map<String, String> lines = GSON.fromJson(reader, TEXT_MAP);
                return lines != null ? lines : new LinkedHashMap<>();
            }
        } catch (IOException | JsonParseException e) {
            ArcadiaDungeon.LOGGER.error("Failed to read the built-in language {}", language, e);
            return new LinkedHashMap<>();
        }
    }

    private static String languageOf(ServerPlayer player) {
        return player == null ? FALLBACK_LANGUAGE : player.getLanguage();
    }

    /** The forced language if there is one, else the exact file, else one of the same language family. */
    private static String resolveLanguage(String clientLanguage) {
        String forced = serverLanguage;
        String wanted = (AUTO.equals(forced) ? clientLanguage : forced).toLowerCase(Locale.ROOT);
        Map<String, Map<String, String>> all = languages;
        if (all.containsKey(wanted)) return wanted;
        int separator = wanted.indexOf('_');
        String family = (separator < 0 ? wanted : wanted.substring(0, separator)) + "_";
        return all.keySet().stream().filter(l -> l.startsWith(family)).sorted().findFirst().orElse(FALLBACK_LANGUAGE);
    }

    private static String fill(String line, Object... args) {
        for (int i = 0; i + 1 < args.length; i += 2) {
            line = line.replace("%" + args[i] + "%", String.valueOf(args[i + 1]));
        }
        return line;
    }

    private static Map<String, Map<String, String>> bundledLanguages() {
        Map<String, Map<String, String>> all = new HashMap<>();
        for (String language : BUNDLED_LANGUAGES) {
            all.put(language, new HashMap<>(bundled(language)));
        }
        return all;
    }

    /** Creates the file, or adds the lines a new version brought, and never changes an existing line. */
    private static void writeMissingLines(Path file, Map<String, String> defaults) throws IOException {
        Map<String, String> lines = Files.exists(file) ? read(file) : new LinkedHashMap<>();
        // A file that does not parse is someone's unfinished work: leave it for them to fix.
        if (lines == null) return;
        boolean changed = !Files.exists(file);
        for (Map.Entry<String, String> line : defaults.entrySet()) {
            if (lines.putIfAbsent(line.getKey(), line.getValue()) == null) changed = true;
        }
        if (changed) Files.writeString(file, GSON.toJson(lines) + "\n", StandardCharsets.UTF_8);
    }

    private static Map<String, String> read(Path file) {
        try {
            Map<String, String> lines = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TEXT_MAP);
            return lines != null ? lines : new LinkedHashMap<>();
        } catch (IOException | JsonParseException e) {
            ArcadiaDungeon.LOGGER.error("Failed to read the language file {}: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }

    private static String readServerLanguage(Path file) {
        try {
            if (Files.notExists(file)) {
                Files.writeString(file, GSON.toJson(Map.of("language", AUTO)) + "\n", StandardCharsets.UTF_8);
                return AUTO;
            }
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to write {}", file.getFileName(), e);
            return AUTO;
        }
        Map<String, String> settings = read(file);
        String language = settings == null ? null : settings.get("language");
        return language == null || language.isBlank() ? AUTO : language.trim().toLowerCase(Locale.ROOT);
    }
}
