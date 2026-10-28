package com.modamod.util;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Señal de redstone en las máquinas y la logística (2026-10-05, "el conveyor belt y las maquinas dejan de circular
 * items cuando reciben señal de redstone" → "se detienen del todo"): con señal, la cinta y el empalme se congelan, y
 * la Modeladora, Tintes y la Sublimadora frenan su proceso y no reciben ni empujan nada por automatización (la mano
 * del jugador sigue valiendo).
 */
public final class Redstone {

    private Redstone() {}

    public static boolean pausada(World mundo, BlockPos pos) {
        return mundo != null && mundo.isReceivingRedstonePower(pos);
    }

    public static boolean pausada(BlockEntity be) {
        return be != null && pausada(be.getWorld(), be.getPos());
    }
}
