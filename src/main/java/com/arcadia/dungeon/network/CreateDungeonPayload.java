package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande de création d'un donjon depuis l'UI admin (Story 8.3).
 *
 * @param id       identifiant unique, pattern [a-z0-9_]+ (validé côté serveur)
 * @param nameKey  nom affiché (ou clé i18n) du donjon
 * @param lives    nombre de vies (>= 1)
 */
public record CreateDungeonPayload(String id, String nameKey, int lives)
        implements CustomPacketPayload {

    public static final Type<CreateDungeonPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "create_dungeon")
    );

    public static final StreamCodec<FriendlyByteBuf, CreateDungeonPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.id());
            buf.writeUtf(p.nameKey());
            buf.writeInt(p.lives());
        },
        buf -> new CreateDungeonPayload(buf.readUtf(), buf.readUtf(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
