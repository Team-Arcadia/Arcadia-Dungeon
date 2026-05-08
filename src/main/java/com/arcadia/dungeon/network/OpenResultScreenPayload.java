package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload S2C — ouvre le ResultScreen avec les données de fin de run (Story S6.3).
 *
 * @param result          "VICTORY", "DEFEAT" ou "DEATH" (mort intermédiaire, respawn en cours)
 * @param elapsedSeconds  durée de la run au moment de l'événement
 * @param currencyEarned  currency distribuée à ce joueur (0 pour DEATH intermédiaire)
 * @param newPb           true si nouveau record personnel
 * @param bestTimeSeconds PB courant (0 pour DEATH intermédiaire)
 * @param respawnSeconds  délai de respawn en secondes (DEATH uniquement, 0 sinon)
 * @param dungeonId       identifiant du donjon (pour le bouton Rejouer)
 * @param lootLines       items obtenus formatés (ex: "3x Diamond"), vide pour DEFEAT/DEATH
 */
public record OpenResultScreenPayload(
    String result,
    long elapsedSeconds,
    long currencyEarned,
    boolean newPb,
    long bestTimeSeconds,
    int respawnSeconds,
    String dungeonId,
    List<String> lootLines
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
            buf.writeInt(p.respawnSeconds());
            buf.writeUtf(p.dungeonId());
            buf.writeVarInt(p.lootLines().size());
            for (String line : p.lootLines()) buf.writeUtf(line);
        },
        buf -> {
            String result       = buf.readUtf();
            long elapsed        = buf.readLong();
            long currency       = buf.readLong();
            boolean newPb       = buf.readBoolean();
            long bestTime       = buf.readLong();
            int respawnSec      = buf.readInt();
            String dungeonId    = buf.readUtf();
            int lootCount       = buf.readVarInt();
            List<String> loot   = new ArrayList<>(lootCount);
            for (int i = 0; i < lootCount; i++) loot.add(buf.readUtf());
            return new OpenResultScreenPayload(result, elapsed, currency, newPb, bestTime, respawnSec, dungeonId, loot);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
