package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande de démarrage d'une run (Story S2.5).
 *
 * <p>Zero Trust : le serveur re-valide dungeonId et archetypeId.
 * Aucune valeur de gameplay dans le payload (cf. C-ARCH-4, archi §6.3).
 */
public record StartRunPayload(String dungeonId, String archetypeId) implements CustomPacketPayload {

    public static final Type<StartRunPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "start_run")
    );

    public static final StreamCodec<FriendlyByteBuf, StartRunPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, StartRunPayload::dungeonId,
        ByteBufCodecs.STRING_UTF8, StartRunPayload::archetypeId,
        StartRunPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
