package com.modamod.client;

import com.modamod.guardarropas.MaletinBlock;
import com.modamod.guardarropas.MaletinBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * El maletín apoyado (2026-10-23): se dibuja como su ícono 3D ({@link IconoAccesorioItemRenderer}, con zonas y apliques),
 * parado sobre el piso y girado hacia quien lo puso.
 */
public class MaletinBlockRenderer implements BlockEntityRenderer<MaletinBlockEntity> {

    private static final IconoAccesorioItemRenderer ICONO = new IconoAccesorioItemRenderer();
    /** El ícono entra en 0,8 bloque de lado; apoyado se achica a un maletín de verdad (unos 9 px de largo). */
    private static final float TAMANO = 0.7f;

    public MaletinBlockRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(MaletinBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int luz, int overlay) {
        if (be.maletin().isEmpty()) return;
        matrices.push();
        matrices.translate(0.5f, 0f, 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-be.getCachedState().get(MaletinBlock.FACING).asRotation()));
        matrices.scale(TAMANO, TAMANO, TAMANO);
        // El ícono va centrado en el cubo 0..1: se baja para que la base apoye en el piso.
        matrices.translate(-0.5f, -0.5f + IconoAccesorioItemRenderer.medioAlto(be.maletin()), -0.5f);
        ICONO.render(be.maletin(), ModelTransformationMode.FIXED, matrices, vertexConsumers, luz, overlay);
        matrices.pop();
    }
}
