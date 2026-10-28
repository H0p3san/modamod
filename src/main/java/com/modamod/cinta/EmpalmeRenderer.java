package com.modamod.cinta;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Dibuja lo que cruza el empalme (2026-10-05, "la conjunction belt muestra los items diferentes y a diferente velocidad...
 * deberia ser un viaje smooth"): igual que en la cinta —la misma prenda con su ícono, el mismo ítem acostado— y a la misma
 * velocidad de 1 px por tick: de atrás recto, de un costado en arco como las curvas y desde arriba del centro al frente.
 */
public class EmpalmeRenderer implements BlockEntityRenderer<EmpalmeBlockEntity> {

    public EmpalmeRenderer(BlockEntityRendererFactory.Context ctx) {}

    private static final net.minecraft.block.BlockState PLATAFORMA = net.minecraft.block.Blocks.DARK_OAK_PLANKS.getDefaultState();
    private static final net.minecraft.block.BlockState BASE_CASILLERO = net.minecraft.block.Blocks.SPRUCE_PLANKS.getDefaultState();
    private static final net.minecraft.block.BlockState PALITO = net.minecraft.block.Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
    private static final net.minecraft.block.BlockState MARCO = net.minecraft.block.Blocks.JUNGLE_PLANKS.getDefaultState();

    private static void caja(MatrixStack m, VertexConsumerProvider b, int luz, int ov, net.minecraft.block.BlockState e,
                             float x, float y, float z, float w, float h, float d) {
        m.push();
        m.translate(x, y, z);
        m.scale(w, h, d);
        net.minecraft.client.MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(e, m, b, luz, ov);
        m.pop();
    }

    /**
     * La estructura de los filtros (2026-10-19): una plataforma sobre el bloque con un casillero por costado y uno al medio (la tolva).
     * Cada item seteado se muestra apoyado en su casillero; con filtro por tag lleva un marco alrededor. Se dibuja con la madera de vanilla.
     */
    private void dibujarFiltros(EmpalmeBlockEntity be, MatrixStack matrices, VertexConsumerProvider buffers, int luz, int overlay) {
        float f = 1f / 16f;
        matrices.push();
        // Palitos de soporte en las esquinas y la plataforma elevada (11 a 12 px): el item que cruza se ve por debajo.
        // Los palitos se apoyan en las esquinitas de madera del modelo (2x2 px, hasta 5,5 de alto) y la plataforma descansa en ellos.
        for (float[] e : new float[][]{{1, 1}, {14, 1}, {1, 14}, {14, 14}}) {
            caja(matrices, buffers, luz, overlay, PALITO, e[0] * f, 5.5f * f, e[1] * f, 1 * f, 5.5f * f, 1 * f);
        }
        caja(matrices, buffers, luz, overlay, PLATAFORMA, 1 * f, 11 * f, 1 * f, 14 * f, 1 * f, 14 * f);
        float giro = (be.getCachedState().get(EmpalmeBlock.FACING).asRotation() + 180f) % 360f;
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-giro));
        // En el marco del bloque girado al frente: el frente es -Z, la izquierda -X, la derecha +X, atras +Z.
        float[][] pos = {{0f, -4.4f}, {0f, 4.4f}, {-4.4f, 0f}, {4.4f, 0f}, {0f, 0f}};
        float base = 12f;
        for (int c = 0; c < EmpalmeBlockEntity.CASILLEROS; c++) {
            float cx = pos[c][0] * f, cz = pos[c][1] * f;
            caja(matrices, buffers, luz, overlay, BASE_CASILLERO, cx - 1.6f * f, base * f, cz - 1.6f * f, 3.2f * f, 0.5f * f, 3.2f * f);
            ItemStack filtro = be.filtro(c);
            if (filtro.isEmpty()) continue;
            if (be.filtroPorTag(c)) {
                // El marco: cuatro tiras de madera alrededor del casillero.
                float r = 2.1f * f, g = 0.6f * f, h = 0.9f * f;
                caja(matrices, buffers, luz, overlay, MARCO, cx - r, (base + 0.5f) * f, cz - r, 2 * r, h, g);
                caja(matrices, buffers, luz, overlay, MARCO, cx - r, (base + 0.5f) * f, cz + r - g, 2 * r, h, g);
                caja(matrices, buffers, luz, overlay, MARCO, cx - r, (base + 0.5f) * f, cz - r, g, h, 2 * r);
                caja(matrices, buffers, luz, overlay, MARCO, cx + r - g, (base + 0.5f) * f, cz - r, g, h, 2 * r);
            }
            matrices.push();
            matrices.translate(cx, (base + 0.55f) * f + 0.06f, cz);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
            matrices.scale(0.22f, 0.22f, 0.22f);
            net.minecraft.client.MinecraftClient.getInstance().getItemRenderer().renderItem(filtro,
                    net.minecraft.client.render.model.json.ModelTransformationMode.FIXED, luz, overlay, matrices, buffers,
                    be.getWorld(), 0);
            matrices.pop();
        }
        matrices.pop();
    }

    @Override
    public void render(EmpalmeBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider buffers, int luz, int overlay) {
        dibujarFiltros(be, matrices, buffers, luz, overlay);
        ItemStack stack = be.carga();
        if (stack.isEmpty() || be.getWorld() == null) return;
        float t = (be.tiempoEfectivo(tickDelta) - be.llegada()) / be.ticks();
        t = Math.max(0f, Math.min(1f, t));
        float px, pz, tx, tz;
        switch (be.lado()) {
            case 1 -> {
                float fi = t * (float) Math.PI / 2f, s = (float) Math.sin(fi), c = (float) Math.cos(fi);
                px = -0.5f + 0.5f * s; pz = -0.5f + 0.5f * c; tx = c; tz = -s;
            }
            case 2 -> {
                float fi = t * (float) Math.PI / 2f, s = (float) Math.sin(fi), c = (float) Math.cos(fi);
                px = 0.5f - 0.5f * s; pz = -0.5f + 0.5f * c; tx = -c; tz = -s;
            }
            case 3 -> { px = 0f; pz = -0.5f * t; tx = 0f; tz = -1f; }
            default -> { px = 0f; pz = 0.5f - t; tx = 0f; tz = -1f; }
        }
        float giro = (be.getCachedState().get(EmpalmeBlock.FACING).asRotation() + 180f) % 360f;   // el mismo giro "y" del blockstate
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-giro));
        CintaRenderer.dibujarCarga(stack, be.getWorld(), matrices, buffers, luz, overlay, px, 0f, pz, tx, 0f, tz);
        matrices.pop();
    }
}
