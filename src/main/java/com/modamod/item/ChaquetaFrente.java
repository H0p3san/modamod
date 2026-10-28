package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** Frente de la chaqueta (2026-10-07, "campera abierta, en abierto se ve lo de abajo"). Por ordinal: nuevos al final. */
public enum ChaquetaFrente implements StringIdentifiable {
    CERRADA("cerrada"),
    ABIERTA("abierta"),
    /** Cruzado (2026-10-08): cerrado, con una solapa que monta sobre la otra (costura corrida del centro). */
    CRUZADA("cruzada"),
    /** Abierto hasta el pecho (2026-10-08): la apertura baja {@link #FILAS_PECHO} filas y de ahí para abajo cierra. */
    ABIERTA_PECHO("abierta_pecho");

    public static final int FILAS_PECHO = 6;

    /** ¿Deja ver lo de abajo? */
    public boolean abre() { return this == ABIERTA || this == ABIERTA_PECHO; }

    /** Cuántas filas del torso abre (el resto queda cerrado). */
    public int filasAbiertas(int filasTorso) { return this == ABIERTA_PECHO ? Math.min(FILAS_PECHO, filasTorso) : filasTorso; }

    public final String clave;

    ChaquetaFrente(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.chaqueta.frente." + clave; }
}
