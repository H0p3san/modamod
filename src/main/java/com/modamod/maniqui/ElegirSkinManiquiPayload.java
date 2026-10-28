package com.modamod.maniqui;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * La skin de un jugador escrita por nombre para la figura del Maniquí (2026-10-04, "elegir skin escribiendo el
 * nombre"). El servidor valida el nombre y busca el perfil (con las texturas) antes de aplicarlo.
 */
public record ElegirSkinManiquiPayload(BlockPos pos, String nombre) implements CustomPayload {

    public static final Id<ElegirSkinManiquiPayload> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "elegir_skin_maniqui"));
    public static final PacketCodec<RegistryByteBuf, ElegirSkinManiquiPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ElegirSkinManiquiPayload::pos,
            PacketCodecs.string(16), ElegirSkinManiquiPayload::nombre,
            ElegirSkinManiquiPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
