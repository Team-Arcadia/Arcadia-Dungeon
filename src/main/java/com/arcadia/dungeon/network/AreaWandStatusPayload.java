package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C - current visual state of the admin area wand selection. */
public record AreaWandStatusPayload(
    String dungeonId,
    boolean selecting,
    boolean pos1Set,
    boolean pos2Set,
    boolean areaSet,
    String dimension,
    int x1,
    int y1,
    int z1,
    int x2,
    int y2,
    int z2
) implements CustomPacketPayload {

    public static final Type<AreaWandStatusPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "area_wand_status")
    );

    public static final StreamCodec<FriendlyByteBuf, AreaWandStatusPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.dungeonId(), 64);
                buf.writeBoolean(p.selecting());
                buf.writeBoolean(p.pos1Set());
                buf.writeBoolean(p.pos2Set());
                buf.writeBoolean(p.areaSet());
                buf.writeUtf(p.dimension(), 256);
                buf.writeInt(p.x1());
                buf.writeInt(p.y1());
                buf.writeInt(p.z1());
                buf.writeInt(p.x2());
                buf.writeInt(p.y2());
                buf.writeInt(p.z2());
            },
            buf -> new AreaWandStatusPayload(
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(256),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
