package com.modamod.guardarropas;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: la lista de outfits de la colección que el jugador tiene abierta (al abrir y después de cada cambio). */
public record OutfitsPayload(OutfitsDatos datos) implements CustomPayload {
    public static final CustomPayload.Id<OutfitsPayload> ID = new CustomPayload.Id<>(Identifier.of(Modamod.MOD_ID, "guardarropas_outfits"));

    public static final PacketCodec<RegistryByteBuf, OutfitsPayload> CODEC = PacketCodec.tuple(
            OutfitsDatos.PACKET_CODEC, OutfitsPayload::datos, OutfitsPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
