package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** Alto de la banda (2026-10-05). {@code cintura} y {@code cuello} son los px de cada zona. Por ordinal: nuevos al final. */
public enum BandaAncho implements StringIdentifiable {
    FINO("fino", 1.6f, 1.0f),
    MEDIO("medio", 2.6f, 1.6f),
    ANCHO("ancho", 3.8f, 2.4f);

    public final String clave;
    public final float cintura, cuello;

    BandaAncho(String clave, float cintura, float cuello) {
        this.clave = clave;
        this.cintura = cintura;
        this.cuello = cuello;
    }

    public float alto(BandaZona zona) { return zona == BandaZona.CINTURA ? cintura : cuello; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.banda.ancho." + clave; }
}
