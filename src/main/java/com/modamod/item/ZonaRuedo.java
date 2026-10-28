package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Cada borde libre de una prenda que puede llevar su propio {@link Ruedo} (2026-10-07: "dejemos la posibilidad de
 * construir asimetricamente" → un pin por lado). Por ordinal: nuevos al final.
 */
public enum ZonaRuedo implements StringIdentifiable {
    /** Ruedo del torso (remera, chaqueta). */
    TORSO("torso"),
    /** Puño de cada manga. */
    PUNO_IZQ("puno_izq"), PUNO_DER("puno_der"),
    /** Botamanga de cada pierna del pantalón. */
    BOTA_IZQ("bota_izq"), BOTA_DER("bota_der"),
    /** Borde de arriba y de abajo de cada media y de cada calientabrazos. */
    SUP_IZQ("sup_izq"), SUP_DER("sup_der"), INF_IZQ("inf_izq"), INF_DER("inf_der"),
    /** Ruedo de la pollera. */
    POLLERA("pollera"),
    /** Ruedo del delantal. */
    DELANTAL("delantal");

    public final String clave;

    ZonaRuedo(String clave) { this.clave = clave; }

    /** La zona del otro lado del cuerpo (la misma si no tiene lado). */
    public ZonaRuedo opuesta() {
        return switch (this) {
            case PUNO_IZQ -> PUNO_DER; case PUNO_DER -> PUNO_IZQ;
            case BOTA_IZQ -> BOTA_DER; case BOTA_DER -> BOTA_IZQ;
            case SUP_IZQ -> SUP_DER; case SUP_DER -> SUP_IZQ;
            case INF_IZQ -> INF_DER; case INF_DER -> INF_IZQ;
            default -> this;
        };
    }

    @Override
    public String asString() { return clave; }
}
