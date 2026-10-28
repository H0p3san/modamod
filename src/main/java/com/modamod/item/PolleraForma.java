package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Forma de la pollera (2026-09-29, "las dos, mira la que ya hay para la
 * tableada"): campana lisa o tableada (pliegues en V, como la de gajos que
 * había). Se elige con el Molde de Pollera en la Modeladora. Viaja por red
 * por ordinal: valores nuevos, siempre al final.
 */
public enum PolleraForma implements StringIdentifiable {
    CAMPANA("campana"),
    TABLEADA("tableada"),
    // Tres formas más (2026-10-05, "vamos a agregar tres polleras": tubo, globo y circular); al FINAL.
    /** Recta o lápiz: pegada a la cadera, casi sin vuelo ni movimiento. */
    TUBO("tubo"),
    /** Abombada: ancha en el medio y cerrada en el ruedo. */
    GLOBO("globo"),
    /** De círculo completo: mucho vuelo y se abre en el twirl. */
    CIRCULAR("circular");

    public final String clave;

    PolleraForma(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.pollera.forma." + clave;
    }
}
