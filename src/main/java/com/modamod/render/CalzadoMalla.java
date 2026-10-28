package com.modamod.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El calzado como malla (2026-10-14, zip "Calzado en malla" de Claude Design, formato {@code modamod-calzado-malla-1} en
 * {@code assets/modamod/calzado/piezas/}). Cada pieza es una superficie por <b>secciones</b> (un loft de curvas tipo
 * superelipse de talón a punta, con la boca abierta en {@code abre} y pared de {@code grosor}) o una <b>cinta</b> (una
 * tira que sigue una poligonal). Coordenadas en px del marco de la pierna derecha: y hacia abajo, piso en y = 12, el frente
 * es −Z, afuera es −X. El pie izquierdo espeja la X. Las piezas que no son suela se mueven en y con la plantilla de la suela
 * elegida ({@code y += plantilla(z) − 11}), así la misma capellada sirve con cualquier suela.
 */
final class CalzadoMalla {
    private CalzadoMalla() {}

    static final class Pieza {
        String id, eje;
        int zona;
        /** [z, y] de la cara de arriba de la suela (solo las suelas), z decreciente. */
        float[][] plantilla;
        SombreroMalla.Quads quads;
        List<SombreroRenderer.Caja> apoyo = new ArrayList<>();
        boolean esSuela() { return "suela".equals(eje); }
        /** El pie del jugador (2026-10-14, sandalias y ojotas): se dibuja con la piel, no con un color de zona. */
        boolean esPiel() { return zona == 3; }
    }

    private static final Map<String, Pieza> CACHE = new HashMap<>();
    private static final Pieza NINGUNA = new Pieza();

    static Pieza de(String id) {
        Pieza p = CACHE.get(id);
        if (p == null) {
            p = cargar(id);
            CACHE.put(id, p == null ? NINGUNA : p);
        }
        return p == NINGUNA ? null : p;
    }

    private static Pieza cargar(String id) {
        try (InputStream in = CalzadoMalla.class.getResourceAsStream("/assets/modamod/calzado/piezas/" + id + ".json")) {
            if (in == null) return null;
            JsonObject j = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Pieza p = new Pieza();
            p.id = id;
            p.eje = j.get("eje").getAsString();
            p.zona = j.get("zona").getAsInt();
            if (j.has("plantilla")) {
                JsonArray pl = j.getAsJsonArray("plantilla");
                p.plantilla = new float[pl.size()][2];
                for (int i = 0; i < pl.size(); i++) {
                    JsonArray e = pl.get(i).getAsJsonArray();
                    p.plantilla[i][0] = e.get(0).getAsFloat();
                    p.plantilla[i][1] = e.get(1).getAsFloat();
                }
            }
            p.quads = "cinta".equals(j.get("forma").getAsString()) ? cinta(j) : secciones(j, p.esSuela());
            for (JsonElement a : j.getAsJsonArray("apoyo")) {
                JsonObject o = a.getAsJsonObject();
                JsonArray mn = o.getAsJsonArray("min"), mx = o.getAsJsonArray("max");
                p.apoyo.add(new SombreroRenderer.Caja(o.get("zona").getAsInt(),
                        new float[] {mn.get(0).getAsFloat(), mn.get(1).getAsFloat(), mn.get(2).getAsFloat()},
                        new float[] {mx.get(0).getAsFloat(), mx.get(1).getAsFloat(), mx.get(2).getAsFloat()}));
            }
            return p;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("modamod").warn("Calzado: no se pudo leer la pieza {}", id, e);
            return null;
        }
    }

    /** La altura de la cara de arriba de la suela en {@code z} (interpolación lineal; fuera de rango, la del extremo). */
    static float alturaPlantilla(float[][] pl, float z) {
        if (z >= pl[0][0]) return pl[0][1];
        for (int i = 0; i + 1 < pl.length; i++) {
            if (z <= pl[i][0] && z >= pl[i + 1][0]) {
                float t = (z - pl[i][0]) / (pl[i + 1][0] - pl[i][0]);
                return pl[i][1] + (pl[i + 1][1] - pl[i][1]) * t;
            }
        }
        return pl[pl.length - 1][1];
    }

