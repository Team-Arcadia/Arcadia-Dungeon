package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande de suppression d'un donjon depuis l'UI admin (Story 8.x).
 *
 * @param dungeonId identifiant du donjon à supprimer (validé côté serveur)
 */
public record DeleteDungeonPayload(String dungeonId) implements CustomPacketPayload {

    public static final Type<DeleteDungeonPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "delete_dungeon")
    );

    public static final StreamCodec<FriendlyByteBuf, DeleteDungeonPayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeUtf(p.dungeonId()),
        buf -> new DeleteDungeonPayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
