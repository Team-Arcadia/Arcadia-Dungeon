package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S - persist the player's custom loadout items. */
public record SaveCustomLoadoutPayload(String mainItem, String offItem, String utilityItem)
    implements CustomPacketPayload {

    public static final Type<SaveCustomLoadoutPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "save_custom_loadout")
    );

    public static final StreamCodec<FriendlyByteBuf, SaveCustomLoadoutPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SaveCustomLoadoutPayload::mainItem,
        ByteBufCodecs.STRING_UTF8, SaveCustomLoadoutPayload::offItem,
        ByteBufCodecs.STRING_UTF8, SaveCustomLoadoutPayload::utilityItem,
        SaveCustomLoadoutPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
