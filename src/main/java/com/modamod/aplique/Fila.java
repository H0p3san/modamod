package com.modamod.aplique;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Un aplique repetido en fila hacia abajo (2026-10-22, "nos faltaron botones y bolsillos" → botonera): {@code cantidad}
 * copias separadas {@code separacion} px de skin, en el plano de la tela donde se puso el primero. Sirve para cualquier
 * aplique de modelo; los de objeto lo ignoran.
 */
public record Fila(int cantidad, float separacion) {

    public static final int CANTIDAD_MAX = 8;
    public static final float SEPARACION_MIN = 0.5f, SEPARACION_MAX = 4f, PASO_SEPARACION = 0.25f;
    public static final Fila DEFECTO = new Fila(1, 1.5f);

    public Fila {
        cantidad = Math.max(1, Math.min(CANTIDAD_MAX, cantidad));
        separacion = Float.isFinite(separacion) ? Math.max(SEPARACION_MIN, Math.min(SEPARACION_MAX, separacion)) : 1.5f;
    }

    public static final Codec<Fila> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("cantidad", 1).forGetter(Fila::cantidad),
            Codec.FLOAT.optionalFieldOf("separacion", 1.5f).forGetter(Fila::separacion)
    ).apply(i, Fila::new));
}
