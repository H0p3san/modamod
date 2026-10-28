package com.modamod.mixin;

import com.modamod.garment.Parte;
import com.modamod.render.CuerpoGeometria;
import com.modamod.render.GarmentFeatureRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Apaga la capa BASE de vanilla donde manda una prenda del mod, para que
 * nuestra reconstrucción la reemplace de verdad — a pedido explícito
 * (2026-09-16), "no quiero que haya dilatación, quiero que nuestro
 * modelo reemplace el original".
 *
 * <h2>Por qué no alcanza con borrar la textura, como con la segunda capa</h2>
 * {@code SkinRegions}/{@code ComposedSkin} ya borran (alfa 0) la SEGUNDA
 * capa de la skin (la "overlay" que vanilla infla 0.25, la que 3D Skin
 * Layers convierte en geometría) donde gobierna una prenda. Pero eso es
 * la capa DECORATIVA — la capa BASE (el cuerpo real del jugador) no tiene
 * un "hueco" análogo que borrar: es la piel de siempre, siempre opaca, y
 * vanilla la dibuja pase lo que pase. Con {@code CuerpoGeometria.Superficie
 * .CUERPO} calzando casi exacto con esa caja base, las dos superficies
 * quedaban compitiendo en profundidad — a veces ganaba la de vanilla, sin
 * reconstruir, y se veía "a través" de la prenda. La única forma real de
 * que gane siempre la nuestra es que vanilla directamente no dibuje esas
 * partes ahí.
 *
 * <h2>El enganche</h2>
 * {@code LivingEntityRenderer#render} dibuja la capa base
 * ({@code this.model.render(...)}) y DESPUÉS corre los feature renderers
 * (donde vive {@link GarmentFeatureRenderer}). Se inyecta ANTES de esa
 * llamada para apagar ({@code ModelPart.visible = false}) las partes que
 * gobierna alguna prenda, y JUSTO DESPUÉS para prenderlas de vuelta —
 * tienen que estar visibles otra vez para cuando corran los feature
 * renderers, porque {@code GarmentFeatureRenderer#render} usa
 * {@code delJugador.visible} para decidir si dibuja algo ahí (si
 * quedaran apagadas, ni vanilla NI nosotros dibujaríamos nada: un
 * agujero real, peor que el bug que esto arregla).
 *
 * Genérico sobre {@code LivingEntityRenderer} (no solo jugadores) porque
 * ahí es donde vive {@code render}, pero adentro se chequea el tipo de
 * entidad — sin efecto sobre mobs u otros jugadores sin ropa del mod.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow
    protected M model;

    /**
     * El busto del jugador que se dibuja, para los modelos de otros mods
     * (2026-10-02, "que adapte las prendas y armaduras de cualquier mod") —
     * ver {@code render.relieve.BustoEnModelos}.
     */
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void modamod$bustoEmpieza(T entidad, float f, float g, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int luz, CallbackInfo ci) {
        com.modamod.render.relieve.BustoEnModelos.enEntidad = entidad instanceof AbstractClientPlayerEntity
                ? GarmentFeatureRenderer.bustoDe(entidad, g, true) : null;
        com.modamod.render.relieve.BustoEnModelos.modeloPrincipal = this.model;
        com.modamod.ropa.CosmeticosRender.entidad = entidad;   // armadura cosmética (2026-10-05)
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void modamod$bustoTermina(T entidad, float f, float g, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int luz, CallbackInfo ci) {
        com.modamod.render.relieve.BustoEnModelos.enEntidad = null;
        com.modamod.render.relieve.BustoEnModelos.modeloPrincipal = null;
        com.modamod.ropa.CosmeticosRender.entidad = null;
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
            shift = At.Shift.BEFORE))
    private void modamod$ocultarCapaBase(T entidad, float f, float g, MatrixStack matrices,
                                             VertexConsumerProvider vertexConsumers, int luz, CallbackInfo ci) {
        if (!(entidad instanceof AbstractClientPlayerEntity jugador)) return;
        if (!(this.model instanceof BipedEntityModel<?> biped)) return;
        for (Parte parte : GarmentFeatureRenderer.partesAOcultarDeVanilla(jugador)) {
            CuerpoGeometria.delJugador(biped, parte).visible = false;
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
            shift = At.Shift.AFTER))
    private void modamod$restaurarCapaBase(T entidad, float f, float g, MatrixStack matrices,
                                               VertexConsumerProvider vertexConsumers, int luz, CallbackInfo ci) {
        if (!(entidad instanceof AbstractClientPlayerEntity jugador)) return;
        if (!(this.model instanceof BipedEntityModel<?> biped)) return;
        for (Parte parte : GarmentFeatureRenderer.partesAOcultarDeVanilla(jugador)) {
            CuerpoGeometria.delJugador(biped, parte).visible = true;
        }
    }

    /**
     * El twirl (2026-09-30): una vuelta entera alrededor del eje del cuerpo,
     * después del giro normal del cuerpo. Ver {@code client/TwirlCliente}.
     */
    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void modamod$twirl(T entidad, MatrixStack matrices, float animationProgress, float bodyYaw,
                                  float tickDelta, float scale, CallbackInfo ci) {
        float p = com.modamod.client.TwirlCliente.progreso(entidad, tickDelta);
        if (p < 0f) return;
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(
                -com.modamod.client.TwirlCliente.angulo(p)));
    }
}
