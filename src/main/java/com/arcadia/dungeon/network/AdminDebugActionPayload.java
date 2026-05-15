package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S - privileged admin debug mutation for player progression. */
public record AdminDebugActionPayload(
    String action,
    String targetPlayer,
    String dungeonId,
    long amount,
    long timeSeconds
) implements CustomPacketPayload {

    public static final Type<AdminDebugActionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "admin_debug_action")
    );

    public static final StreamCodec<FriendlyByteBuf, AdminDebugActionPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.action());
            buf.writeUtf(p.targetPlayer());
            buf.writeUtf(p.dungeonId());
            buf.writeLong(p.amount());
            buf.writeLong(p.timeSeconds());
        },
        buf -> new AdminDebugActionPayload(
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readLong(),
            buf.readLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
