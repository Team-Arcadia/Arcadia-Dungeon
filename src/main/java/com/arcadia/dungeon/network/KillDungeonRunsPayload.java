package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — termine de force toutes les runs actives d'un donjon (debug admin).
 *
 * <p>Utilisé depuis l'écran debug admin pour nettoyer les runs bloquées.
 * Requiert niveau op2 côté serveur.
 *
 * @param dungeonId identifiant du donjon dont les runs doivent être terminées
 */
public record KillDungeonRunsPayload(String dungeonId) implements CustomPacketPayload {

    public static final Type<KillDungeonRunsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "kill_dungeon_runs")
    );

    public static final StreamCodec<FriendlyByteBuf, KillDungeonRunsPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.dungeonId(), 64),
            buf      -> new KillDungeonRunsPayload(buf.readUtf(64))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
