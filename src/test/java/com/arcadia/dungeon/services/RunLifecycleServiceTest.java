package com.arcadia.dungeon.services;

import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunPhase;
import com.arcadia.dungeon.domain.run.RunResult;
import com.arcadia.dungeon.persistence.DungeonConfigLoader;
import com.arcadia.dungeon.persistence.DungeonExampleSeeder;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires RunLifecycleService (Story S2.2).
 *
 * <p>Note : startRun et les opérations de lecture ne nécessitent pas le SGT.
 * completeRun/abandonRun appellent run.completeRun() qui enforce requireSGT() —
 * en contexte JUnit (hors Minecraft), ils lèvent IllegalStateException.
 * Les tests du cycle complet (start → complete → absent) sont couverts
 * par les gametests (contexte serveur réel).
 */
class RunLifecycleServiceTest {

    private RunLifecycleService service;

    @BeforeEach
    void setup(@TempDir Path tmp) throws IOException {
        Path configDir = tmp.resolve("dungeon");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("test.json"), """
            {
              "schemaVersion": 1,
              "id": "test:dungeon",
              "nameKey": "Test Dungeon",
              "currency": { "nameKey": "Coins", "iconPath": "x:y" },
              "lives": 3,
              "rooms": [{ "id": "r1", "templateRef": "arcadia:crypt_entry", "waves": [] }],
              "bosses": [{ "id": "boss_1", "type": "minecraft:zombie", "hp": 100, "phases": [], "optional": false, "spawnChance": 1.0, "requiredKill": true, "rewards": [] }],
              "rewards": { "currency": 10, "loot": [] },
              "archetypes": []
            }
            """);
        DungeonRegistry registry = new DungeonRegistry(
            new DungeonExampleSeeder(configDir),
            new DungeonConfigLoader(configDir)
        );
        registry.bootstrap();
        service = new RunLifecycleService(registry);
    }

    @Test
    void startRun_createsRunInStartingPhase() {
        UUID playerId = UUID.randomUUID();
        Run run = service.startRun("test:dungeon", List.of(playerId));

        assertEquals(RunPhase.STARTING, run.phase());
        assertEquals("test:dungeon", run.dungeonId());
        assertEquals(3, run.livesRemaining());
        assertTrue(run.playerIds().contains(playerId));
        assertNotNull(run.id());
    }

    @Test
    void startRun_registeredInActiveRuns() {
        Run run = service.startRun("test:dungeon", List.of(UUID.randomUUID()));
        assertEquals(1, service.activeRuns().size());
        assertTrue(service.activeRuns().containsKey(run.id()));
    }

    @Test
    void startRun_unknownDungeon_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            service.startRun("unknown:dungeon", List.of(UUID.randomUUID()))
        );
    }

    @Test
    void findActiveRunForPlayer_returnsRun() {
        UUID playerId = UUID.randomUUID();
        Run run = service.startRun("test:dungeon", List.of(playerId));

        Optional<Run> found = service.findActiveRunForPlayer(playerId);
        assertTrue(found.isPresent());
        assertEquals(run.id(), found.get().id());
    }

    @Test
    void findActiveRunForPlayer_unknownPlayer_returnsEmpty() {
        service.startRun("test:dungeon", List.of(UUID.randomUUID()));
        Optional<Run> found = service.findActiveRunForPlayer(UUID.randomUUID());
        assertTrue(found.isEmpty());
    }

    @Test
    void completeRun_throwsOffSGT() {
        // En contexte JUnit (hors SGT), run.completeRun() doit lever IllegalStateException.
        // Ce comportement valide que requireSGT() est actif (cf. S2.1 AC4).
        Run run = service.startRun("test:dungeon", List.of(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () ->
            service.completeRun(run, RunResult.VICTORY)
        );
    }

    @Test
    void abandonRun_throwsOffSGT() {
        UUID playerId = UUID.randomUUID();
        Run run = service.startRun("test:dungeon", List.of(playerId));
        assertThrows(IllegalStateException.class, () ->
            service.abandonRun(run, playerId)
        );
    }

    @Test
    void multipleRuns_differentPlayers_coexist() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        service.startRun("test:dungeon", List.of(p1));
        service.startRun("test:dungeon", List.of(p2));

        assertEquals(2, service.activeRuns().size());
        assertTrue(service.findActiveRunForPlayer(p1).isPresent());
        assertTrue(service.findActiveRunForPlayer(p2).isPresent());
    }
}
