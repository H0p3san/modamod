package com.modamod.modelado;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * C2S (2026-10-12, bolsas de moldes de la Modeladora): el jugador elige un molde de la bolsa para el casillero
 * {@code pin} de la categoría actual ({@code modo} 0) o saca uno de la bolsa a su inventario ({@code modo} 1,
 * click derecho). Viaja como paquete propio porque lleva un id de ítem, no un entero de botón.
 */
public record ElegirMoldePayload(BlockPos pos, int pin, Identifier item, int modo) implements CustomPayload {
    public static final int ELEGIR = 0, SACAR = 1;
    /** Bolsa de diseños (2026-10-12): {@code pin} es el índice del diseño en la bolsa. */
    public static final int CARGAR_DISENO = 2, SACAR_DISENO = 3, EXPORTAR_DISENO = 4;

    public static final CustomPayload.Id<ElegirMoldePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Modamod.MOD_ID, "modelado_elegir_molde"));

    public static final PacketCodec<RegistryByteBuf, ElegirMoldePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ElegirMoldePayload::pos,
            PacketCodecs.VAR_INT, ElegirMoldePayload::pin,
            Identifier.PACKET_CODEC, ElegirMoldePayload::item,
            PacketCodecs.VAR_INT, ElegirMoldePayload::modo,
            ElegirMoldePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
