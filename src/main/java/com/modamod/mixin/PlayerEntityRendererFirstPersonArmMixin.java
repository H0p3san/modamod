package com.modamod.mixin;

import com.modamod.garment.Parte;
import com.modamod.render.GarmentFeatureRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mangas/cubrebrazos en la mano de PRIMERA PERSONA (2026-09-24, "la mano
 * en primera persona nunca muestra las prendas custom"). El render normal
 * ({@link GarmentFeatureRenderer}, enganchado en
 * {@code LivingEntityRenderer#render}) NUNCA corre para esta vista:
 * {@code PlayerEntityRenderer#renderRightArm}/{@code #renderLeftArm}
 * dibujan el brazo/manga de vanilla por su cuenta (llamados desde
 * {@code HeldItemRenderer}), sin pasar por el modelo completo del
 * jugador ni sus {@code FeatureRenderer}.
 *
 * <p>Se dibuja ENCIMA, después de que vanilla ya terminó ({@code TAIL}) —
 * mismo mecanismo (superficie TELA sobre el {@code ModelPart} real, ver
 * {@link GarmentFeatureRenderer#dibujarBrazoPrimeraPersona}) que usa el
 * render de tercera persona para las piezas de tela. No hace falta
 * ocultar nada de vanilla acá (a diferencia de {@link LivingEntityRendererMixin}):
 * la tela es opaca y ya gana por orden de dibujo, sin competir en
 * profundidad con la piel real (que es exactamente lo que se quiere
 * mostrar donde la prenda no tapa).
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererFirstPersonArmMixin {

    @Inject(method = "renderRightArm", at = @At("TAIL"))
    private void modamod$brazoDerechoPrimeraPersona(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                        int luz, AbstractClientPlayerEntity jugador, CallbackInfo ci) {
        PlayerEntityModel model = ((PlayerEntityRenderer) (Object) this).getModel();
        GarmentFeatureRenderer.dibujarBrazoPrimeraPersona(Parte.BRAZO_DER, model.rightArm, jugador,
                matrices, vertexConsumers, luz);
    }

    @Inject(method = "renderLeftArm", at = @At("TAIL"))
    private void modamod$brazoIzquierdoPrimeraPersona(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                          int luz, AbstractClientPlayerEntity jugador, CallbackInfo ci) {
        PlayerEntityModel model = ((PlayerEntityRenderer) (Object) this).getModel();
        GarmentFeatureRenderer.dibujarBrazoPrimeraPersona(Parte.BRAZO_IZQ, model.leftArm, jugador,
                matrices, vertexConsumers, luz);
    }
}
