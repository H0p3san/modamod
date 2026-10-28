package com.modamod.mixin;

import com.modamod.cinta.CintaFisica;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Deslizarse sobre la cinta (2026-10-07, "se puede hacer que sea un deslizamiento? porque se ve como si el personaje
 * caminara"): las piernas y los brazos se animan por lo que se desplazó la entidad en el tick, y la cinta la desplaza sin
 * que camine. Se le descuenta lo que la banda la lleva ({@link CintaFisica#pasoPropio}): quieta, no mueve las
 * piernas; si camina, se anima por su propio paso. Va en común porque también lo corre el cliente con los otros jugadores.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCintaMixin {

    @ModifyVariable(method = "updateLimbs(F)V", at = @At("HEAD"), argsOnly = true)
    private float modamod$sinLaBanda(float posDelta) {
        return CintaFisica.pasoPropio((LivingEntity) (Object) this, posDelta);
    }
}
