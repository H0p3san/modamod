package com.modamod.maniqui;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Un slider de pose libre del Maniquí (2026-09-30, "pose libre por partes"):
 * un ángulo ({@code indice}, ver {@link PoseManiqui}) con su valor en grados.
 * No entra en un clickButton (solo lleva un int), por eso paquete propio —
 * mismo criterio que {@code GuardarDisenoTinturasPayload}.
 */
public record PoseManiquiPayload(BlockPos pos, int indice, float grados) implements CustomPayload {

    public static final Id<PoseManiquiPayload> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "pose_maniqui"));
    public static final PacketCodec<RegistryByteBuf, PoseManiquiPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, PoseManiquiPayload::pos,
            PacketCodecs.VAR_INT, PoseManiquiPayload::indice,
            PacketCodecs.FLOAT, PoseManiquiPayload::grados,
            PoseManiquiPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
