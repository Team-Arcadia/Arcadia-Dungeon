package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — sauvegarde la config JSON éditée d'un donjon.
 *
 * <p>Requiert niveau op2 côté serveur.
 * Le serveur écrit le fichier JSON sur disque et recharge le registre.
 *
 * @param dungeonId  identifiant du donjon
 * @param configJson config complète sérialisée en JSON (Gson compact)
 */
public record SaveDungeonConfigPayload(String dungeonId, String configJson) implements CustomPacketPayload {

    public static final Type<SaveDungeonConfigPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "save_dungeon_config")
    );

    public static final StreamCodec<FriendlyByteBuf, SaveDungeonConfigPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.dungeonId(), 64);
                buf.writeUtf(p.configJson(), 65536);
            },
            buf -> new SaveDungeonConfigPayload(
                buf.readUtf(64),
                buf.readUtf(65536)
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