    // ── geometría ─────────────────────────────────────────────────────────

    private static final class Salida {
        final List<float[]> pos = new ArrayList<>(), nor = new ArrayList<>(), uv = new ArrayList<>();

        void v(Vector3f p, Vector3f n, float u, float v) {
            pos.add(new float[] {p.x, p.y, p.z});
            nor.add(new float[] {n.x, n.y, n.z});
            uv.add(new float[] {u, v});
        }

        SombreroMalla.Quads quads() {
            float[] p = new float[pos.size() * 3], n = new float[pos.size() * 3], t = new float[pos.size() * 2];
            for (int i = 0; i < pos.size(); i++) {
                System.arraycopy(pos.get(i), 0, p, i * 3, 3);
                System.arraycopy(nor.get(i), 0, n, i * 3, 3);
                System.arraycopy(uv.get(i), 0, t, i * 2, 2);
            }
            SombreroMalla.proyectarUv(p, n, t);
            return new SombreroMalla.Quads(p, n, t);
        }
    }

    private static float potencia(float v, float n) {
        return Math.signum(v) * (float) Math.pow(Math.abs(v), 2.0 / n);
    }

    /** Los ángulos (grados, 0 = arriba, 90 = adentro) de cada columna de una sección; NaN = la columna no existe. */
    private static float[] angulos(int cols, JsonArray abre) {
        float[] a = new float[cols];
        for (int j = 0; j < cols; j++) {
            float t = 360f * j / cols;
            a[j] = t > 180f ? t - 360f : t;
        }
        if (abre == null) return a;
        float lo = abre.get(0).getAsFloat(), hi = abre.get(1).getAsFloat();
        int bajo = -1, alto = -1;
        for (int j = 0; j < cols; j++) {
            if (a[j] > lo && a[j] < hi) continue;
            if (a[j] <= lo && (bajo < 0 || a[j] > a[bajo])) bajo = j;
            if (a[j] >= hi && (alto < 0 || a[j] < a[alto])) alto = j;
        }
        for (int j = 0; j < cols; j++) if (a[j] > lo && a[j] < hi) a[j] = Float.NaN;
        // La primera columna que existe de cada lado se corre al borde del rango, para que la boca quede donde se pidió.
        if (bajo >= 0) a[bajo] = lo;
        if (alto >= 0) a[alto] = hi;
        return a;
    }

