package com.modamod.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Los sombreros como malla (2026-10-14, "sombreros estilo mallas": las piezas salen de Claude Design, formato
 * {@code modamod-sombrero-malla-1} en {@code assets/modamod/sombreros/}). Cada pieza es una superficie de revolución: una
 * lista de anillos (y, rx, rz relativos a {@code yBase}) con una modulación por columna (escala, dy, solo) y, en las copas
 * con punta blanda, tramos encadenados que pivotan. Coordenadas en px del marco de la cabeza (y negativo = arriba, el
 * frente es −Z). La malla de cada pieza se arma UNA vez y se cachea; la textura es lisa por zona (u alrededor, v a lo
 * largo del perfil, 1 texel por px, como las otras mallas del mod).
 */
final class SombreroMalla {
    private SombreroMalla() {}

    record Anillo(float y, float rx, float rz) {}

    /** Un tramo blando de la punta: gira alrededor de {@code pivoteY} (x = z = 0) sobre el anterior. */
    static final class Tramo {
        final float pivoteY, inclinacion;
        final Quads quads;
        Tramo(float pivoteY, float inclinacion, Quads quads) {
            this.pivoteY = pivoteY;
            this.inclinacion = inclinacion;
            this.quads = quads;
        }
    }

    /** Vértices en cuartetos (un cuadro cada 4), en px. */
    static final class Quads {
        final float[] pos, nor, uv;
        final int vertices;
        Quads(float[] pos, float[] nor, float[] uv) {
            this.pos = pos;
            this.nor = nor;
            this.uv = uv;
            this.vertices = pos.length / 3;
        }
    }

    static final class Pieza {
        String id;
        int zona;
        float yBase, cx, cz;
        Quads base;
        List<Tramo> punta = new ArrayList<>();
        List<SombreroRenderer.Caja> apoyo = new ArrayList<>();
        /** Las mismas cajas gruesas como cuadros (se arma al primer pedido): el sombrero de cuboides. */
        Quads cuboides;
    }

    private static final Map<String, Pieza> CACHE = new HashMap<>();
    private static final Pieza NINGUNA = new Pieza();

    /** La pieza {@code id} de la carpeta {@code carpeta} ("fina" o "cuadrada"), o null si no existe. */
    static Pieza de(String carpeta, String id) {
        String clave = carpeta + "/" + id;
        Pieza p = CACHE.get(clave);
        if (p == null) {
            p = cargar(carpeta, id);
            CACHE.put(clave, p == null ? NINGUNA : p);
        }
        return p == NINGUNA ? null : p;
    }

    private static Pieza cargar(String carpeta, String id) {
        try (InputStream in = SombreroMalla.class.getResourceAsStream("/assets/modamod/sombreros/" + carpeta + "/" + id + ".json")) {
            if (in == null) return null;
            JsonObject j = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Pieza p = new Pieza();
            p.id = id;
            p.zona = j.get("zona").getAsInt();
            p.yBase = j.get("yBase").getAsFloat();
            int cols = Math.max(3, j.get("columnas").getAsInt());
            String cierre = j.get("cierre").getAsString();
            float grosor = j.has("grosor") ? j.get("grosor").getAsFloat() : 0f;
            if (j.has("centro")) {
                JsonArray c = j.getAsJsonArray("centro");
                p.cx = c.get(0).getAsFloat();
                p.cz = c.get(1).getAsFloat();
            }
            float[] escala = null, dy = null;
            boolean[] solo = null;
            if (j.has("porColumna") && j.get("porColumna").isJsonObject()) {
                JsonObject pc = j.getAsJsonObject("porColumna");
                if (pc.has("escala")) escala = flotantes(pc.getAsJsonArray("escala"));
                if (pc.has("dy")) dy = flotantes(pc.getAsJsonArray("dy"));
                if (pc.has("solo")) {
                    solo = new boolean[cols];
                    for (JsonElement e : pc.getAsJsonArray("solo")) solo[Math.floorMod(e.getAsInt(), cols)] = true;
                }
            }
            Anillo[] anillos = anillos(j.getAsJsonArray("anillos"));
            boolean plana = zonaEsAla(j) && !id.equals("vincha") && !id.equals("cofia");   // las alas planas miran hacia arriba y hacia abajo
            p.base = construir(anillos, cols, escala, dy, solo, cierre, grosor, p.yBase, p.cx, p.cz, plana);
            if (j.has("punta") && j.get("punta").isJsonArray()) {
                for (JsonElement t : j.getAsJsonArray("punta")) {
                    JsonObject o = t.getAsJsonObject();
                    Quads q = construir(anillos(o.getAsJsonArray("anillos")), cols, null, null, null, "punta", 0f, p.yBase, 0f, 0f, false);
                    p.punta.add(new Tramo(p.yBase + o.get("pivoteY").getAsFloat(), o.get("inclinacionRad").getAsFloat(), q));
                }
            }
            for (JsonElement a : j.getAsJsonArray("apoyo")) {
                JsonObject o = a.getAsJsonObject();
                JsonArray mn = o.getAsJsonArray("min"), mx = o.getAsJsonArray("max");
                p.apoyo.add(new SombreroRenderer.Caja(o.get("zona").getAsInt(),
                        new float[] {mn.get(0).getAsFloat(), mn.get(1).getAsFloat(), mn.get(2).getAsFloat()},
                        new float[] {mx.get(0).getAsFloat(), mx.get(1).getAsFloat(), mx.get(2).getAsFloat()}));
            }
            return p;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("modamod").warn("Sombrero: no se pudo leer la pieza {}/{}", carpeta, id, e);
            return null;
        }
    }

