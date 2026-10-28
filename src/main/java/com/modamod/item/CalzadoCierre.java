package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** El cierre del calzado: cordones, velcro, tiras o costura (2026-10-14, zip de Claude Design). Por ordinal: nuevos al final. */
public enum CalzadoCierre implements StringIdentifiable {
    CORDONES("cordones"),
    VELCRO("velcro"),
    TIRA_HEBILLA("tira_hebilla"),
    TIRA_V("tira_v"),
    TOBILLERA("tobillera"),
    GLADIADORA("gladiadora"),
    COSTURA_LATERAL("costura_lateral"),
    SIN("sin");

    public final String clave;

    CalzadoCierre(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.calzado.cierre." + clave; }
}
