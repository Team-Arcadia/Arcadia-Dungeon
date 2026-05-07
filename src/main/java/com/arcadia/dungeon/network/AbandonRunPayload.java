package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande d'abandon de la run courante du joueur.
 *
 * <p>Pas de champ : le serveur identifie la run via le joueur émetteur.
 * Zero Trust : validation serveur que le joueur est bien en run.
 */
public record AbandonRunPayload() implements CustomPacketPayload {

    public static final Type<AbandonRunPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "abandon_run")
    );

    public static final StreamCodec<FriendlyByteBuf, AbandonRunPayload> CODEC =
        StreamCodec.of((buf, p) -> {}, buf -> new AbandonRunPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
