package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Cuánto del cuerpo cubre el delantal (2026-10-11, "un molde para que el delantal solo cubra una parte de adelante del
 * jugador en lugar de todo el frente y los costados"): ENVOLVENTE da la vuelta a los costados (como hasta ahora),
 * FRENTE cubre solo la cara de adelante del torso (8 px) y ANGOSTO un panel de 4 px (la mitad del frente). Por ordinal: nuevos al final.
 */
public enum DelantalCobertura implements StringIdentifiable {
    ENVOLVENTE("envolvente", 6f), FRENTE("frente", 4f), ANGOSTO("angosto", 2f);

    public final String clave;
    /** Media anchura máxima del paño, en px. */
    public final float ancho;

    DelantalCobertura(String clave, float ancho) {
        this.clave = clave;
        this.ancho = ancho;
    }

    /** Columnas de malla a cada lado del centro (0,5 px por columna). */
    public int columnas() { return Math.round(ancho * 2f); }

    @Override
    public String asString() { return clave; }
}
