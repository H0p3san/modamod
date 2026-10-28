package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Solapas de la chaqueta (2026-10-07, "traje separado... varios tipos de solapa"): dos tiras de tela que bajan del
 * cuello a cada lado del frente. Por ordinal: nuevos al final.
 */
public enum ChaquetaSolapa implements StringIdentifiable {
    NINGUNA("ninguna"),
    /** Solapa en pico (peak lapel): ancha, con una punta que sale hacia afuera. */
    PICO("pico"),
    /** Solapa redondeada (de saco clásico): ancha y sin punta. */
    REDONDA("redonda"),
    /** Solapa chal (esmoquin): una sola curva larga y pareja, hasta la cintura. */
    CHAL("chal");

    public final String clave;

    ChaquetaSolapa(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.chaqueta.solapa." + clave; }
}
