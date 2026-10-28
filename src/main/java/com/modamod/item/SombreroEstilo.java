package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** Cómo se dibuja el sombrero (2026-10-14, "las dos opciones a traves de un molde de cuboide vs malla"): malla suave o cuboides. */
public enum SombreroEstilo implements StringIdentifiable {
    MALLA("malla"),
    CAJAS("cajas");

    public final String clave;

    SombreroEstilo(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.sombrero.estilo." + clave; }
}
