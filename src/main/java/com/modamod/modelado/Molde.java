package com.modamod.modelado;

import com.modamod.item.EjeBilateral;
import com.modamod.item.Ruedo;
import com.modamod.item.Ruedos;
import com.modamod.item.ZonaRuedo;
import com.modamod.prenda.CategoriaDePrenda;
import com.modamod.prenda.Corte;
import com.modamod.prenda.EjeDeCorte;
import com.modamod.prenda.Ejes;
import com.modamod.prenda.Prendas;
import com.modamod.region.Lado;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que fija un molde de la Modeladora (una fijada, o una de las que guarda un diseño de corte): un {@link Corte} con
 * solo los ejes que toca, el lado al que va (los ejes con izquierda y derecha) y el ícono del molde que lo produjo.
 * Aplicarlo copia esos ejes sobre la prenda: cada eje sabe en qué prendas vale, su valor de fábrica y su otro lado
 * ({@link EjeDeCorte}), así que un mismo molde sirve para cualquier prenda y cada una toma solo lo suyo.
 */
public record Molde(Corte corte, Lado lado, Optional<Identifier> icono) {

    public static final Molde VACIO = new Molde(Corte.VACIO, Lado.AMBAS, Optional.empty());

    private static final Codec<Lado> LADO_CODEC = Codec.STRING.xmap(Lado::valueOf, Enum::name);

    public static final Codec<Molde> CODEC = RecordCodecBuilder.create(i -> i.group(
            Corte.CODEC.optionalFieldOf("corte", Corte.VACIO).forGetter(Molde::corte),
            LADO_CODEC.optionalFieldOf("lado", Lado.AMBAS).forGetter(Molde::lado),
            Identifier.CODEC.optionalFieldOf("icono").forGetter(Molde::icono)
    ).apply(i, Molde::new));

    // --- Armado ---

    public static <T> Molde de(EjeDeCorte<T> eje, T valor) {
        return new Molde(Corte.VACIO.con(eje, valor), Lado.AMBAS, Optional.empty());
    }

    /** Un nivel de molde de rango; uno negativo o mayor que 6 es "ninguno" ({@link EjeDeCorte#SIN_NIVEL}). */
    public static Molde nivel(EjeDeCorte<Integer> eje, int nivel) {
        return de(eje, nivel < 0 || nivel > 6 ? EjeDeCorte.SIN_NIVEL : nivel);
    }

    /** El ruedo de un borde; {@code espejo} lo pone también en el borde del otro lado. */
    public static Molde ruedo(ZonaRuedo zona, Ruedo ruedo, boolean espejo) {
        Map<ZonaRuedo, Ruedo> m = new EnumMap<>(ZonaRuedo.class);
        m.put(zona, ruedo);
        if (espejo) m.put(zona.opuesta(), ruedo);
        return de(Ejes.RUEDOS, Map.copyOf(m));
    }

    public Molde conLado(Lado nuevo) {
        return new Molde(corte, nuevo, icono);
    }

    public Molde conIcono(Identifier id) {
        return new Molde(corte, lado, Optional.of(id));
    }

    public boolean vacio() {
        return corte.vacio();
    }

    // --- Sobre la prenda ---

    /** Una copia de {@code prenda} con los ejes del molde que valen para ella, o la misma si no cambió nada. */
    public ItemStack aplicar(ItemStack prenda) {
        if (vacio() || prenda.isEmpty()) return prenda;
        ItemStack out = prenda.copy();
        CategoriaDePrenda categoria = Prendas.categoria(out);
        boolean cambio = false;
        for (var e : corte.valores().entrySet()) cambio |= poner(out, categoria, e.getKey(), e.getValue());
        return cambio ? out : prenda;
    }

    @SuppressWarnings("unchecked")
    private <T> boolean poner(ItemStack out, CategoriaDePrenda categoria, EjeDeCorte<T> eje, Object crudo) {
        T valor = (T) crudo;
        if (eje == Ejes.RUEDOS) return Ruedos.aplicar(out, (Map<ZonaRuedo, Ruedo>) valor);
        // El cinto tiene su propio calce, de fábrica Oversize: el molde de calce de siempre lo escribe ahí.
        if (eje == Ejes.CALCE && categoria == CategoriaDePrenda.BANDA) {
            Corte.escribir(out, Ejes.BANDA_CALCE, (com.modamod.item.Calce) valor);
            return true;
        }
        if (!eje.valeEn(categoria)) return false;
        EjeDeCorte<T> otro = eje.otroLado();
        if (otro == null) Corte.escribir(out, eje, valor);
        else EjeBilateral.escribir(out, eje.principalEsDerecho() ? lado.opuesto() : lado, eje, otro, eje.deFabrica(), valor);
        return true;
    }
}
