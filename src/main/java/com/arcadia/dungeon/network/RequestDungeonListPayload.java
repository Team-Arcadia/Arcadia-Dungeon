package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande la liste des donjons disponibles (Story S6.2).
 */
public record RequestDungeonListPayload() implements CustomPacketPayload {

    public static final Type<RequestDungeonListPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "request_dungeon_list")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestDungeonListPayload> CODEC =
        StreamCodec.of((buf, p) -> {}, buf -> new RequestDungeonListPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
