package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Dónde se lleva una banda (2026-10-05, "correas y cintos"): cintura (cinto) o cuello (choker). Por ordinal: nuevos al final.
 */
public enum BandaZona implements StringIdentifiable {
    CINTURA("cintura"),
    CUELLO("cuello");

    public final String clave;

    BandaZona(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.banda.zona." + clave; }
}
