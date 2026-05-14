package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S - saves the global free-class catalog.
 */
public record SaveGlobalClassesPayload(String classesJson) implements CustomPacketPayload {

    public static final Type<SaveGlobalClassesPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "save_global_classes")
    );

    public static final StreamCodec<FriendlyByteBuf, SaveGlobalClassesPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.classesJson(), 32768),
            buf -> new SaveGlobalClassesPayload(buf.readUtf(32768))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
