package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** La planta del ala plana del sombrero (2026-10-14, "un molde mas para cuadrada o malla"): redonda de fábrica o cuadrada. */
public enum SombreroPlanta implements StringIdentifiable {
    REDONDA("redonda"),
    CUADRADA("cuadrada");

    public final String clave;

    SombreroPlanta(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.sombrero.planta." + clave; }
}
