package com.modamod.client;

import com.modamod.aplique.Aplique;
import com.modamod.aplique.MoldeApliquePersonalizadoItem;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Ícono del molde de aplique personalizado (2026-10-04, "el ícono del molde sea un fondo de papel kraft con el
 * objeto dibujado encima"): el papel de los moldes de aplique, con el grosor de un item/generated, y encima el
 * objeto del aplique dibujado por Minecraft como en el inventario (un bloque se ve en isométrico). Los moldes de
 * un modelo del mod (moño, mariposa, flor) llevan solo el papel. El nombre y el resto salen del tooltip.
 */
public class MoldeApliqueItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    /** Media del grosor de un item/generated (1/16). */
    private static final float Z = 0.5f / 16f;

    @Override
    public void render(ItemStack stack, ModelTransformationMode modo, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int luz, int overlay) {
        Aplique a = MoldeApliquePersonalizadoItem.plantilla(stack);
        // Un objeto: el ícono armado en el juego (papel doblado + dibujo lineal + recuadro lila). Un modelo del mod:
        // el ícono dibujado a mano de su molde.
        Identifier textura = a == null ? Identifier.of("modamod", "textures/item/molde_en_blanco.png")
                : a.objeto() != null ? IconoMoldeAplique.de(a.objeto())
                : Identifier.of("modamod", "textures/item/molde_aplique_" + a.modelo().clave + ".png");
        // vanilla ya hizo translate(-0.5,-0.5,-0.5): el ítem ocupa [0,1]³.
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(textura));
        MatrixStack.Entry e = matrices.peek();
        float zf = 0.5f + Z, za = 0.5f - Z;
        vertice(buffer, e, 0, 0, zf, 0, 1, 0, 0, 1, luz, overlay);
        vertice(buffer, e, 1, 0, zf, 1, 1, 0, 0, 1, luz, overlay);
        vertice(buffer, e, 1, 1, zf, 1, 0, 0, 0, 1, luz, overlay);
        vertice(buffer, e, 0, 1, zf, 0, 0, 0, 0, 1, luz, overlay);
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
