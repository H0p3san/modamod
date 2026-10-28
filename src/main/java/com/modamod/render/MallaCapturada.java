package com.modamod.render;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Los cuadriláteros de una malla de tela tal como se dibujaron (2026-10-02,
 * "Pollera y Capa no registran click on garment. No se pueden generar
 * apliques en pollera ni capa"): la pollera ({@link PolleraMalla}) y la capa
 * ({@link CapaMalla}) no son cajas por parte del cuerpo, así que sus apliques
 * se anclan por coordenada de TEXTURA (u, v de la tela, que no cambian
 * aunque la tela se mueva) y se ubican sobre lo que se dibujó en este cuadro.
 *
 * <p>Mientras {@link #grabando} no es null, las mallas guardan cada vértice
 * ya transformado (el mismo espacio en que la GPU lo recibe: la cámara en el
 * mundo, los píxeles de pantalla en una GUI), con su UV y su normal. Con eso:
 * <ul>
 *   <li>{@link #enUv}: dónde queda un punto (u, v) de la tela, su normal y
 *       hacia dónde queda "arriba" (v decreciente) — para dibujar el aplique;</li>
 *   <li>{@link #tocar}: qué punto (u, v) de la tela está bajo el mouse en
 *       una GUI — para el click de la Mesa de estilado.</li>
 * </ul>
 */
public final class MallaCapturada {

    /** La malla que se está dibujando ahora (null = no se graba nada). */
    @Nullable
    public static MallaCapturada grabando;

    /** x, y, z, u, v, nx, ny, nz por vértice; de a 4 por cuadrilátero. */
    private final List<float[]> vertices = new ArrayList<>();
    /** Cuánto mide un bloque en este espacio (la escala de la matriz). */
    private float escala = 1f;
    /** Solo los vértices con u en [uDesde, uHasta) (la capa graba solo el paño y el forro). */
    private float uDesde = 0f, uHasta = 1f;

    public MallaCapturada() {}

    public MallaCapturada(float uDesde, float uHasta) {
        this.uDesde = uDesde;
        this.uHasta = uHasta;
    }

    public float escala() {
        return escala;
    }

    public boolean vacia() {
        return vertices.isEmpty();
    }

    /** Graba un vértice (en bloques del espacio local, como se le pasa a {@code VertexConsumer}). */
    public void agregar(Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                        float nx, float ny, float nz) {
        Vector3f p = m.transformPosition(x, y, z, new Vector3f());
        Vector3f nn = n.transform(new Vector3f(nx, ny, nz));
        if (nn.lengthSquared() > 1e-10f) nn.normalize();
        escala = m.getScale(new Vector3f()).x;
        vertices.add(new float[]{p.x, p.y, p.z, u, v, nn.x, nn.y, nn.z});
    }

    /** Ubicación de un punto de la tela: posición, normal y "arriba" (unitarios), o null. */
    public record Ubicacion(Vector3f pos, Vector3f normal, Vector3f arriba) {}

    /** Dónde quedó el punto (u, v) (UV normalizadas) de la tela en este dibujo, o null. */
    @Nullable
    public Ubicacion enUv(float u, float v) {
        for (int q = 0; q + 3 < vertices.size(); q += 4) {
            for (int[] tri : TRIANGULOS) {
                float[] a = vertices.get(q + tri[0]), b = vertices.get(q + tri[1]), c = vertices.get(q + tri[2]);
                if (!enRango(a) || !enRango(b) || !enRango(c)) continue;
                float[] w = baricentricas(u, v, a[3], a[4], b[3], b[4], c[3], c[4]);
                if (w == null) continue;
                Vector3f pos = mezcla(a, b, c, w, 0);
                Vector3f nor = mezcla(a, b, c, w, 5);
                if (nor.lengthSquared() < 1e-10f) continue;
                nor.normalize();
                // "Arriba" = hacia donde baja v en la tela: el gradiente de la posición respecto de v.
                Vector3f arriba = gradienteV(a, b, c).negate();
                arriba.sub(new Vector3f(nor).mul(arriba.dot(nor)));
                if (arriba.lengthSquared() < 1e-10f) continue;
                return new Ubicacion(pos, nor, arriba.normalize());
            }
        }
        return null;
    }

    /**
     * El punto de la tela bajo el mouse (x, y de pantalla), el más cercano a
     * quien mira (z más alto): {u, v, z}, o null.
     */
    @Nullable
    public float[] tocar(float mx, float my) {
        float[] mejor = null;
        for (int q = 0; q + 3 < vertices.size(); q += 4) {
            for (int[] tri : TRIANGULOS) {
                float[] a = vertices.get(q + tri[0]), b = vertices.get(q + tri[1]), c = vertices.get(q + tri[2]);
                if (!enRango(a) || !enRango(b) || !enRango(c)) continue;
                float[] w = baricentricas(mx, my, a[0], a[1], b[0], b[1], c[0], c[1]);
                if (w == null) continue;
                float z = w[0] * a[2] + w[1] * b[2] + w[2] * c[2];
                if (mejor != null && z <= mejor[2]) continue;
                mejor = new float[]{w[0] * a[3] + w[1] * b[3] + w[2] * c[3], w[0] * a[4] + w[1] * b[4] + w[2] * c[4], z};
            }
        }
        return mejor;
    }

    private boolean enRango(float[] v) {
        return v[3] >= uDesde && v[3] < uHasta;
    }

    private static final int[][] TRIANGULOS = {{0, 1, 2}, {0, 2, 3}};

    /** Baricéntricas de (px, py) en el triángulo 2D, o null si cae afuera (o es degenerado). */
    @Nullable
    private static float[] baricentricas(float px, float py, float ax, float ay, float bx, float by, float cx, float cy) {
        float e1x = bx - ax, e1y = by - ay, e2x = cx - ax, e2y = cy - ay;
        float det = e1x * e2y - e2x * e1y;
        if (Math.abs(det) < 1e-12f) return null;
        float dx = px - ax, dy = py - ay;
        float w1 = (dx * e2y - e2x * dy) / det, w2 = (e1x * dy - dx * e1y) / det;
        float eps = 1e-4f;
        if (w1 < -eps || w2 < -eps || w1 + w2 > 1f + eps) return null;
        return new float[]{1f - w1 - w2, w1, w2};
    }

    private static Vector3f mezcla(float[] a, float[] b, float[] c, float[] w, int i) {
        return new Vector3f(w[0] * a[i] + w[1] * b[i] + w[2] * c[i],
                w[0] * a[i + 1] + w[1] * b[i + 1] + w[2] * c[i + 1],
                w[0] * a[i + 2] + w[1] * b[i + 2] + w[2] * c[i + 2]);
    }

    /** dP/dv del triángulo (P lineal en u, v). */
    private static Vector3f gradienteV(float[] a, float[] b, float[] c) {
        float du1 = b[3] - a[3], dv1 = b[4] - a[4], du2 = c[3] - a[3], dv2 = c[4] - a[4];
        float det = du1 * dv2 - du2 * dv1;
        if (Math.abs(det) < 1e-12f) return new Vector3f();
        Vector3f e1 = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
        Vector3f e2 = new Vector3f(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
        // P = a + s·e1 + t·e2 con (u, v) = a + s·(du1, dv1) + t·(du2, dv2) → ∂(s,t)/∂v.
        float dsdv = -du2 / det, dtdv = du1 / det;
        return e1.mul(dsdv).add(e2.mul(dtdv));
    }
}
