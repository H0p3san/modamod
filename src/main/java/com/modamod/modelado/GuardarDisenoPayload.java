package com.modamod.modelado;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * C2S: "Guardar diseño" (2026-09-27, concepto de casillero de nombre) — el
 * nombre escrito en el {@code TextFieldWidget} de {@code ModeladoScreen} no
 * entra en un simple {@code clickButton(int)} (ver el resto de botones de
 * esta pantalla), así que este es el único click de la Mesa de Modelado que
 * viaja como paquete propio en vez de {@code ScreenHandler#onButtonClick}.
 */
public record GuardarDisenoPayload(BlockPos pos, String nombre) implements CustomPayload {
    public static final CustomPayload.Id<GuardarDisenoPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Modamod.MOD_ID, "modelado_guardar_diseno"));

    public static final PacketCodec<RegistryByteBuf, GuardarDisenoPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, GuardarDisenoPayload::pos,
            PacketCodecs.string(24), GuardarDisenoPayload::nombre,
            GuardarDisenoPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