    private static SombreroMalla.Quads secciones(JsonObject j, boolean suela) {
        int cols = Math.max(3, j.get("columnas").getAsInt());
        float g = j.has("grosor") ? j.get("grosor").getAsFloat() : 0f;
        JsonArray secs = j.getAsJsonArray("secciones");
        int ns = secs.size();
        boolean tapaTalon = true, tapaPunta = true;
        if (j.has("tapas")) {
            JsonArray t = j.getAsJsonArray("tapas");
            tapaTalon = t.get(0).getAsBoolean();
            tapaPunta = t.get(1).getAsBoolean();
        }
        float[] z = new float[ns], cx = new float[ns], cy = new float[ns], sa = new float[ns], sb = new float[ns], n = new float[ns];
        float[][] ang = new float[ns][];
        float minMedio = Float.MAX_VALUE;
        for (int s = 0; s < ns; s++) {
            JsonObject o = secs.get(s).getAsJsonObject();
            z[s] = o.get("z").getAsFloat();
            float x0 = o.get("x0").getAsFloat(), x1 = o.get("x1").getAsFloat(), yt = o.get("yTop").getAsFloat(), yb = o.get("yBot").getAsFloat();
            cx[s] = (x0 + x1) / 2f;
            cy[s] = (yt + yb) / 2f;
            sa[s] = Math.abs(x1 - x0) / 2f;
            sb[s] = Math.abs(yb - yt) / 2f;
            n[s] = o.get("n").getAsFloat();
            ang[s] = angulos(cols, o.has("abre") && o.get("abre").isJsonArray() ? o.getAsJsonArray("abre") : null);
            minMedio = Math.min(minMedio, Math.min(sa[s], sb[s]));
        }
        boolean maciza = suela || g <= 0f || g >= minMedio;
        Vector3f[][] ext = new Vector3f[ns][cols], inn = new Vector3f[ns][cols];
        for (int s = 0; s < ns; s++) {
            for (int c = 0; c < cols; c++) {
                float t = ang[s][c];
                if (Float.isNaN(t)) continue;
                double rad = Math.toRadians(t);
                float sn = (float) Math.sin(rad), cs = (float) Math.cos(rad);
                ext[s][c] = new Vector3f(cx[s] + sa[s] * potencia(sn, n[s]), cy[s] - sb[s] * potencia(cs, n[s]), z[s]);
                if (!maciza) {
                    float a2 = Math.max(0.05f, sa[s] - g), b2 = Math.max(0.05f, sb[s] - g);
                    inn[s][c] = new Vector3f(cx[s] + a2 * potencia(sn, n[s]), cy[s] - b2 * potencia(cs, n[s]), z[s]);
                }
            }
        }
        float[] largo = new float[ns];
        for (int s = 1; s < ns; s++) largo[s] = largo[s - 1] + Math.abs(z[s] - z[s - 1]) + 0.01f;
        Vector3f[] centro = new Vector3f[ns];
        for (int s = 0; s < ns; s++) centro[s] = new Vector3f(cx[s], cy[s], z[s]);

        Salida out = new Salida();
        grilla(out, ext, centro, largo, cols, false);
        if (!maciza) {
            grilla(out, inn, centro, largo, cols, true);
            // La boca: el canto entre la cara de afuera y la de adentro, en las columnas que bordean el hueco.
            for (int s = 0; s + 1 < ns; s++) {
                for (int c = 0; c < cols; c++) {
                    int c2 = (c + 1) % cols, c0 = (c + cols - 1) % cols;
                    boolean borde = ext[s][c] != null && ext[s + 1][c] != null && (ext[s][c2] == null || ext[s][c0] == null);
                    if (!borde) continue;
                    quadPlano(out, ext[s][c], ext[s + 1][c], inn[s + 1][c], inn[s][c], new Vector3f(0f, -1f, 0f), true,
                            (1f + 30f * c / cols) / 64f, (1f + Math.min(60f, largo[s])) / 64f, (1f + Math.min(60f, largo[s + 1])) / 64f);
                }
            }
        }
        // Tapas del talón (primera sección) y de la punta (la última): un abanico al centro (macizo) o el aro (hueco).
        // El talon de la capellada (la pared de atras de la caña) se cierra entera con un abanico al centro, aunque la pieza sea
        // hueca: con el aro quedaba un agujero en el medio por donde se veia el pantalon (2026-10-19, "el borcego tiene un hueco
        // en el medio de la parte de atras"). Las columnas de la boca (sin vertice) siguen abiertas.
        boolean capellada = j.has("eje") && "capellada".equals(j.get("eje").getAsString());
        if (tapaTalon) {
            float sentido = z[0] >= z[ns - 1] ? 1f : -1f;
            tapa(out, ext, inn, centro[0], 0, cols, maciza || capellada, sentido, largo);
            if (capellada) cuerdaDeLaBoca(out, ext, centro[0], cols, sentido, largo);
        }
        if (tapaPunta) tapa(out, ext, inn, centro[ns - 1], ns - 1, cols, maciza, z[ns - 1] <= z[0] ? -1f : 1f, largo);
        return out.quads();
    }

