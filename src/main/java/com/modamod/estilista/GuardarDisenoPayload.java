package com.modamod.estilista;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Guardar el diseño de la muestra con el nombre escrito en el panel de la Estilista (2026-10-08): el nombre es texto. */
public record GuardarDisenoPayload(BlockPos pos, String nombre) implements CustomPayload {

    public static final Id<GuardarDisenoPayload> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "guardar_diseno_estilista"));
    public static final PacketCodec<RegistryByteBuf, GuardarDisenoPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, GuardarDisenoPayload::pos,
            PacketCodecs.string(40), GuardarDisenoPayload::nombre,
            GuardarDisenoPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
