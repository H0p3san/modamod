package com.modamod.estilado;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * "Crear molde" de la Mesa de estilado creativa con el nombre escrito en el casillero del panel (2026-10-04): el
 * nombre es texto, no entra en un clickButton.
 */
public record CrearMoldePayload(BlockPos pos, String nombre) implements CustomPayload {

    public static final Id<CrearMoldePayload> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "crear_molde"));
    public static final PacketCodec<RegistryByteBuf, CrearMoldePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, CrearMoldePayload::pos,
            PacketCodecs.string(64), CrearMoldePayload::nombre,
            CrearMoldePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
