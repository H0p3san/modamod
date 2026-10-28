package com.modamod.mixin;

import com.modamod.cinta.CintaFisica;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sin el bamboleo de la cámara cuando la cinta te lleva y no tocás las teclas (2026-10-07, "se puede hacer que sea un
 * deslizamiento?"): el balanceo de la vista en primera persona sale de {@code strideDistance}, que sube con la velocidad
 * aunque no camines. Con la cinta moviéndote y sin input se baja a 0.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityCintaMixin {

    @Inject(method = "tickMovement()V", at = @At("TAIL"))
    private void modamod$sinBamboleo(CallbackInfo ci) {
        ClientPlayerEntity yo = (ClientPlayerEntity) (Object) this;
        boolean quieto = yo.input != null && yo.input.getMovementInput().lengthSquared() < 1e-4f;
        CintaFisica.jugadorQuieto = quieto ? yo : null;
        if (quieto && CintaFisica.llevada(yo)) {
            yo.strideDistance = 0f;
        }
    }
}
