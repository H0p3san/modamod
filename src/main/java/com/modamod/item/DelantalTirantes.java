package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Tirantes del delantal (2026-10-11, "dale con tirantes y sublimacion"): dos cintas que suben del borde de arriba por los
 * hombros y bajan por la espalda, rectas o cruzadas. Por ordinal: nuevos al final.
 */
public enum DelantalTirantes implements StringIdentifiable {
    SIN("sin"), RECTOS("rectos"), CRUZADOS("cruzados");

    public final String clave;

    DelantalTirantes(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }
}
