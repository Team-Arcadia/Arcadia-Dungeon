package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C - progress feedback for async dungeon NBT placement jobs. */
public record StructurePlacementStatusPayload(
    String dungeonId,
    String stage,
    int processed,
    int total,
    boolean done,
    boolean success,
    String message
) implements CustomPacketPayload {

    public static final Type<StructurePlacementStatusPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "structure_placement_status")
    );

    public static final StreamCodec<FriendlyByteBuf, StructurePlacementStatusPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.dungeonId(), 64);
                buf.writeUtf(p.stage(), 32);
                buf.writeInt(p.processed());
                buf.writeInt(p.total());
                buf.writeBoolean(p.done());
                buf.writeBoolean(p.success());
                buf.writeUtf(p.message(), 256);
            },
            buf -> new StructurePlacementStatusPayload(
                buf.readUtf(64),
                buf.readUtf(32),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(256)
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
