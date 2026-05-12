package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload S2C — snapshot des runs actives pour l'AdminMonitorScreen (Story 8.5).
 *
 * <p>Envoyé en réponse à {@link MonitorRefreshPayload} C2S.
 * Chaque entrée porte les infos nécessaires à l'affichage d'une ligne de monitoring.
 */
public record MonitorDataPayload(List<RunSummary> runs) implements CustomPacketPayload {

    public record RunSummary(
        String runId,          // UUID complet (utilisé pour les actions admin)
        String dungeonId,
        String dungeonName,
        String phase,          // RunPhase.name()
        int    currentRoomIndex,
        int    totalRooms,
        int    livesRemaining,
        int    playerCount,
        String playerNames,    // Noms séparés par " · "
        long   elapsedSeconds
    ) {}

    public static final Type<MonitorDataPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "monitor_data")
    );

    public static final StreamCodec<FriendlyByteBuf, MonitorDataPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeInt(p.runs().size());
            for (RunSummary r : p.runs()) {
                buf.writeUtf(r.runId());
                buf.writeUtf(r.dungeonId());
                buf.writeUtf(r.dungeonName());
                buf.writeUtf(r.phase());
                buf.writeInt(r.currentRoomIndex());
                buf.writeInt(r.totalRooms());
                buf.writeInt(r.livesRemaining());
                buf.writeInt(r.playerCount());
                buf.writeUtf(r.playerNames());
                buf.writeLong(r.elapsedSeconds());
            }
        },
        buf -> {
            int count = buf.readInt();
            if (count < 0 || count > 64)
                throw new io.netty.handler.codec.DecoderException("run count hors limites: " + count);
            List<RunSummary> runs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                runs.add(new RunSummary(
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readUtf(), buf.readLong()
                ));
            }
            return new MonitorDataPayload(runs);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
