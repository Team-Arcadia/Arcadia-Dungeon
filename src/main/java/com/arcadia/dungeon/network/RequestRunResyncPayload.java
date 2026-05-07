package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande de resync complet d'état run (Story S3.4).
 *
 * <p>Envoyé par le client quand une désync est détectée (runId inconnu ou
 * état incohérent avec le dernier payload reçu). Rate-limité côté serveur
 * à 1 requête/5 s par joueur via {@link PacketRateLimiter}.
 */
public record RequestRunResyncPayload() implements CustomPacketPayload {

    public static final Type<RequestRunResyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "request_run_resync")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestRunResyncPayload> CODEC = StreamCodec.of(
        (buf, p) -> {},
        buf -> new RequestRunResyncPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
