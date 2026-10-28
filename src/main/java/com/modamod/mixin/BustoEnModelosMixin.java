package com.modamod.mixin;

import com.modamod.render.relieve.BustoEnModelos;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * El busto en los modelos humanoides de cualquier mod (2026-10-02) — ver
 * {@link BustoEnModelos}. Al final del dibujo del modelo, con el mismo
 * {@code VertexConsumer}: misma textura, tinte, ajustes y brillo.
 */
@Mixin(AnimalModel.class)
public abstract class BustoEnModelosMixin {

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
            at = @At("TAIL"))
    private void modamod$busto(MatrixStack matrices, VertexConsumer vertices, int luz, int overlay, int color,
                                  CallbackInfo ci) {
        BustoEnModelos.alDibujar(this, matrices, vertices, luz, overlay, color);
    }
}
