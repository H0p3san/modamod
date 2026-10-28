package com.modamod.util;

import net.minecraft.util.math.Direction;

/**
 * Una máquina que empuja su resultado a un lado fijo (2026-10-04, "activemos
 * la linea de produccion textil"): la cinta transportadora lo mira para
 * curvarse hacia la máquina que la alimenta.
 */
public interface ConSalida {
    /** Hacia dónde empuja el resultado (la "derecha" de la máquina). */
    net.minecraft.util.math.Direction ladoSalida();
}
