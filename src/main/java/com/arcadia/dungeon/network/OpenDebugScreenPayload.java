package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload S2C — signal d'ouverture du debug screen (Story S2.6).
 * Envoyé par le serveur sur commande {@code /arcadia debug showscreen}.
 */
public record OpenDebugScreenPayload() implements CustomPacketPayload {

    public static final Type<OpenDebugScreenPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "open_debug_screen")
    );

    public static final StreamCodec<FriendlyByteBuf, OpenDebugScreenPayload> CODEC =
        StreamCodec.of((buf, p) -> {}, buf -> new OpenDebugScreenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
