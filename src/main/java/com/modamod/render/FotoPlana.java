package com.modamod.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Un panel plano con una foto, apoyado sobre una pieza en el marco de una parte del cuerpo (en px, Y hacia abajo, el
 * frente en −Z). Es lo que usan las piezas que no tienen una tela donde estampar (el cinto): se dibuja encima de la pieza
 * con la textura que arma {@code sublimadora.BannerHD}.
 */
public final class FotoPlana {

    private FotoPlana() {}

    /**
     * Un rectángulo vertical que va de ({@code xa, za}) a ({@code xb, zb}) en horizontal y de {@code ya} (arriba) a
     * {@code yb} (abajo), con la parte {@code u0..u1} y {@code v0..v1} de la textura; {@code nx, nz} apuntan hacia afuera.
     */
    public static void panel(MatrixStack matrices, ModelPart marco, VertexConsumer vc, int luz,
                             float xa, float za, float xb, float zb, float ya, float yb,
                             float u0, float u1, float v0, float v1, float nx, float nz) {
        matrices.push();
        marco.rotate(matrices);
        MatrixStack.Entry e = matrices.peek();
        punto(vc, e, luz, xa, ya, za, u0, v0, nx, nz);
        punto(vc, e, luz, xb, ya, zb, u1, v0, nx, nz);
        punto(vc, e, luz, xb, yb, zb, u1, v1, nx, nz);
        punto(vc, e, luz, xa, yb, za, u0, v1, nx, nz);
        matrices.pop();
    }

    private static void punto(VertexConsumer vc, MatrixStack.Entry e, int luz, float x, float y, float z,
                              float u, float v, float nx, float nz) {
        vc.vertex(e.getPositionMatrix(), x / 16f, y / 16f, z / 16f)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(luz)
                .normal(e, nx, 0f, nz);
    }
}
