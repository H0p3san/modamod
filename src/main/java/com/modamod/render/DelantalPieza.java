package com.modamod.render;

import com.modamod.item.DelantalTirantes;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;

/**
 * El delantal como UNA sola pieza de tela (2026-10-12, "si lo haces en una pieza para ver cómo queda bien y después
 * de ahí lo desensamblamos"): la cinta de la cintura y los tirantes dejan de ser cajas aparte y se arman como cintas
 * de malla que salen del borde de arriba del propio delantal (el primer corte de cada tirante es el borde de la malla),
 * suben por el frente, pasan por el hombro con las esquinas redondeadas (Chaikin) y bajan por la espalda hasta la cinta.
 * Todo con la misma textura del delantal (un texel de su tela, así toma su color). Después se ata cada parte a su molde.
 * {@code /modamoddebug delantal partes|pieza} alterna con las cajas de antes.
 */
public final class DelantalPieza {
    private DelantalPieza() {}

    /** Si se arma como pieza única (true) o con las cajas sueltas de {@link TirantesDelantal} (false). */
    public static boolean activa = true;

    private static final float ANCHO = 1.2F, GROSOR = 0.5F, X = 2.6F, BAJADA_ESPALDA = 7F;
    private static final float Y_CINTA = BAJADA_ESPALDA - 0.6F, ALTO_CINTA = 1.2F;

    static void dibujar(float[][][] p, PolleraMalla.AdornoDelantal ad, MatrixStack.Entry e, VertexConsumer vc, int luz, int ov) {
        float dil = ad.dil();
        float u = 24F / 64F, v = (20F + Math.min(6F, ad.largo() * 0.5F)) / 64F;
        cinta(ad, u, v, e, vc, luz, ov);
        if (ad.tirantes() == null || ad.tirantes() == DelantalTirantes.SIN) return;
        float zF = -(2F + dil) - 0.3F, zB = 2F + dil + 0.3F, top = -dil - GROSOR;
        for (int lado = -1; lado <= 1; lado += 2) {
            float arc = lado * Math.max(0.8F, Math.min(X, ad.forma().medio(0f, ad.largo(), ad.cap()) - 0.6F));
            float[] s0 = PolleraMalla.punto(p, 0F, arc);
            float x0 = s0[0];
            List<float[]> ruta = new ArrayList<>();
            ruta.add(s0);
            // frente: sube del borde de arriba del delantal hasta el hombro, apoyándose de a poco en el plano del pecho
            int n = 4;
            for (int i = 1; i <= n; i++) {
                float t = i / (float) n, k = t * t * (3F - 2F * t);
                ruta.add(new float[]{x0, s0[1] + (top - s0[1]) * t, s0[2] + (zF - s0[2]) * k});
            }
            // hombro y espalda
            ruta.add(new float[]{x0, top, zB});
            float xFin = ad.tirantes() == DelantalTirantes.CRUZADOS ? -x0 : x0;
            float yFin = Y_CINTA + ALTO_CINTA / 2F;
            for (int i = 1; i <= n; i++) {
                float t = i / (float) n;
                ruta.add(new float[]{x0 + (xFin - x0) * t, top + (yFin - top) * t, zB});
            }
            ruta = chaikin(chaikin(ruta));
            float[][][] g = new float[ruta.size()][2][];
            for (int j = 0; j < ruta.size(); j++) {
                float[] q = ruta.get(j);
                g[j][0] = new float[]{q[0] - ANCHO / 2F, q[1], q[2], u, v};
                g[j][1] = new float[]{q[0] + ANCHO / 2F, q[1], q[2], u, v};
            }
            PolleraMalla.emitirTira(g, e, vc, luz, ov);
        }
    }

    /** La cinta de la cintura: un anillo de malla (superelipse) a la altura donde terminan los tirantes por la espalda. */
    private static void cinta(PolleraMalla.AdornoDelantal ad, float u, float v, MatrixStack.Entry e, VertexConsumer vc, int luz, int ov) {
        float a = 4F + ad.dil() + GROSOR, b = 2F + ad.dil() + GROSOR, sale = ad.bustoExtra() * 0.3F;
        int seg = 40;
        float[][][] g = new float[seg + 1][2][];
        for (int i = 0; i <= seg; i++) {
            double th = 2 * Math.PI * i / seg;
            float c = (float) Math.cos(th), s = (float) Math.sin(th);
            float x = a * Math.signum(c) * (float) Math.pow(Math.abs(c), 0.4);
            float z = b * Math.signum(s) * (float) Math.pow(Math.abs(s), 0.4);
            if (z < 0F) z -= sale * Math.max(0F, 1F - Math.abs(x) / a);
            g[i][0] = new float[]{x, Y_CINTA, z, u, v};
            g[i][1] = new float[]{x, Y_CINTA + ALTO_CINTA, z, u, v};
        }
        PolleraMalla.emitirTira(g, e, vc, luz, ov);
    }

    private static List<float[]> chaikin(List<float[]> pts) {
        List<float[]> r = new ArrayList<>();
        r.add(pts.get(0));
        for (int i = 0; i < pts.size() - 1; i++) {
            float[] a = pts.get(i), b = pts.get(i + 1);
            r.add(new float[]{0.75F * a[0] + 0.25F * b[0], 0.75F * a[1] + 0.25F * b[1], 0.75F * a[2] + 0.25F * b[2]});
            r.add(new float[]{0.25F * a[0] + 0.75F * b[0], 0.25F * a[1] + 0.75F * b[1], 0.25F * a[2] + 0.75F * b[2]});
        }
        r.add(pts.get(pts.size() - 1));
        return r;
    }
}
