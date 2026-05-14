package com.arcadia.dungeon.persistence;

import com.arcadia.dungeon.domain.config.DungeonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stories S1.2 (loader), S1.3 (seeder), S1.6 (fail-safe).
 */
class DungeonRegistryTest {

    @Test
    void emptyDirTriggersSeedThenLoadOk(@TempDir Path tmp) throws IOException {
        // Given : dossier vide
        Path configDir = tmp.resolve("dungeon");
        DungeonExampleSeeder seeder = new DungeonExampleSeeder(configDir);
        DungeonConfigLoader loader = new DungeonConfigLoader(configDir);
        DungeonRegistry registry = new DungeonRegistry(seeder, loader);

        // When : bootstrap
        registry.bootstrap();

        // Then : exemple seed + chargé
        assertFalse(registry.isFallbackActive(), "should not be in fallback when seed succeeded");
        Map<String, DungeonConfig> dungeons = registry.dungeons();
        assertEquals(1, dungeons.size());
        DungeonConfig cfg = dungeons.values().iterator().next();
        assertEquals(DungeonConfig.CURRENT_SCHEMA_VERSION, cfg.schemaVersion());
        assertFalse(cfg.configuredBosses().isEmpty());
        assertTrue(cfg.archetypes().isEmpty());
    }

    @Test
    void invalidJsonTriggersFallback(@TempDir Path tmp) throws IOException {
        // Given : dossier avec UN seul JSON invalide (pas seedé : non vide donc seeder skip)
        Path configDir = tmp.resolve("dungeon");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("broken.json"), "{ this is not valid json");

        DungeonExampleSeeder seeder = new DungeonExampleSeeder(configDir);
        DungeonConfigLoader loader = new DungeonConfigLoader(configDir);
        DungeonRegistry registry = new DungeonRegistry(seeder, loader);

        // When : bootstrap
        registry.bootstrap();

        // Then : fail-safe activé, exemple JAR chargé
        assertTrue(registry.isFallbackActive(), "fallback should activate when no valid dungeon");
        assertEquals(1, registry.dungeons().size());
        // Le donjon est chargé depuis le JAR (id arcadia_dungeon:example)
        assertTrue(registry.dungeons().containsKey("arcadia_dungeon:example"));
    }

    @Test
    void validJsonOverridesFallback(@TempDir Path tmp) throws IOException {
        // Given : dossier avec UN JSON valide minimal
        Path configDir = tmp.resolve("dungeon");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("custom.json"), """
            {
              "schemaVersion": 2,
              "id": "test:custom",
              "nameKey": "Custom Dungeon",
              "currency": { "nameKey": "Coins", "iconPath": "minecraft:textures/items/coin.png" },
              "lives": 5,
              "rooms": [],
              "waves": [
                { "name": "Wave 1", "triggerMode": "ordered", "delayTicks": 0, "mobs": [{ "mobType": "minecraft:zombie", "count": 1 }] }
              ],
              "bosses": [{ "id": "boss_1", "type": "minecraft:zombie", "hp": 100, "phases": [], "optional": false, "spawnChance": 1.0, "requiredKill": true, "rewards": [] }],
              "rewards": { "currency": 10, "loot": [] },
              "archetypes": []
            }
            """);

        DungeonExampleSeeder seeder = new DungeonExampleSeeder(configDir);
        DungeonConfigLoader loader = new DungeonConfigLoader(configDir);
        DungeonRegistry registry = new DungeonRegistry(seeder, loader);

        // When
        registry.bootstrap();

        // Then : pas de fallback, le custom est chargé
        assertFalse(registry.isFallbackActive());
        assertEquals(1, registry.dungeons().size());
        assertEquals("test:custom", registry.dungeons().keySet().iterator().next());
        assertEquals(5, registry.get("test:custom").orElseThrow().lives());
    }

    @Test
    void missingObligatoryBossSkipsConfig(@TempDir Path tmp) throws IOException {
        // Given : un JSON sans boss
        Path configDir = tmp.resolve("dungeon");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("noboss.json"), """
            {
              "schemaVersion": 2,
              "id": "test:noboss",
              "nameKey": "No Boss",
              "currency": { "nameKey": "Coins", "iconPath": "x:y" },
              "lives": 3,
              "rooms": [],
              "waves": [
                { "name": "Wave 1", "triggerMode": "ordered", "delayTicks": 0, "mobs": [{ "mobType": "minecraft:zombie", "count": 1 }] }
              ],
              "rewards": { "currency": 0, "loot": [] },
              "archetypes": []
            }
            """);

        DungeonExampleSeeder seeder = new DungeonExampleSeeder(configDir);
        DungeonConfigLoader loader = new DungeonConfigLoader(configDir);
        DungeonRegistry registry = new DungeonRegistry(seeder, loader);

        // When
        registry.bootstrap();

        // Then : invalid skippé → fallback
        assertTrue(registry.isFallbackActive());
        assertEquals(1, registry.dungeons().size());
        assertTrue(registry.dungeons().containsKey("arcadia_dungeon:example"));
    }

    @Test
    void seedIsIdempotent(@TempDir Path tmp) throws IOException {
        Path configDir = tmp.resolve("dungeon");
        DungeonExampleSeeder seeder = new DungeonExampleSeeder(configDir);

        // Premier seed : OK
        assertTrue(seeder.seedIfEmpty());
        // Seed à nouveau : skip car non-vide
        assertFalse(seeder.seedIfEmpty());
    }
}
