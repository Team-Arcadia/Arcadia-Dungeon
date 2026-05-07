package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload S2C — ouvre le ResultScreen avec les données de fin de run (Story S6.3).
 *
 * @param result         "VICTORY" ou "DEFEAT"
 * @param elapsedSeconds durée de la run
 * @param currencyEarned currency distribuée à ce joueur
 * @param newPb          true si nouveau record personnel
 * @param bestTimeSeconds PB courant (après cette run)
 */
public record OpenResultScreenPayload(
    String result,
    long elapsedSeconds,
    long currencyEarned,
    boolean newPb,
    long bestTimeSeconds
) implements CustomPacketPayload {

    public static final Type<OpenResultScreenPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "open_result_screen")
    );

    public static final StreamCodec<FriendlyByteBuf, OpenResultScreenPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.result());
            buf.writeLong(p.elapsedSeconds());
            buf.writeLong(p.currencyEarned());
            buf.writeBoolean(p.newPb());
            buf.writeLong(p.bestTimeSeconds());
        },
        buf -> new OpenResultScreenPayload(
            buf.readUtf(),
            buf.readLong(),
            buf.readLong(),
            buf.readBoolean(),
            buf.readLong()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
