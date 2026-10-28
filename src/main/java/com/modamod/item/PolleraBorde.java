package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Borde decorativo del ruedo de la pollera (2026-10-05, "era un custom para el ruedo" → "un ruedo distinto: ondulado,
 * festoneado, con pico"): cambia el largo de la tela a lo largo de la vuelta. Se pone con un molde en el pin Custom
 * de la Modeladora. Por ordinal: nuevos al final.
 */
public enum PolleraBorde implements StringIdentifiable {
    ONDULADO("ondulado"),
    FESTONEADO("festoneado"),
    PICO("pico");

    public final String clave;

    PolleraBorde(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.pollera.borde." + clave; }

    /** Cuántas veces se repite el dibujo en la vuelta de la pollera. */
    public static final int REPETICIONES = 8;

    /**
     * Cuánto baja (px) el borde en la posición {@code u} (0..1 de la vuelta): ondulado = onda suave de ±0,9;
     * festoneado = arcos de medio círculo hacia abajo (hasta 1,6); pico = triángulos (hasta 2,2).
     */
    public float baja(float u) {
        float f = (u * REPETICIONES) % 1f;
        return switch (this) {
            case ONDULADO -> 0.9f * (float) Math.sin(2.0 * Math.PI * f);
            case FESTONEADO -> 1.6f * (float) Math.sqrt(Math.max(0.0, 1.0 - (2.0 * f - 1.0) * (2.0 * f - 1.0)));
            case PICO -> 2.2f * (1f - Math.abs(2f * f - 1f));
        };
    }
}
