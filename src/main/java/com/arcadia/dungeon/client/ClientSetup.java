package com.arcadia.dungeon.client;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.client.hud.RunOverlayHud;
import com.arcadia.dungeon.client.screen.PlayerHubScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.tesseraui.TesseraHotReload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

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
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KEY_OPEN_HUB);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            TesseraHotReload.tryEnable(!net.neoforged.fml.loading.FMLEnvironment.production);
        }

        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "run_hud"),
                RunOverlayHud.INSTANCE
            );
            ArcadiaDungeon.LOGGER.info("[Arcadia][BOOT] HUD layer registered: run_hud");
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

        @SubscribeEvent
        public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
            if (VanillaGuiLayers.HOTBAR.equals(event.getName()) && RunOverlayHud.shouldReplaceVanillaHotbar()) {
                event.setCanceled(true);
                return;
            }
            if (RunOverlayHud.shouldReplaceVanillaSurvivalBars()
                && (VanillaGuiLayers.PLAYER_HEALTH.equals(event.getName())
                    || VanillaGuiLayers.FOOD_LEVEL.equals(event.getName())
                    || VanillaGuiLayers.ARMOR_LEVEL.equals(event.getName()))) {
                event.setCanceled(true);
            }
        }
    }
}
