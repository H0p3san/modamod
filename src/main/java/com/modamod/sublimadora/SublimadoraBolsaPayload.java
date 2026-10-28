package com.modamod.sublimadora;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * C2S (2026-10-13): lo que se hace con las bolsas de la Sublimadora. {@code tipo}: 0 fotos, 1 máscaras. ELEGIR pone la
 * entrada {@code indice} de la bolsa en la cara elegida (foto) o en el casillero de la máscara; SACAR la devuelve al
 * inventario; QUITAR vacía lo elegido sin devolver nada (era una referencia); CARGAR_DISENO / SACAR_DISENO trabajan sobre
 * la bolsa de diseños.
 */
public record SublimadoraBolsaPayload(BlockPos pos, int accion, int tipo, int indice) implements CustomPayload {
    public static final int ELEGIR = 0, SACAR = 1, QUITAR = 2, CARGAR_DISENO = 3, SACAR_DISENO = 4, EXPORTAR_DISENO = 5;

    public static final CustomPayload.Id<SublimadoraBolsaPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Modamod.MOD_ID, "sublimadora_bolsa"));

    public static final PacketCodec<RegistryByteBuf, SublimadoraBolsaPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, SublimadoraBolsaPayload::pos,
            PacketCodecs.VAR_INT, SublimadoraBolsaPayload::accion,
            PacketCodecs.VAR_INT, SublimadoraBolsaPayload::tipo,
            PacketCodecs.VAR_INT, SublimadoraBolsaPayload::indice,
            SublimadoraBolsaPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
