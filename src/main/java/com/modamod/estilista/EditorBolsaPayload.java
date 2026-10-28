package com.modamod.estilista;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * C2S (2026-10-12, "bolsa de apliques, bolsa de retazos y bolsa de moldes"): lo que el jugador hace con las bolsas y
 * las listas de la Estilista. {@code ELEGIR} pone en el editor la entrada {@code indice} de la bolsa {@code tipo};
 * {@code SACAR} la devuelve al inventario; {@code TRIM} y {@code MATERIAL} eligen un molde de trim o un material de la
 * lista de posibles (por el id del ítem).
 */
public record EditorBolsaPayload(BlockPos pos, int accion, int tipo, int indice, Identifier id) implements CustomPayload {
    public static final int ELEGIR = 0, SACAR = 1, TRIM = 2, MATERIAL = 3, CAMBIAR_PESTANA = 4, FIJAR = 5, CARGAR_DISENO = 6, SACAR_DISENO = 7, EXPORTAR_DISENO = 8;

    public static final CustomPayload.Id<EditorBolsaPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Modamod.MOD_ID, "estilista_editor_bolsa"));

    public static final PacketCodec<RegistryByteBuf, EditorBolsaPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, EditorBolsaPayload::pos,
            PacketCodecs.VAR_INT, EditorBolsaPayload::accion,
            PacketCodecs.VAR_INT, EditorBolsaPayload::tipo,
            PacketCodecs.VAR_INT, EditorBolsaPayload::indice,
            Identifier.PACKET_CODEC, EditorBolsaPayload::id,
            EditorBolsaPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
