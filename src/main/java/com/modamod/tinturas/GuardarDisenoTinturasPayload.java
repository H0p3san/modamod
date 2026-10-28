package com.modamod.tinturas;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * C2S: "Guardar diseño" de Tinturas (2026-09-27, mismo sistema que la
 * Modeladora) — copia exacta de {@code com.modamod.modelado.GuardarDisenoPayload},
 * como paquete propio porque el nombre es texto libre y no entra en un
 * simple {@code clickButton(int)}.
 */
public record GuardarDisenoTinturasPayload(BlockPos pos, String nombre) implements CustomPayload {
    public static final CustomPayload.Id<GuardarDisenoTinturasPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Modamod.MOD_ID, "tinturas_guardar_diseno"));

    public static final PacketCodec<RegistryByteBuf, GuardarDisenoTinturasPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, GuardarDisenoTinturasPayload::pos,
            PacketCodecs.string(24), GuardarDisenoTinturasPayload::nombre,
            GuardarDisenoTinturasPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
