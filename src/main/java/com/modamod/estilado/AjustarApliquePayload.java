package com.modamod.estilado;

import com.modamod.Modamod;
import com.modamod.aplique.Colocacion;
import com.modamod.aplique.Oscilacion;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Panel lateral de la Mesa de estilado (2026-10-04, "posición, rotación, escala, cara de contacto, profundidad,
 * pivote, oscilación"): manda de una vez la colocación, la oscilación y la blandura del aplique elegido. Son
 * muchos floats, no entran en un clickButton; el servidor los acota con los records.
 */
public record AjustarApliquePayload(BlockPos pos, int indice, Colocacion colocacion, Oscilacion oscilacion, float blandura)
        implements CustomPayload {

    public static final Id<AjustarApliquePayload> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "ajustar_aplique"));
    public static final PacketCodec<RegistryByteBuf, AjustarApliquePayload> CODEC = PacketCodec.of(
            (p, buf) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.indice());
                p.colocacion().escribir(buf);
                p.oscilacion().escribir(buf);
                buf.writeFloat(p.blandura());
            },
            buf -> new AjustarApliquePayload(buf.readBlockPos(), buf.readVarInt(), Colocacion.leer(buf),
                    Oscilacion.leer(buf), buf.readFloat()));

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
