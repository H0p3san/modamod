package com.modamod.prenda;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Un eje de la forma de una prenda (largo, manga, cintura, ala del sombrero...): su nombre guardado, cómo se escribe y lo
 * que hace falta para aplicarlo desde un molde de la Modeladora: su valor de fábrica (escribirlo es borrarlo), en qué
 * prendas vale y, si tiene un lado y el otro, el eje del otro lado. Todos los ejes de todas las prendas viven en un solo
 * componente, {@link Corte}; los declara {@link Ejes}.
 */
public final class EjeDeCorte<T> {
    private static final Map<String, EjeDeCorte<?>> POR_NOMBRE = new HashMap<>();

    /** El nivel "ninguno" de los ejes de nivel (0..6): sin cintura, sin faldón, sin embozo. */
    public static final int SIN_NIVEL = 7;

    private final String nombre;
    private final Codec<T> codec;
    @Nullable private T deFabrica;
    private Set<CategoriaDePrenda> prendas = Set.of();
    @Nullable private EjeDeCorte<T> otroLado;
    private boolean principalEsDerecho;
    @Nullable private EjeDeCorte<?> reinicia;

    private EjeDeCorte(String nombre, Codec<T> codec) {
        this.nombre = nombre;
        this.codec = codec;
        if (POR_NOMBRE.putIfAbsent(nombre, this) != null) {
            throw new IllegalStateException("Eje de corte repetido: " + nombre);
        }
    }

    public static <T> EjeDeCorte<T> de(String nombre, Codec<T> codec) {
        return new EjeDeCorte<>(nombre, codec);
    }

    public static <E extends Enum<E> & StringIdentifiable> EjeDeCorte<E> enumerado(String nombre,
                                                                                 java.util.function.Supplier<E[]> valores) {
        return new EjeDeCorte<>(nombre, StringIdentifiable.createCodec(valores));
    }

    /** Un nivel de 0 a 6 (el de los moldes de rango), o {@link #SIN_NIVEL}. */
    public static EjeDeCorte<Integer> nivel(String nombre) {
        return new EjeDeCorte<>(nombre, Codec.intRange(0, SIN_NIVEL));
    }

    public static EjeDeCorte<Boolean> bandera(String nombre) {
        return new EjeDeCorte<>(nombre, Codec.BOOL);
    }

    // --- Lo que sabe para aplicarse (se declara una vez, en Ejes) ---

    /** El valor que tiene sin tocar: escribirlo borra el eje. */
    EjeDeCorte<T> fabrica(T valor) {
        this.deFabrica = valor;
        return this;
    }

    /** Las prendas en las que vale; en las demás, un molde con este eje no hace nada. */
    EjeDeCorte<T> en(CategoriaDePrenda primera, CategoriaDePrenda... resto) {
        this.prendas = EnumSet.of(primera, resto);
        return this;
    }

    /**
     * El eje que guarda el valor propio del otro lado ({@code otro}); el de este vale para los dos hasta que el otro tenga
     * el suyo. {@code principalEsDerecho}: este es el del lado derecho (el calzado) y el otro el izquierdo; si no, al revés.
     */
    EjeDeCorte<T> conOtroLado(EjeDeCorte<T> otro, boolean principalEsDerecho) {
        this.otroLado = otro;
        this.principalEsDerecho = principalEsDerecho;
        otro.prendas = prendas;          // el otro lado vale en las mismas prendas y tiene la misma fábrica
        otro.deFabrica = deFabrica;      // (se llama después de fabrica y en)
        return this;
    }

    /** Un eje que se vuelve a fábrica cada vez que se escribe este (el frente abierto al cambiar el frente). */
    EjeDeCorte<T> reinicia(EjeDeCorte<?> otro) {
        this.reinicia = otro;
        return this;
    }

    public String nombre() {
        return nombre;
    }

    public Codec<T> codec() {
        return codec;
    }

    @Nullable
    public T deFabrica() {
        return deFabrica;
    }

    public boolean valeEn(@Nullable CategoriaDePrenda categoria) {
        return categoria != null && prendas.contains(categoria);
    }

    @Nullable
    public EjeDeCorte<T> otroLado() {
        return otroLado;
    }

    public boolean principalEsDerecho() {
        return principalEsDerecho;
    }

    @Nullable
    public EjeDeCorte<?> reinicia() {
        return reinicia;
    }

    @Nullable
    public static EjeDeCorte<?> porNombre(String nombre) {
        return POR_NOMBRE.get(nombre);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
