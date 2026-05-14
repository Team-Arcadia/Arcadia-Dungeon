package com.arcadia.dungeon.client;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.client.screen.PlayerHubScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.tesseraui.TesseraHotReload;
import com.tesseraui.TesseraTemplate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ClientSetup {

    public static final KeyMapping KEY_OPEN_HUB = new KeyMapping(
        "key.arcadia_dungeon.open_hub",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_H,
        "key.categories.arcadia_dungeon"
    );

    /** Enregistrement du keybind + init TesseraUI, mod event bus uniquement. */
    @EventBusSubscriber(value = Dist.CLIENT, modid = ArcadiaDungeon.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private static boolean adminStylesRegistered;

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KEY_OPEN_HUB);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            TesseraHotReload.tryEnable(!net.neoforged.fml.loading.FMLEnvironment.production);
            registerAdminGlobalStyles();
        }

        private static void registerAdminGlobalStyles() {
            if (adminStylesRegistered) return;
            adminStylesRegistered = true;

            try (var stream = ClientSetup.class.getResourceAsStream(
                    "/assets/" + ArcadiaDungeon.MODID + "/ui/admin/admin-global.css")) {
                if (stream == null) {
                    ArcadiaDungeon.LOGGER.warn("[Arcadia][UI] ui/admin/admin-global.css not found; admin screens will use Tessera defaults");
                    return;
                }
                TesseraTemplate.addGlobalStylesheet(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                ArcadiaDungeon.LOGGER.info("[Arcadia][UI] Registered TesseraUI global admin stylesheet");
            } catch (IOException e) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia][UI] Failed to load ui/admin/admin-global.css", e);
            }
        }
    }

    /** Detection appui, NeoForge game event bus. */
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
