package com.vyrriox.arcadiadungeon.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Consumer;

/**
 * The mod's GameTests, registered as test functions.
 *
 * <p>1.21.5 replaced the annotated method with a pair: a function registered here and a
 * {@code test_instance} file saying which structure to build it in and how long to give it. The
 * method bodies did not need to change for that, so they are the same as on the other targets.</p>
 *
 * @author vyrriox
 */
@EventBusSubscriber(modid = "arcadia_dungeon")
public final class DungeonTestFunctions {

    private DungeonTestFunctions() {}

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath("arcadia_dungeon", name);
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, DungeonTestFunctions::registerAll);
    }

    private static void registerAll(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        r.register(id("a_wave_nobody_is_near_is_not_cleared"), RunReliabilityGameTests::aWaveNobodyIsNearIsNotCleared);
        r.register(id("a_boss_nobody_is_near_can_be_hit_later"), RunReliabilityGameTests::aBossNobodyIsNearCanBeHitLater);
        r.register(id("a_wave_mob_another_mod_refused_does_not_hold_the_wave"), RunReliabilityGameTests::aWaveMobAnotherModRefusedDoesNotHoldTheWave);
        r.register(id("a_summon_another_mod_refused_does_not_protect_the_boss"), RunReliabilityGameTests::aSummonAnotherModRefusedDoesNotProtectTheBoss);
        r.register(id("summons_only_protect_the_boss_when_the_phase_says_so"), RunReliabilityGameTests::summonsOnlyProtectTheBossWhenThePhaseSaysSo);
        r.register(id("kill_summons_still_protects_the_boss"), RunReliabilityGameTests::killSummonsStillProtectsTheBoss);
        r.register(id("a_dungeon_without_waves_spawns_its_boss"), RunReliabilityGameTests::aDungeonWithoutWavesSpawnsItsBoss);
        r.register(id("killing_an_early_boss_does_not_bring_the_final_boss_early"), RunReliabilityGameTests::killingAnEarlyBossDoesNotBringTheFinalBossEarly);
        r.register(id("a_piglin_boss_does_not_convert_away_from_its_run"), RunReliabilityGameTests::aPiglinBossDoesNotConvertAwayFromItsRun);
        r.register(id("a_boss_removed_without_death_ends_the_fight"), RunReliabilityGameTests::aBossRemovedWithoutDeathEndsTheFight);
        r.register(id("a_boss_removed_by_another_mod_comes_back"), RunReliabilityGameTests::aBossRemovedByAnotherModComesBack);
        r.register(id("entities_loaded_from_disk_survive_a_run"), RunReliabilityGameTests::entitiesLoadedFromDiskSurviveARun);
        r.register(id("a_leftover_from_an_ended_run_is_removed_when_it_loads"), RunReliabilityGameTests::aLeftoverFromAnEndedRunIsRemovedWhenItLoads);
        r.register(id("a_mob_stays_in_a_carpeted_room"), RunReliabilityGameTests::aMobStaysInACarpetedRoom);
        r.register(id("a_mob_that_left_the_area_is_brought_back"), RunReliabilityGameTests::aMobThatLeftTheAreaIsBroughtBack);
        r.register(id("the_rescue_can_be_turned_off"), RunReliabilityGameTests::theRescueCanBeTurnedOff);
        r.register(id("a_mob_spawned_outside_the_area_is_left_to_walk_in"), RunReliabilityGameTests::aMobSpawnedOutsideTheAreaIsLeftToWalkIn);
        r.register(id("a_wave_nobody_touches_is_brought_to_the_players_in_steps"), RunReliabilityGameTests::aWaveNobodyTouchesIsBroughtToThePlayersInSteps);
        r.register(id("an_optional_boss_is_not_brought_to_the_players"), RunReliabilityGameTests::anOptionalBossIsNotBroughtToThePlayers);
        r.register(id("a_summon_nobody_reaches_stops_protecting"), RunReliabilityGameTests::aSummonNobodyReachesStopsProtecting);
        r.register(id("the_summon_limit_can_be_turned_off"), RunReliabilityGameTests::theSummonLimitCanBeTurnedOff);
        r.register(id("a_boss_back_from_disk_is_the_same_boss"), RunReliabilityGameTests::aBossBackFromDiskIsTheSameBoss);
        r.register(id("a_wave_mob_back_from_disk_still_counts"), RunReliabilityGameTests::aWaveMobBackFromDiskStillCounts);
        r.register(id("rewards_wait_for_a_dead_player"), RunReliabilityGameTests::rewardsWaitForADeadPlayer);
        r.register(id("leaving_a_recruitment_does_not_use_up_the_dungeon"), RunReliabilityGameTests::leavingARecruitmentDoesNotUseUpTheDungeon);
        r.register(id("warnings_are_counted_per_run"), RunReliabilityGameTests::warningsAreCountedPerRun);
        r.register(id("a_pillager_gets_the_crossbow_it_cannot_fight_without"), RunReliabilityGameTests::aPillagerGetsTheCrossbowItCannotFightWithout);
        r.register(id("attributes_are_found_under_either_name"), RunReliabilityGameTests::attributesAreFoundUnderEitherName);
        r.register(id("the_first_phase_of_a_boss_is_not_skipped"), RunReliabilityGameTests::theFirstPhaseOfABossIsNotSkipped);
        r.register(id("phase_speed_multiplies_the_configured_speed"), RunReliabilityGameTests::phaseSpeedMultipliesTheConfiguredSpeed);
        r.register(id("a_phase_command_can_summon_inside_the_area"), RunReliabilityGameTests::aPhaseCommandCanSummonInsideTheArea);
        r.register(id("only_modded_bosses_bring_their_own_mobs"), RunReliabilityGameTests::onlyModdedBossesBringTheirOwnMobs);
        r.register(id("the_validator_names_what_is_wrong"), RunReliabilityGameTests::theValidatorNamesWhatIsWrong);
        r.register(id("an_old_config_file_still_reads"), RunReliabilityGameTests::anOldConfigFileStillReads);
        r.register(id("a_player_in_a_run_cannot_start_another"), RunReliabilityGameTests::aPlayerInARunCannotStartAnother);
        r.register(id("an_empty_dungeon_refuses_to_start"), RunReliabilityGameTests::anEmptyDungeonRefusesToStart);
        r.register(id("a_dungeon_with_only_an_absent_optional_boss_ends"), RunReliabilityGameTests::aDungeonWithOnlyAnAbsentOptionalBossEnds);
        r.register(id("a_chunk_load_keeps_only_what_the_run_needs"), RunReliabilityGameTests::aChunkLoadKeepsOnlyWhatTheRunNeeds);
        r.register(id("a_required_start_boss_comes_before_the_next_one"), RunReliabilityGameTests::aRequiredStartBossComesBeforeTheNextOne);
        r.register(id("custom_damage_survives_the_armor_scaling"), RunReliabilityGameTests::customDamageSurvivesTheArmorScaling);
        r.register(id("a_boss_that_cannot_come_gives_no_reward"), RunReliabilityGameTests::aBossThatCannotComeGivesNoReward);
        r.register(id("a_boss_discarded_at_zero_health_is_defeated"), RunReliabilityGameTests::aBossDiscardedAtZeroHealthIsDefeated);
        r.register(id("a_point_inside_a_floor_goes_on_top_of_it"), RunReliabilityGameTests::aPointInsideAFloorGoesOnTopOfIt);
        r.register(id("starting_a_run_only_clears_monsters"), RunReliabilityGameTests::startingARunOnlyClearsMonsters);
        r.register(id("unlimited_lives_are_not_a_number"), RunReliabilityGameTests::unlimitedLivesAreNotANumber);
        r.register(id("every_dungeon_plays_from_start_to_finish"), FullRunGameTests::everyDungeonPlaysFromStartToFinish);
        r.register(id("what_comes_back_is_standable_and_nearby"), SpawnSafetyGameTests::whatComesBackIsStandableAndNearby);
        r.register(id("a_spawn_inside_stone_is_moved_out"), SpawnSafetyGameTests::aSpawnInsideStoneIsMovedOut);
        r.register(id("nowhere_safe_falls_back_to_the_origin"), SpawnSafetyGameTests::nowhereSafeFallsBackToTheOrigin);
        r.register(id("the_clamp_holds_whichever_corner_came_first"), SpawnSafetyGameTests::theClampHoldsWhicheverCornerCameFirst);
        r.register(id("apoint_inside_keeps_its_place"), SpawnSafetyGameTests::apointInsideKeepsItsPlace);
    }
}