    private static boolean zonaEsAla(JsonObject j) {
        return "ala".equals(j.get("tipo").getAsString());
    }

    private static float[] flotantes(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    private static Anillo[] anillos(JsonArray a) {
        Anillo[] r = new Anillo[a.size()];
        for (int i = 0; i < r.length; i++) {
            JsonObject o = a.get(i).getAsJsonObject();
            r[i] = new Anillo(o.get("y").getAsFloat(), o.get("rx").getAsFloat(), o.get("rz").getAsFloat());
        }
        return r;
    }

    // ── geometría ─────────────────────────────────────────────────────────

    /**
     * Arma los cuadros de una pieza. Con {@code grosor} > 0 la superficie es de doble cara (una arriba y otra {@code grosor}
     * px más abajo, unidas por el borde de afuera). {@code solo} deja la pieza en esas columnas. Las normales se suavizan
     * por vértice (la suma de los cuadros vecinos) y miran hacia afuera del centro de la pieza.
     */
    private static Quads construir(Anillo[] anillos, int cols, float[] escala, float[] dy, boolean[] solo, String cierre,
                                   float grosor, float yBase, float cx, float cz, boolean plana) {
        List<Anillo> an = new ArrayList<>(List.of(anillos));
        if (!cierre.equals("abierto")) {
            Anillo ultimo = an.get(an.size() - 1);
            an.add(new Anillo(ultimo.y(), 0f, 0f));   // tapa o punta: se cierra en el eje
        }
        int R = an.size();
        // Largo acumulado del perfil (px) para la v de la textura.
        float[] largo = new float[R];
        for (int r = 1; r < R; r++) {
            float rm = (an.get(r).rx() + an.get(r).rz() - an.get(r - 1).rx() - an.get(r - 1).rz()) / 2f;
            float dyr = an.get(r).y() - an.get(r - 1).y();
            largo[r] = largo[r - 1] + (float) Math.sqrt(rm * rm + dyr * dyr);
        }
        boolean[] par = new boolean[cols];
        for (int c = 0; c < cols; c++) par[c] = solo == null || (solo[c] && solo[(c + 1) % cols]);
        Vector3f[][] arriba = new Vector3f[R][cols];
        for (int r = 0; r < R; r++) {
            for (int c = 0; c < cols; c++) {
                double fi = 2 * Math.PI * c / cols;
                float e = escala == null || c >= escala.length ? 1f : escala[c];
                float d = dy == null || c >= dy.length ? 0f : dy[c];
                arriba[r][c] = new Vector3f(cx + an.get(r).rx() * e * (float) Math.sin(fi), yBase + an.get(r).y() + d,
                        cz - an.get(r).rz() * e * (float) Math.cos(fi));
            }
        }
        Vector3f centro = new Vector3f();
        int n = 0;
        for (Vector3f[] fila : arriba) for (Vector3f v : fila) { centro.add(v); n++; }
        centro.div(Math.max(1, n));
        if (grosor > 0f) centro.add(0f, grosor / 2f, 0f);

        Salida s = new Salida();
        grilla(s, arriba, par, largo, cols, centro, plana ? ARRIBA : CENTRO);
        if (grosor > 0f) {
            Vector3f[][] abajo = new Vector3f[R][cols];
            for (int r = 0; r < R; r++) for (int c = 0; c < cols; c++) abajo[r][c] = new Vector3f(arriba[r][c]).add(0f, grosor, 0f);
            grilla(s, abajo, par, largo, cols, centro, plana ? ABAJO : CENTRO);
            // El borde de afuera une las dos caras.
            Vector3f[][] borde = new Vector3f[2][cols];
            for (int c = 0; c < cols; c++) {
                borde[0][c] = arriba[R - 1][c];
                borde[1][c] = abajo[R - 1][c];
            }
            grilla(s, borde, par, new float[] {largo[R - 1], largo[R - 1] + grosor}, cols, centro, CENTRO);
        }
        return s.quads();
    }

    private static final class Salida {
        final List<float[]> pos = new ArrayList<>(), nor = new ArrayList<>(), uv = new ArrayList<>();

        Quads quads() {
            float[] p = new float[pos.size() * 3], n = new float[pos.size() * 3], t = new float[pos.size() * 2];
            for (int i = 0; i < pos.size(); i++) {
                System.arraycopy(pos.get(i), 0, p, i * 3, 3);
                System.arraycopy(nor.get(i), 0, n, i * 3, 3);
                System.arraycopy(uv.get(i), 0, t, i * 2, 2);
            }
            proyectarUv(p, n, t);
            return new Quads(p, n, t);
        }
    }

    /**
     * Las UV de cada cuadro salen de DÓNDE está en el espacio (2026-10-15, "aparecen muy pijeados los patrones... una uv
     * nueva para sombreros y calzados"): antes cada pieza estiraba su vuelta entera en 30 texels y arrancaba en (1,1), así
     * que cada cuadro mostraba otro recorte del patrón y cada pieza lo suyo. Ahora se proyecta plano según hacia dónde mira
     * el cuadro, a 1 texel por px del modelo (la misma densidad que las prendas): las caras de costado usan el eje
     * horizontal que ve quien las mira (derecha = arriba × normal) y la altura; las de arriba y abajo, X y Z. Así un dibujo
     * sigue parejo de una pieza a la otra (la copa y el ala, la capellada y la suela). El rango es ±32 px del origen del marco.
     */
    static void proyectarUv(float[] pos, float[] nor, float[] uv) {
        int vertices = pos.length / 3;
        for (int q = 0; q + 3 < vertices; q += 4) {
            float nx = 0f, ny = 0f, nz = 0f;
            for (int k = 0; k < 4; k++) {
                nx += nor[(q + k) * 3];
                ny += nor[(q + k) * 3 + 1];
                nz += nor[(q + k) * 3 + 2];
            }
            float largo = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (largo > 1e-6f) {
                nx /= largo;
                ny /= largo;
                nz /= largo;
            }
            boolean horizontal = Math.abs(ny) > 0.75f;
            float rx = -nz, rz = nx;   // derecha = (0,-1,0) × n, en el plano horizontal
            float lr = (float) Math.sqrt(rx * rx + rz * rz);
            if (lr > 1e-6f) {
                rx /= lr;
                rz /= lr;
            }
            for (int k = 0; k < 4; k++) {
                float x = pos[(q + k) * 3], y = pos[(q + k) * 3 + 1], z = pos[(q + k) * 3 + 2];
                float u = horizontal ? x : x * rx + z * rz;
                float v = horizontal ? (ny < 0f ? z : -z) : y;
                uv[(q + k) * 2] = (u + 32f) / 64f;
                uv[(q + k) * 2 + 1] = (v + 32f) / 64f;
            }
        }
    }

    /** Una grilla de R filas × cols columnas (cerrada en las columnas) como cuadros; solo los pares de columnas válidos. */
    private static final int CENTRO = 0, ARRIBA = 1, ABAJO = 2;

    private static void grilla(Salida s, Vector3f[][] p, boolean[] par, float[] largo, int cols, Vector3f centro, int modo) {
        int R = p.length;
        Vector3f[][] nrm = new Vector3f[R][cols];
        for (Vector3f[] f : nrm) for (int c = 0; c < cols; c++) f[c] = new Vector3f();
        for (int r = 0; r < R - 1; r++) {
            for (int c = 0; c < cols; c++) {
                if (!par[c]) continue;
                int c2 = (c + 1) % cols;
                Vector3f a = p[r][c], b = p[r][c2], d = p[r + 1][c2], e = p[r + 1][c];
                Vector3f nq = new Vector3f(d).sub(a).cross(new Vector3f(e).sub(b));
                Vector3f med = new Vector3f(a).add(b).add(d).add(e).div(4f).sub(centro);
                // Hacia afuera del centro de la pieza; las alas planas, hacia arriba (cara de arriba) o hacia abajo.
                boolean volver = modo == ARRIBA ? nq.y > 0f : modo == ABAJO ? nq.y < 0f : nq.dot(med) < 0f;
                if (volver) nq.negate();
                nrm[r][c].add(nq);
                nrm[r][c2].add(nq);
                nrm[r + 1][c2].add(nq);
                nrm[r + 1][c].add(nq);
            }
        }
        for (Vector3f[] f : nrm) for (Vector3f v : f) {
            if (v.lengthSquared() < 1e-12f) v.set(0f, -1f, 0f);
            v.normalize();
        }
        for (int r = 0; r < R - 1; r++) {
            for (int c = 0; c < cols; c++) {
                if (!par[c]) continue;
                int c2 = (c + 1) % cols;
                float ua = (1f + 30f * c / cols) / 64f, ub = (1f + 30f * (c + 1) / cols) / 64f;
                float va = (1f + Math.min(60f, largo[r])) / 64f, vb = (1f + Math.min(60f, largo[r + 1])) / 64f;
                vertice(s, p[r][c], nrm[r][c], ua, va);
                vertice(s, p[r][c2], nrm[r][c2], ub, va);
                vertice(s, p[r + 1][c2], nrm[r + 1][c2], ub, vb);
                vertice(s, p[r + 1][c], nrm[r + 1][c], ua, vb);
            }
        }
    }

    private static void vertice(Salida s, Vector3f p, Vector3f n, float u, float v) {
        s.pos.add(new float[] {p.x, p.y, p.z});
        s.nor.add(new float[] {n.x, n.y, n.z});
        s.uv.add(new float[] {u, v});
    }

    /**
     * El sombrero de cuboides (2026-10-14, "las dos opciones a traves de un molde de cuboide vs malla"): las cajas
     * gruesas de la pieza ({@code apoyo}) dibujadas tal cual, con la punta recta y sin física.
     */
    static Quads cuboides(Pieza p) {
        if (p.cuboides == null) {
            Salida s = new Salida();
            for (SombreroRenderer.Caja c : p.apoyo) caja(s, c.min(), c.max());
            p.cuboides = s.quads();
        }
        return p.cuboides;
    }

    private static void caja(Salida s, float[] a, float[] b) {
        float x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]), y0 = Math.min(a[1], b[1]), y1 = Math.max(a[1], b[1]);
        float z0 = Math.min(a[2], b[2]), z1 = Math.max(a[2], b[2]);
        float dx = Math.min(60f, x1 - x0), dy = Math.min(60f, y1 - y0), dz = Math.min(60f, z1 - z0);
        // {caras: normal, 4 esquinas, ancho y alto del cuadro de textura}
        cara(s, 0f, 0f, -1f, new float[][] {{x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}}, dx, dy);   // frente (−Z)
        cara(s, 0f, 0f, 1f, new float[][] {{x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}}, dx, dy);    // atrás
        cara(s, -1f, 0f, 0f, new float[][] {{x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}}, dz, dy);  // −X
        cara(s, 1f, 0f, 0f, new float[][] {{x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}}, dz, dy);   // +X
        cara(s, 0f, -1f, 0f, new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, dx, dz);  // arriba (−Y)
        cara(s, 0f, 1f, 0f, new float[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, dx, dz);   // abajo
    }

