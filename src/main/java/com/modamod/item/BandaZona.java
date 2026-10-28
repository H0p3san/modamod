package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Dónde se lleva una banda (2026-10-05, "correas y cintos"): cintura (cinto) o cuello (choker). Cada una va en su
 * slot de Trinkets ({@code torso/cinto}, {@code head/choker}). Por ordinal: nuevos al final.
 */
public enum BandaZona implements StringIdentifiable {
    CINTURA("cintura", "torso", "cinto"),
    CUELLO("cuello", "head", "choker");

    public final String clave;
    /** Grupo y slot de Trinkets. */
    public final String grupo, slot;

    BandaZona(String clave, String grupo, String slot) {
        this.clave = clave;
        this.grupo = grupo;
        this.slot = slot;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.banda.zona." + clave; }
}
