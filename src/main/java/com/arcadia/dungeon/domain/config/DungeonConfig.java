package com.arcadia.dungeon.domain.config;

import java.util.List;
import java.util.Map;

/**
 * Domain aggregate - immutable dungeon JSON config.
 *
 * <p>Loaded from config/arcadia/dungeon/<id>.json during server boot,
 * hot-reload via commande {@code /arcadia reload}.
 *
 * <p>All sub-records are serializable/deserializable through Gson.
 *
 * @see <a href="../../../../../../../../_bmad-output/planning-artifacts/architecture-v1.md">architecture-v1 section 4.2</a>
 */
public record DungeonConfig(
    int schemaVersion,
    String id,
    String nameKey,
    Currency currency,
    int lives,
    List<RoomRef> rooms,
    List<Wave> waves,
    List<BossDefinition> bosses,
    Rewards rewards,
    List<ArchetypeDefinition> archetypes,
    String structureRef,  // nullable - ex: "arcadia_dungeon:chateau_defaut"
    String dimension,     // nullable - ex: "arcadia_dungeon:dungeon". Null = current admin dimension during setup
    Integer placementY,   // nullable - forced Y for NBT placement (ex: 64). Null = current admin Y
    AreaPos areaPos1,     // nullable - coin 1 de la zone globale du donjon
    AreaPos areaPos2,     // nullable - coin 2 de la zone globale du donjon
    String generationMode, // nullable - "template" or "custom"
    AreaPos generatedOrigin, // nullable - last generated NBT origin
    GeneratedSize generatedSize, // nullable - last generated NBT size
    Integer generatedSlot, // nullable - deterministic placement slot used by last generated NBT
    // Post-MVP configurable fields
    String startMessage,   // nullable - chat message broadcast when the run starts
    String victoryMessage, // nullable - victory chat message
    String failMessage,    // nullable - defeat chat message
    Integer requiredLevel, // nullable - minimum Arcadia level required to join
    Double xpMultiplier,   // nullable - Arcadia XP multiplier for this dungeon (1.0 = normal)
    Integer lobbyCountdownSeconds, // nullable - lobby launch countdown, instance regen can extend this wait
    Integer minPlayers, // nullable - minimum players required before the leader can launch
    Integer maxPlayers // nullable - maximum players in a shared lobby/run
) {

    /** Schema version supported by the current development config format. */
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int DEFAULT_LOBBY_COUNTDOWN_SECONDS = 3;
    public static final int DEFAULT_MIN_PLAYERS = 1;
    public static final int DEFAULT_MAX_PLAYERS = 2;

    public List<BossDefinition> configuredBosses() {
        return bosses != null ? bosses : List.of();
    }

    public BossDefinition primaryBoss() {
        List<BossDefinition> list = configuredBosses();
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Wave> configuredWaves() {
        return waves != null ? waves : List.of();
    }

    public boolean hasArea() {
        return areaPos1 != null && areaPos2 != null;
    }

    public boolean isInArea(String dimension, double px, double py, double pz) {
        return isInsideArea(areaPos1, areaPos2, dimension, px, py, pz);
    }

    public int lobbyCountdownSecondsOrDefault() {
        if (lobbyCountdownSeconds == null) return DEFAULT_LOBBY_COUNTDOWN_SECONDS;
        return Math.max(0, Math.min(120, lobbyCountdownSeconds));
    }

    public int minPlayersOrDefault() {
        int max = maxPlayersOrDefault();
        int value = minPlayers == null ? DEFAULT_MIN_PLAYERS : minPlayers;
        return Math.max(1, Math.min(max, value));
    }

    public int maxPlayersOrDefault() {
        int value = maxPlayers == null ? DEFAULT_MAX_PLAYERS : maxPlayers;
        return Math.max(1, Math.min(8, value));
    }

    public DungeonConfig withArea(AreaPos pos1, AreaPos pos2) {
        return new DungeonConfig(schemaVersion, id, nameKey, currency, lives, rooms, waves, bosses, rewards,
            archetypes, structureRef, dimension, placementY, pos1, pos2, generationMode, generatedOrigin,
            generatedSize, generatedSlot, startMessage, victoryMessage,
            failMessage, requiredLevel, xpMultiplier, lobbyCountdownSeconds, minPlayers, maxPlayers);
    }

    public DungeonConfig withGeneration(String structureRef,
                                        String dimension,
                                        Integer placementY,
                                        AreaPos areaPos1,
                                        AreaPos areaPos2,
                                        AreaPos generatedOrigin,
                                        GeneratedSize generatedSize,
                                        Integer generatedSlot) {
        return new DungeonConfig(schemaVersion, id, nameKey, currency, lives, rooms, waves, bosses, rewards,
            archetypes, structureRef, dimension, placementY, areaPos1, areaPos2, "template", generatedOrigin,
            generatedSize, generatedSlot, startMessage, victoryMessage, failMessage, requiredLevel,
            xpMultiplier, lobbyCountdownSeconds, minPlayers, maxPlayers);
    }

    public static boolean isInsideArea(AreaPos areaPos1, AreaPos areaPos2, String dimension,
                                       double px, double py, double pz) {
        if (areaPos1 == null || areaPos2 == null) return false;
        String expectedDim = areaPos1.dimension() != null ? areaPos1.dimension() : "";
        if (!expectedDim.equals(dimension)) return false;

        int minX = Math.min(areaPos1.x(), areaPos2.x());
        int maxX = Math.max(areaPos1.x(), areaPos2.x());
        int minY = Math.min(areaPos1.y(), areaPos2.y());
        int maxY = Math.max(areaPos1.y(), areaPos2.y());
        int minZ = Math.min(areaPos1.z(), areaPos2.z());
        int maxZ = Math.max(areaPos1.z(), areaPos2.z());

        return px >= minX && px <= maxX + 1
            && py >= minY && py <= maxY + 1
            && pz >= minZ && pz <= maxZ + 1;
    }

    // ============================================================
    // Sub-records
    // ============================================================

    /**
     * Currency Arcadia configurable par donjon.
     * @param nameKey i18n key for the display name (ex: "arcadia.currency.gears.name") or literal
     * @param iconPath chemin texture (ex: "arcadia_dungeon:textures/icons/gear.png")
     */
    public record Currency(String nameKey, String iconPath) {}

    /** Coin de zone cuboide dans une dimension. */
    public record AreaPos(String dimension, int x, int y, int z) {}

    /** Taille d'une structure generee. */
    public record GeneratedSize(int x, int y, int z) {}

    /**
     * Reserved room metadata for this dungeon.
     * Runtime wave order is configured by the top-level {@link DungeonConfig#waves()} list.
     *
     * @param id          identifiant de la salle dans le donjon (unique au donjon)
     * @param templateRef ref vers un RoomTemplate (ex: "arcadia_dungeon:rooms/entry_basic")
     */
    public record RoomRef(String id, String templateRef) {}

    /** Wave de mobs dans l'ordre global de spawn du donjon. */
    public record Wave(
        String name,
        List<MobSpawn> mobs,
        String triggerMode,
        int delayTicks,
        String startMessage,
        Boolean glowingAfterDelay,
        Integer glowingDelaySeconds
    ) {
        public boolean ticksTrigger() {
            return "ticks".equalsIgnoreCase(triggerMode);
        }
    }

    /**
     * Spawn d'un type de mob, count fois.
     * @param mobType  resourceLocation (ex: "minecraft:zombie")
     * @param count    number of instances to spawn
     */
    public record MobSpawn(
        String mobType,
        int count,
        SpawnPoint spawnPoint,
        AreaPos areaPos1,
        AreaPos areaPos2,
        String customName,
        Double health,
        Double damage,
        Double speed,
        Equipment equipment,
        Map<String, Double> customAttributes,
        CombatTuning combat
    ) {
        public MobSpawn(String mobType, int count, SpawnPoint spawnPoint) {
            this(mobType, count, spawnPoint, null, null, null, null, null, null, null, null, null);
        }
    }

    public record SpawnPoint(String dimension, double x, double y, double z) {}

    public record Equipment(String mainHand, String offHand, String helmet, String chestplate, String leggings, String boots) {}

    public record CombatTuning(
        Double attackRange,
        Integer attackCooldownMs,
        Double aggroRange,
        Integer projectileCooldownMs,
        Double dodgeChance,
        Integer dodgeCooldownMs,
        Boolean dodgeProjectilesOnly,
        String dodgeMessage
    ) {}

    /**
     * Dungeon boss definition.
     * @param type        entity resourceLocation (ex: "minecraft:wither_skeleton" or "arcadia_dungeon:custom_boss")
     * @param hp          HP max du boss
     * @param phases      transitions configurables
     */
    public record BossDefinition(
        String id,
        String type,
        int hp,
        List<Phase> phases,
        Boolean optional,
        Double spawnChance,
        Boolean requiredKill,
        List<BossReward> rewards,
        String customName,
        Double baseDamage,
        Boolean adaptivePower,
        Double healthMultiplierPerPlayer,
        Double damageMultiplierPerPlayer,
        SpawnPoint spawnPoint,
        Boolean showBossBar,
        String bossBarColor,
        String spawnMessage,
        String skipMessage,
        Integer spawnAfterWave,
        Boolean spawnAtStart,
        Equipment equipment,
        Map<String, Double> customAttributes,
        CombatTuning combat
    ) {
        public BossDefinition(String type, int hp, List<Phase> phases) {
            this(null, type, hp, phases, false, 1.0, true, List.of(),
                null, null, true, 0.5, 0.1, null, true, "RED", null, null, 0, false, null, null, null);
        }

        public String idOrDefault(int index) {
            return id != null && !id.isBlank() ? id : "boss_" + (index + 1);
        }

        public List<Phase> phasesOrEmpty() {
            return phases != null ? phases : List.of();
        }

        public boolean optionalOrDefault() {
            return optional != null && optional;
        }

        public double spawnChanceOrDefault() {
            if (spawnChance == null) return 1.0;
            return Math.max(0.0, Math.min(1.0, spawnChance));
        }

        public boolean requiredKillOrDefault() {
            return requiredKill == null || requiredKill;
        }

        public List<BossReward> rewardsOrEmpty() {
            return rewards != null ? rewards : List.of();
        }

        public boolean spawnAtStartOrDefault() {
            return spawnAtStart != null && spawnAtStart;
        }

        public int spawnAfterWaveOrDefault() {
            return spawnAfterWave != null ? Math.max(0, spawnAfterWave) : 0;
        }

        public boolean showBossBarOrDefault() {
            return showBossBar == null || showBossBar;
        }
    }

    /**
     * Drop distribue quand ce boss precis meurt.
     * @param item   resourceLocation item (ex: "minecraft:diamond")
     * @param min    quantite min
     * @param max    quantite max
     * @param chance chance par joueur, entre 0.0 et 1.0
     */
    public record BossReward(String item, int min, int max, double chance) {}

    /**
     * Phase boss avec trigger HP et multiplicateurs.
     * @param triggerHpPercent  HP threshold (ex: 50 = trigger when boss reaches 50% HP)
     * @param damageMultiplier  boss damage multiplier during this phase (1.0 = normal)
     * @param speedMultiplier   multiplicateur vitesse boss
     */
    public record Phase(
        int triggerHpPercent,
        double damageMultiplier,
        double speedMultiplier,
        String description,
        List<MobSpawn> summonMobs,
        String requiredAction,
        String phaseStartMessage,
        Boolean invulnerableDuringTransition,
        Double transitionDurationSeconds,
        Double immunityDuration,
        List<PhaseEffect> playerEffects,
        List<String> phaseCommands,
        Boolean vignetteEnabled,
        Boolean shakeEnabled,
        String vignetteColorHex,
        String soundId
    ) {
        public Phase(int triggerHpPercent, double damageMultiplier, double speedMultiplier) {
            this(triggerHpPercent, damageMultiplier, speedMultiplier, null, List.of(), "NONE", null,
                false, 2.0, 0.0, List.of(), List.of(), false, false, "#FF0000", null);
        }

        public List<MobSpawn> summonsOrEmpty() {
            return summonMobs != null ? summonMobs : List.of();
        }

        public List<PhaseEffect> effectsOrEmpty() {
            return playerEffects != null ? playerEffects : List.of();
        }

        public List<String> commandsOrEmpty() {
            return phaseCommands != null ? phaseCommands : List.of();
        }
    }

    public record PhaseEffect(String effect, int durationSeconds, int amplifier) {}

    /**
     * Rewards distributed at end of run.
     * @param currency  currency amount to credit
     * @param loot      distributed loot items
     */
    public record Rewards(long currency, List<LootEntry> loot) {}

    /**
     * Item de loot avec range min-max et chance de drop.
     * @param item    resourceLocation item (ex: "minecraft:diamond")
     * @param min     minimum quantity
     * @param max     maximum quantity (inclusive)
     * @param chance  chance par joueur, entre 0.0 et 1.0. Null = 1.0 pour les configs de dev plus anciennes.
     */
    public record LootEntry(String item, int min, int max, Double chance) {
        public double chanceOrDefault() {
            return chance == null ? 1.0 : Math.max(0.0, Math.min(1.0, chance));
        }
    }

    /**
     * Archetype = starting item kit.
     * Version MVP ULTRA simple : juste id + nameKey + items fixes (pas de stats).
     *
     * @param id        identifiant unique (ex: "warrior", "mage", "archer")
     * @param nameKey   display name i18n key
     * @param items     items du kit (resourceLocation)
     */
    public record ArchetypeDefinition(String id, String nameKey, List<String> items) {}
}
