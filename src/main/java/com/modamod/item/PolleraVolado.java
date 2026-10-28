package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Volado de la pollera (2026-10-05, "dos moldes de volado, uno recto y uno circular"): se aplica solo al borde de
 * abajo (pin Volado inferior de la Modeladora) o a toda la pollera, en tres filas de arriba a abajo (pin Volado
 * total). RECTO: tira fruncida, poca apertura y muchas ondas chicas. CIRCULAR: corte en círculo, abierto, con pocas
 * ondas grandes. Por ordinal: nuevos al final.
 */
public enum PolleraVolado implements StringIdentifiable {
    RECTO("recto"),
    CIRCULAR("circular"),
    /** Sin volado (2026-10-12): lo pone el molde en blanco para quitarlo; nunca se guarda en la prenda. */
    SIN("sin");

    public final String clave;

    PolleraVolado(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.pollera.volado." + clave; }
}
