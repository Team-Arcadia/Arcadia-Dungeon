package com.arcadia.dungeon.domain.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Story S1.1 AC4 — sérialisation/désérialisation Gson roundtrip OK pour fixture exemple.
 */
class DungeonConfigGsonTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void roundtripExampleDungeon() {
        // Given : un DungeonConfig complet
        DungeonConfig original = new DungeonConfig(
            DungeonConfig.CURRENT_SCHEMA_VERSION,
            "arcadia_dungeon:example",
            "arcadia.dungeon.example.name",
            new DungeonConfig.Currency("arcadia.currency.gears.name", "arcadia_dungeon:textures/icons/gear.png"),
            3,
            List.of(),
            List.of(
                new DungeonConfig.Wave(
                    "Wave 1",
                    List.of(new DungeonConfig.MobSpawn("minecraft:zombie", 2, null)),
                    "ordered",
                    0,
                    "Wave 1 arrives",
                    true,
                    60
                ),
                new DungeonConfig.Wave(
                    "Wave 2",
                    List.of(new DungeonConfig.MobSpawn("minecraft:skeleton", 1, null)),
                    "ticks",
                    30,
                    "Wave 2 arrives",
                    false,
                    0
                )
            ),
            List.of(
                new DungeonConfig.BossDefinition(
                    "minecraft:wither_skeleton",
                    200,
                    List.of(
                        new DungeonConfig.Phase(50, 1.2, 1.0),
                        new DungeonConfig.Phase(25, 1.5, 1.1)
                    )
                )
            ),
            new DungeonConfig.Rewards(
                50,
                List.of(
                    new DungeonConfig.LootEntry("minecraft:diamond", 1, 3, 0.5)
                )
            ),
            List.of(
                new DungeonConfig.ArchetypeDefinition(
                    "warrior",
                    "arcadia.archetype.warrior.name",
                    List.of("minecraft:iron_sword", "minecraft:iron_chestplate")
                ),
                new DungeonConfig.ArchetypeDefinition(
                    "mage",
                    "arcadia.archetype.mage.name",
                    List.of("minecraft:stick", "minecraft:leather_chestplate")
                ),
                new DungeonConfig.ArchetypeDefinition(
                    "archer",
                    "arcadia.archetype.archer.name",
                    List.of("minecraft:bow", "minecraft:leather_chestplate", "minecraft:arrow")
                )
            ),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            DungeonConfig.DEFAULT_LOBBY_COUNTDOWN_SECONDS,
            DungeonConfig.DEFAULT_MIN_PLAYERS,
            DungeonConfig.DEFAULT_MAX_PLAYERS
        );

        // When : serialize → deserialize
        String json = GSON.toJson(original);
        DungeonConfig roundtrip = GSON.fromJson(json, DungeonConfig.class);

        // Then : equals (records ont equals généré)
        assertEquals(original, roundtrip);
        assertEquals(2, roundtrip.schemaVersion());
        assertEquals("arcadia_dungeon:example", roundtrip.id());
        assertEquals(3, roundtrip.lives());
        assertEquals(1, roundtrip.minPlayersOrDefault());
        assertEquals(2, roundtrip.maxPlayersOrDefault());
        assertEquals(0, roundtrip.rooms().size());
        assertEquals(2, roundtrip.configuredWaves().size());
        assertEquals("ticks", roundtrip.configuredWaves().get(1).triggerMode());
        assertEquals(1, roundtrip.configuredBosses().size());
        assertEquals(2, roundtrip.configuredBosses().get(0).phases().size());
        assertEquals(50, roundtrip.rewards().currency());
        assertEquals(3, roundtrip.archetypes().size());
        assertEquals("warrior", roundtrip.archetypes().get(0).id());
    }

    @Test
    void schemaVersionConstantIsTwo() {
        assertEquals(2, DungeonConfig.CURRENT_SCHEMA_VERSION);
    }
}
