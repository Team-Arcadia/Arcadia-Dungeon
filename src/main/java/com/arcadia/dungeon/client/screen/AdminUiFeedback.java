package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.network.SaveDungeonConfigPayload;
import com.tesseraui.TesseraToast;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Small shared helpers for admin screens that need immediate client feedback.
 */
final class AdminUiFeedback {

    private AdminUiFeedback() {}

    static void saveDungeonConfig(String dungeonId) {
        PacketDistributor.sendToServer(new SaveDungeonConfigPayload(dungeonId, DungeonEditClient.toJson()));
        TesseraToast.success(I18n.get("arcadia.admin.toast.save.sent"));
    }

    static void saveZone() {
        TesseraToast.success(I18n.get("arcadia.admin.toast.zone.saved"));
    }

    static void saveZoneConfig(String dungeonId) {
        PacketDistributor.sendToServer(new SaveDungeonConfigPayload(dungeonId, DungeonEditClient.toJson()));
        TesseraToast.success(I18n.get("arcadia.admin.toast.zone.saved"));
    }

    static void templateGenerationSent(boolean reset) {
        TesseraToast.success(I18n.get(reset ? "arcadia.admin.toast.nbt.reset.sent" : "arcadia.admin.toast.nbt.generate.sent"));
    }

    static void renderToasts(GuiGraphics graphics, int width, int height) {
        TesseraToast.render(graphics, width, height);
    }
}
