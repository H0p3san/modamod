package com.modamod.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Que las luces de las máquinas iluminen de verdad (2026-09-29, "hace que
 * las luces de las maquinas iluminen"): el LED ya se dibujaba emisivo
 * ({@code LedGlowLayer}), pero no le daba luz al bloque ni a lo que tiene
 * alrededor. La luz de un bloque sale de su estado, así que cada máquina
 * lleva {@link #LIT} y lo prende mientras su LED está prendido: trabajando
 * (rojo) o con la prenda lista (verde). Fijo mientras trabaja, sin seguir el
 * titileo del rojo — prender y apagar la luz cada segundo obliga a
 * recalcular la iluminación de alrededor todo el tiempo.
 */
public final class LuzMaquina {

    public static final BooleanProperty LIT = Properties.LIT;
    /** Nivel de luz del LED: alumbra el bloque y un poco alrededor, menos que una antorcha (14). */
    public static final int NIVEL = 10;
    /**
     * Luz tenue de la pantallita con la máquina quieta (2026-09-29, "que
     * generen una luz tenue cuando la maquina no funciona y mas fuerte cuando
     * funciona"): {@link #NIVEL} trabajando o con la prenda lista, esto el resto.
     */
    public static final int NIVEL_REPOSO = 4;

    private LuzMaquina() {}

    public static int luminancia(BlockState state) {
        if (!state.contains(LIT)) return 0;
        return state.get(LIT) ? NIVEL : NIVEL_REPOSO;
    }

    /** Solo servidor: cambia el estado si el LED se prendió o se apagó. */
    public static void actualizar(World world, BlockPos pos, BlockState state, boolean prendida) {
        if (world == null || world.isClient || !state.contains(LIT) || state.get(LIT) == prendida) return;
        world.setBlockState(pos, state.with(LIT, prendida), Block.NOTIFY_ALL);
    }
}
