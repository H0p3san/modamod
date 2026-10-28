package com.modamod.client;

import com.modamod.item.BandaItem;
import com.modamod.item.BorcegosItem;
import com.modamod.render.AccesorioRenderer;
import com.modamod.render.BorcegosRenderer;
import com.modamod.render.FisicaApliques;
import com.modamod.render.SombreroRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * El ícono del sombrero, el calzado y la banda (2026-10-15, "me gusta como los dibujas de acuerdo a las decisiones"): la
 * pieza armada de verdad, con sus moldes, colores y patrones, en vez de un PNG fijo. Se dibuja con los mismos renderers de
 * cuando se lleva puesta, sobre un modelo de jugador sin entidad, centrada y escalada a la caja del ítem según las cajas
 * de apoyo de la pieza (así entra igual un gorro que un sombrero de ala ancha).
 */
public class IconoAccesorioItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    private static PlayerEntityModel<?> modelo;

    private static PlayerEntityModel<?> modelo() {
        if (modelo == null) {
            modelo = new PlayerEntityModel<>(MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER), false);
        }
        return modelo;
    }

    /**
     * Los límites (px) de la pieza en el marco del modelo: el calzado en las dos piernas (pivote en y=12, x=±1,9) y el maletín
     * en la mano derecha (pivote del brazo en x=-5, y=2).
     */
    private static float[][] limites(ItemStack stack) {
        boolean calzado = stack.getItem() instanceof BorcegosItem;
        boolean maletin = stack.getItem() instanceof com.modamod.guardarropas.MaletinItem;
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (boolean izq : calzado ? new boolean[] {false, true} : new boolean[] {false}) {
            List<SombreroRenderer.Caja> cajas = calzado ? BorcegosRenderer.cajas(stack, izq) : AccesorioRenderer.cajas(stack);
            float dx = calzado ? (izq ? 1.9f : -1.9f) : maletin ? -5f : 0f, dy = calzado ? 12f : maletin ? 2f : 0f;
            for (SombreroRenderer.Caja c : cajas) {
                min[0] = Math.min(min[0], c.min()[0] + dx); max[0] = Math.max(max[0], c.max()[0] + dx);
                min[1] = Math.min(min[1], c.min()[1] + dy); max[1] = Math.max(max[1], c.max()[1] + dy);
                min[2] = Math.min(min[2], c.min()[2]); max[2] = Math.max(max[2], c.max()[2]);
            }
        }
        return new float[][] {min, max};
    }

    private static float escala(float[] min, float[] max) {
        float extension = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
        return 0.8f * 16f / Math.max(4f, extension);
    }

    /** Medio alto del ícono en el cubo 0..1 (lo usa el maletín apoyado para poner la base en el piso). */
    public static float medioAlto(ItemStack stack) {
        float[][] l = limites(stack);
        return (l[1][1] - l[0][1]) / 2f * escala(l[0], l[1]) / 16f;
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode modo, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int luz, int overlay) {
        if (!AccesorioRenderer.es(stack)) return;
        float[][] lim = limites(stack);
        float[] min = lim[0], max = lim[1];
        if (min[0] > max[0]) return;
        float k = escala(min, max);
        var cliente = MinecraftClient.getInstance();
        BorcegosRenderer.pielActual = cliente.player != null ? cliente.player.getSkinTextures().texture()
                : net.minecraft.client.util.DefaultSkinHelper.getTexture();
        var inercia = FisicaApliques.actual;
        FisicaApliques.actual = null;   // sin el bamboleo de la última entidad dibujada
        matrices.push();
        try {
            // El marco del modelo tiene la Y hacia abajo y el frente en −Z: se da vuelta para mirar de frente al ítem.
            matrices.translate(0.5f, 0.5f, 0.5f);
            // El maletín cuelga de costado (su largo va hacia adelante): se gira para mostrar la cara ancha.
            if (stack.getItem() instanceof com.modamod.guardarropas.MaletinItem) {
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(90f));
            }
            matrices.scale(k, -k, -k);
            matrices.translate(-(min[0] + max[0]) / 32f, -(min[1] + max[1]) / 32f, -(min[2] + max[2]) / 32f);
            int l = modo == ModelTransformationMode.GUI ? LightmapTextureManager.MAX_LIGHT_COORDINATE : luz;
            AccesorioRenderer.dibujar(stack, modelo(), matrices, vertexConsumers, l, 0f);
        } finally {
            matrices.pop();
            FisicaApliques.actual = inercia;
        }
    }
}
