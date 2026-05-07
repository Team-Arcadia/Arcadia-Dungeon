package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload S2C — liste des donjons disponibles (Story S6.2).
 *
 * <p>Envoyé en réponse à {@link RequestDungeonListPayload} C2S.
 * Chaque entrée porte l'id, le nameKey (affiché direct), la version de schéma
 * et les archétypes disponibles.
 */
public record DungeonListPayload(List<DungeonSummary> dungeons) implements CustomPacketPayload {

    public static final Type<DungeonListPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "dungeon_list")
    );

    public record DungeonSummary(String id, String name, int schemaVersion,
                                 List<ArchetypeSummary> archetypes) {}

    public record ArchetypeSummary(String id, String nameKey) {}

    public static final StreamCodec<FriendlyByteBuf, DungeonListPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeInt(p.dungeons().size());
            for (DungeonSummary d : p.dungeons()) {
                buf.writeUtf(d.id());
                buf.writeUtf(d.name());
                buf.writeInt(d.schemaVersion());
                buf.writeInt(d.archetypes().size());
                for (ArchetypeSummary a : d.archetypes()) {
                    buf.writeUtf(a.id());
                    buf.writeUtf(a.nameKey());
                }
            }
        },
        buf -> {
            int count = buf.readInt();
            List<DungeonSummary> dungeons = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = buf.readUtf();
                String name = buf.readUtf();
                int schemaVersion = buf.readInt();
                int archCount = buf.readInt();
                List<ArchetypeSummary> archetypes = new ArrayList<>(archCount);
                for (int j = 0; j < archCount; j++) {
                    archetypes.add(new ArchetypeSummary(buf.readUtf(), buf.readUtf()));
                }
                dungeons.add(new DungeonSummary(id, name, schemaVersion, archetypes));
            }
            return new DungeonListPayload(dungeons);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
