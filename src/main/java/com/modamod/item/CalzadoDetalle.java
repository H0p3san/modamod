package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** El detalle del calzado: lengüeta, parche o costura de empeine (2026-10-14, zip de Claude Design). Por ordinal: nuevos al final. */
public enum CalzadoDetalle implements StringIdentifiable {
    NINGUNO("ninguno"),
    LENGUETA("lengueta"),
    PARCHE("parche"),
    COSTURA_EMPEINE("costura_empeine");

    public final String clave;

    CalzadoDetalle(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.calzado.detalle." + clave; }
}
