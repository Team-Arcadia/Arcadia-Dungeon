package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — définit manuellement les coordonnées spawn d'un donjon.
 *
 * <p>Requiert niveau op2 côté serveur.
 * Complément de {@link CaptureSpawnPayload} (capture automatique depuis la position du joueur).
 *
 * @param dungeonId identifiant du donjon
 * @param x         coordonnée X du spawn
 * @param y         coordonnée Y du spawn
 * @param z         coordonnée Z du spawn
 * @param dimension identifiant de dimension (ex: "minecraft:overworld")
 */
public record SaveZonePayload(String dungeonId, double x, double y, double z, String dimension)
        implements CustomPacketPayload {

    public static final Type<SaveZonePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "save_zone")
    );

    public static final StreamCodec<FriendlyByteBuf, SaveZonePayload> CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.dungeonId(), 64);
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeUtf(p.dimension(), 256);
            },
            buf -> new SaveZonePayload(
                buf.readUtf(64),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readUtf(256)
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
