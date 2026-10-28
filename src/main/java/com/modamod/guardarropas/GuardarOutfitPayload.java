package com.modamod.guardarropas;

import com.modamod.Modamod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: "Guardar mi outfit" (2026-10-21, "tiene que poder guardar con nombre el outfit del jugador"): el nombre es texto libre y no entra
 * en un {@code clickButton(int)}. Vale para el Guardarropas y el Maletín: el servidor lo aplica a la pantalla que el jugador tiene abierta.
 */
public record GuardarOutfitPayload(String nombre) implements CustomPayload {
    public static final CustomPayload.Id<GuardarOutfitPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Modamod.MOD_ID, "guardarropas_guardar_outfit"));

    public static final PacketCodec<RegistryByteBuf, GuardarOutfitPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(24), GuardarOutfitPayload::nombre,
            GuardarOutfitPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
