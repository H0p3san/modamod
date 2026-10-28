package com.modamod.render;

import com.modamod.render.relieve.BustoRender;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Solapa de tela continua (2026-10-08, "las solapas se ven muy feas... se pueden hacer mas de tela como las polleras y
 * pegarlas al borde del cuello?" → "vamos con eso"): en vez de pilas de cajitas de 1 px, una tira de quads por lado
 * que baja pegada al borde del escote (y de la apertura, ver {@code CuelloYCapucha#bordeDelEscote}), con el borde de
 * afuera levantado como un doblez y sus cantos cerrados. Toma la textura de la propia prenda (UV planas del frente
 * del torso), así lleva sus patrones y estampas.
 *
 * <p>Es una malla en el marco del torso (px, ModelPart: x −4..4, y 0..12, frente en z −2). Con busto cada vértice sube
 * sobre la cúpula ({@code CuelloYCapucha#elevacion}).
 */
final class SolapaMalla {
    private SolapaMalla() {}

    /** Cuánto se levanta de la tela el borde de adentro y el de afuera (px): el doblez. */
    private static final float SALE_ADENTRO = 0.25F, SALE_AFUERA = 0.75F;
    private static final float LIMITE_X = 4.05F;
    private static final int COLOR_FRENTE = 0xFFEBEBEB, COLOR_CANTO = 0xFFB0B0B0;

    /**
     * @param borde    distancia (px) del centro al borde de la tela por sub-fila de {@code sub} por px (de arriba hacia abajo)
     * @param anchos   ancho (px) de la solapa a la altura del centro de cada fila de 1 px
     * @param d        dilatación de la tela de la prenda
     */
    static void dibujar(Identifier tela, float[] borde, int sub, float[] anchos, float d, float carpa,
                        BipedEntityModel<?> biped, MatrixStack matrices, VertexConsumerProvider vcp, int luz) {
        dibujar(tela, borde, sub, anchos, d, carpa, SALE_ADENTRO, SALE_AFUERA, biped, matrices, vcp, luz);
    }

    /** Con el doblez a gusto: {@code saleAdentro}/{@code saleAfuera} = cuánto se levanta cada borde de la tela (px). */
    static void dibujar(Identifier tela, float[] borde, int sub, float[] anchos, float d, float carpa,
                        float saleAdentro, float saleAfuera,
                        BipedEntityModel<?> biped, MatrixStack matrices, VertexConsumerProvider vcp, int luz) {
        if (borde == null || borde.length == 0 || anchos.length == 0) return;
        int filas = Math.min(anchos.length, borde.length / sub);
        int n = filas * sub;                      // segmentos por lado
        if (n < 1) return;
        BustoRender.Busto busto = BustoRender.actual;
        VertexConsumer vc = vcp.getBuffer(ClothingTextureCache.capaDeRender(tela));
        matrices.push();
        biped.body.rotate(matrices);
        Matrix4f m = matrices.peek().getPositionMatrix();
        Matrix3f nm = matrices.peek().getNormalMatrix();
        float zTela = -2 - d;
        for (int lado = -1; lado <= 1; lado += 2) {
            // Puntos del borde de adentro (a) y de afuera (b), de arriba hacia abajo.
            Vector3f[] a = new Vector3f[n + 1], b = new Vector3f[n + 1];
            boolean[] vale = new boolean[n + 1];
            for (int j = 0; j <= n; j++) {
                float y = j / (float) sub;
                float xin = Math.max(0F, borde[Math.min(j, n - 1)]);
                float w = anchoEn(anchos, y);
                float xout = Math.min(LIMITE_X, xin + w);
                vale[j] = xout - xin > 0.05F;
                float zin = zTela - saleAdentro, zout = zTela - saleAfuera;
                if (busto != null) {
                    zin += CuelloYCapucha.elevacion(busto, lado * xin, y, d, carpa);
                    zout += CuelloYCapucha.elevacion(busto, lado * xout, y, d, carpa);
                }
                a[j] = new Vector3f(lado * xin, y, zin);
                b[j] = new Vector3f(lado * xout, y, zout);
            }
            for (int j = 0; j < n; j++) {
                if (!vale[j] || !vale[j + 1]) continue;
                // Cara de adelante, con la textura de la tela debajo.
                quad(m, nm, vc, luz, COLOR_FRENTE, new Vector3f(0, 0, -1), a[j], b[j], b[j + 1], a[j + 1]);
                // Canto de afuera (del borde levantado a la tela) y canto de adentro.
                Vector3f tb0 = new Vector3f(b[j].x, b[j].y, zTela), tb1 = new Vector3f(b[j + 1].x, b[j + 1].y, zTela);
                Vector3f ta0 = new Vector3f(a[j].x, a[j].y, zTela), ta1 = new Vector3f(a[j + 1].x, a[j + 1].y, zTela);
                quad(m, nm, vc, luz, COLOR_CANTO, new Vector3f(lado, 0, 0), b[j], tb0, tb1, b[j + 1]);
                quad(m, nm, vc, luz, COLOR_CANTO, new Vector3f(-lado, 0, 0), a[j], ta0, ta1, a[j + 1]);
            }
            // Cierre de abajo: el último tramo vuelve a la tela.
            if (vale[n]) {
                Vector3f ta = new Vector3f(a[n].x, a[n].y, zTela), tb = new Vector3f(b[n].x, b[n].y, zTela);
                quad(m, nm, vc, luz, COLOR_CANTO, new Vector3f(0, 1, 0), a[n], b[n], tb, ta);
            }
        }
        matrices.pop();
    }

    /**
     * Anillo redondeado de malla (2026-10-11, "el choker me lo estas haciendo cuadrado... como el de leashable collars"):
     * una banda cerrada de sección superelíptica (esquinas redondas), de {@code grosor} px, entre {@code y0} y {@code y1}, con
     * pared de afuera, pared de adentro y tapas. {@code ox}/{@code oz} = semiejes de la pared de AFUERA (px). Va en el marco
     * de {@code marco}. Las UV recorren la textura de {@code u0..u1} a lo largo del perímetro y de {@code v0..v1} de arriba a abajo.
     */
    static void anillo(Identifier tela, float u0, float u1, float v0, float v1, float ox, float oz, float grosor,
                       float y0, float y1, ModelPart marco, MatrixStack matrices, VertexConsumerProvider vcp, int luz) {
        VertexConsumer vc = vcp.getBuffer(ClothingTextureCache.capaDeRender(tela));
        matrices.push();
        marco.rotate(matrices);
        Matrix4f m = matrices.peek().getPositionMatrix();
        Matrix3f nm = matrices.peek().getNormalMatrix();
        final int seg = 40;
        final double n = 4.0;
        final float ix = Math.max(0.2F, ox - grosor), iz = Math.max(0.2F, oz - grosor);
        float[][] fuera = new float[seg + 1][2], dentro = new float[seg + 1][2], norma = new float[seg + 1][2];
        for (int i = 0; i <= seg; i++) {
            double t = Math.PI * 2 * i / seg;
            double c = Math.cos(t), s = Math.sin(t);
            double px = Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n), pz = Math.signum(s) * Math.pow(Math.abs(s), 2.0 / n);
            fuera[i][0] = (float) px * ox;
            fuera[i][1] = (float) pz * oz;
            dentro[i][0] = (float) px * ix;
            dentro[i][1] = (float) pz * iz;
            // Gradiente de |x/a|^n + |z/b|^n: la normal de la pared de afuera.
            double gx = Math.signum(px) * Math.pow(Math.abs(px), n - 1) / ox, gz = Math.signum(pz) * Math.pow(Math.abs(pz), n - 1) / oz;
            double l = Math.max(1e-6, Math.hypot(gx, gz));
            norma[i][0] = (float) (gx / l);
            norma[i][1] = (float) (gz / l);
        }
        for (int i = 0; i < seg; i++) {
            float ua = u0 + (u1 - u0) * i / seg, ub = u0 + (u1 - u0) * (i + 1) / seg;
            float um = (ua + ub) / 2F, vm = (v0 + v1) / 2F;
            // Pared de afuera, pared de adentro, tapa de abajo y tapa de arriba.
            quadUv(m, nm, vc, luz, new Vector3f(norma[i][0], 0, norma[i][1]),
                    new float[]{fuera[i][0], y0, fuera[i][1], ua, v0}, new float[]{fuera[i + 1][0], y0, fuera[i + 1][1], ub, v0},
                    new float[]{fuera[i + 1][0], y1, fuera[i + 1][1], ub, v1}, new float[]{fuera[i][0], y1, fuera[i][1], ua, v1});
            quadUv(m, nm, vc, luz, new Vector3f(-norma[i][0], 0, -norma[i][1]),
                    new float[]{dentro[i][0], y0, dentro[i][1], ua, v0}, new float[]{dentro[i + 1][0], y0, dentro[i + 1][1], ub, v0},
                    new float[]{dentro[i + 1][0], y1, dentro[i + 1][1], ub, v1}, new float[]{dentro[i][0], y1, dentro[i][1], ua, v1});
            quadUv(m, nm, vc, luz, new Vector3f(0, 1, 0),
                    new float[]{fuera[i][0], y1, fuera[i][1], um, vm}, new float[]{fuera[i + 1][0], y1, fuera[i + 1][1], um, vm},
                    new float[]{dentro[i + 1][0], y1, dentro[i + 1][1], um, vm}, new float[]{dentro[i][0], y1, dentro[i][1], um, vm});
            quadUv(m, nm, vc, luz, new Vector3f(0, -1, 0),
                    new float[]{dentro[i][0], y0, dentro[i][1], um, vm}, new float[]{dentro[i + 1][0], y0, dentro[i + 1][1], um, vm},
                    new float[]{fuera[i + 1][0], y0, fuera[i + 1][1], um, vm}, new float[]{fuera[i][0], y0, fuera[i][1], um, vm});
        }
        matrices.pop();
    }

    /** Un cuadrilátero con UV propias por vértice ({@code {x, y, z, u, v}} en px). */
    private static void quadUv(Matrix4f m, Matrix3f nm, VertexConsumer vc, int luz, Vector3f normal, float[]... p) {
        Vector3f nn = nm.transform(new Vector3f(normal)).normalize();
        for (float[] q : p) {
            Vector3f w = m.transformPosition(q[0] / 16F, q[1] / 16F, q[2] / 16F, new Vector3f());
            vc.vertex(w.x, w.y, w.z, 0xFFFFFFFF, q[3], q[4], OverlayTexture.DEFAULT_UV, luz, nn.x, nn.y, nn.z);
        }
    }

    /** Ancho de la solapa a la altura {@code y} (px), interpolado entre los centros de las filas. */
    static float anchoEn(float[] anchos, float y) {
        float t = y - 0.5F;
        int i = (int) Math.floor(t);
        float f = t - i;
        float a = anchos[Math.max(0, Math.min(anchos.length - 1, i))];
        float b = anchos[Math.max(0, Math.min(anchos.length - 1, i + 1))];
        return a + (b - a) * f;
    }

    private static void quad(Matrix4f m, Matrix3f nm, VertexConsumer vc, int luz, int color, Vector3f normal,
                             Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3) {
        vertice(m, nm, vc, luz, color, normal, p0);
        vertice(m, nm, vc, luz, color, normal, p1);
        vertice(m, nm, vc, luz, color, normal, p2);
        vertice(m, nm, vc, luz, color, normal, p3);
    }

    /** UV planas del frente del torso (textura de 64: u 20..28 de lado a lado, v 20 + y), como el resto de la prenda. */
    private static void vertice(Matrix4f m, Matrix3f nm, VertexConsumer vc, int luz, int color, Vector3f normal,
                                Vector3f p) {
        Vector3f w = m.transformPosition(p.x / 16F, p.y / 16F, p.z / 16F, new Vector3f());
        Vector3f nn = nm.transform(new Vector3f(normal)).normalize();
        float u = (24F + Math.max(-4F, Math.min(4F, p.x))) / 64F;
        float v = (20F + Math.max(0F, p.y)) / 64F;
        vc.vertex(w.x, w.y, w.z, color, u, v, OverlayTexture.DEFAULT_UV, luz, nn.x, nn.y, nn.z);
    }
}
