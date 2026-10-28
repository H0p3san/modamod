package com.modamod.mixin;

import com.modamod.client.TwirlCliente;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brazos abiertos durante el twirl (2026-09-30, "podemos hacer que en el
 * twirl el personaje abra los brazos?"). Al final del posado de
 * {@code BipedEntityModel#setAngles} — antes de que {@code PlayerEntityModel}
 * copie los brazos a las mangas de la skin — los brazos suben hacia los
 * costados con una curva seno: arrancan pegados, quedan casi horizontales a
 * mitad de la vuelta y bajan al terminar. La ropa de los brazos copia esta
 * pose sola (GarmentFeatureRenderer usa copyTransform).
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelTwirlMixin {

    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    /** Cuánto se abren en el medio de la vuelta (radianes, ~75°). */
    private static final float MODAMOD$APERTURA = 1.3F;

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void modamod$abrirBrazosEnTwirl(LivingEntity entidad, float limbAngle, float limbDistance,
                                              float animationProgress, float headYaw, float headPitch,
                                              CallbackInfo ci) {
        // animationProgress = age + tickDelta: de ahí sale el tickDelta sin pedírselo al cliente.
        float p = TwirlCliente.progreso(entidad, animationProgress - entidad.age);
        if (p < 0F) return;
        float e = MathHelper.sin((float) Math.PI * p);
        rightArm.roll = MathHelper.lerp(e, rightArm.roll, MODAMOD$APERTURA);
        leftArm.roll = MathHelper.lerp(e, leftArm.roll, -MODAMOD$APERTURA);
        rightArm.pitch = MathHelper.lerp(e, rightArm.pitch, 0F);
        leftArm.pitch = MathHelper.lerp(e, leftArm.pitch, 0F);
        rightArm.yaw = MathHelper.lerp(e, rightArm.yaw, 0F);
        leftArm.yaw = MathHelper.lerp(e, leftArm.yaw, 0F);
    }
}
