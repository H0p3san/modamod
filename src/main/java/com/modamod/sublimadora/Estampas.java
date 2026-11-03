package com.modamod.sublimadora;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Todas las fotos de la Sublimadora en una prenda, en un solo componente: la estampa libre del frente y de la espalda
 * y las capas con máscara (hasta {@link CapaEstampa#MAXIMO}), que pintan encima en orden. Un banner, un escudo, una
 * gorra o una máscara usan solo el frente.
 */
public record Estampas(Optional<Estampa> frente, Optional<Estampa> espalda, List<CapaEstampa> capas) {

    public static final Estampas NINGUNA = new Estampas(Optional.empty(), Optional.empty(), List.of());

    public Estampas {
        capas = List.copyOf(capas.subList(0, Math.min(CapaEstampa.MAXIMO, capas.size())));
    }

    public static final Codec<Estampas> CODEC = RecordCodecBuilder.create(i -> i.group(
            Estampa.CODEC.optionalFieldOf("frente").forGetter(Estampas::frente),
            Estampa.CODEC.optionalFieldOf("espalda").forGetter(Estampas::espalda),
            CapaEstampa.CODEC.listOf().optionalFieldOf("capas", List.of()).forGetter(Estampas::capas)
    ).apply(i, Estampas::new));

    /** El componente vive aparte para que el codec se pueda usar sin los registros del juego. */
    public static final class Tipo {
        public static final ComponentType<Estampas> COMPONENTE = Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", "estampas"),
                ComponentType.<Estampas>builder().codec(CODEC).packetCodec(PacketCodecs.codec(CODEC)).build());

        private Tipo() {}
    }

    public static void init() {
        Tipo.COMPONENTE.getClass();
    }

    public static Estampas soloFrente(Estampa estampa) {
        return new Estampas(Optional.of(estampa), Optional.empty(), List.of());
    }

    public boolean vacia() {
        return frente.isEmpty() && espalda.isEmpty() && capas.isEmpty();
    }

    @Nullable
    public Estampa de(Estampa.Cara cara) {
        return (cara == Estampa.Cara.FRENTE ? frente : espalda).orElse(null);
    }

    public Estampas con(Estampa.Cara cara, @Nullable Estampa e) {
        Optional<Estampa> o = Optional.ofNullable(e);
        return cara == Estampa.Cara.FRENTE ? new Estampas(o, espalda, capas) : new Estampas(frente, o, capas);
    }

    public Estampas conCapas(List<CapaEstampa> nuevas) {
        return new Estampas(frente, espalda, nuevas);
    }

    // --- En el ítem ---

    public static Estampas de(ItemStack stack) {
        return stack.getOrDefault(Tipo.COMPONENTE, NINGUNA);
    }

    @Nullable
    public static Estampa frente(ItemStack stack) {
        return de(stack).de(Estampa.Cara.FRENTE);
    }

    @Nullable
    public static Estampa frenteDe(@Nullable Estampas e) {
        return e == null ? null : e.de(Estampa.Cara.FRENTE);
    }

    public static void guardar(ItemStack stack, Estampas e) {
        if (e.vacia()) stack.remove(Tipo.COMPONENTE);
        else stack.set(Tipo.COMPONENTE, e);
    }
}
