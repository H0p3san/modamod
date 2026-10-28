package com.modamod.mixin;

import com.modamod.cinta.CintaFisica;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cinta parada sobre hielo (2026-10-07, "cuando esta detenida una cinta transportadora con redstone y tiene hielo abajo
 * cuando camino sobre la cinta deslizo como si caminara sobre hielo"): la fricción sale del bloque a medio bloque bajo
 * los pies, y como la cinta es baja ese punto cae en el hielo de abajo. Con la cinta parada se usa la fricción de la
 * propia cinta. En marcha se deja como está (el hielo de abajo sigue acelerando).
 */
@Mixin(Entity.class)
public abstract class EntityCintaParadaMixin {

    @Inject(method = "getVelocityAffectingPos", at = @At("HEAD"), cancellable = true)
    private void modamod$cintaParada(CallbackInfoReturnable<BlockPos> cir) {
        BlockPos p = CintaFisica.paradaBajo((Entity) (Object) this);
        if (p != null) cir.setReturnValue(p);
    }
}
