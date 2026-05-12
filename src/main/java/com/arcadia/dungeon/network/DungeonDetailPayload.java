package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload S2C — détail complet d'un donjon pour l'écran admin (Story 8.4).
 *
 * <p>Envoyé en réponse à {@link RequestDungeonDetailPayload}.
 * Transporte assez d'infos pour peupler les quatre onglets
 * de {@code AdminDungeonDetailScreen} (Core / Boss / Rooms / Rewards).
 */
public record DungeonDetailPayload(
    // ── Core ──────────────────────────────────────────────────────────────
    String id,
    String name,
    int    schemaVersion,
    int    lives,
    String structureRef,   // "—" si null
    String dimension,      // "—" si null

    // ── Boss ──────────────────────────────────────────────────────────────
    String bossType,
    int    bossHp,
    int    phaseCount,
    int    bossCount,

    // ── Rooms ─────────────────────────────────────────────────────────────
    int roomCount,
    int totalWaveCount,

    // ── Rewards ───────────────────────────────────────────────────────────
    long  rewardCurrency,
    int   lootCount,
    int   archetypeCount

) implements CustomPacketPayload {

    public static final Type<DungeonDetailPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "dungeon_detail")
    );

    public static final StreamCodec<FriendlyByteBuf, DungeonDetailPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.id());
            buf.writeUtf(p.name());
            buf.writeInt(p.schemaVersion());
            buf.writeInt(p.lives());
            buf.writeUtf(p.structureRef());
            buf.writeUtf(p.dimension());
            buf.writeUtf(p.bossType());
            buf.writeInt(p.bossHp());
            buf.writeInt(p.phaseCount());
            buf.writeInt(p.bossCount());
            buf.writeInt(p.roomCount());
            buf.writeInt(p.totalWaveCount());
            buf.writeLong(p.rewardCurrency());
            buf.writeInt(p.lootCount());
            buf.writeInt(p.archetypeCount());
        },
        buf -> new DungeonDetailPayload(
            buf.readUtf(),   // id
            buf.readUtf(),   // name
            buf.readInt(),   // schemaVersion
            buf.readInt(),   // lives
            buf.readUtf(),   // structureRef
            buf.readUtf(),   // dimension
            buf.readUtf(),   // bossType
            buf.readInt(),   // bossHp
            buf.readInt(),   // phaseCount
            buf.readInt(),   // bossCount
            buf.readInt(),   // roomCount
            buf.readInt(),   // totalWaveCount
            buf.readLong(),  // rewardCurrency
            buf.readInt(),   // lootCount
            buf.readInt()    // archetypeCount
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
