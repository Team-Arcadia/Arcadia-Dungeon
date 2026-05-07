package com.arcadia.dungeon.client;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.client.screen.PlayerHubScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {

    public static final KeyMapping KEY_OPEN_HUB = new KeyMapping(
        "key.arcadia_dungeon.open_hub",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_H,
        "key.categories.arcadia_dungeon"
    );

    /** Enregistrement du keybind — mod event bus uniquement. */
    @EventBusSubscriber(value = Dist.CLIENT, modid = ArcadiaDungeon.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KEY_OPEN_HUB);
        }
    }

    /** Détection appui — NeoForge game event bus. */
    @EventBusSubscriber(value = Dist.CLIENT, modid = ArcadiaDungeon.MODID)
    public static final class GameEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            while (KEY_OPEN_HUB.consumeClick()) {
                mc.setScreen(new PlayerHubScreen());
            }
        }
    }
}
