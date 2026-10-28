package com.modamod.guardarropas;

import com.modamod.guardarropas.GuardarropasBlockEntity.Outfit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Uuids;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Una colección de outfits tal como se guarda y viaja al cliente: la lista y el último nombre de cada jugador. */
public record OutfitsDatos(List<Outfit> outfits, Map<UUID, String> ultimos) {

    public static final OutfitsDatos VACIO = new OutfitsDatos(List.of(), Map.of());

    private static final Codec<Outfit> OUTFIT = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("nombre").forGetter(Outfit::nombre),
            Codec.unboundedMap(Codec.STRING, ItemStack.CODEC).fieldOf("piezas").forGetter(o -> o.piezas())
    ).apply(i, (n, m) -> new Outfit(n, new LinkedHashMap<>(m))));

    public static final Codec<OutfitsDatos> CODEC = RecordCodecBuilder.create(i -> i.group(
            OUTFIT.listOf().fieldOf("outfits").forGetter(OutfitsDatos::outfits),
            Codec.unboundedMap(Uuids.STRING_CODEC, Codec.STRING).optionalFieldOf("ultimos", Map.of()).forGetter(OutfitsDatos::ultimos)
    ).apply(i, OutfitsDatos::new));

    public static final PacketCodec<RegistryByteBuf, OutfitsDatos> PACKET_CODEC = PacketCodecs.registryCodec(CODEC);
}
