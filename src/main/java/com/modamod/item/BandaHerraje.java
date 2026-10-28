package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** Herraje del frente de la banda (2026-10-05): ninguno, una placa o un aro. Por ordinal: nuevos al final. */
public enum BandaHerraje implements StringIdentifiable {
    NINGUNO("ninguno"),
    PLACA("placa"),
    ARO("aro"),
    // Colgantes del collar (2026-10-08, "los colgantes... 3 o 4 fijos"): modelos de cubitos que cuelgan del frente y se
    // balancean con la inercia.
    CAMPANA("campana"),
    HUESO("hueso"),
    CORAZON("corazon"),
    MEDALLA("medalla");

    public final String clave;

    BandaHerraje(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }

    /** ¿Es un colgante que se balancea (collar) en vez de un herraje fijo? */
    public boolean esColgante() { return ordinal() >= CAMPANA.ordinal(); }

    public String traduccion() { return "modamod.banda.herraje." + clave; }
}
