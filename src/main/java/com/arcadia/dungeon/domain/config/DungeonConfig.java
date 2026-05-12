package com.arcadia.dungeon.domain.config;

import java.util.List;

/**
 * Agrégat domain — config JSON immutable d'un donjon.
 *
 * <p>Chargée depuis {@code config/arcadia/dungeon/<id>.json} au boot serveur,
 * hot-reload via commande {@code /arcadia reload}.
 *
 * <p>Tous les sub-records sont also sérialisables/désérialisables via Gson.
 *
 * @see <a href="../../../../../../../../_bmad-output/planning-artifacts/architecture-v1.md">architecture-v1 §4.2</a>
 */
public record DungeonConfig(
    int schemaVersion,
    String id,
    String nameKey,
    Currency currency,
    int lives,
    List<RoomRef> rooms,
    List<BossDefinition> bosses,
    Rewards rewards,
    List<ArchetypeDefinition> archetypes,
    String structureRef,  // nullable — ex: "arcadia_dungeon:chateau_defaut"
    String dimension,     // nullable — ex: "arcadia_dungeon:dungeon". Null = dimension courante de l'admin au setup
    Integer placementY,   // nullable — Y forcé pour le placement NBT (ex: 64). Null = Y courant de l'admin
    // ── Post-MVP configurable fields ──────────────────────────────────────────
    String startMessage,   // nullable — message diffusé en chat au début du run
    String victoryMessage, // nullable — message diffusé sur victoire
    String failMessage,    // nullable — message diffusé sur défaite
    Integer requiredLevel, // nullable — niveau Arcadia minimum requis pour rejoindre
    Double xpMultiplier    // nullable — multiplicateur XP Arcadia pour ce donjon (1.0 = normal)
) {

    /** Schema version supportée en MVP. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public List<BossDefinition> configuredBosses() {
        return bosses != null ? bosses : List.of();
    }

    public BossDefinition primaryBoss() {
        List<BossDefinition> list = configuredBosses();
        return list.isEmpty() ? null : list.get(0);
    }

    // ============================================================
    // Sub-records (cohérents avec architecture-v1.md §4.2)
    // ============================================================

    /**
     * Currency Arcadia configurable par donjon.
     * @param nameKey clé i18n du nom (ex: "arcadia.currency.gears.name") ou literal
     * @param iconPath chemin texture (ex: "arcadia_dungeon:textures/icons/gear.png")
     */
    public record Currency(String nameKey, String iconPath) {}

    /**
     * Référence à un {@link com.arcadia.dungeon.domain.room.RoomTemplate} pour ce donjon.
     * Permet d'override les waves par donjon sans dupliquer le template.
     *
     * @param id          identifiant de la salle dans le donjon (unique au donjon)
     * @param templateRef ref vers un RoomTemplate (ex: "arcadia_dungeon:rooms/entry_basic")
     * @param waves       waves de mobs spécifiques à cette salle
     */
    public record RoomRef(String id, String templateRef, List<Wave> waves) {}

    /** Wave de mobs dans une salle. */
    public record Wave(List<MobSpawn> mobs, int delayTicks) {}

    /**
     * Spawn d'un type de mob, count fois.
     * @param mobType  resourceLocation (ex: "minecraft:zombie")
     * @param count    nombre d'instances à spawn
     */
    public record MobSpawn(String mobType, int count) {}

    /**
     * Définition du boss du donjon.
     * @param type        resourceLocation entité (ex: "minecraft:wither_skeleton" ou "arcadia_dungeon:custom_boss")
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
        List<BossReward> rewards
    ) {
        public BossDefinition(String type, int hp, List<Phase> phases) {
            this(null, type, hp, phases, false, 1.0, true, List.of());
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
     * @param triggerHpPercent  HP threshold (ex: 50 = trigger quand boss à 50% HP)
     * @param damageMultiplier  multiplicateur dégâts boss durant cette phase (1.0 = normal)
     * @param speedMultiplier   multiplicateur vitesse boss
     */
    public record Phase(int triggerHpPercent, double damageMultiplier, double speedMultiplier) {}

    /**
     * Récompenses distribuées à fin de run.
     * @param currency  montant currency à créditer (chaque joueur OU group selon design)
     * @param loot      items distribués (shared multi)
     */
    public record Rewards(long currency, List<LootEntry> loot) {}

    /**
     * Item de loot avec range min-max.
     * @param item  resourceLocation item (ex: "minecraft:diamond")
     * @param min   quantité min
     * @param max   quantité max (inclusif)
     */
    public record LootEntry(String item, int min, int max) {}

    /**
     * Archétype = kit d'items de départ.
     * Version MVP ULTRA simple : juste id + nameKey + items fixes (pas de stats).
     *
     * @param id        identifiant unique (ex: "warrior", "mage", "archer")
     * @param nameKey   clé i18n du nom affiché
     * @param items     items du kit (resourceLocation)
     */
    public record ArchetypeDefinition(String id, String nameKey, List<String> items) {}
}
