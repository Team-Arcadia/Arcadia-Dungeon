package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C aggregate for the current player's Arcadia progression.
 */
public record PlayerProgressPayload(
    long currency,
    int totalRuns,
    long bestTimeSeconds,
    List<DungeonStat> dungeons
) implements CustomPacketPayload {

    public static final Type<PlayerProgressPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "player_progress")
    );

    public record DungeonStat(String dungeonId, int completions, long bestTimeSeconds) {}

    public static final StreamCodec<FriendlyByteBuf, PlayerProgressPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeLong(p.currency());
            buf.writeInt(p.totalRuns());
            buf.writeLong(p.bestTimeSeconds());
            buf.writeInt(p.dungeons().size());
            for (DungeonStat d : p.dungeons()) {
                buf.writeUtf(d.dungeonId());
                buf.writeInt(d.completions());
                buf.writeLong(d.bestTimeSeconds());
            }
        },
        buf -> {
            long currency = buf.readLong();
            int totalRuns = buf.readInt();
            long bestTimeSeconds = buf.readLong();
            int count = buf.readInt();
            if (count < 0 || count > 512) {
                throw new io.netty.handler.codec.DecoderException("progress dungeon count out of bounds: " + count);
            }
            List<DungeonStat> dungeons = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                dungeons.add(new DungeonStat(buf.readUtf(), buf.readInt(), buf.readLong()));
            }
            return new PlayerProgressPayload(currency, totalRuns, bestTimeSeconds, dungeons);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
