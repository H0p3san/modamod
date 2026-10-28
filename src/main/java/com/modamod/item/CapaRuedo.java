package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** Borde de abajo de la capa (2026-09-29): recto como la vanilla o redondeado. Por ordinal: nuevos al final. */
public enum CapaRuedo implements StringIdentifiable {
    RECTO("recto"),
    REDONDEADO("redondeado"),
    /** Con cola (2026-10-04, "una capa extra larga que arrastre en el piso"): {@link #COLA_PIXELES} más de tela que se apoyan en el suelo. */
    COLA("cola");

    /** Cuánta tela de más tiene el ruedo con cola, en píxeles del modelo. */
    public static final float COLA_PIXELES = 10f;

    public final String clave;

    CapaRuedo(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.capa.ruedo." + clave;
    }
}
