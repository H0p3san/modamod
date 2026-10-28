package com.modamod.sublimadora;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * C2S: "Guardar diseño" de la Sublimadora (2026-09-28, "cambiemos la gui de
 * la sublimadora para hacerla sintonizar con sus bloques hermanos") — copia
 * de {@code com.modamod.tinturas.GuardarDisenoTinturasPayload}: el nombre
 * es texto libre y no entra en un {@code clickButton(int)}.
 */
public record GuardarDisenoSublimadoraPayload(BlockPos pos, String nombre) implements CustomPayload {
    public static final CustomPayload.Id<GuardarDisenoSublimadoraPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Modamod.MOD_ID, "sublimadora_guardar_diseno"));

    public static final PacketCodec<RegistryByteBuf, GuardarDisenoSublimadoraPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, GuardarDisenoSublimadoraPayload::pos,
            PacketCodecs.string(24), GuardarDisenoSublimadoraPayload::nombre,
            GuardarDisenoSublimadoraPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
