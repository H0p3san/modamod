package com.modamod.prenda;

import com.modamod.item.TexturaTela;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * El acabado de la tela, en un solo componente: el relieve ({@link TexturaTela}, con un Molde de textura) y el trim de
 * vanilla con su material opcional (brillo, animación, reflejos; ver {@code render.EfectoTrim}). Lo pone la Mesa de
 * estilado o la Estilista. Sin material, el trim usa la paleta de fábrica de su patrón.
 */
public record Acabado(TexturaTela textura, Optional<Identifier> trim, Optional<Identifier> material) {

    public static final Acabado NINGUNO = new Acabado(TexturaTela.LISA, Optional.empty(), Optional.empty());

    public static final Codec<Acabado> CODEC = RecordCodecBuilder.create(i -> i.group(
            TexturaTela.CODEC.optionalFieldOf("textura", TexturaTela.LISA).forGetter(Acabado::textura),
            Identifier.CODEC.optionalFieldOf("trim").forGetter(Acabado::trim),
            Identifier.CODEC.optionalFieldOf("material").forGetter(Acabado::material)
    ).apply(i, Acabado::new));

    /** El componente vive aparte para que el codec se pueda usar sin los registros del juego. */
    public static final class Tipo {
        public static final ComponentType<Acabado> COMPONENTE = Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", "acabado"),
                ComponentType.<Acabado>builder().codec(CODEC).packetCodec(PacketCodecs.codec(CODEC)).build());

        private Tipo() {}
    }

    public static void init() {
        Tipo.COMPONENTE.getClass();
    }

    public boolean vacio() {
        return textura == TexturaTela.LISA && trim.isEmpty() && material.isEmpty();
    }

    // --- Lectura ---

    public static Acabado de(ItemStack stack) {
        return stack.getOrDefault(Tipo.COMPONENTE, NINGUNO);
    }

    public static TexturaTela textura(ItemStack stack) {
        return de(stack).textura();
    }

    @Nullable
    public static Identifier trim(ItemStack stack) {
        return de(stack).trim().orElse(null);
    }

    @Nullable
    public static Identifier material(ItemStack stack) {
        return de(stack).material().orElse(null);
    }

    // --- Escritura ---

    private static void guardar(ItemStack stack, Acabado a) {
        if (a.vacio()) stack.remove(Tipo.COMPONENTE);
        else stack.set(Tipo.COMPONENTE, a);
    }

    public static void ponerTextura(ItemStack stack, TexturaTela t) {
        Acabado a = de(stack);
        guardar(stack, new Acabado(t, a.trim(), a.material()));
    }

    /** El trim y su material; {@code trim} null saca los dos, {@code material} null deja la paleta de fábrica. */
    public static void ponerTrim(ItemStack stack, @Nullable Identifier trim, @Nullable Identifier material) {
        Acabado a = de(stack);
        guardar(stack, trim == null ? new Acabado(a.textura(), Optional.empty(), Optional.empty())
                : new Acabado(a.textura(), Optional.of(trim), Optional.ofNullable(material)));
    }
}
