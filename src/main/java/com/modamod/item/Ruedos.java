package com.modamod.item;

import com.modamod.prenda.CategoriaDePrenda;

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
        Map<ZonaRuedo, Ruedo> m = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.RUEDOS);
        Ruedo r = m == null ? null : m.get(zona);
        return r == null ? deFabrica(stack, zona) : r;
    }

    /** Anota el ruedo; si coincide con el de fábrica se saca para no ensuciar el componente. */
    public static void set(ItemStack stack, ZonaRuedo zona, Ruedo ruedo) {
        Map<ZonaRuedo, Ruedo> m = new EnumMap<>(ZonaRuedo.class);
        Map<ZonaRuedo, Ruedo> actual = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.RUEDOS);
        if (actual != null) m.putAll(actual);
        if (ruedo == deFabrica(stack, zona)) m.remove(zona);
        else m.put(zona, ruedo);
        if (m.isEmpty()) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.RUEDOS);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.RUEDOS, m);
    }

    /** Los ruedos de un molde, cada uno en su borde si vale en esta prenda. Devuelve si puso alguno. */
    public static boolean aplicar(ItemStack prenda, Map<ZonaRuedo, Ruedo> ruedos) {
        boolean puso = false;
        for (var e : ruedos.entrySet()) {
            if (!vale(prenda, e.getKey(), e.getValue())) continue;
            set(prenda, e.getKey(), e.getValue());
            puso = true;
        }
        return puso;
    }

    /** Si el ruedo {@code r} tiene sentido en esa zona de esta prenda (el borde decorativo solo en la pollera). */
    public static boolean vale(ItemStack prenda, ZonaRuedo zona, Ruedo r) {
        if (r.esDeCapa()) return false;
        // El fruncido (2026-10-17) solo vale en los bordes de arriba y de abajo de medias y calientabrazos.
        if (r == Ruedo.FRUNCIDO && zona != ZonaRuedo.SUP_IZQ && zona != ZonaRuedo.SUP_DER
                && zona != ZonaRuedo.INF_IZQ && zona != ZonaRuedo.INF_DER) return false;
        // El ribete de piel no vale en la pollera ni el delantal (su malla no lo dibuja).
        if (r == Ruedo.PIEL && (zona == ZonaRuedo.POLLERA || zona == ZonaRuedo.DELANTAL)) return false;
        boolean extremo = r.ordinal() <= Ruedo.CAMPANA.ordinal() || r.volado() != null || r.borde() != null || r == Ruedo.PIEL;
        return switch (zona) {
            case TORSO -> es(prenda, CategoriaDePrenda.REMERA) && r.valeEnTela();
            case PUNO_IZQ, PUNO_DER -> es(prenda, CategoriaDePrenda.REMERA);
            case POLLERA -> es(prenda, CategoriaDePrenda.POLLERA);
            case DELANTAL -> es(prenda, CategoriaDePrenda.DELANTAL);
            case BOTA_IZQ, BOTA_DER -> es(prenda, CategoriaDePrenda.PANTALON) && extremo;
            case SUP_IZQ, SUP_DER, INF_IZQ, INF_DER -> (es(prenda, CategoriaDePrenda.MEDIAS) || es(prenda, CategoriaDePrenda.CALIENTABRAZOS))
                    && (extremo || r == Ruedo.FRUNCIDO);
            default -> false;
        };
    }

    private static boolean es(ItemStack prenda, CategoriaDePrenda c) {
        return com.modamod.prenda.Prendas.es(prenda, c);
    }
}
