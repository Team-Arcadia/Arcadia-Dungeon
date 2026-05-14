package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S - generate or reset a template-backed dungeon structure. */
public record GenerateDungeonTemplatePayload(
    String dungeonId,
    String structureRef,
    String dimension,
    int originX,
    int originY,
    int originZ,
    boolean resetExisting
) implements CustomPacketPayload {

    public static final Type<GenerateDungeonTemplatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "generate_dungeon_template")
    );

    public static final StreamCodec<FriendlyByteBuf, GenerateDungeonTemplatePayload> CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.dungeonId(), 64);
                buf.writeUtf(p.structureRef(), 256);
                buf.writeUtf(p.dimension(), 256);
                buf.writeInt(p.originX());
                buf.writeInt(p.originY());
                buf.writeInt(p.originZ());
                buf.writeBoolean(p.resetExisting());
            },
            buf -> new GenerateDungeonTemplatePayload(
                buf.readUtf(64),
                buf.readUtf(256),
                buf.readUtf(256),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
