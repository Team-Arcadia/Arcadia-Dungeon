package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — force la fin d'une run par un admin (Story 8.5).
 *
 * <p>Requiert op2. {@code success = true} → VICTORY, {@code success = false} → DEFEAT.
 */
public record ForceEndRunPayload(String runId, boolean success) implements CustomPacketPayload {

    public static final Type<ForceEndRunPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "force_end_run")
    );

    public static final StreamCodec<FriendlyByteBuf, ForceEndRunPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.runId());
            buf.writeBoolean(p.success());
        },
        buf -> new ForceEndRunPayload(buf.readUtf(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
