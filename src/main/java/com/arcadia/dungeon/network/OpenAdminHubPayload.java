package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload S2C — signal d'ouverture du panneau admin (Story 8.1).
 *
 * <p>Envoyé par le serveur sur commande {@code /arcadia admin}.
 * Le client répond en ouvrant {@code AdminHubScreen}.
 */
public record OpenAdminHubPayload() implements CustomPacketPayload {

    public static final Type<OpenAdminHubPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "open_admin_hub")
    );

    public static final StreamCodec<FriendlyByteBuf, OpenAdminHubPayload> CODEC =
        StreamCodec.of((buf, p) -> {}, buf -> new OpenAdminHubPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
