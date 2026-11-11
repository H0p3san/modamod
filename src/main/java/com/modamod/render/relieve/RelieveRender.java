package com.modamod.render.relieve;

import com.modamod.prenda.Parte;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Dibuja una {@link ModelPart} de {@code CuerpoGeometria} con relieve
 * (2026-10-01): las caras de los costados (frente, espalda y los dos
 * laterales) se parten en una grilla y cada punto sale hacia afuera lo que
 * dice el {@link MapaRelieve}; las tapas quedan como siempre. Reemplaza a
 * {@code ModelPart.render} — mismo recorrido ({@code forEachCuboid}: giro,
 * escala e hijos) y mismos datos de vértice.
 *
 * <p>Qué cara es y dónde cae cada punto sale de las UV del cuboide (layout de
 * la skin, normalizado): así sirve igual para la caja entera, para las filas
 * sueltas de {@code telaPorFilas} y para el cuerpo segmentado.
 *
 * <p>Dos estilos para comparar (2026-10-01, "podemos hacer una muestra de
 * prueba de los dos?"), con {@code /modamoddebug relieve}:
 * <ul>
 *   <li>{@link Estilo#SUAVE}: superficie continua, normales del relieve;</li>
 *   <li>{@link Estilo#ESCALONADO}: bloquecitos de medio px con alturas de a
 *       1/4 px y paredes — el look de 3D Skin Layers.</li>
 * </ul>
 */
public final class RelieveRender {

    private RelieveRender() {}

    public enum Estilo { SUAVE, ESCALONADO, APAGADO, ESCALONADO_GRUESO }

    /** El estilo del cuadro en curso: sale del volumen del perfil que se dibuja ({@link #fijarVolumen}) o del debug. */
    public static Estilo estilo = Estilo.SUAVE;
    /** Estilo forzado por {@code /modamoddebug relieve} (null = el del perfil). */
    @Nullable
    public static Estilo forzado;
    /** {@code /modamoddebug relieve cuerpo}: el cuerpo sin relieve y la tela con el suyo, para ver si el cuerpo atraviesa la tela. */
    public static boolean cuerpoPlano;

    /**
     * Fija el estilo del cuadro según el perfil (2026-10-04, "que el volumen de las skins base tambien pueda
     * alternar entre redondeado y voxelizado como los pechos"): lo llama quien va a dibujar cuerpo o tela con
     * relieve antes de empezar.
     */
    public static void fijarVolumen(com.modamod.body.PerfilCuerpo perfil) {
        estilo = forzado != null ? forzado : switch (perfil.volumen()) {
            case com.modamod.body.PerfilCuerpo.VOLUMEN_VOXEL -> Estilo.ESCALONADO;
            case com.modamod.body.PerfilCuerpo.VOLUMEN_VOXEL_GRUESO -> Estilo.ESCALONADO_GRUESO;
            default -> Estilo.SUAVE;
        };
    }

    /** Celdas por px de skin al dibujar (el voxel grueso, bloques de 1 px). */
    private static int celdasPorPx() {
        return estilo == Estilo.ESCALONADO_GRUESO ? 1 : 2;
    }

    /** Escalón de altura del estilo escalonado (px): 1/4 px el fino, 1/2 px el grueso. */
    private static float escalonDeAltura() {
        return estilo == Estilo.ESCALONADO_GRUESO ? 0.5f : 0.25f;
    }

    /**
     * Corrimiento del busto de este cuadro ({@link FisicaBusto}), en fracción
     * de la cara del frente del torso; {@code busto} para saber qué zona se mueve.
     */
    public record Rebote(float busto, float dx, float dy) {}

    public record Contexto(Parte parte, MapaRelieve mapa, @Nullable Rebote rebote) {
        /** Con el rebote del dibujo en curso ({@link #reboteActual}). */
        public Contexto(Parte parte, MapaRelieve mapa) {
            this(parte, mapa, reboteActual);
        }
    }

    /** El rebote del jugador que se está dibujando (lo pone {@code GarmentFeatureRenderer}). */
    @Nullable
    public static Rebote reboteActual;

    /** La altura de la cara con el busto corrido por el rebote. */
    static float altura(Contexto c, int cara, float fu, float fv) {
        // El busto rebota aparte (BustoRender): el relieve queda quieto.
        return c.mapa().altura(c.parte(), cara, fu, fv);
    }

    /**
     * El relieve del dibujo en curso: lo pone {@code GarmentFeatureRenderer}
     * alrededor del cuerpo y de cada pieza de tela, y lo lee
     * {@code dibujarModelPart}.
     */
    @Nullable
    public static Contexto actual;

    public static boolean aplica(@Nullable Contexto c) {
        return c != null && estilo != Estilo.APAGADO && c.mapa() != MapaRelieve.PLANO
                && MapaRelieve.indice(c.parte()) >= 0;
    }

    /**
     * @param escala unidades de modelo por px de skin (la escala de la superficie:
     *               las cajas de tela están multiplicadas por 8 y se achican después)
     */
    public static void dibujar(ModelPart parte, Contexto ctx, float escala, MatrixStack matrices,
                               VertexConsumer vc, int luz, int overlay) {
        parte.forEachCuboid(matrices, (entry, path, indice, cuboide) -> {
            boolean sinRelieve = path.endsWith(SIN_RELIEVE);
            for (ModelPart.Quad q : cuboide.sides) {
                float[] v = sinRelieve ? null : geometria(q, ctx, escala);
                if (v == null) plano(entry, q, vc, luz, overlay);
                else emitir(entry, v, vc, luz, overlay);
            }
        });
    }

    // ── geometría guardada (2026-11-11, medido: el relieve era 2/3 del costo de la ropa, hasta 2,5 ms por jugador) ──
    // Los vértices de una cara con relieve solo dependen de la cara, el mapa, la parte, el estilo y la escala: no de la
    // pose. Se calculan una vez, en el espacio del cuboide, y cada cuadro solo se transforman y se emiten.

    /** Una cara ya calculada: 8 floats por vértice (x, y, z en bloques, u, v, normal); {@code v} null = va plana. */
    private record Hecha(MapaRelieve mapa, Parte parte, Estilo estilo, float escala, float @Nullable [] v) {}

    /** Por cara (identidad): se van solas cuando CuerpoGeometria suelta la ModelPart. Hasta {@link #POR_CARA} contextos cada una. */
    private static final java.util.Map<ModelPart.Quad, Hecha[]> HECHAS = new java.util.WeakHashMap<>();
    private static final int POR_CARA = 4;
    private static float[] armando = new float[4096];
    private static int largo;

    @Nullable
    private static float[] geometria(ModelPart.Quad q, Contexto ctx, float escala) {
        Hecha[] hechas = HECHAS.computeIfAbsent(q, x -> new Hecha[POR_CARA]);
        int libre = -1;
        for (int i = 0; i < POR_CARA; i++) {
            Hecha h = hechas[i];
            if (h == null) { if (libre < 0) libre = i; continue; }
            if (h.mapa == ctx.mapa() && h.parte == ctx.parte() && h.estilo == estilo && h.escala == escala) return h.v;
        }
        largo = 0;
        boolean conRelieve = cara(q, ctx, escala);
        float[] v = conRelieve ? java.util.Arrays.copyOf(armando, largo) : null;
        // Sin lugar: se pisa una al azar (una cara compartida por más de 4 cuerpos distintos a la vez es raro).
        hechas[libre >= 0 ? libre : (int) (System.nanoTime() & (POR_CARA - 1))] = new Hecha(ctx.mapa(), ctx.parte(), estilo, escala, v);
        return v;
    }

    private static final Vector3f TMP_P = new Vector3f(), TMP_N = new Vector3f();

    private static void emitir(MatrixStack.Entry e, float[] v, VertexConsumer vc, int luz, int ov) {
        org.joml.Matrix4f m = e.getPositionMatrix();
        for (int i = 0; i < v.length; i += 8) {
            m.transformPosition(v[i], v[i + 1], v[i + 2], TMP_P);
            e.transformNormal(v[i + 5], v[i + 6], v[i + 7], TMP_N);
            vc.vertex(TMP_P.x, TMP_P.y, TMP_P.z, 0xFFFFFFFF, v[i + 3], v[i + 4], ov, luz, TMP_N.x, TMP_N.y, TMP_N.z);
        }
    }

    /** Caras guardadas y sus MB, para el HUD de rendimiento. */
    public static double[] estadoCache() {
        long floats = 0;
        int caras = 0;
        for (Hecha[] hs : HECHAS.values()) for (Hecha h : hs) if (h != null && h.v != null) { caras++; floats += h.v.length; }
        return new double[]{caras, floats * 4 / 1048576.0};
    }

    /**
     * Nombre del hijo de una ModelPart que se dibuja sin relieve (2026-11-05, "la tapa del pie sigue abierta"): la planta y la
     * punta de la media usan el UV de la pierna, así que el relieve empujaba sus costados y no su tapa, y se abrían.
     */
    public static final String SIN_RELIEVE = "sin_relieve";

    /** Arma en {@link #armando} los vértices de la cara con relieve; false si va plana. */
    private static boolean cara(ModelPart.Quad q, Contexto ctx, float escala) {
        Vector3f n = q.direction;
        if (Math.abs(n.y()) > 0.5f) return false;
        ModelPart.Vertex[] vs = q.vertices;
        float umin = Float.MAX_VALUE, umax = -Float.MAX_VALUE, vmin = Float.MAX_VALUE, vmax = -Float.MAX_VALUE;
        for (ModelPart.Vertex v : vs) {
            umin = Math.min(umin, v.u); umax = Math.max(umax, v.u);
            vmin = Math.min(vmin, v.v); vmax = Math.max(vmax, v.v);
        }
        Parte parte = ctx.parte();
        MapaRelieve mapa = ctx.mapa();
        boolean slim = mapa.slim;
        int caraIdx = MapaRelieve.caraEn(parte, (umin + umax) * 0.5f * 64f, slim);
        if (caraIdx < 0 || mapa.plana(parte, caraIdx) || umax - umin < 1e-6f) return false;
        // Esquinas: A (umin, vmin), B (umax, vmin), D (umin, vmax).
        ModelPart.Vertex a = null, b = null, d = null;
        for (ModelPart.Vertex v : vs) {
            boolean uMin = Math.abs(v.u - umin) < 1e-6f, vMin = Math.abs(v.v - vmin) < 1e-6f;
            if (uMin && vMin) a = v;
            else if (!uMin && vMin) b = v;
            else if (uMin) d = v;
        }
        if (a == null || b == null || d == null) return false;
        Vector3f pa = a.pos, ab = new Vector3f(b.pos).sub(a.pos), ad = new Vector3f(d.pos).sub(a.pos);
        float largoU = ab.length(), largoV = ad.length();
        if (largoU < 1e-5f || largoV < 1e-5f) return false;
        Vector3f tu = new Vector3f(ab).div(largoU), tv = new Vector3f(ad).div(largoV);

        int ancho = MapaRelieve.anchoPx(parte, caraIdx, slim);
        int x0 = MapaRelieve.inicioCara(parte, caraIdx, slim), arriba = MapaRelieve.arribaCostados(parte);
        float fu0 = (umin * 64f - x0) / ancho, fu1 = (umax * 64f - x0) / ancho;
        float fv0 = (vmin * 64f - arriba) / MapaRelieve.FILAS_PX, fv1 = (vmax * 64f - arriba) / MapaRelieve.FILAS_PX;
        int nu = Math.max(1, Math.round((fu1 - fu0) * ancho * celdasPorPx()));
        int nv = Math.max(1, Math.round((fv1 - fv0) * MapaRelieve.FILAS_PX * celdasPorPx()));

        // Se empuja en la dirección horizontal de la cara, sin su inclinación (2026-11-05, "caída lisa"): las filas de una malla
        // con caída tienen inclinaciones apenas distintas, y empujando cada una por su normal el borde común se abría en una
        // rendija finísima (líneas de puntos). En una caja recta la normal ya es horizontal: no cambia nada.
        Vector3f empuje = new Vector3f(n.x(), 0f, n.z()).normalize();
        Geo g = new Geo(pa, ab, ad, n, empuje, escala, umin, umax, vmin, vmax);
        if (estilo == Estilo.ESCALONADO || estilo == Estilo.ESCALONADO_GRUESO) {
            escalonado(g, ctx, caraIdx, ancho, fu0, fu1, fv0, fv1, nu, nv, tu, tv);
        } else {
            suave(g, ctx, caraIdx, ancho, fu0, fu1, fv0, fv1, nu, nv, tu, tv);
        }
        return true;
    }

    /** Lo que hace falta para emitir vértices de una cara. */
    private record Geo(Vector3f a, Vector3f ab, Vector3f ad,
                       Vector3f n, Vector3f empuje, float escala, float umin, float umax, float vmin, float vmax) {
        /** Punto (s, t) de la cara, salido {@code h} px hacia afuera. */
        Vector3f punto(float s, float t, float h) {
            return new Vector3f(a).add(ab.x * s, ab.y * s, ab.z * s).add(ad.x * t, ad.y * t, ad.z * t)
                    .add(empuje.x * h * escala, empuje.y * h * escala, empuje.z * h * escala);
        }

        void vertice(Vector3f p, float s, float t, Vector3f normal) {
            if (largo + 8 > armando.length) armando = java.util.Arrays.copyOf(armando, armando.length * 2);
            float[] b = armando;
            b[largo++] = p.x / 16f; b[largo++] = p.y / 16f; b[largo++] = p.z / 16f;
            b[largo++] = umin + (umax - umin) * s; b[largo++] = vmin + (vmax - vmin) * t;
            b[largo++] = normal.x; b[largo++] = normal.y; b[largo++] = normal.z;
        }
    }

    private static void suave(Geo g, Contexto ctx, int cara, int ancho,
                              float fu0, float fu1, float fv0, float fv1, int nu, int nv, Vector3f tu, Vector3f tv) {
        Vector3f[][] p = new Vector3f[nu + 1][nv + 1];
        Vector3f[][] nor = new Vector3f[nu + 1][nv + 1];
        float eu = 0.25f / ancho, ev = 0.25f / MapaRelieve.FILAS_PX;
        for (int i = 0; i <= nu; i++) {
            float s = i / (float) nu, fu = fu0 + (fu1 - fu0) * s;
            for (int j = 0; j <= nv; j++) {
                float t = j / (float) nv, fv = fv0 + (fv1 - fv0) * t;
                float h = altura(ctx, cara, fu, fv);
                p[i][j] = g.punto(s, t, h);
                // Normal del relieve: la de la cara inclinada por la pendiente (px/px).
                float dx = (altura(ctx, cara, fu + eu, fv) - altura(ctx, cara, fu - eu, fv)) / 0.5f;
                float dy = (altura(ctx, cara, fu, fv + ev) - altura(ctx, cara, fu, fv - ev)) / 0.5f;
                nor[i][j] = new Vector3f(g.n()).sub(tu.x * dx, tu.y * dx, tu.z * dx).sub(tv.x * dy, tv.y * dy, tv.z * dy).normalize();
            }
        }
        for (int i = 0; i < nu; i++) {
            for (int j = 0; j < nv; j++) {
                float s0 = i / (float) nu, s1 = (i + 1) / (float) nu, t0 = j / (float) nv, t1 = (j + 1) / (float) nv;
                g.vertice(p[i][j], s0, t0, nor[i][j]);
                g.vertice(p[i][j + 1], s0, t1, nor[i][j + 1]);
                g.vertice(p[i + 1][j + 1], s1, t1, nor[i + 1][j + 1]);
                g.vertice(p[i + 1][j], s1, t0, nor[i + 1][j]);
            }
        }
    }

    private static void escalonado(Geo g, Contexto ctx, int cara, int ancho,
                                   float fu0, float fu1, float fv0, float fv1, int nu, int nv, Vector3f tu, Vector3f tv) {
        float du = (fu1 - fu0) / nu, dv = (fv1 - fv0) / nv;
        Vector3f menosTu = new Vector3f(tu).negate(), menosTv = new Vector3f(tv).negate();
        float[][] alto = new float[nu][nv];
        for (int i = 0; i < nu; i++) for (int j = 0; j < nv; j++) alto[i][j] = escalon(ctx, cara, fu0 + du * (i + 0.5f), fv0 + dv * (j + 0.5f));
        // Tapas: en cada fila, los bloquecitos seguidos de la misma altura van en una sola cara (2026-11-11, medido: un
        // jugador con voxel fino emitía decenas de miles de vértices por cuadro). La UV es lineal en la cara: se ve igual.
        for (int j = 0; j < nv; j++) {
            float t0 = j / (float) nv, t1 = (j + 1) / (float) nv;
            for (int i = 0; i < nu; ) {
                float h = alto[i][j];
                int fin = i + 1;
                while (fin < nu && alto[fin][j] == h) fin++;
                float s0 = i / (float) nu, s1 = fin / (float) nu;
                g.vertice(g.punto(s0, t0, h), s0, t0, g.n());
                g.vertice(g.punto(s0, t1, h), s0, t1, g.n());
                g.vertice(g.punto(s1, t1, h), s1, t1, g.n());
                g.vertice(g.punto(s1, t0, h), s1, t0, g.n());
                i = fin;
            }
        }
        for (int i = 0; i < nu; i++) {
            float s0 = i / (float) nu, s1 = (i + 1) / (float) nu;
            for (int j = 0; j < nv; j++) {
                float t0 = j / (float) nv, t1 = (j + 1) / (float) nv;
                float fu = fu0 + du * (i + 0.5f), fv = fv0 + dv * (j + 0.5f);
                float h = alto[i][j];
                // Paredes hacia los vecinos más bajos (la pared la pone el más alto).
                pared(g, h, escalon(ctx, cara, fu + du, fv), s1, t0, s1, t1, tu);
                pared(g, h, escalon(ctx, cara, fu - du, fv), s0, t1, s0, t0, menosTu);
                pared(g, h, escalon(ctx, cara, fu, fv + dv), s1, t1, s0, t1, tv);
                pared(g, h, escalon(ctx, cara, fu, fv - dv), s0, t0, s1, t0, menosTv);
            }
        }
    }

    private static float escalon(Contexto ctx, int cara, float fu, float fv) {
        if (fu < 0f || fu > 1f) return 0f;
        return Math.round(altura(ctx, cara, fu, fv) / escalonDeAltura()) * escalonDeAltura();
    }

    private static void pared(Geo g, float h, float vecino, float sA, float tA, float sB, float tB, Vector3f normal) {
        if (h <= vecino + 1e-4f) return;
        g.vertice(g.punto(sA, tA, vecino), sA, tA, normal);
        g.vertice(g.punto(sB, tB, vecino), sB, tB, normal);
        g.vertice(g.punto(sB, tB, h), sB, tB, normal);
        g.vertice(g.punto(sA, tA, h), sA, tA, normal);
    }

    /** Una cara sin relieve, igual que {@code Cuboid.renderCuboid}. */
    private static void plano(MatrixStack.Entry e, ModelPart.Quad q, VertexConsumer vc, int luz, int ov) {
        Vector3f nn = e.transformNormal(q.direction, new Vector3f());
        for (ModelPart.Vertex v : q.vertices) {
            Vector3f w = e.getPositionMatrix().transformPosition(v.pos.x() / 16f, v.pos.y() / 16f, v.pos.z() / 16f, new Vector3f());
            vc.vertex(w.x, w.y, w.z, 0xFFFFFFFF, v.u, v.v, ov, luz, nn.x, nn.y, nn.z);
        }
    }
}
