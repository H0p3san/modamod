package com.modamod.maniqui;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** El emote de Emotecraft que baila el Maniquí (2026-10-04); {@code emote} vacío = ninguno. */
public record ElegirEmoteManiquiPayload(BlockPos pos, String emote) implements CustomPayload {

    public static final Id<ElegirEmoteManiquiPayload> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "elegir_emote_maniqui"));
    public static final PacketCodec<RegistryByteBuf, ElegirEmoteManiquiPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ElegirEmoteManiquiPayload::pos,
            PacketCodecs.string(40), ElegirEmoteManiquiPayload::emote,
            ElegirEmoteManiquiPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
