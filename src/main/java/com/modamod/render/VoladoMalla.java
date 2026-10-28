package com.modamod.render;

import com.modamod.item.PolleraVolado;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Volado de un puño, una botamanga o el borde de un calentador (2026-10-11, "unos calentadores de brazos recortados que
 * acepten volado en sus dos cortes"): un anillo de malla alrededor de la sección de la parte (4×4 px), pegado al borde
 * de la tela, que se abre hacia afuera y ondula como los volados de la pollera ({@code PolleraMalla#volado}: recto =
 * poca apertura y muchas ondas chicas, circular = abierto y con pocas ondas grandes). Toma la textura de la fila del
 * borde, así sigue el color y el dibujo de la prenda.
 */
final class VoladoMalla {

    private VoladoMalla() {}

    private static final int FILAS = 4;

    /**
     * @param hacia      +1 el volado cuelga hacia abajo (borde de abajo), -1 sube (borde de arriba)
     * @param x0,z0      esquina de la caja de la parte (px, en su marco); {@code w}, {@code d} su ancho y su fondo
     * @param yBorde     y del borde (px)
     * @param dil        cuánto sale la tela de la caja en el borde
     * @param uOrigen    origen de la caja en la skin (u), {@code v} fila de la textura (ya en 0..1)
     */
    static void dibujar(MatrixStack.Entry e, VertexConsumer vc, int luz, PolleraVolado volado, float hacia,
                        float x0, float z0, float w, float d, float yBorde, float dil, float uOrigen, float v) {
        dibujar(e, vc, luz, volado, null, hacia, x0, z0, w, d, yBorde, dil, uOrigen, v);
    }

    /**
     * Con {@code borde} (2026-10-11, "el recorte de formas del puño"): sin volado, una faldilla chata y apenas abierta
     * cuyo borde de abajo sigue el dibujo decorativo de la pollera (ondulado, festoneado, pico).
     */
    static void dibujar(MatrixStack.Entry e, VertexConsumer vc, int luz, @org.jetbrains.annotations.Nullable PolleraVolado volado,
                        @org.jetbrains.annotations.Nullable com.modamod.item.PolleraBorde borde, float hacia,
                        float x0, float z0, float w, float d, float yBorde, float dil, float uOrigen, float v) {
        boolean circular = volado == PolleraVolado.CIRCULAR;
        float largo = volado == null ? 2.6f : circular ? 3.2f : 2.2f;
        float abrir = volado == null ? 0.45f : circular ? 2.2f : 0.9f;
        float ondas = circular ? 6f : 16f;
        float amplitud = volado == null ? 0f : circular ? 0.8f : 0.3f;
        int ancho = Math.round(w), hondo = Math.round(d);
        float perimetro = 2f * (ancho + hondo);
        float cx = x0 + w / 2f, cz = z0 + d / 2f;
        float sx = w / 2f + dil, sz = d / 2f + dil;
        int pasos = Math.round(perimetro) * 3;
        float[][][] p = new float[FILAS + 1][pasos + 1][];
        for (int f = 0; f <= FILAS; f++) {
            float t = f / (float) FILAS;
            float off = 0.05f + abrir * (float) Math.pow(t, 1.25) + (f == 0 ? 0f : 0f);
            float y = yBorde + hacia * largo * t;
            final float tt = t;
            for (int k = 0; k <= pasos; k++) {
                float s = k / (float) pasos * perimetro;
                float[] q = enPerimetro(s, ancho, hondo);      // -1..1 en x y z de la sección
                float onda = amplitud * t * (float) Math.sin(2.0 * Math.PI * ondas * s / perimetro);
                float rx = sx + off + onda, rz = sz + off + onda;
                float yy = y;
                // El borde decorativo baja (o sube) más hacia la última fila, según el dibujo a lo largo de la vuelta.
                if (borde != null) yy += hacia * borde.baja(s / perimetro) * tt * tt;
                p[f][k] = new float[]{cx + q[0] * rx, yy, cz + q[1] * rz, s};
            }
        }
        for (int f = 0; f < FILAS; f++) {
            for (int k = 0; k < pasos; k++) {
                // La u de este tramo: la tira de caras arranca en la izquierda, el anillo en el frente (hondo más allá).
                float uA = uOrigen + (hondo + p[f][k][3]) % perimetro, uB = uA + perimetro / pasos;
                emitir(e, vc, luz, p[f][k], p[f][k + 1], p[f + 1][k + 1], p[f + 1][k], uA, uB, v, cx, cz);
            }
        }
    }

    /**
     * Un punto del perímetro de la sección, ordenado como la tira de caras de la skin (frente, derecha, atrás,
     * izquierda): devuelve (x, z) con el borde del rectángulo en ±1.
     */
    private static float[] enPerimetro(float s, int ancho, int hondo) {
        if (s < ancho) return new float[]{-1f + 2f * s / ancho, -1f};                         // frente
        s -= ancho;
        if (s < hondo) return new float[]{1f, -1f + 2f * s / hondo};                          // costado derecho
        s -= hondo;
        if (s < ancho) return new float[]{1f - 2f * s / ancho, 1f};                           // atrás
        s -= ancho;
        return new float[]{-1f, 1f - 2f * Math.min(s, hondo) / hondo};                        // costado izquierdo
    }

    private static void emitir(MatrixStack.Entry e, VertexConsumer vc, int luz, float[] a, float[] b, float[] c, float[] d,
                               float uA, float uB, float v, float cx, float cz) {
        float mx = (a[0] + c[0]) / 2f - cx, mz = (a[2] + c[2]) / 2f - cz;
        float len = (float) Math.sqrt(mx * mx + mz * mz);
        float nx = len > 1e-4f ? mx / len : 0f, nz = len > 1e-4f ? mz / len : -1f;
        float[][] vs = {a, b, c, d};
        float[] us = {uA, uB, uB, uA};
        for (int i = 0; i < 4; i++) {
            float[] q = vs[i];
            vc.vertex(e.getPositionMatrix(), q[0] / 16f, q[1] / 16f, q[2] / 16f)
                    .color(0xFFFFFFFF)
                    .texture(us[i] / 64f, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(luz)
                    .normal(e, nx, 0.3f, nz);
        }
    }
}
