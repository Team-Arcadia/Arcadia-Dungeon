package com.vyrriox.arcadiadungeon.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * What differs from one game version to another, so the scenarios read the same on every target.
 *
 * @author vyrriox
 */
final class RunTestKit {

    private RunTestKit() {}

    /** Thrown inside a sequence's wait: the step is tried again on the next tick. */
    static RuntimeException waiting(GameTestHelper h, String why) {
        return new GameTestAssertException(why);
    }

    static ServerPlayer fakePlayer(GameTestHelper h, String name) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    static String dimension(GameTestHelper h) {
        return h.getLevel().dimension().location().toString();
    }

    static Path gameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    static <T extends Entity> T create(GameTestHelper h, EntityType<T> type) {
        return type.create(h.getLevel());
    }

    static Set<String> tags(Entity entity) {
        return entity.getTags();
    }

    static void hurt(GameTestHelper h, LivingEntity entity, float amount) {
        entity.hurt(h.getLevel().damageSources().generic(), amount);
    }

    static void hitAs(GameTestHelper h, ServerPlayer player, LivingEntity entity, float amount) {
        entity.hurt(h.getLevel().damageSources().playerAttack(player), amount);
    }

    static void kill(GameTestHelper h, LivingEntity entity) {
        entity.kill();
    }

    /** Whether the world refuses this entity, as it would on spawning or on loading from disk. */
    static boolean joinRefused(GameTestHelper h, Entity entity, boolean fromDisk) {
        return MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(entity, h.getLevel(), fromDisk));
    }

    /** Refuses this type of mob while the condition holds, the way a spawn-control mod would. */
    static void refuseWhile(EntityType<?> type, BooleanSupplier condition) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, event -> {
            if (event.getEntity().getType() == type && condition.getAsBoolean()) event.setCanceled(true);
        });
    }
}
