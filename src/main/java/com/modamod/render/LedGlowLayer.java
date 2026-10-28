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
 * Segunda pasada de render para los 3 LEDs (verde/rojo/amarillo) de una
 * máquina — brilla de verdad, ignora la luz ambiente (2026-09-21, "que
 * brillen de verdad, tipo lucecitas geckolib"). Usa
 * {@link RenderLayer#getEyes} (el mismo mecanismo que los ojos de
 * Enderman/araña) en vez de {@code AutoGlowingGeoLayer} de GeckoLib —
 * ver el javadoc de {@link PantallaLed} para por qué.
 *
 * @param <T> el tipo de block entity de la máquina (necesita ser
 *            BlockEntity de verdad, {@link #render} usa su
 *            {@code getPos()} para cachear la textura por máquina).
 */
public class LedGlowLayer<T extends BlockEntity & GeoAnimatable> extends GeoRenderLayer<T> {

    private final int anchoAtlas, altoAtlas;
    private final PantallaLed.Rect[] leds;
    private final Function<T, int[]> colores;

    public LedGlowLayer(GeoRenderer<T> renderer, int anchoAtlas, int altoAtlas,
                         PantallaLed.Rect[] leds, Function<T, int[]> colores) {
        super(renderer);
        this.anchoAtlas = anchoAtlas;
        this.altoAtlas = altoAtlas;
        this.leds = leds;
        this.colores = colores;
    }

    @Override
    public void render(MatrixStack matrices, T animatable, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                        VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                        int packedLight, int packedOverlay) {
        Identifier textura = PantallaLed.con(anchoAtlas, altoAtlas, leds, colores.apply(animatable), animatable.getPos());
        RenderLayer capaGlow = RenderLayer.getEyes(textura);
        VertexConsumer vc = bufferSource.getBuffer(capaGlow);
        getRenderer().reRender(bakedModel, matrices, bufferSource, animatable, capaGlow, vc, partialTick,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, packedOverlay, -1);
    }
}
