package com.modamod.client;

import com.modamod.aplique.RetazoApliqueItem;
import com.modamod.region.RegionPintura;
import com.modamod.region.RegionResolver;
import com.modamod.render.ZonaTextura;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** El retazo de aplique: tres franjas, una por zona (de arriba abajo), con su color y sus tramas de Tintes. */
public class RetazoItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    private static final float Z = 0.5f / 16f;
    /** Margen del paño dentro del ícono (en fracción del ítem). */
    private static final float M = 2f / 16f;

    @Override
    public void render(ItemStack stack, ModelTransformationMode modo, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int luz, int overlay) {
        List<Integer> colores = RetazoApliqueItem.colores(stack);
        List<RegionResolver.CapaPatron> todas = RetazoApliqueItem.capas(stack);
        MatrixStack.Entry e = matrices.peek();
        float alto = (1f - 2 * M) / 3f;
        for (int z = 0; z < 3; z++) {
            RegionPintura reg = RegionPintura.values()[RegionPintura.ZONA_1.ordinal() + z];
            List<RegionResolver.CapaPatron> capas = new ArrayList<>();
            for (var c : todas) if (c.region() == reg) capas.add(c);
            Identifier tela = ZonaTextura.banner(colores.get(z), capas);
            VertexConsumer b = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(tela));
            // vanilla ya hizo translate(-0.5,-0.5,-0.5): el ítem ocupa [0,1]³; la zona 1 arriba.
            float y1 = 1f - M - z * alto, y0 = y1 - alto;
            float v0 = z / 3f, v1 = (z + 1) / 3f;
            float zf = 0.5f + Z, za = 0.5f - Z;
            vertice(b, e, M, y0, zf, 0, v1, 1, luz, overlay);
            vertice(b, e, 1 - M, y0, zf, 1, v1, 1, luz, overlay);
            vertice(b, e, 1 - M, y1, zf, 1, v0, 1, luz, overlay);
            vertice(b, e, M, y1, zf, 0, v0, 1, luz, overlay);
            vertice(b, e, M, y1, za, 0, v0, -1, luz, overlay);
            vertice(b, e, 1 - M, y1, za, 1, v0, -1, luz, overlay);
            vertice(b, e, 1 - M, y0, za, 1, v1, -1, luz, overlay);
            vertice(b, e, M, y0, za, 0, v1, -1, luz, overlay);
        }
    }

    private static void vertice(VertexConsumer buffer, MatrixStack.Entry e, float x, float y, float z,
                                float u, float v, float nz, int luz, int overlay) {
        buffer.vertex(e.getPositionMatrix(), x, y, z)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(overlay)
                .light(luz)
                .normal(e, 0, 0, nz);
    }
}
