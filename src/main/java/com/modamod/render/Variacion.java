package com.modamod.render;

import net.minecraft.util.StringIdentifiable;

/**
 * Cómo varía el color de RELLENO de una capa entre sus colores activos
 * (2026-09-28, Fase 2 de la propuesta de patrones, "agregar mas de un
 * color a un patron para que varie"). Se resuelve pixel a pixel al
 * componer, con los datos que la máscara trae en sus canales de color —
 * ver {@link PatronGenerador} ("Canales de la máscara").
 */
public enum Variacion implements StringIdentifiable {
    /** Todo del Color 1. */
    FIJO("fijo"),
    /** Cada repetición (motivo, raya, cuadro) toma el color siguiente. */
    ALTERNAR("alternar"),
    /** Cada repetición toma uno al azar — la semilla del cuadradito lo cambia. */
    ALEATORIO("aleatorio"),
    /** De un color al otro, de arriba a abajo — también en una capa lisa. */
    DEGRADE("degrade");

    public final String clave;

    Variacion(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.variacion." + clave;
    }

    public Variacion siguiente() {
        Variacion[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
