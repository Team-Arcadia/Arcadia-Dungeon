package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** S2C - shows a translated Arcadia toast on the client. */
public record ArcadiaToastPayload(String variant, String translationKey, List<String> args, int durationMs)
    implements CustomPacketPayload {

    public static final Type<ArcadiaToastPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "toast")
    );

    public static final StreamCodec<FriendlyByteBuf, ArcadiaToastPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.variant(), 16);
            buf.writeUtf(payload.translationKey(), 128);
            List<String> safeArgs = payload.args() != null ? payload.args() : List.of();
            buf.writeInt(safeArgs.size());
            for (String arg : safeArgs) {
                buf.writeUtf(arg != null ? arg : "", 256);
            }
            buf.writeInt(payload.durationMs());
        },
        buf -> {
            String variant = buf.readUtf(16);
            String translationKey = buf.readUtf(128);
            int count = buf.readInt();
            if (count < 0 || count > 8) {
                throw new io.netty.handler.codec.DecoderException("toast arg count out of bounds: " + count);
            }
            List<String> args = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                args.add(buf.readUtf(256));
            }
            int durationMs = Math.max(500, Math.min(10_000, buf.readInt()));
            return new ArcadiaToastPayload(variant, translationKey, args, durationMs);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
