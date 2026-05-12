package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload S2C — réponse à {@link RequestDungeonEditPayload}.
 *
 * <p>Contient la config JSON sérialisée du donjon + les coordonnées spawn
 * (lues depuis le fichier placement ou 0/0/0 si non défini).
 *
 * @param dungeonId   identifiant du donjon
 * @param configJson  config complète sérialisée en JSON (Gson compact)
 * @param spawnX      X du spawn (0.0 si non défini)
 * @param spawnY      Y du spawn (0.0 si non défini)
 * @param spawnZ      Z du spawn (0.0 si non défini)
 * @param spawnDim    dimension du spawn ("" si non défini)
 * @param spawnSet    true si les coordonnées spawn ont été configurées
 */
public record DungeonEditDataPayload(
    String dungeonId,
    String configJson,
    double spawnX,
    double spawnY,
    double spawnZ,
    String spawnDim,
    boolean spawnSet
) implements CustomPacketPayload {

    public static final Type<DungeonEditDataPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "dungeon_edit_data")
    );

    public static final StreamCodec<FriendlyByteBuf, DungeonEditDataPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.dungeonId(), 64);
                buf.writeUtf(p.configJson(), 65536);
                buf.writeDouble(p.spawnX());
                buf.writeDouble(p.spawnY());
                buf.writeDouble(p.spawnZ());
                buf.writeUtf(p.spawnDim(), 256);
                buf.writeBoolean(p.spawnSet());
            },
            buf -> new DungeonEditDataPayload(
                buf.readUtf(64),
                buf.readUtf(65536),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readUtf(256),
                buf.readBoolean()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
