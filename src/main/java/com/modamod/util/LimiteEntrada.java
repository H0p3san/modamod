package com.modamod.util;

/** Máquinas cuyo casillero de entrada de prenda admite una sola por vez (2026-10-14, "que el input de las maquinas no stackee mas de 1 prenda"). */
public interface LimiteEntrada {
    /** Cuántos ítems caben en ese casillero por automatización. */
    int topeEntrada(int slot);
}
