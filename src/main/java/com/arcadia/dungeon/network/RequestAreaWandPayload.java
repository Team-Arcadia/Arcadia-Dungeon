package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S - start area wand selection for the current dungeon. */
public record RequestAreaWandPayload(String dungeonId) implements CustomPacketPayload {

    public static final Type<RequestAreaWandPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "request_area_wand")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestAreaWandPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.dungeonId(), 64),
            buf -> new RequestAreaWandPayload(buf.readUtf(64))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
