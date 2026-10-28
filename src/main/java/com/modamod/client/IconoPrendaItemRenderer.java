package com.modamod.client;

import com.modamod.render.IconoPrenda;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Dibuja el ícono de 64x64 armado con la tela real ({@link IconoPrenda}) —
 * 2026-09-29, "ok pero los hagamos 64x64" / "B". Un cuadrado de frente y
 * otro de espalda (espejado), con el grosor de un item/generated; las
 * transformaciones de cada vista (mano, inventario, piso, marco) las
 * aplica vanilla con el "display" del modelo builtin/entity del ítem.
 */
public class IconoPrendaItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    /** Media del grosor de un item/generated (1/16). */
    private static final float Z = 0.5f / 16f;

    /** Si el ícono no se pudo armar (sin plantilla): lo que se dibujaba antes. */
    @Nullable
    private final BuiltinItemRendererRegistry.DynamicItemRenderer respaldo;

    public IconoPrendaItemRenderer(@Nullable BuiltinItemRendererRegistry.DynamicItemRenderer respaldo) {
        this.respaldo = respaldo;
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode modo, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int luz, int overlay) {
        Identifier icono = IconoPrenda.de(stack);
        if (icono == null) {
            if (respaldo != null) respaldo.render(stack, modo, matrices, vertexConsumers, luz, overlay);
            return;
        }
        // vanilla ya hizo translate(-0.5,-0.5,-0.5): el ítem ocupa [0,1]³.
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(icono));
        MatrixStack.Entry e = matrices.peek();
        float zf = 0.5f + Z, za = 0.5f - Z;
        // Frente (la v invertida: el eje Y del ítem sube, el de la textura baja).
        vertice(buffer, e, 0, 0, zf, 0, 1, 0, 0, 1, luz, overlay);
        vertice(buffer, e, 1, 0, zf, 1, 1, 0, 0, 1, luz, overlay);
        vertice(buffer, e, 1, 1, zf, 1, 0, 0, 0, 1, luz, overlay);
        vertice(buffer, e, 0, 1, zf, 0, 0, 0, 0, 1, luz, overlay);
        // Espalda: orden al revés para que mire para atrás, mismo dibujo que visto a través.
        vertice(buffer, e, 0, 1, za, 0, 0, 0, 0, -1, luz, overlay);
        vertice(buffer, e, 1, 1, za, 1, 0, 0, 0, -1, luz, overlay);
        vertice(buffer, e, 1, 0, za, 1, 1, 0, 0, -1, luz, overlay);
        vertice(buffer, e, 0, 0, za, 0, 1, 0, 0, -1, luz, overlay);
    }

    private static void vertice(VertexConsumer buffer, MatrixStack.Entry e, float x, float y, float z,
                                float u, float v, float nx, float ny, float nz, int luz, int overlay) {
        buffer.vertex(e.getPositionMatrix(), x, y, z)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(overlay)
                .light(luz)
                .normal(e, nx, ny, nz);
    }
}
