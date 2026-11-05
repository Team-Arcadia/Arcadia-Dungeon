package com.vyrriox.arcadiadungeon.gametest;

import com.google.gson.Gson;
import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.Messages;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The language files, and what a server owner can do with them.
 *
 * <p><b>Why this one.</b> A server owner translated a dungeon's config and the chat stayed in
 * French: the lines were written into the code, and the one announcement the config did offer was
 * never read. These tests hold the promise made instead: a file on disk decides the text, an edit
 * survives an update, and a dungeon's own message outranks the file.</p>
 *
 * <p>Each test loads a throwaway config folder and puts the real one back when it is done, so the
 * other tests of the run see the server's own text.</p>
 *
 * @author vyrriox
 */
@GameTestHolder("arcadia_dungeon")
@PrefixGameTestTemplate(false)
public class MessagesGameTests {

    private static final String T = "floor";
    private static final Gson GSON = new Gson();
    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z]+%");

    private interface Body {
        void run(Path dir) throws IOException;
    }

    private static void withConfig(GameTestHelper h, Body body) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("arcadia-dungeon-lang");
            body.run(dir);
        } catch (IOException e) {
            h.fail("I/O error: " + e);
        } finally {
            Messages.load(ConfigManager.getInstance().getConfigDir());
            if (dir != null) delete(dir);
        }
    }

    private static void delete(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
        }
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static Set<String> placeholders(String line) {
        Set<String> found = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(line);
        while (m.find()) found.add(m.group());
        return found;
    }

    private static Component segment(Component line, String text) {
        return line.getSiblings().stream().filter(c -> c.getString().equals(text)).findFirst().orElseThrow();
    }

    private static void check(GameTestHelper h, boolean ok, String message) {
        if (!ok) h.fail(message);
    }

    /** A French line with a missing placeholder would show a blank, or worse, a raw %name%. */
    @GameTest(template = T)
    public static void bundledLanguagesMatch(GameTestHelper h) {
        Map<String, String> en = Messages.bundled("en_us");
        Map<String, String> fr = Messages.bundled("fr_fr");
        check(h, !en.isEmpty(), "the jar has no en_us.json");
        check(h, en.keySet().equals(fr.keySet()), "en_us and fr_fr differ in their keys");
        for (Map.Entry<String, String> line : en.entrySet()) {
            check(h, placeholders(line.getValue()).equals(placeholders(fr.get(line.getKey()))),
                    "placeholders differ on " + line.getKey());
        }
        h.succeed();
    }

    /** This is the report: an edited line reaches the chat, and an update does not undo the edit. */
    @GameTest(template = T)
    public static void editedLineWinsAndSurvivesAnUpdate(GameTestHelper h) {
        withConfig(h, dir -> {
            Path file = dir.resolve("lang/en_us.json");
            write(file, "{\"start.begins\":\"&6Edited %count%\"}");
            Messages.load(dir);

            check(h, Messages.textIn("en_us", "start.begins", "count", 2).equals("&6Edited 2"), "edit ignored");
            String after = Files.readString(file, StandardCharsets.UTF_8);
            check(h, after.contains("&6Edited %count%"), "the edit was overwritten");
            check(h, after.contains("\"end.failed\""), "the missing lines were not added");
            check(h, Files.exists(dir.resolve("lang/fr_fr.json")), "fr_fr.json was not written");
            check(h, Files.exists(dir.resolve("language.json")), "language.json was not written");
        });
        h.succeed();
    }

    @GameTest(template = T)
    public static void aNewLanguageIsADroppedInFile(GameTestHelper h) {
        withConfig(h, dir -> {
            write(dir.resolve("lang/es_es.json"), "{\"end.failed\":\"&c¡Mazmorra fallida!\"}");
            Messages.load(dir);

            check(h, Messages.textIn("es_es", "end.failed").equals("&c¡Mazmorra fallida!"), "exact language");
            check(h, Messages.textIn("es_mx", "end.failed").equals("&c¡Mazmorra fallida!"), "same language family");
            check(h, Messages.textIn("es_es", "boss.next").equals(Messages.textIn("en_us", "boss.next")),
                    "a line the file lacks comes from en_us");
            check(h, Messages.textIn("de_de", "end.failed").equals(Messages.textIn("en_us", "end.failed")),
                    "an unknown language falls back on en_us");
            check(h, Messages.textIn("fr_fr", "end.failed").contains("échoué"), "fr_fr keeps its text in auto");
        });
        h.succeed();
    }

    @GameTest(template = T)
    public static void forcedLanguageAppliesToEveryone(GameTestHelper h) {
        withConfig(h, dir -> {
            write(dir.resolve("lang/es_es.json"), "{\"end.failed\":\"&c¡Mazmorra fallida!\"}");
            write(dir.resolve("language.json"), "{\"language\":\"es_es\"}");
            Messages.load(dir);

            check(h, Messages.textIn("fr_fr", "end.failed").equals("&c¡Mazmorra fallida!"), "French client");
            check(h, Messages.text(null, "end.failed").equals("&c¡Mazmorra fallida!"), "the console");
        });
        h.succeed();
    }

    /** A typo in a file must not cost the owner the file, nor the server its text. */
    @GameTest(template = T)
    public static void aBrokenFileIsKeptAndIgnored(GameTestHelper h) {
        withConfig(h, dir -> {
            Path file = dir.resolve("lang/en_us.json");
            write(file, "{ not json");
            Messages.load(dir);

            check(h, Files.readString(file, StandardCharsets.UTF_8).equals("{ not json"), "the file was rewritten");
            check(h, Messages.textIn("en_us", "end.failed").equals(Messages.bundled("en_us").get("end.failed")),
                    "the built-in text was not used");
        });
        h.succeed();
    }

    /** The field the owner edited, which nothing read. */
    @GameTest(template = T)
    public static void recruitmentMessageIsRead(GameTestHelper h) {
        DungeonConfig config = new DungeonConfig("mazmorra", "Mazmorra");
        check(h, DungeonManager.recruitmentAnnouncement(null, config, "Ana", 90).getString()
                        .equals(Messages.get(null, "recruit.announce", "player", "Ana", "dungeon", "Mazmorra").getString()),
                "an empty message should use the language file");

        config.recruitmentMessage = "&e%player% abre %dungeon% (%time%s) %id%";
        check(h, DungeonManager.recruitmentAnnouncement(null, config, "Ana", 90).getString()
                        .equals("Ana abre Mazmorra (90s) mazmorra"),
                "the dungeon's own message was not used");
        h.succeed();
    }

    /** Existing configs carry the old French text saved as if it were a choice. */
    @GameTest(template = T)
    public static void oldDefaultsAreClearedAndEditsKept(GameTestHelper h) {
        DungeonConfig config = GSON.fromJson("""
                {"id":"a","name":"A",
                 "startMessage":"&6[Donjon] &e%player% &7lance &e%dungeon%&7! Rejoignez avec &f/arcadia_dungeon join %id%&7!",
                 "completionMessage":"&6[Donjon] &a%player% &7a vaincu &e%dungeon%&7!",
                 "failMessage":"&6[Donjon] &c%player% &7a echoue dans &e%dungeon%&7!",
                 "availabilityMessage":"&6[Donjon] &e%dungeon% &7est de nouveau accessible! &fLancez avec &e/arcadia_dungeon start %id%",
                 "recruitmentMessage":"Mi texto"}""", DungeonConfig.class);
        config.clearLegacyDefaultMessages();

        check(h, config.startMessage.isEmpty() && config.completionMessage.isEmpty()
                && config.failMessage.isEmpty() && config.availabilityMessage.isEmpty(), "an old default survived");
        check(h, config.recruitmentMessage.equals("Mi texto"), "an edited message was cleared");
        h.succeed();
    }

    /** The default French title is {@code &6&l...}: it must come out both gold and bold. */
    @GameTest(template = T)
    public static void colourCodesCombine(GameTestHelper h) {
        Component line = DungeonManager.parseColorCodes("&c&lBold red&a green");
        Component bold = segment(line, "Bold red");
        Component green = segment(line, " green");

        check(h, bold.getStyle().isBold()
                && ChatFormatting.RED.getColor().equals(bold.getStyle().getColor().getValue()), "&c&l lost a part");
        check(h, !green.getStyle().isBold()
                && ChatFormatting.GREEN.getColor().equals(green.getStyle().getColor().getValue()), "a colour should reset");
        h.succeed();
    }
}