    private static void cara(Salida s, float nx, float ny, float nz, float[][] e, float w, float h) {
        float[] n = {nx, ny, nz};
        float[][] uv = {{1f / 64f, 1f / 64f}, {(1f + w) / 64f, 1f / 64f}, {(1f + w) / 64f, (1f + h) / 64f}, {1f / 64f, (1f + h) / 64f}};
        for (int i = 0; i < 4; i++) {
            s.pos.add(e[i]);
            s.nor.add(n);
            s.uv.add(uv[i]);
        }
    }

    // ── dibujo ────────────────────────────────────────────────────────────

    /**
     * Dibuja la pieza ya con la matriz de la cabeza aplicada en {@code matrices}. {@code dy} la baja (px) para apoyarla en
     * la copa elegida; {@code pitch}/{@code roll} son lo que la tela blanda le suma a cada tramo de la punta.
     */
    static void dibujar(Pieza p, float dy, MatrixStackLike m, VertexConsumer vc, int luz, float pitch, float roll, boolean cuboides) {
        if (cuboides) {
            emitir(cuboides(p), dy, m, vc, luz, null);
            return;
        }
        emitir(p.base, dy, m, vc, luz, null);
        for (int i = 0; i < p.punta.size(); i++) {
            Tramo t = p.punta.get(i);
            // El tramo gira sobre su pivote y después lo arrastran los de abajo (la cadena anidada).
            List<Quaternionf> giros = new ArrayList<>();
            List<float[]> pivotes = new ArrayList<>();
            for (int k = i; k >= 0; k--) {
                Tramo u = p.punta.get(k);
                giros.add(new Quaternionf().rotationZYX(roll * 0.5f, 0f, u.inclinacion + pitch * 0.5f));
                pivotes.add(new float[] {0f, u.pivoteY, 0f});
            }
            emitir(t.quads, dy, m, vc, luz, new Object[] {giros, pivotes});
        }
    }

