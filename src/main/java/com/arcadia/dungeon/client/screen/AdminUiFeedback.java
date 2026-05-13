package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.network.SaveDungeonConfigPayload;
import com.tesseraui.TesseraToast;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Small shared helpers for admin screens that need immediate client feedback.
 */
final class AdminUiFeedback {

    private AdminUiFeedback() {}

    static void saveDungeonConfig(String dungeonId) {
        PacketDistributor.sendToServer(new SaveDungeonConfigPayload(dungeonId, DungeonEditClient.toJson()));
        TesseraToast.success("Sauvegarde envoyee");
    }

    static void saveZone() {
        TesseraToast.success("Zone sauvegardee");
    }

    static void renderToasts(GuiGraphics graphics, int width, int height) {
        TesseraToast.render(graphics, width, height);
    }
}
