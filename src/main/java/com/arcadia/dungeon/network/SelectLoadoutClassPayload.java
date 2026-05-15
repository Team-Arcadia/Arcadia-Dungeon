package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S - persist the player's selected global free class. */
public record SelectLoadoutClassPayload(String classId) implements CustomPacketPayload {

    public static final Type<SelectLoadoutClassPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "select_loadout_class")
    );

    public static final StreamCodec<FriendlyByteBuf, SelectLoadoutClassPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SelectLoadoutClassPayload::classId,
        SelectLoadoutClassPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
