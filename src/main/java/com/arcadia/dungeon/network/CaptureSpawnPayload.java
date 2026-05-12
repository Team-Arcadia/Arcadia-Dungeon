package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — capture le spawn à la position courante de l'admin (Zero Trust).
 *
 * <p>Le client envoie seulement l'ID du donjon ; le serveur lit les coordonnées
 * directement depuis {@code player.getX/Y/Z()} et {@code player.level().dimension()}.
 * Cela évite tout spoofing de coordonnées depuis le client.
 *
 * <p>Requiert niveau op2 côté serveur.
 *
 * @param dungeonId identifiant du donjon
 */
public record CaptureSpawnPayload(String dungeonId) implements CustomPacketPayload {

    public static final Type<CaptureSpawnPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "capture_spawn")
    );

    public static final StreamCodec<FriendlyByteBuf, CaptureSpawnPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.dungeonId(), 64),
            buf      -> new CaptureSpawnPayload(buf.readUtf(64))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