    /** Una grilla de ns secciones × cols columnas (cerrada en las columnas), con las normales suavizadas por vértice. */
    private static void grilla(Salida out, Vector3f[][] p, Vector3f[] centro, float[] largo, int cols, boolean haciaAdentro) {
        int ns = p.length;
        Vector3f[][] nrm = new Vector3f[ns][cols];
        for (Vector3f[] f : nrm) for (int c = 0; c < cols; c++) f[c] = new Vector3f();
        for (int s = 0; s + 1 < ns; s++) {
            for (int c = 0; c < cols; c++) {
                int c2 = (c + 1) % cols;
                Vector3f a = p[s][c], b = p[s][c2], d = p[s + 1][c2], e = p[s + 1][c];
                if (a == null || b == null || d == null || e == null) continue;
                Vector3f nq = new Vector3f(d).sub(a).cross(new Vector3f(e).sub(b));
                Vector3f med = new Vector3f(a).add(b).add(d).add(e).div(4f).sub(new Vector3f(centro[s]).add(centro[s + 1]).div(2f));
                // Hacia afuera del centro de la sección (o hacia adentro, en la cara de adentro de la pared).
                boolean volver = haciaAdentro ? nq.dot(med) > 0f : nq.dot(med) < 0f;
                if (volver) nq.negate();
                nrm[s][c].add(nq);
                nrm[s][c2].add(nq);
                nrm[s + 1][c2].add(nq);
                nrm[s + 1][c].add(nq);
            }
        }
        for (Vector3f[] f : nrm) for (Vector3f v : f) {
            if (v.lengthSquared() < 1e-12f) v.set(0f, -1f, 0f);
            v.normalize();
        }
        for (int s = 0; s + 1 < ns; s++) {
            for (int c = 0; c < cols; c++) {
                int c2 = (c + 1) % cols;
                if (p[s][c] == null || p[s][c2] == null || p[s + 1][c2] == null || p[s + 1][c] == null) continue;
                float ua = (1f + 30f * c / cols) / 64f, ub = (1f + 30f * (c + 1) / cols) / 64f;
                float va = (1f + Math.min(60f, largo[s])) / 64f, vb = (1f + Math.min(60f, largo[s + 1])) / 64f;
                out.v(p[s][c], nrm[s][c], ua, va);
                out.v(p[s][c2], nrm[s][c2], ub, va);
                out.v(p[s + 1][c2], nrm[s + 1][c2], ub, vb);
                out.v(p[s + 1][c], nrm[s + 1][c], ua, vb);
            }
        }
    }

    private static void tapa(Salida out, Vector3f[][] ext, Vector3f[][] inn, Vector3f centro, int s, int cols, boolean maciza,
                             float sentidoZ, float[] largo) {
        Vector3f n = new Vector3f(0f, 0f, sentidoZ);
        float v = (1f + Math.min(60f, largo[s])) / 64f;
        for (int c = 0; c < cols; c++) {
            int c2 = (c + 1) % cols;
            if (ext[s][c] == null || ext[s][c2] == null) continue;
            float ua = (1f + 30f * c / cols) / 64f, ub = (1f + 30f * (c + 1) / cols) / 64f;
            if (maciza) {
                out.v(ext[s][c], n, ua, v);
                out.v(ext[s][c2], n, ub, v);
                out.v(centro, n, ub, v);
                out.v(centro, n, ua, v);
            } else if (inn[s][c] != null && inn[s][c2] != null) {
                out.v(ext[s][c], n, ua, v);
                out.v(ext[s][c2], n, ub, v);
                out.v(inn[s][c2], n, ub, v);
                out.v(inn[s][c], n, ua, v);
            }
        }
    }

    /**
     * La boca de la caña en la pared del talon: el abanico deja una cuña vacia (en V, hasta el centro) donde faltan columnas.
     * Se cierra con el triangulo centro-borde-borde, asi la pared sube hasta una cuerda recta y la boca queda como un corte
     * chato y no como una V (2026-10-19, "el borcego tiene un corte en V atras ahora").
     */
    private static void cuerdaDeLaBoca(Salida out, Vector3f[][] ext, Vector3f centro, int cols, float sentidoZ, float[] largo) {
        int a = -1, b = -1;
        for (int c = 0; c < cols; c++) {
            if (ext[0][c] == null) continue;
            if (ext[0][(c + 1) % cols] == null) a = c;
            if (ext[0][(c + cols - 1) % cols] == null) b = c;
        }
        if (a < 0 || b < 0 || a == b) return;
        Vector3f n = new Vector3f(0f, 0f, sentidoZ);
        float v = (1f + Math.min(60f, largo[0])) / 64f;
        out.v(ext[0][a], n, 0f, v);
        out.v(ext[0][b], n, 1f / 64f, v);
        out.v(centro, n, 1f / 64f, v);
        out.v(centro, n, 0f, v);
    }

