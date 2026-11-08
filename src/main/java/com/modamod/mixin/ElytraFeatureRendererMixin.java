package com.modamod.mixin;

import com.modamod.render.GarmentFeatureRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La capa del mod tapa los élitros (2026-11-08, "la capa siempre gana"): con una capa puesta los élitros no se dibujan,
 * ni en el piso ni al planear. Siguen funcionando igual; solo no se ven.
 */
@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraFeatureRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void modamod$capaTapaElitros(MatrixStack matrices, VertexConsumerProvider vcp, int luz, LivingEntity entidad,
                                         float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        if (GarmentFeatureRenderer.capaDe(GarmentFeatureRenderer.equipadas(entidad)) != null) ci.cancel();
    }
}
