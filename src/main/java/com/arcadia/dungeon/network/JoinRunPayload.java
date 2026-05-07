package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande de rejoindre une run existante en phase STARTING (Story S3.2).
 *
 * <p>Zero Trust : le serveur re-valide runId, phase, capacité et état joueur.
 */
public record JoinRunPayload(String runId, String archetypeId) implements CustomPacketPayload {

    public static final Type<JoinRunPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "join_run")
    );

    public static final StreamCodec<FriendlyByteBuf, JoinRunPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, JoinRunPayload::runId,
        ByteBufCodecs.STRING_UTF8, JoinRunPayload::archetypeId,
        JoinRunPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
