package com.modamod.mixin;

import com.modamod.render.relieve.BustoEnModelos;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Qué slot de armadura se está dibujando (2026-10-02, "quizas con armaduras
 * pueden mantenerse rigidas"): la pechera lleva el busto rígido y las
 * demás piezas (que también dibujan el torso) no lo llevan. Ver
 * {@link BustoEnModelos}.
 */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmaduraBustoMixin<T extends LivingEntity, M extends BipedEntityModel<T>, A extends BipedEntityModel<T>> {

    @Inject(method = "renderArmor", at = @At("HEAD"))
    private void modamod$slot(MatrixStack matrices, VertexConsumerProvider vcp, T entidad, EquipmentSlot slot,
                                 int luz, A modelo, CallbackInfo ci) {
        BustoEnModelos.slotArmadura = slot;
    }

    @Inject(method = "renderArmor", at = @At("RETURN"))
    private void modamod$finSlot(MatrixStack matrices, VertexConsumerProvider vcp, T entidad, EquipmentSlot slot,
                                    int luz, A modelo, CallbackInfo ci) {
        BustoEnModelos.slotArmadura = null;
    }
}
