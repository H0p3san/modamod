package com.modamod.render;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.function.Function;

/**
 * La pantallita siempre iluminada (2026-09-29, "que las pantallitas de las
 * maquinas siempre esten iluminadas"): vuelve a dibujar el modelo con la
 * textura de {@link PantallaMaquina#glow} — solo la pantalla opaca — en la
 * capa emisiva ({@code getEyes}, a luz máxima), mismo truco que
 * {@link LedGlowLayer} con los LEDs. Se ve igual de noche que de día.
 */
public class PantallaGlowLayer<T extends BlockEntity & GeoAnimatable> extends GeoRenderLayer<T> {

    private final Function<T, Identifier> textura;

    public PantallaGlowLayer(GeoRenderer<T> renderer, Function<T, Identifier> textura) {
        super(renderer);
        this.textura = textura;
    }

    @Override
    public void render(MatrixStack matrices, T animatable, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                       VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        Identifier id = textura.apply(animatable);
        if (id == null) return;
        RenderLayer capa = RenderLayer.getEyes(id);
        VertexConsumer vc = bufferSource.getBuffer(capa);
        getRenderer().reRender(bakedModel, matrices, bufferSource, animatable, capa, vc, partialTick,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, packedOverlay, -1);
    }
}
