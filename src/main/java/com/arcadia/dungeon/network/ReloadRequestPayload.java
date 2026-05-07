package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande un rechargement des configs donjons (Story S6.4 — AdminHubScreen).
 *
 * <p>Requiert niveau op2 côté serveur.
 */
public record ReloadRequestPayload() implements CustomPacketPayload {

    public static final Type<ReloadRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "reload_request")
    );

    public static final StreamCodec<FriendlyByteBuf, ReloadRequestPayload> CODEC =
        StreamCodec.of((buf, p) -> {}, buf -> new ReloadRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
