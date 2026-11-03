package com.modamod.util;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Dónde se clickeó una máquina (a pedido, 2026-10-08, "si se clickea en la parte de la tapa entra la prenda, si se
 * clickea abajo abre la gui"): arriba (la cara de arriba o la franja alta del frente) se carga la prenda o el molde;
 * abajo se abre la pantalla aunque tengas algo en la mano. La altura la da cada máquina ({@code MaquinaBlock#alturaTapaPx}).
 */
public final class ZonaMaquina {

    private ZonaMaquina() {}

    /** ¿El click cayó en la zona de carga? */
    public static boolean arriba(BlockHitResult hit, BlockPos pos, double umbralPx) {
        if (hit.getSide() == net.minecraft.util.math.Direction.UP) return true;
        return hit.getPos().y - pos.getY() >= umbralPx / 16.0;
    }
}
