package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload C2S — demande de détail d'un donjon spécifique (Story 8.4).
 *
 * <p>Envoyé par {@code AdminDungeonDetailScreen} à l'ouverture.
 * Le serveur répond avec {@link DungeonDetailPayload}.
 */
public record RequestDungeonDetailPayload(String dungeonId) implements CustomPacketPayload {

    public static final Type<RequestDungeonDetailPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "request_dungeon_detail")
    );

    public static final StreamCodec<FriendlyByteBuf, RequestDungeonDetailPayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeUtf(p.dungeonId()),
        buf -> new RequestDungeonDetailPayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
