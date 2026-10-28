package com.modamod.ropa;

/**
 * Un slot que no es de Trinkets pero guarda una pieza de ropa de un slot de Trinkets (los del Maniquí, 2026-10-25, "tienen
 * que tener todos un hover indicativo"): con esto {@code HandledScreenSlotInfoMixin} muestra la misma info al pasar el
 * mouse por el slot vacío.
 */
public interface SlotDeRopa {
    String grupoDeRopa();

    String slotDeRopa();

    /** Cuántos lugares de ese tipo hay en la pantalla. */
    int lugaresDeRopa();
}
