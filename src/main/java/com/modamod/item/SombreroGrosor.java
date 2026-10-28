package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** El grosor de las alas y viseras del sombrero (2026-10-14, "ala gruesa"): fino de fábrica o grueso (el doble). */
public enum SombreroGrosor implements StringIdentifiable {
    FINA("fina"),
    GRUESA("gruesa");

    public final String clave;

    SombreroGrosor(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.sombrero.grosor." + clave; }
}
