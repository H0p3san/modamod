package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Cómo se lleva el pantalón con los borcegos (2026-10-08, "quiero las dos opciones"): la botamanga por dentro de la
 * caña (se ve el cuero y los cordones) o por fuera (el pantalón tapa la caña). Por ordinal: nuevos al final.
 */
public enum BorcegoBotamanga implements StringIdentifiable {
    ADENTRO("adentro"),
    AFUERA("afuera");

    public final String clave;

    BorcegoBotamanga(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.borcegos.botamanga." + clave; }
}
