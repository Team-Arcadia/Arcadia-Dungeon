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
public record DungeonListPayload(List<DungeonSummary> dungeons, List<ClassSummary> globalClasses) implements CustomPacketPayload {

    public static final Type<DungeonListPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "dungeon_list")
    );

    public record DungeonSummary(String id, String name, int schemaVersion,
                                 int minPlayers, int maxPlayers,
                                 List<ArchetypeSummary> archetypes) {}

    public record ArchetypeSummary(String id, String nameKey) {}

    public record ClassSummary(String id, String nameKey, List<String> items) {}

    public static final StreamCodec<FriendlyByteBuf, DungeonListPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeInt(p.globalClasses().size());
            for (ClassSummary c : p.globalClasses()) {
                buf.writeUtf(c.id());
                buf.writeUtf(c.nameKey());
                buf.writeInt(c.items().size());
                for (String item : c.items()) {
                    buf.writeUtf(item);
                }
            }
            buf.writeInt(p.dungeons().size());
            for (DungeonSummary d : p.dungeons()) {
                buf.writeUtf(d.id());
                buf.writeUtf(d.name());
                buf.writeInt(d.schemaVersion());
                buf.writeInt(d.minPlayers());
                buf.writeInt(d.maxPlayers());
                buf.writeInt(d.archetypes().size());
                for (ArchetypeSummary a : d.archetypes()) {
                    buf.writeUtf(a.id());
                    buf.writeUtf(a.nameKey());
                }
            }
        },
        buf -> {
            int classCount = buf.readInt();
            if (classCount < 0 || classCount > 64)
                throw new io.netty.handler.codec.DecoderException("classCount hors limites: " + classCount);
            List<ClassSummary> classes = new ArrayList<>(classCount);
            for (int i = 0; i < classCount; i++) {
                String id = buf.readUtf();
                String nameKey = buf.readUtf();
                int itemCount = buf.readInt();
                if (itemCount < 0 || itemCount > 16)
                    throw new io.netty.handler.codec.DecoderException("itemCount hors limites: " + itemCount);
                List<String> items = new ArrayList<>(itemCount);
                for (int j = 0; j < itemCount; j++) {
                    items.add(buf.readUtf());
                }
                classes.add(new ClassSummary(id, nameKey, items));
            }
            int count = buf.readInt();
            if (count < 0 || count > 256)
                throw new io.netty.handler.codec.DecoderException("count hors limites: " + count);
            List<DungeonSummary> dungeons = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = buf.readUtf();
                String name = buf.readUtf();
                int schemaVersion = buf.readInt();
                int minPlayers = buf.readInt();
                int maxPlayers = buf.readInt();
                int archCount = buf.readInt();
                if (archCount < 0 || archCount > 32)
                    throw new io.netty.handler.codec.DecoderException("archCount hors limites: " + archCount);
                List<ArchetypeSummary> archetypes = new ArrayList<>(archCount);
                for (int j = 0; j < archCount; j++) {
                    archetypes.add(new ArchetypeSummary(buf.readUtf(), buf.readUtf()));
                }
                dungeons.add(new DungeonSummary(id, name, schemaVersion, minPlayers, maxPlayers, archetypes));
            }
            return new DungeonListPayload(dungeons, classes);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
