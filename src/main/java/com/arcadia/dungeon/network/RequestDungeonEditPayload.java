package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande la config complète d'un donjon pour l'édition admin.
 *
 * <p>Requiert niveau op2 côté serveur.
 * Le serveur répond avec {@link DungeonEditDataPayload}.
 */
public record RequestDungeonEditPayload(String dungeonId) implements CustomPacketPayload {

    public static final Type<RequestDungeonEditPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "request_dungeon_edit")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestDungeonEditPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.dungeonId(), 64),
            buf      -> new RequestDungeonEditPayload(buf.readUtf(64))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
