package com.modamod.cinta;

import com.modamod.render.IconoPrenda;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * Dibuja la prenda que viaja por la cinta (2026-10-04, "hace vos los assets"):
 * el ícono de 64x64 acostado sobre la banda, con el cuello hacia donde va. El
 * avance sale de la hora del mundo, así cuadra con la banda animada
 * (1 px por tick).
 */
public class CintaRenderer implements BlockEntityRenderer<CintaBlockEntity> {

    /** Mitad del lado del ícono, en bloques. */
    private static final float MEDIO = 0.3f;
    /** Altura de la banda (4,05 px) más un pelo. */
    static final float ALTO = 4.5f / 16f;

    public CintaRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(CintaBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider buffers, int luz, int overlay) {
        ItemStack stack = be.carga();
        if (stack.isEmpty() || be.getWorld() == null) return;
        var estado = be.getCachedState();
        CintaBlock.Forma forma = estado.get(CintaBlock.FORMA);
        float t = (be.tiempoEfectivo(tickDelta) - be.llegada()) / CintaBlockEntity.ticksDe(be.getWorld(), be.getPos(), estado);
        t = Math.max(0f, Math.min(1f, t));

        // Posición y rumbo en el marco "mira al norte" centrado en el bloque. En las rampas la banda sube/baja
        // un bloque entero en un bloque de recorrido (45°): el ícono se inclina con ella.
        float px, pz, tx, tz;
        float py = 0f, ty = 0f;
        if (forma.esRampa()) {
            // Sube: de 0 a +1 bloque. Baja: arranca un bloque arriba (a la altura de la cinta que le entrega) y baja a 0.
            boolean sube = forma == CintaBlock.Forma.RAMPA_SUBE;
            px = 0f; pz = 0.5f - t; tx = 0f; tz = -1f;
            py = sube ? t : 1f - t; ty = sube ? 1f : -1f;
        } else if (forma == CintaBlock.Forma.RECTA) {
            px = 0f; pz = 0.5f - t; tx = 0f; tz = -1f;
        } else {
            float fi = t * (float) Math.PI / 2f;
            float s = (float) Math.sin(fi), c = (float) Math.cos(fi);
            if (forma == CintaBlock.Forma.CURVA_IZQ) {
                px = -0.5f + 0.5f * s; pz = -0.5f + 0.5f * c; tx = c; tz = -s;
            } else {
                px = 0.5f - 0.5f * s; pz = -0.5f + 0.5f * c; tx = -c; tz = -s;
            }
        }

        Direction frente = estado.get(CintaBlock.FACING);
        float giroBloque = (frente.asRotation() + 180f) % 360f;   // el mismo giro "y" del blockstate
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-giroBloque));

        dibujarCarga(stack, be.getWorld(), matrices, buffers, luz, overlay, px, py, pz, tx, ty, tz);
        matrices.pop();
    }

    /**
     * Lo que viaja, en el marco "mira al norte" centrado en el bloque ya aplicado a {@code matrices}: la prenda con su
     * ícono acostado y apuntando al rumbo (tx, ty, tz), o cualquier ítem acostado. Lo usan la cinta y el empalme, así se
     * ven igual (2026-10-05, "la conjunction belt muestra los items diferentes").
     */
    static void dibujarCarga(ItemStack stack, net.minecraft.world.World mundo, MatrixStack matrices,
                             VertexConsumerProvider buffers, int luz, int overlay,
                             float px, float py, float pz, float tx, float ty, float tz) {
        Identifier icono = IconoPrenda.de(stack);
        if (icono == null) {
            // Cualquier otro ítem (2026-10-05, "que no solo transporte ropa"): dibujado plano sobre la banda.
            dibujarItem(stack, mundo, matrices, buffers, luz, overlay, px, ALTO + py, pz);
            return;
        }
        // Arriba de la prenda = hacia donde avanza; su derecha = ese rumbo girado 90° en sentido horario.
        float rx = -tz, rz = tx;
        // Rumbo unitario en 3D y normal = derecha × rumbo.
        float lf = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        float fx = tx / lf, fy = ty / lf, fz = tz / lf;
        float nx = 0f * fz - fy * rz * 0f;   // (rx,0,rz) × (fx,fy,fz)
        nx = 0f * fz - rz * fy;
        float ny = rz * fx - rx * fz;
        float nz = rx * fy - 0f * fx;
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(icono));
        MatrixStack.Entry e = matrices.peek();
        float cy = ALTO + py;
        vertice(vc, e, px - rx * MEDIO + fx * MEDIO, cy + fy * MEDIO, pz - rz * MEDIO + fz * MEDIO, 0, 0, luz, nx, ny, nz);
        vertice(vc, e, px - rx * MEDIO - fx * MEDIO, cy - fy * MEDIO, pz - rz * MEDIO - fz * MEDIO, 0, 1, luz, nx, ny, nz);
        vertice(vc, e, px + rx * MEDIO - fx * MEDIO, cy - fy * MEDIO, pz + rz * MEDIO - fz * MEDIO, 1, 1, luz, nx, ny, nz);
        vertice(vc, e, px + rx * MEDIO + fx * MEDIO, cy + fy * MEDIO, pz + rz * MEDIO + fz * MEDIO, 1, 0, luz, nx, ny, nz);
    }

    /** Un ítem cualquiera apoyado en (x, y, z) del marco actual, como los ítems tirados al piso pero quieto. */
    static void dibujarItem(ItemStack stack, net.minecraft.world.World mundo, MatrixStack matrices,
                            VertexConsumerProvider buffers, int luz, int overlay, float x, float y, float z) {
        matrices.push();
        matrices.translate(x, y + 0.12f, z);
        matrices.scale(0.5f, 0.5f, 0.5f);
        // Acostados (2026-10-05, "que todos los items en la conveyor junction y belt aparezcan acostados"): los ítems
        // planos se tumban sobre la banda; los bloques ya apoyan por su base. Se decide por el modelo y no por BlockItem
        // (2026-10-21, "algunos aparecen acostados y otros parados como el hilo"): el hilo, las semillas o la redstone son
        // BlockItem pero se dibujan planos.
        if (!net.minecraft.client.MinecraftClient.getInstance().getItemRenderer().getModel(stack, mundo, null, 0).hasDepth()) {
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(90f));
        }
        net.minecraft.client.MinecraftClient.getInstance().getItemRenderer().renderItem(stack,
                net.minecraft.client.render.model.json.ModelTransformationMode.GROUND, luz, overlay, matrices, buffers, mundo, 0);
        matrices.pop();
    }

    private static void vertice(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, float u, float v, int luz,
                                float nx, float ny, float nz) {
        vc.vertex(e.getPositionMatrix(), x, y, z)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(luz)
                .normal(e, nx, ny, nz);
    }
}
