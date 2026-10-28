package com.modamod.tinturas;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * C2S (2026-10-12, bolsa de patrones de la Estación de Tintes, mismo esquema que {@code ElegirMoldePayload} de la
 * Modeladora): elegir un patrón de la bolsa para un cuadradito ({@code modo} 0) o sacar uno de la bolsa al inventario
 * ({@code modo} 1). {@code casilla} es el cuadradito de la categoría actual.
 */
public record ElegirPatronPayload(BlockPos pos, int casilla, Identifier item, int modo) implements CustomPayload {
    public static final int ELEGIR = 0, SACAR = 1;
    /** Bolsa de diseños (2026-10-12): {@code casilla} es el índice del diseño en la bolsa. */
    public static final int CARGAR_DISENO = 2, SACAR_DISENO = 3, EXPORTAR_DISENO = 4;

    public static final CustomPayload.Id<ElegirPatronPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Modamod.MOD_ID, "tinturas_elegir_patron"));

    public static final PacketCodec<RegistryByteBuf, ElegirPatronPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ElegirPatronPayload::pos,
            PacketCodecs.VAR_INT, ElegirPatronPayload::casilla,
            Identifier.PACKET_CODEC, ElegirPatronPayload::item,
            PacketCodecs.VAR_INT, ElegirPatronPayload::modo,
            ElegirPatronPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
