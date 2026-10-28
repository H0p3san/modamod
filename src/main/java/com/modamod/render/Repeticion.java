package com.modamod.render;

import net.minecraft.util.StringIdentifiable;

/**
 * Cómo se reparte un {@link Motivo} sobre la prenda (2026-09-28, Fase 1 de
 * la propuesta de patrones). Vive en el cuadradito de Tinturas, no en el
 * molde: un mismo molde de corazones sirve en grilla, disperso o como logo.
 */
public enum Repeticion implements StringIdentifiable {
    /** Filas y columnas parejas. */
    GRILLA("grilla"),
    /** Cada fila corrida media celda — el clásico de los estampados. */
    LADRILLO("ladrillo"),
    /** Posiciones al azar dentro de cada celda (algunas vacías), con semilla guardada. */
    DISPERSO("disperso"),
    /** Un solo motivo grande, centrado en el frente de cada pieza — tipo logo. */
    UNICO("unico");

    public final String clave;

    Repeticion(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.repeticion." + clave;
    }

    public Repeticion siguiente() {
        Repeticion[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
