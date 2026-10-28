package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** La punta del calzado: redonda, alargada, cuadrada, fina, ancha, con puntera de goma o de cuero, o descubierta (2026-10-14, zip de Claude Design). Por ordinal: nuevos al final. */
public enum CalzadoPunta implements StringIdentifiable {
    REDONDA("redonda"),
    ALARGADA("alargada"),
    CUADRADA("cuadrada"),
    FINA("fina"),
    ANCHA("ancha"),
    GOMA("goma"),
    CUERO("cuero"),
    DESCUBIERTA("descubierta");

    public final String clave;

    CalzadoPunta(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.calzado.punta." + clave; }
}
