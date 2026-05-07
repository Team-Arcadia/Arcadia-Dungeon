package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.run.Run;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload S2C — état complet d'une run, envoyé à tous les joueurs de la run
 * sur chaque changement majeur (phase, salle, boss phase, mort).
 *
 * <p>Story S2.4. Agrégat-scoped (pas screen-scoped, cf. C-ARCH-4).
 * Inclut {@code serverTimestampMs} pour détection désync (Story S5.4).
 */
public record RunStatePayload(
    String runId,
    String phase,
    int currentRoomIndex,
    int currentWaveIndex,
    int livesRemaining,
    long startTimestampMs,
    boolean hasBoss,
    String bossType,
    int bossHpCurrent,
    int bossHpMax,
    int bossPhaseIndex,
    long serverTimestampMs
) implements CustomPacketPayload {

    public static final Type<RunStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "run_state")
    );

    public static final StreamCodec<FriendlyByteBuf, RunStatePayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.runId());
            buf.writeUtf(p.phase());
            buf.writeInt(p.currentRoomIndex());
            buf.writeInt(p.currentWaveIndex());
            buf.writeInt(p.livesRemaining());
            buf.writeLong(p.startTimestampMs());
            buf.writeBoolean(p.hasBoss());
            if (p.hasBoss()) {
                buf.writeUtf(p.bossType());
                buf.writeInt(p.bossHpCurrent());
                buf.writeInt(p.bossHpMax());
                buf.writeInt(p.bossPhaseIndex());
            }
            buf.writeLong(p.serverTimestampMs());
        },
        buf -> {
            String runId = buf.readUtf();
            String phase = buf.readUtf();
            int roomIndex = buf.readInt();
            int waveIndex = buf.readInt();
            int lives = buf.readInt();
            long startMs = buf.readLong();
            boolean hasBoss = buf.readBoolean();
            String bossType = "";
            int bossHpCur = 0, bossHpMax = 0, bossPhaseIdx = 0;
            if (hasBoss) {
                bossType = buf.readUtf();
                bossHpCur = buf.readInt();
                bossHpMax = buf.readInt();
                bossPhaseIdx = buf.readInt();
            }
            long serverMs = buf.readLong();
            return new RunStatePayload(runId, phase, roomIndex, waveIndex,
                lives, startMs, hasBoss, bossType, bossHpCur, bossHpMax, bossPhaseIdx, serverMs);
        }
    );

    /** Construit un payload depuis un agrégat Run vivant (server-side). */
    public static RunStatePayload from(Run run) {
        boolean hasBoss = run.bossState() != null;
        return new RunStatePayload(
            run.id().toString(),
            run.phase().name(),
            run.currentRoomIndex(),
            run.currentWaveIndex(),
            run.livesRemaining(),
            run.startTimestampMs(),
            hasBoss,
            hasBoss ? run.bossState().type() : "",
            hasBoss ? run.bossState().hpCurrent() : 0,
            hasBoss ? run.bossState().hpMax() : 0,
            hasBoss ? run.bossState().currentPhaseIndex() : 0,
            System.currentTimeMillis()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
