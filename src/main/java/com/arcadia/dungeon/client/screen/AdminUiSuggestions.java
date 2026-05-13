package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.ArcadiaDungeon;

/**
 * Common autocomplete pools for TesseraUI admin forms.
 *
 * <p>TesseraUI currently accepts suggestion lists as a flat separated string
 * from the model, so these constants intentionally stay compact and stable.</p>
 */
final class AdminUiSuggestions {

    static final String DEFAULT_DIMENSION = ArcadiaDungeon.DUNGEON_DIMENSION_ID;

    static final String DIMENSIONS = String.join(",",
        ArcadiaDungeon.DUNGEON_DIMENSION_ID,
        "minecraft:overworld",
        "minecraft:the_nether",
        "minecraft:the_end"
    );

    static final String ENTITIES = String.join(",",
        "minecraft:zombie",
        "minecraft:skeleton",
        "minecraft:spider",
        "minecraft:cave_spider",
        "minecraft:husk",
        "minecraft:stray",
        "minecraft:drowned",
        "minecraft:pillager",
        "minecraft:vindicator",
        "minecraft:evoker",
        "minecraft:witch",
        "minecraft:slime",
        "minecraft:magma_cube",
        "minecraft:blaze",
        "minecraft:wither_skeleton",
        "minecraft:enderman",
        "minecraft:warden"
    );

    static final String ITEMS = String.join(",",
        "minecraft:wooden_sword",
        "minecraft:stone_sword",
        "minecraft:iron_sword",
        "minecraft:diamond_sword",
        "minecraft:netherite_sword",
        "minecraft:bow",
        "minecraft:crossbow",
        "minecraft:shield",
        "minecraft:leather_helmet",
        "minecraft:chainmail_helmet",
        "minecraft:iron_helmet",
        "minecraft:diamond_helmet",
        "minecraft:netherite_helmet",
        "minecraft:leather_chestplate",
        "minecraft:chainmail_chestplate",
        "minecraft:iron_chestplate",
        "minecraft:diamond_chestplate",
        "minecraft:netherite_chestplate",
        "minecraft:leather_leggings",
        "minecraft:iron_leggings",
        "minecraft:diamond_leggings",
        "minecraft:netherite_leggings",
        "minecraft:leather_boots",
        "minecraft:iron_boots",
        "minecraft:diamond_boots",
        "minecraft:netherite_boots",
        "minecraft:bread",
        "minecraft:golden_apple",
        "minecraft:diamond",
        "minecraft:emerald",
        "minecraft:experience_bottle"
    );

    static final String ATTRIBUTES = String.join(",",
        "minecraft:generic.max_health",
        "minecraft:generic.attack_damage",
        "minecraft:generic.attack_speed",
        "minecraft:generic.movement_speed",
        "minecraft:generic.armor",
        "minecraft:generic.armor_toughness",
        "minecraft:generic.knockback_resistance",
        "minecraft:generic.follow_range",
        "minecraft:generic.scale"
    );

    static final String EFFECTS = String.join(",",
        "minecraft:speed",
        "minecraft:slowness",
        "minecraft:strength",
        "minecraft:weakness",
        "minecraft:resistance",
        "minecraft:regeneration",
        "minecraft:poison",
        "minecraft:wither",
        "minecraft:blindness",
        "minecraft:glowing"
    );

    private AdminUiSuggestions() {
    }
}
