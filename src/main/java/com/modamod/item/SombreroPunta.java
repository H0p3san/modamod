package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * La copa del sombrero (2026-10-04 la punta de bruja, recta o doblada; ampliada 2026-10-14 con las copas de sombrero de
 * copa, vaquero, gorra, boina y gorro). Por ordinal: nuevos al final.
 */
public enum SombreroPunta implements StringIdentifiable {
    RECTA("recta", 0f, 0f),
    DOBLADA("doblada", -0.35f, -0.55f),
    CILINDRO_ALTO("cilindro_alto", 0f, 0f),
    HENDIDURA("hendidura", 0f, 0f),
    CUPULA("cupula", 0f, 0f),
    CUPULA_CUADRADA("cupula_cuadrada", 0f, 0f),
    PLATO("plato", 0f, 0f),
    GORRO_CAIDO("gorro_caido", -0.7f, -1.2f),
    CHARRO("charro", 0f, 0f),
    TOQUE("toque", 0f, 0f),
    PANADERO("panadero", 0f, 0f),
    MARINERO("marinero", 0f, 0f);

    public final String clave;
    /** Cuánto se inclina de fábrica cada tramo de la punta (rad; negativo = hacia atrás). */
    public final float inclinacion1, inclinacion2;

    SombreroPunta(String clave, float inclinacion1, float inclinacion2) {
        this.clave = clave;
        this.inclinacion1 = inclinacion1;
        this.inclinacion2 = inclinacion2;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.sombrero.punta." + clave; }
}
