package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.run.Run;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    int totalRooms,
    long startTimestampMs,
    boolean hasBoss,
    String bossType,
    int bossHpCurrent,
    int bossHpMax,
    int bossPhaseIndex,
    List<String> playerNames,
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
            buf.writeInt(p.totalRooms());
            buf.writeLong(p.startTimestampMs());
            buf.writeBoolean(p.hasBoss());
            if (p.hasBoss()) {
                buf.writeUtf(p.bossType());
                buf.writeInt(p.bossHpCurrent());
                buf.writeInt(p.bossHpMax());
                buf.writeInt(p.bossPhaseIndex());
            }
            buf.writeInt(p.playerNames().size());
            for (String playerName : p.playerNames()) {
                buf.writeUtf(playerName);
            }
            buf.writeLong(p.serverTimestampMs());
        },
        buf -> {
            String runId = buf.readUtf();
            String phase = buf.readUtf();
            int roomIndex = buf.readInt();
            int waveIndex = buf.readInt();
            int lives = buf.readInt();
            int totalRooms = buf.readInt();
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
            int playerNameCount = buf.readInt();
            List<String> playerNames = new ArrayList<>(playerNameCount);
            for (int i = 0; i < playerNameCount; i++) {
                playerNames.add(buf.readUtf());
            }
            long serverMs = buf.readLong();
            return new RunStatePayload(runId, phase, roomIndex, waveIndex,
                lives, totalRooms, startMs, hasBoss, bossType, bossHpCur, bossHpMax, bossPhaseIdx, playerNames, serverMs);
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
            run.totalRooms(),
            run.startTimestampMs(),
            hasBoss,
            hasBoss ? run.bossState().type() : "",
            hasBoss ? run.bossState().hpCurrent() : 0,
            hasBoss ? run.bossState().hpMax() : 0,
            hasBoss ? run.bossState().currentPhaseIndex() : 0,
            resolvePlayerNames(run),
            System.currentTimeMillis()
        );
    }

    private static List<String> resolvePlayerNames(Run run) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        List<String> names = new ArrayList<>();
        for (UUID playerId : run.playerIds()) {
            ServerPlayer player = server != null ? server.getPlayerList().getPlayer(playerId) : null;
            names.add(player != null ? player.getGameProfile().getName() : playerId.toString().substring(0, 8));
        }
        return List.copyOf(names);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
