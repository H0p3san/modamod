package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Escala del patrón aplicado (raya fina/gruesa) — a pedido (2026-09-18):
 * "mejor un boton que diga mas grande o mas chico" (y después "grande
 * chico y medio mejor"). Un botón más en la Estación de Tintes, como
 * Lado (ver {@code TinturasBlockEntity#BTN_TAMANO}).
 *
 * {@link #escala} multiplica el grosor BASE de cada patrón (ver
 * {@link com.modamod.render.PatronGenerador}) — no hay archivos por
 * tamaño: la máscara se genera en código, así que un tamaño nuevo es
 * un enum value más, no N PNGs más.
 */
public enum TamanoPatron implements StringIdentifiable {

    // Orden de ciclo del botón: de más chico a más grande — a pedido
    // (2026-09-18, "me gustaria agregar dos dimensiones mas extra grande
    // y extra chico"), un escalón de 0.25 parejo entre los 5.
    EXTRA_CHICO("extra_chico", 0.25f),
    CHICO("chico", 0.5f),
    MEDIANO("mediano", 0.75f),
    GRANDE("grande", 1.0f),
    EXTRA_GRANDE("extra_grande", 1.25f);

    public final String clave;
    public final float escala;

    TamanoPatron(String clave, float escala) {
        this.clave = clave;
        this.escala = escala;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.tamanopatron." + clave;
    }
}
