package com.arcadia.dungeon.persistence;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Global free-class catalog used by the player loadout.
 */
public final class GlobalClassRegistry {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA_VERSION = 1;

    private final Path file;
    private List<DungeonConfig.ArchetypeDefinition> classes = defaultClasses();

    public GlobalClassRegistry() {
        this(FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("classes.json"));
    }

    public GlobalClassRegistry(Path file) {
        this.file = file;
    }

    public void bootstrap() {
        if (!Files.exists(file)) {
            save(defaultClasses());
            return;
        }
        load();
    }

    public void load() {
        try {
            GlobalClassConfig config = GSON.fromJson(Files.readString(file), GlobalClassConfig.class);
            if (config == null || config.classes() == null || config.classes().isEmpty()) {
                classes = defaultClasses();
                save(classes);
                return;
            }
            classes = sanitize(config.classes());
            ArcadiaDungeon.LOGGER.info("[Arcadia][CLASSES] loaded count={} from={}", classes.size(), file);
        } catch (JsonSyntaxException | IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CLASSES] load_failed path={} error={}", file, e.getMessage());
            classes = defaultClasses();
        }
    }

    public void save(List<DungeonConfig.ArchetypeDefinition> nextClasses) {
        classes = sanitize(nextClasses);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, PRETTY_GSON.toJson(new GlobalClassConfig(SCHEMA_VERSION, classes)));
            ArcadiaDungeon.LOGGER.info("[Arcadia][CLASSES] saved count={} path={}", classes.size(), file);
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CLASSES] save_failed path={} error={}", file, e.getMessage());
        }
    }

    public List<DungeonConfig.ArchetypeDefinition> classes() {
        return List.copyOf(classes);
    }

    public boolean isKnownClass(String classId) {
        return find(classId).isPresent();
    }

    public List<String> itemsFor(String classId) {
        return find(classId)
            .map(DungeonConfig.ArchetypeDefinition::items)
            .orElse(List.of());
    }

    private Optional<DungeonConfig.ArchetypeDefinition> find(String classId) {
        if (classId == null || classId.isBlank()) {
            return Optional.empty();
        }
        return classes.stream().filter(c -> classId.equals(c.id())).findFirst();
    }

    private static List<DungeonConfig.ArchetypeDefinition> sanitize(List<DungeonConfig.ArchetypeDefinition> input) {
        if (input == null || input.isEmpty()) {
            return defaultClasses();
        }
        List<DungeonConfig.ArchetypeDefinition> sanitized = input.stream()
            .filter(c -> c != null && isValidClassId(c.id()))
            .map(c -> new DungeonConfig.ArchetypeDefinition(
                c.id().trim(),
                c.nameKey() != null && !c.nameKey().isBlank() ? c.nameKey().trim() : c.id().trim(),
                sanitizeItems(c.items())))
            .filter(c -> !c.items().isEmpty())
            .toList();
        return sanitized.isEmpty() ? defaultClasses() : sanitized;
    }

    private static List<String> sanitizeItems(List<String> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
            .filter(item -> item != null && ResourceLocation.tryParse(item.trim()) != null)
            .map(String::trim)
            .distinct()
            .limit(9)
            .toList();
    }

    private static boolean isValidClassId(String id) {
        return id != null && id.matches("[a-z0-9_:-]{1,64}");
    }

    private static List<DungeonConfig.ArchetypeDefinition> defaultClasses() {
        return List.of(
            new DungeonConfig.ArchetypeDefinition("warrior", "arcadia.archetype.warrior.name",
                List.of("minecraft:iron_sword", "minecraft:shield", "minecraft:bread")),
            new DungeonConfig.ArchetypeDefinition("mage", "arcadia.archetype.mage.name",
                List.of("minecraft:blaze_rod", "minecraft:book", "minecraft:golden_apple")),
            new DungeonConfig.ArchetypeDefinition("archer", "arcadia.archetype.archer.name",
                List.of("minecraft:bow", "minecraft:arrow", "minecraft:cooked_beef")),
            new DungeonConfig.ArchetypeDefinition("healer", "arcadia.archetype.healer.name",
                List.of("minecraft:golden_carrot", "minecraft:shield", "minecraft:potion"))
        );
    }

    private record GlobalClassConfig(int schemaVersion, List<DungeonConfig.ArchetypeDefinition> classes) {}
}