    /**
     * La foto de la Sublimadora sobre el frente de la copa (2026-10-25, "quiero que gorras y mascaras sean sublimables
     * agrandando la textura x16"): las caras que miran hacia adelante (−Z) se vuelven a dibujar con la UV proyectada de
     * frente sobre la caja de la copa, al rectángulo {@code rect} (x, y, ancho, alto en px de 64) de la textura de la foto.
     */
    static void dibujarFoto(Pieza p, float dy, MatrixStackLike m, VertexConsumer vc, int luz, boolean cuboides, float[] rect) {
        Quads q = cuboides ? cuboides(p) : p.base;
        float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = 0; i < q.vertices; i++) {
            x0 = Math.min(x0, q.pos[i * 3]); x1 = Math.max(x1, q.pos[i * 3]);
            y0 = Math.min(y0, q.pos[i * 3 + 1]); y1 = Math.max(y1, q.pos[i * 3 + 1]);
        }
        if (x1 <= x0 || y1 <= y0) return;
        Vector3f v = new Vector3f(), nv = new Vector3f();
        for (int c = 0; c + 3 < q.vertices; c += 4) {
            float nz = 0f;
            for (int k = 0; k < 4; k++) nz += q.nor[(c + k) * 3 + 2];
            if (nz > -1.2f) continue;   // solo lo que mira de frente
            for (int k = 0; k < 4; k++) {
                int i = c + k;
                v.set(q.pos[i * 3], q.pos[i * 3 + 1] + dy, q.pos[i * 3 + 2]);
                nv.set(q.nor[i * 3], q.nor[i * 3 + 1], q.nor[i * 3 + 2]);
                // De frente, la izquierda de quien mira es +X.
                float u = (rect[0] + (x1 - q.pos[i * 3]) / (x1 - x0) * rect[2]) / 64f;
                float vv = (rect[1] + (q.pos[i * 3 + 1] - y0) / (y1 - y0) * rect[3]) / 64f;
                Vector3f w = m.pos().transformPosition(v.x / 16f, v.y / 16f, v.z / 16f, new Vector3f());
                Vector3f nn = m.nor().transform(nv, new Vector3f()).normalize();
                vc.vertex(w.x, w.y, w.z, 0xFFFFFFFF, u, vv, OverlayTexture.DEFAULT_UV, luz, nn.x, nn.y, nn.z);
            }
        }
    }

    /** Lo que hace falta de una MatrixStack: la matriz de posición y la de normales. */
    record MatrixStackLike(Matrix4f pos, Matrix3f nor) {}

    @SuppressWarnings("unchecked")
    private static void emitir(Quads q, float dy, MatrixStackLike m, VertexConsumer vc, int luz, Object[] cadena) {
        List<Quaternionf> giros = cadena == null ? null : (List<Quaternionf>) cadena[0];
        List<float[]> pivotes = cadena == null ? null : (List<float[]>) cadena[1];
        Vector3f v = new Vector3f(), nv = new Vector3f();
        for (int i = 0; i < q.vertices; i++) {
            v.set(q.pos[i * 3], q.pos[i * 3 + 1], q.pos[i * 3 + 2]);
            nv.set(q.nor[i * 3], q.nor[i * 3 + 1], q.nor[i * 3 + 2]);
            if (giros != null) {
                for (int k = 0; k < giros.size(); k++) {
                    float[] pv = pivotes.get(k);
                    v.sub(pv[0], pv[1], pv[2]);
                    giros.get(k).transform(v);
                    v.add(pv[0], pv[1], pv[2]);
                    giros.get(k).transform(nv);
                }
            }
            v.y += dy;
            Vector3f w = m.pos().transformPosition(v.x / 16f, v.y / 16f, v.z / 16f, new Vector3f());
            Vector3f nn = m.nor().transform(nv, new Vector3f()).normalize();
            vc.vertex(w.x, w.y, w.z, 0xFFFFFFFF, q.uv[i * 2], q.uv[i * 2 + 1], OverlayTexture.DEFAULT_UV, luz, nn.x, nn.y, nn.z);
        }
    }
}