    /** Un cuadro plano con una normal que mira hacia el lado de {@code hacia} (o su contrario si {@code orientar} y apunta al revés). */
    private static void quadPlano(Salida out, Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f hacia, boolean orientar,
                                  float u, float va, float vb) {
        Vector3f n = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b));
        if (n.lengthSquared() < 1e-12f) n.set(hacia);
        n.normalize();
        if (orientar && n.dot(hacia) < 0f) n.negate();
        float u2 = u + 1f / 64f;
        out.v(a, n, u, va);
        out.v(b, n, u, vb);
        out.v(c, n, u2, vb);
        out.v(d, n, u2, va);
    }

    // ── cintas ────────────────────────────────────────────────────────────

    private static SombreroMalla.Quads cinta(JsonObject j) {
        JsonArray pa = j.getAsJsonArray("puntos"), na = j.getAsJsonArray("normales");
        boolean cerrada = j.has("cerrada") && j.get("cerrada").getAsBoolean();
        float ancho = j.get("ancho").getAsFloat(), grosor = j.get("grosor").getAsFloat();
        int n = pa.size();
        Vector3f[] p = new Vector3f[n], nr = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            JsonArray a = pa.get(i).getAsJsonArray(), b = na.get(i).getAsJsonArray();
            p[i] = new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
            nr[i] = new Vector3f(b.get(0).getAsFloat(), b.get(1).getAsFloat(), b.get(2).getAsFloat()).normalize();
        }
        // Se suaviza con Catmull-Rom que pasa por los puntos (3 pasos entre cada par).
        List<Vector3f> pts = new ArrayList<>(), nrs = new ArrayList<>();
        int tramos = cerrada ? n : n - 1;
        for (int i = 0; i < tramos; i++) {
            Vector3f p0 = p[cerrada ? (i + n - 1) % n : Math.max(0, i - 1)], p1 = p[i], p2 = p[(i + 1) % n];
            Vector3f p3 = p[cerrada ? (i + 2) % n : Math.min(n - 1, i + 2)];
            for (int k = 0; k < 3; k++) {
                float t = k / 3f;
                pts.add(catmull(p0, p1, p2, p3, t));
                nrs.add(new Vector3f(nr[i]).lerp(nr[(i + 1) % n], t).normalize());
            }
        }
        if (!cerrada) {
            pts.add(new Vector3f(p[n - 1]));
            nrs.add(new Vector3f(nr[n - 1]));
        }
        int m = pts.size();
        Vector3f[] ba = new Vector3f[m];
        for (int i = 0; i < m; i++) {
            Vector3f t = new Vector3f(pts.get(cerrada ? (i + 1) % m : Math.min(m - 1, i + 1)))
                    .sub(pts.get(cerrada ? (i + m - 1) % m : Math.max(0, i - 1)));
            if (t.lengthSquared() < 1e-12f) t.set(0f, 0f, -1f);
            Vector3f b = new Vector3f(t).cross(nrs.get(i));
            if (b.lengthSquared() < 1e-12f) b.set(1f, 0f, 0f);
            ba[i] = b.normalize().mul(ancho / 2f);
        }
        Salida out = new Salida();
        float acumulado = 0f;
        for (int i = 0; i < (cerrada ? m : m - 1); i++) {
            int k = (i + 1) % m;
            Vector3f g0 = new Vector3f(nrs.get(i)).mul(grosor), g1 = new Vector3f(nrs.get(k)).mul(grosor);
            // Las cuatro esquinas de la sección en i y en k: 0 = adentro-izq, 1 = adentro-der, 2 = afuera-der, 3 = afuera-izq.
            Vector3f[] ci = {new Vector3f(pts.get(i)).sub(ba[i]), new Vector3f(pts.get(i)).add(ba[i]),
                    new Vector3f(pts.get(i)).add(ba[i]).add(g0), new Vector3f(pts.get(i)).sub(ba[i]).add(g0)};
            Vector3f[] ck = {new Vector3f(pts.get(k)).sub(ba[k]), new Vector3f(pts.get(k)).add(ba[k]),
                    new Vector3f(pts.get(k)).add(ba[k]).add(g1), new Vector3f(pts.get(k)).sub(ba[k]).add(g1)};
            float paso = pts.get(i).distance(pts.get(k));
            float ua = (1f + Math.min(60f, acumulado)) / 64f, ub = (1f + Math.min(60f, acumulado + paso)) / 64f;
            acumulado += paso;
            Vector3f ni = nrs.get(i), nk = nrs.get(k);
            Vector3f bi = new Vector3f(ba[i]).normalize();
            // cara de afuera (normal = N), de adentro (−N), y los dos costados (±B)
            cara(out, ci[3], ci[2], ck[2], ck[3], ni, nk, ua, ub);
            cara(out, ci[1], ci[0], ck[0], ck[1], new Vector3f(ni).negate(), new Vector3f(nk).negate(), ua, ub);
            cara(out, ci[0], ci[3], ck[3], ck[0], new Vector3f(bi).negate(), new Vector3f(bi).negate(), ua, ub);
            cara(out, ci[2], ci[1], ck[1], ck[2], bi, bi, ua, ub);
        }
        return out.quads();
    }

    private static void cara(Salida out, Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f n1, Vector3f n2, float ua, float ub) {
        out.v(a, n1, ua, 1f / 64f);
        out.v(b, n1, ua, 3f / 64f);
        out.v(c, n2, ub, 3f / 64f);
        out.v(d, n2, ub, 1f / 64f);
    }

    private static Vector3f catmull(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        Vector3f r = new Vector3f();
        r.x = 0.5f * ((2f * p1.x) + (-p0.x + p2.x) * t + (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 + (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3);
        r.y = 0.5f * ((2f * p1.y) + (-p0.y + p2.y) * t + (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 + (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3);
        r.z = 0.5f * ((2f * p1.z) + (-p0.z + p2.z) * t + (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * t2 + (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * t3);
        return r;
    }

    /**
     * El calzado de cuboides (2026-10-14, "el mismo molde cuboide"): las cajas gruesas de la pieza ({@code apoyo}), cada una
     * movida en y con la plantilla evaluada en el centro de la caja (como pide el zip) salvo en la suela.
     */
    static SombreroMalla.Quads cuboides(Pieza p, float[][] plantilla) {
        Salida s = new Salida();
        List<SombreroRenderer.Caja> cajas = p.esPiel()
                ? List.of(new SombreroRenderer.Caja(3, new float[] {-2.2f, 9.6f, -5f}, new float[] {2.2f, 11f, 2.6f})) : p.apoyo;
        for (SombreroRenderer.Caja c : cajas) {
            float dy = p.esSuela() || plantilla == null ? 0f : alturaPlantilla(plantilla, (c.min()[2] + c.max()[2]) / 2f) - 11f;
            float x0 = c.min()[0], x1 = c.max()[0], y0 = c.min()[1] + dy, y1 = c.max()[1] + dy, z0 = c.min()[2], z1 = c.max()[2];
            float dx = Math.min(60f, x1 - x0), dyy = Math.min(60f, y1 - y0), dz = Math.min(60f, z1 - z0);
            cuadro(s, 0f, 0f, -1f, new float[][] {{x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}}, dx, dyy);
            cuadro(s, 0f, 0f, 1f, new float[][] {{x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}}, dx, dyy);
            cuadro(s, -1f, 0f, 0f, new float[][] {{x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}}, dz, dyy);
            cuadro(s, 1f, 0f, 0f, new float[][] {{x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}}, dz, dyy);
            cuadro(s, 0f, -1f, 0f, new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, dx, dz);
            cuadro(s, 0f, 1f, 0f, new float[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, dx, dz);
        }
        return s.quads();
    }

    private static void cuadro(Salida s, float nx, float ny, float nz, float[][] e, float w, float h) {
        Vector3f n = new Vector3f(nx, ny, nz);
        float[][] uv = {{1f / 64f, 1f / 64f}, {(1f + w) / 64f, 1f / 64f}, {(1f + w) / 64f, (1f + h) / 64f}, {1f / 64f, (1f + h) / 64f}};
        for (int i = 0; i < 4; i++) s.v(new Vector3f(e[i][0], e[i][1], e[i][2]), n, uv[i][0], uv[i][1]);
    }

    // ── dibujo ────────────────────────────────────────────────────────────

    /**
     * Dibuja una pieza ya con la matriz de la pierna aplicada. {@code plantilla} (null en la suela) mueve cada vértice en y
     * según la suela elegida; {@code espejo} da vuelta la X (el pie izquierdo).
     */
    /** El talón del pie generado (z mayor de {@code pie_skin.json}) y cuántas filas de la media recorre al alternar. */
    private static final float PIE_TALON_Z = 2.6f;
    private static final int FILAS_PIE = 3;

    static void emitir(Pieza p, float[][] plantilla, boolean espejo, int media, Matrix4f pos, Matrix3f nor, VertexConsumer vc, int luz) {
        emitir(p.quads, plantilla, espejo, p.esPiel(), media, pos, nor, vc, luz);
    }

    /**
     * {@code media}: el pie generado va envuelto en la media (2026-10-18, "podemos envolver tambien el pie q se genera para el
     * calzado abierto"): 1 = pierna derecha, 2 = izquierda, 0 = con la piel. Con media, la tela de la media es la textura y la
     * UV recorre las 4 columnas de la cara de adelante de la pierna según la x (así las rayas verticales y los dibujos por
     * columna bajan por el pie) en la última fila.
     */
    static void emitir(SombreroMalla.Quads q, float[][] plantilla, boolean espejo, boolean piel, int media, Matrix4f pos,
                       Matrix3f nor, VertexConsumer vc, int luz) {
        Vector3f v = new Vector3f(), nv = new Vector3f();
        for (int i = 0; i < q.vertices; i++) {
            v.set(q.pos[i * 3], q.pos[i * 3 + 1], q.pos[i * 3 + 2]);
            nv.set(q.nor[i * 3], q.nor[i * 3 + 1], q.nor[i * 3 + 2]);
            if (plantilla != null) v.y += alturaPlantilla(plantilla, v.z) - 11f;
            if (espejo) {
                v.x = -v.x;
                nv.x = -nv.x;
            }
            Vector3f w = pos.transformPosition(v.x / 16f, v.y / 16f, v.z / 16f, new Vector3f());
            Vector3f nn = nor.transform(nv, new Vector3f()).normalize();
            // La piel: un texel de abajo de la cara de adelante de la pierna (u 4..8, v 20..32 de la skin).
            float u = piel ? 5.5f / 64f : q.uv[i * 2], t = piel ? 29.5f / 64f : q.uv[i * 2 + 1];
            if (piel && media != 0) {
                float columna = Math.max(0.25f, Math.min(3.75f, (v.x + 2.2f) / 4.4f * 4f));
                u = ((media == 2 ? 20f : 4f) + columna) / 64f;      // cara de adelante: u 4..8 (derecha) o 20..24 (izquierda)
                // Las filas (2026-10-18, "las rayas horizontales seria ideal que giren por el talon y alternen hasta la
                // punta"): del talón (fila 11, la de abajo de la pierna) a la punta suben y bajan por las últimas
                // FILAS_PIE filas, una fila por px de largo, así las rayas siguen alternando como en la pierna.
                float d = Math.max(0f, PIE_TALON_Z - v.z) % (2 * FILAS_PIE);
                float sube = d < FILAS_PIE ? d : 2 * FILAS_PIE - d;
                t = ((media == 2 ? 52f : 20f) + 11.99f - sube) / 64f;
            }
            vc.vertex(w.x, w.y, w.z, 0xFFFFFFFF, u, t, OverlayTexture.DEFAULT_UV, luz, nn.x, nn.y, nn.z);
        }
    }
}
