package com.modamod.util;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Dónde se clickeó una máquina (a pedido, 2026-10-08, "si se clickea en la parte de la tapa entra la prenda, si se
 * clickea abajo abre la gui"): arriba (la cara de arriba o la franja alta del frente) se carga la prenda o el molde;
 * abajo se abre la pantalla aunque tengas algo en la mano.
 */
public final class ZonaMaquina {

    private ZonaMaquina() {}

    /** Altura (px) desde la que el costado cuenta como "arriba" en la Modeladora, Tintes y Sublimadora. */
    public static final double ARRIBA_PX = 11.0;
    /** Idem en la Estilista y el Telar, cuyo cuerpo es más bajo y el resto es el pórtico. */
    public static final double ARRIBA_BAJA_PX = 10.0;

    /** ¿El click cayó en la zona de carga? */
    public static boolean arriba(BlockHitResult hit, BlockPos pos, double umbralPx) {
        if (hit.getSide() == net.minecraft.util.math.Direction.UP) return true;
        return hit.getPos().y - pos.getY() >= umbralPx / 16.0;
    }
}
