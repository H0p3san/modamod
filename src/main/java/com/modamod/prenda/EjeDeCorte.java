package com.modamod.prenda;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

import java.util.HashMap;
import java.util.Map;

/**
 * Un eje de la forma de una prenda (largo, manga, cintura, ala del sombrero...): su nombre guardado y cómo se escribe.
 * Todos los ejes de todas las prendas viven en un solo componente, {@link Corte}; los declara {@link Ejes}.
 */
public final class EjeDeCorte<T> {
    private static final Map<String, EjeDeCorte<?>> POR_NOMBRE = new HashMap<>();

    private final String nombre;
    private final Codec<T> codec;

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

    public static EjeDeCorte<Integer> nivel(String nombre) {
        return new EjeDeCorte<>(nombre, Codec.intRange(0, 6));
    }

    public static EjeDeCorte<Boolean> bandera(String nombre) {
        return new EjeDeCorte<>(nombre, Codec.BOOL);
    }

    public String nombre() {
        return nombre;
    }

    public Codec<T> codec() {
        return codec;
    }

    static EjeDeCorte<?> porNombre(String nombre) {
        return POR_NOMBRE.get(nombre);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
