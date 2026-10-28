package com.modamod.item;

import com.mojang.serialization.Codec;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringIdentifiable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Los {@link Ruedo} de una prenda, uno por {@link ZonaRuedo} (componente {@code modamod:ruedos}). Lo que no está
 * anotado vale el de fábrica de la prenda: recto.
 */
public final class Ruedos {
    private Ruedos() {}

    public static final Codec<Map<ZonaRuedo, Ruedo>> CODEC = Codec.unboundedMap(
            StringIdentifiable.createCodec(ZonaRuedo::values), StringIdentifiable.createCodec(Ruedo::values));

    /** El ruedo de fábrica de {@code zona}: recto (el hoodie lo trae ajustado, el hoodie se arma en la Modeladora). */
    public static Ruedo deFabrica(ItemStack stack, ZonaRuedo zona) {
        return Ruedo.RECTO;
    }

    public static Ruedo get(ItemStack stack, ZonaRuedo zona) {
        Map<ZonaRuedo, Ruedo> m = stack.get(ModamodComponents.RUEDOS);
        Ruedo r = m == null ? null : m.get(zona);
        return r == null ? deFabrica(stack, zona) : r;
    }

    /** Anota el ruedo; si coincide con el de fábrica se saca para no ensuciar el componente. */
    public static void set(ItemStack stack, ZonaRuedo zona, Ruedo ruedo) {
        Map<ZonaRuedo, Ruedo> m = new EnumMap<>(ZonaRuedo.class);
        Map<ZonaRuedo, Ruedo> actual = stack.get(ModamodComponents.RUEDOS);
        if (actual != null) m.putAll(actual);
        if (ruedo == deFabrica(stack, zona)) m.remove(zona);
        else m.put(zona, ruedo);
        if (m.isEmpty()) stack.remove(ModamodComponents.RUEDOS);
        else stack.set(ModamodComponents.RUEDOS, m);
    }
}
