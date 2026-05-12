package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande un rafraîchissement des données de monitoring (Story 8.5).
 *
 * <p>Requiert op2 côté serveur. Rate-limité à 1 req / 2 s par joueur.
 */
public record MonitorRefreshPayload() implements CustomPacketPayload {

    public static final Type<MonitorRefreshPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "monitor_refresh")
    );

    public static final StreamCodec<FriendlyByteBuf, MonitorRefreshPayload> CODEC =
        StreamCodec.of((buf, p) -> {}, buf -> new MonitorRefreshPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
