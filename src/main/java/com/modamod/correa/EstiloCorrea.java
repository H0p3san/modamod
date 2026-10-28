package com.modamod.correa;

import net.minecraft.util.StringIdentifiable;

/**
 * Cómo se ve una correa (2026-10-05, "correas libres" + "molde de cadenas"): tira lisa de cuero o tela, cadena de
 * eslabones, cadena fina de bolitas, tira con ojalillos o cordón con puntas (el de la capucha de un hoodie). Por
 * ordinal: nuevos al final.
 */
public enum EstiloCorrea implements StringIdentifiable {
    LISA("lisa"),
    CADENA("cadena"),
    CADENA_FINA("cadena_fina"),
    OJALILLOS("ojalillos"),
    CORDON("cordon");

    public final String clave;

    EstiloCorrea(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.correa.estilo." + clave; }

    /** Las cadenas y las bolitas son metal: toman el color de herraje (la 3.ª zona). */
    public boolean esMetal() { return this == CADENA || this == CADENA_FINA; }
}
