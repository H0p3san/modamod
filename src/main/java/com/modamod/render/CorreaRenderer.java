package com.modamod.render;

import com.modamod.correa.Correa;
import com.modamod.correa.EstiloCorrea;
import com.modamod.correa.ModoCorrea;
import com.modamod.item.ModamodComponents;
import com.modamod.item.SombreroPatron;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Correas libres (2026-10-05, "correas libres... rectas u oblicuas, pegadas o colgantes" + "molde de cadenas"): cada
 * {@link Correa} es un camino de nodos sobre o cerca de la caja de su parte del cuerpo (en el espacio local de la
 * parte, px) que se dibuja como cinta (lisa, ojalillos), cadena de eslabones, bolitas o cordón.
 *
 * <ul>
 *   <li>PEGADA: la recta entre los dos puntos se proyecta sobre la superficie de la caja (inflada lo que infla la
 *       prenda), así da la vuelta por las aristas; entre caras opuestas pasa por una tercera.</li>
 *   <li>COLGANTE: solo el 1.º punto la sujeta; el resto cuelga en la dirección del 2.º con tela blanda (la inercia de
 *       {@link FisicaApliques}), sin entrar en la caja.</li>
 *   <li>ANCLADA (2026-10-06, "un solo punto y que cuelgue con la gravedad"): un punto y {@code largo} px de cadena que
 *       cuelgan hacia abajo, con un vaivén por la inercia, apoyándose en el cuerpo sin atravesarlo.</li>
 *   <li>COLGADA ("la cadena cuelgue"): dos puntos y {@code largo} px de cadena (mínimo la distancia): si sobra, cuelga en
 *       curva con la gravedad entre los dos.</li>
 * </ul>
 */
public final class CorreaRenderer {

    private CorreaRenderer() {}

    private record Nodo(Vector3f pos, Vector3f normal) {}

    /** Una superficie de cajas (la de la parte del cuerpo, o las del sombrero y la banda) sobre la que se apoya la correa. */
    private static final class Sup {
        final List<float[]> mn = new ArrayList<>(), mx = new ArrayList<>();
        final float[] bmn = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, bmx = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

        void agregar(float[] a, float[] b, float e) {
            float[] lo = {a[0] - e, a[1] - e, a[2] - e}, hi = {b[0] + e, b[1] + e, b[2] + e};
            mn.add(lo);
            mx.add(hi);
            for (int i = 0; i < 3; i++) {
                bmn[i] = Math.min(bmn[i], lo[i]);
                bmx[i] = Math.max(bmx[i], hi[i]);
            }
        }

        /**
         * Con una sola caja es la proyección de siempre. Con varias (sombrero, banda): lo más cercano entre las
         * superficies de cada caja que no quede metido adentro de otra (el ala bajo el cono, por ejemplo).
         */
        Nodo proyectar(Vector3f p, boolean pegar) {
            if (mn.size() == 1) return CorreaRenderer.proyectar(p, mn.get(0), mx.get(0), pegar);
            Nodo mejorMovido = null, mejorLibre = null, cualquiera = null;
            float dMovido = Float.MAX_VALUE, dLibre = Float.MAX_VALUE, dCualquiera = Float.MAX_VALUE;
            for (int i = 0; i < mn.size(); i++) {
                Nodo n = CorreaRenderer.proyectar(p, mn.get(i), mx.get(i), pegar);
                float d = n.pos().distanceSquared(p);
                if (d < dCualquiera) { dCualquiera = d; cualquiera = n; }
                if (metidoEnOtra(n.pos(), i)) continue;
                if (pegar || d > 1e-8f) {
                    if (d < dMovido) { dMovido = d; mejorMovido = n; }
                } else if (d < dLibre) {
                    dLibre = d;
                    mejorLibre = n;
                }
            }
            if (mejorMovido != null) return mejorMovido;
            return mejorLibre != null ? mejorLibre : cualquiera;
        }

        private boolean metidoEnOtra(Vector3f q, int propia) {
            final float eps = 1e-3f;
            for (int i = 0; i < mn.size(); i++) {
                if (i == propia) continue;
                boolean dentro = true;
                for (int k = 0; k < 3; k++) if (q.get(k) <= mn.get(i)[k] + eps || q.get(k) >= mx.get(i)[k] - eps) dentro = false;
                if (dentro) return true;
            }
            return false;
        }
    }

    /** Dibuja las correas de {@code item} que van sobre cajas; {@code marco} es la normal del cuerpo antes de la pose de cada parte. */
    public static void dibujar(ItemStack item, float dil, BipedEntityModel<?> biped, MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers, int luz, Matrix3f marco) {
        long t0 = com.modamod.render.Perf.ini();
        try {
            dibujar0(item, dil, biped, matrices, vertexConsumers, luz, marco);
        } finally {
            com.modamod.render.Perf.fin(com.modamod.render.Perf.Seccion.CORREAS, t0);
        }
    }

    public static void dibujar0(ItemStack item, float dil, BipedEntityModel<?> biped, MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers, int luz, Matrix3f marco) {
        List<Correa> lista = item.get(ModamodComponents.CORREAS);
        if (lista == null || lista.isEmpty()) return;
        for (Correa c : lista) if (c.superficie() == Correa.Superficie.CAJA) una(item, c, dil, biped, matrices, vertexConsumers, luz, marco);
    }

    /** ¿Tiene {@code item} correas sobre esa malla? (la malla solo se graba si hay algo que ubicar en ella) */
    public static boolean hayEn(ItemStack item, Correa.Superficie sup) {
        List<Correa> lista = item.get(ModamodComponents.CORREAS);
        if (lista == null) return false;
        for (Correa c : lista) if (c.superficie() == sup) return true;
        return false;
    }

    private static float espesorDe(Correa c) {
        float w = c.ancho();
        return switch (c.estilo()) {
            case LISA, OJALILLOS -> 0.3f;
            case CORDON -> Math.max(0.5f, w * 0.45f);
            case CADENA -> w * 0.9f;
            case CADENA_FINA -> Math.max(1f, w * 0.75f);
        };
    }

    private static float pasoDe(Correa c, float w) {
        return switch (c.estilo()) {
            case LISA, OJALILLOS -> Math.max(2f, 2f * w);
            case CADENA -> w * 1.5f;
            case CADENA_FINA -> Math.max(1f, w * 0.75f) * 1.05f;
            case CORDON -> 2f;
        };
    }

    private static void una(ItemStack item, Correa c, float dil, BipedEntityModel<?> biped, MatrixStack matrices,
                            VertexConsumerProvider vcp, int luz, Matrix3f marco) {
        ModelPart parte = CuerpoGeometria.delJugador(biped, c.parte());
        if (!parte.visible) return;
        float espesor = espesorDe(c);
        float e = dil + 0.05f + espesor / 2f;
        Sup sup = new Sup();
        if (item.getItem() instanceof com.modamod.item.ZonasTenibles) {
            // Sombrero y banda: las cajas con las que ellos mismos se dibujan.
            for (SombreroRenderer.Caja caja : AccesorioRenderer.cajas(item, c.parte())) sup.agregar(caja.min(), caja.max(), 0.05f + espesor / 2f);
        } else {
            if (parte.cuboids.isEmpty()) return;
            ModelPart.Cuboid cu = parte.cuboids.get(0);
            sup.agregar(new float[] {cu.minX, cu.minY, cu.minZ}, new float[] {cu.maxX, cu.maxY, cu.maxZ}, e);
        }
        if (sup.mn.isEmpty()) return;
        float ee = item.getItem() instanceof com.modamod.item.ZonasTenibles ? 0.05f + espesor / 2f : e;
        Vector3f a = punto(c.desde(), ee), b = punto(c.hasta(), ee);
        matrices.push();
        parte.rotate(matrices);
        List<Nodo> camino;
        Matrix3f normalLocal = new Matrix3f(matrices.peek().getNormalMatrix());
        if (c.modo() == ModoCorrea.PEGADA) {
            camino = rutaPegada(a, c.desde().cara(), b, c.hasta().cara(), sup);
        } else if (c.modo().usaLargo()) {
            // Gravedad e inercia, del marco del cuerpo al de esta parte (las dos son rotaciones: la inversa es la transpuesta).
            Matrix3f aLocal = new Matrix3f(normalLocal).transpose().mul(marco);
            Vector3f g = aLocal.transform(new Vector3f(0, 1, 0)).normalize();       // en el espacio del modelo +Y es hacia abajo
            Vector3f inclinacion = inclinacion(c.blandura(), aLocal);
            Vector3f hacia = new Vector3f(c.desde().cara().getOffsetX(), c.desde().cara().getOffsetY(), c.desde().cara().getOffsetZ());
            List<Vector3f> curva = c.modo() == ModoCorrea.ANCLADA
                    ? curvaAnclada(a, c.largo(), g, inclinacion)
                    : curvaColgada(a, b, c.largo(), g, inclinacion, hacia);
            camino = new ArrayList<>();
            for (Vector3f q : curva) camino.add(sup.proyectar(q, false));
        } else {
            camino = rutaColgante(a, b, sup, c.blandura(), normalLocal, marco);
        }
        camino = remuestrear(camino, pasoDe(c, c.ancho()));
        if (camino.size() >= 2) emitir(c, camino, c.ancho(), espesor, matrices.peek(), vcp, luz);
        matrices.pop();
    }

    /**
     * Las correas que van sobre la malla de la pollera o la capa (2026-10-05, "superame esos limites"): los dos puntos
     * son (u, v) de la tela y se ubican en la malla de este cuadro con {@link MallaCapturada#enUv}; la pegada sigue la
     * tela entre los dos, la colgante sale del primero hacia el segundo con tela blanda.
     */
    public static void dibujarEnMalla(ItemStack item, Correa.Superficie sup, MallaCapturada malla,
                                      VertexConsumerProvider vcp, int luz, Matrix3f marco) {
        List<Correa> lista = item.get(ModamodComponents.CORREAS);
        if (lista == null || lista.isEmpty() || malla.vacia()) return;
        float s = malla.escala();
        MatrixStack ms = new MatrixStack();
        for (Correa c : lista) {
            if (c.superficie() != sup) continue;
            float espesor = espesorDe(c) * s;
            float w = c.ancho() * s;
            float fuera = 0.05f * s + espesor / 2f;
            List<Nodo> camino = new ArrayList<>();
            MallaCapturada.Ubicacion a = malla.enUv(c.desde().x() / 64f, c.desde().y() / 64f);
            MallaCapturada.Ubicacion b = malla.enUv(c.hasta().x() / 64f, c.hasta().y() / 64f);
            if (c.modo() == ModoCorrea.PEGADA) {
                final int n = 20;
                for (int k = 0; k <= n; k++) {
                    float t = k / (float) n;
                    MallaCapturada.Ubicacion u = malla.enUv((c.desde().x() + (c.hasta().x() - c.desde().x()) * t) / 64f,
                            (c.desde().y() + (c.hasta().y() - c.desde().y()) * t) / 64f);
                    if (u == null) continue;
                    camino.add(new Nodo(new Vector3f(u.pos()).add(new Vector3f(u.normal()).mul(fuera / 16f)).mul(16f), new Vector3f(u.normal())));
                }
            } else if (a != null && c.modo().usaLargo()) {
                // Anclada / colgada sobre la tela (2026-10-06): la gravedad y la inercia en el marco de la malla.
                Vector3f pa = new Vector3f(a.pos()).add(new Vector3f(a.normal()).mul(fuera / 16f)).mul(16f);
                Vector3f g = marco.transform(new Vector3f(0, 1, 0)).normalize();
                Vector3f incl = new Vector3f();
                FisicaApliques.Desplazamiento dd = FisicaApliques.actual;
                if (dd != null && c.blandura() > 0f) {
                    incl = marco.transform(new Vector3f(dd.blando())).mul(8f * c.blandura());
                    if (incl.length() > 5f) incl.normalize(5f);
                    incl.div(5f);
                }
                List<Vector3f> curva;
                if (c.modo() == ModoCorrea.ANCLADA || b == null) {
                    curva = curvaAnclada(pa, c.largo() * s, g, incl);
                } else {
                    Vector3f pb = new Vector3f(b.pos()).add(new Vector3f(b.normal()).mul(fuera / 16f)).mul(16f);
                    curva = curvaColgada(pa, pb, c.largo() * s, g, incl, a.normal());
                }
                // Siempre hacia afuera de la tela (2026-10-06, "que los straps cuelguen hacia afuera de la pollera en lugar
                // de hacia adentro"): cuanto más baja, más se separa por la normal, como el vuelo de la falda.
                for (Vector3f q : curva) {
                    float d = q.distance(pa);
                    camino.add(new Nodo(new Vector3f(q).add(new Vector3f(a.normal()).mul(Math.min(4f * s, 0.3f * d))), new Vector3f(a.normal())));
                }
            } else if (a != null) {
                Vector3f pa = new Vector3f(a.pos()).add(new Vector3f(a.normal()).mul(fuera / 16f)).mul(16f);
                Vector3f pb = b != null ? new Vector3f(b.pos()).add(new Vector3f(b.normal()).mul(fuera / 16f)).mul(16f)
                        : new Vector3f(pa).add(new Vector3f(a.arriba()).mul(-8f * s));
                Vector3f desp = new Vector3f();
                FisicaApliques.Desplazamiento d = FisicaApliques.actual;
                if (d != null && c.blandura() > 0f) {
                    desp = marco.transform(new Vector3f(d.blando())).mul(8f * c.blandura() * s);
                    if (desp.length() > 5f * s) desp.normalize(5f * s);
                }
                final int k = 12;
                for (int i = 0; i <= k; i++) {
                    float t = i / (float) k;
                    camino.add(new Nodo(new Vector3f(pa).lerp(pb, t).add(new Vector3f(desp).mul(t * t)), new Vector3f(a.normal())));
                }
            }
            camino = remuestrear(camino, pasoDe(c, c.ancho()) * s);
            if (camino.size() >= 2) emitir(c, camino, w, espesor, ms.peek(), vcp, luz);
        }
    }

    private static Vector3f punto(Correa.Punto p, float e) {
        Direction d = p.cara();
        return new Vector3f(p.x() + d.getOffsetX() * e, p.y() + d.getOffsetY() * e, p.z() + d.getOffsetZ() * e);
    }

    // ── caminos ──────────────────────────────────────────────────────────

    private static List<Nodo> rutaPegada(Vector3f a, Direction ca, Vector3f b, Direction cb, Sup sup) {
        List<Nodo> out = new ArrayList<>();
        float[] mn = sup.bmn, mx = sup.bmx;
        if (ca.getOpposite() == cb) {
            // Caras opuestas: por la tercera cara más corta (la recta pasaría por el medio de la caja).
            Vector3f medio = new Vector3f(a).add(b).mul(0.5f);
            Vector3f mejorQ = null;
            float mejorLargo = Float.MAX_VALUE;
            for (Direction d : Direction.values()) {
                if (d.getAxis() == ca.getAxis()) continue;
                Vector3f q = new Vector3f(medio);
                int eje = d.getAxis().ordinal();
                q.setComponent(eje, d.getDirection() == Direction.AxisDirection.POSITIVE ? mx[eje] : mn[eje]);
                for (int i = 0; i < 3; i++) q.setComponent(i, Math.max(mn[i], Math.min(mx[i], q.get(i))));
                float largo = a.distance(q) + q.distance(b);
                if (largo < mejorLargo) {
                    mejorLargo = largo;
                    mejorQ = q;
                }
            }
            tramo(out, a, mejorQ, sup);
            tramo(out, mejorQ, b, sup);
        } else {
            tramo(out, a, b, sup);
        }
        return out;
    }

    /** La recta de {@code a} a {@code b} muestreada y proyectada sobre la superficie. */
    private static void tramo(List<Nodo> out, Vector3f a, Vector3f b, Sup sup) {
        final int n = 16;
        for (int k = 0; k <= n; k++) {
            if (k == 0 && !out.isEmpty()) continue;
            Vector3f p = new Vector3f(a).lerp(b, k / (float) n);
            out.add(sup.proyectar(p, true));
        }
    }

    private static List<Nodo> rutaColgante(Vector3f a, Vector3f b, Sup sup, float blandura,
                                           Matrix3f normalLocal, Matrix3f marco) {
        Vector3f desp = new Vector3f();
        FisicaApliques.Desplazamiento d = FisicaApliques.actual;
        if (d != null && blandura > 0f) {
            // Del marco del cuerpo al de esta parte (las dos son rotaciones: la inversa es la transpuesta).
            Matrix3f aLocal = new Matrix3f(normalLocal).transpose().mul(marco);
            desp = aLocal.transform(new Vector3f(d.blando())).mul(8f * blandura);
            if (desp.length() > 5f) desp.normalize(5f);
        }
        final int k = 12;
        List<Nodo> out = new ArrayList<>();
        for (int i = 0; i <= k; i++) {
            float s = i / (float) k;
            Vector3f p = new Vector3f(a).lerp(b, s).add(new Vector3f(desp).mul(s * s));
            out.add(sup.proyectar(p, false));
        }
        return out;
    }

    /** El vaivén por la inercia como una inclinación (módulo hasta 1) que se suma a la gravedad. */
    private static Vector3f inclinacion(float blandura, Matrix3f aLocal) {
        Vector3f v = new Vector3f();
        FisicaApliques.Desplazamiento d = FisicaApliques.actual;
        if (d != null && blandura > 0f) {
            v = aLocal.transform(new Vector3f(d.blando())).mul(8f * blandura);
            if (v.length() > 5f) v.normalize(5f);
            v.div(5f);
        }
        return v;
    }

    /**
     * ANCLADA: {@code largo} px de cadena desde {@code a} hacia abajo ({@code g}); cada tramo se inclina un poco más con
     * la inercia, así la cadena se curva como un péndulo y mantiene su largo.
     */
    private static List<Vector3f> curvaAnclada(Vector3f a, float largo, Vector3f g, Vector3f inclinacion) {
        final int k = 16;
        List<Vector3f> out = new ArrayList<>();
        Vector3f p = new Vector3f(a);
        out.add(new Vector3f(p));
        for (int i = 1; i <= k; i++) {
            float s = i / (float) k;
            Vector3f dir = new Vector3f(g).add(new Vector3f(inclinacion).mul(s)).normalize();
            p.add(dir.mul(largo / k));
            out.add(new Vector3f(p));
        }
        return out;
    }

    /**
     * COLGADA: una parábola de {@code largo} px entre {@code a} y {@code b} que cuelga hacia la gravedad (si la cuerda
     * va casi en vertical, hacia afuera de la cara del primer punto). El largo mínimo es la distancia: tensa, sin curva.
     */
    private static List<Vector3f> curvaColgada(Vector3f a, Vector3f b, float largo, Vector3f g, Vector3f inclinacion, Vector3f afuera) {
        Vector3f c = new Vector3f(b).sub(a);
        float dist = c.length();
        if (dist < 1e-3f) return curvaAnclada(a, largo, g, inclinacion);
        Vector3f ch = new Vector3f(c).div(dist);
        Vector3f gp = new Vector3f(g).sub(new Vector3f(ch).mul(g.dot(ch)));
        float glen = gp.length();
        Vector3f dirSag = new Vector3f(gp);
        if (glen > 1e-4f) dirSag.div(glen);
        if (glen < 0.25f) {
            Vector3f o = new Vector3f(afuera).sub(new Vector3f(ch).mul(afuera.dot(ch)));
            if (o.lengthSquared() < 1e-6f) o = Math.abs(ch.y) < 0.9f ? new Vector3f(0, -1, 0) : new Vector3f(0, 0, -1);
            o.sub(new Vector3f(ch).mul(o.dot(ch))).normalize();
            dirSag = new Vector3f(gp).add(o.mul(1f - glen * 4f)).normalize();
        }
        float total = Math.max(largo, dist);
        // La flecha h de la parábola con ese largo de arco (por bisección: la fórmula chica falla con cuerdas muy flojas).
        float lo = 0f, hi = Math.max(dist, total);
        for (int it = 0; it < 24; it++) {
            float h = (lo + hi) / 2f;
            if (largoParabola(dist, h) < total) lo = h; else hi = h;
        }
        float h = total <= dist * 1.0005f ? 0f : (lo + hi) / 2f;
        final int k = 20;
        List<Vector3f> out = new ArrayList<>();
        for (int i = 0; i <= k; i++) {
            float t = i / (float) k;
            float comba = 4f * t * (1f - t);
            Vector3f p = new Vector3f(a).add(new Vector3f(c).mul(t))
                    .add(new Vector3f(dirSag).mul(h * comba))
                    .add(new Vector3f(inclinacion).mul(comba * Math.min(h + 1f, 4f)));
            out.add(p);
        }
        return out;
    }

    private static float largoParabola(float dist, float h) {
        final int m = 24;
        float suma = 0f, yAnt = 0f;
        for (int i = 1; i <= m; i++) {
            float t = i / (float) m;
            float y = 4f * h * t * (1f - t);
            suma += (float) Math.hypot(dist / m, y - yAnt);
            yAnt = y;
        }
        return suma;
    }

    /**
     * El punto {@code p} sobre la superficie de la caja: con {@code pegar}, también si está afuera; sin él, solo si
     * está adentro (lo de afuera queda libre y su normal apunta lejos de la caja).
     */
    private static Nodo proyectar(Vector3f p, float[] mn, float[] mx, boolean pegar) {
        final float eps = 1e-4f;
        boolean dentro = true;
        for (int i = 0; i < 3; i++) if (p.get(i) <= mn[i] + eps || p.get(i) >= mx[i] - eps) dentro = false;
        Vector3f q = new Vector3f(p), nrm = new Vector3f();
        if (dentro) {
            float mejor = Float.MAX_VALUE;
            int eje = 0;
            float lado = 0f;
            for (int i = 0; i < 3; i++) {
                float dMin = p.get(i) - mn[i], dMax = mx[i] - p.get(i);
                if (dMin < mejor) { mejor = dMin; eje = i; lado = -1f; }
                if (dMax < mejor) { mejor = dMax; eje = i; lado = 1f; }
            }
            q.setComponent(eje, lado < 0 ? mn[eje] : mx[eje]);
            nrm.setComponent(eje, lado);
        } else {
            Vector3f c = new Vector3f();
            for (int i = 0; i < 3; i++) c.setComponent(i, Math.max(mn[i], Math.min(mx[i], p.get(i))));
            for (int i = 0; i < 3; i++) {
                if (c.get(i) <= mn[i] + eps) nrm.setComponent(i, nrm.get(i) - 1f);
                if (c.get(i) >= mx[i] - eps) nrm.setComponent(i, nrm.get(i) + 1f);
            }
            if (pegar) {
                q = c;
            } else {
                Vector3f lejos = new Vector3f(p).sub(c);
                if (lejos.lengthSquared() > 1e-8f) nrm = lejos;
            }
        }
        if (nrm.lengthSquared() < 1e-8f) nrm.set(0, -1, 0);
        return new Nodo(q, nrm.normalize());
    }

    /** Nodos a distancias parejas ({@code paso} aproximado) a lo largo del camino. */
    private static List<Nodo> remuestrear(List<Nodo> in, float paso) {
        List<Nodo> out = new ArrayList<>();
        if (in.size() < 2) return out;
        float[] acum = new float[in.size()];
        for (int i = 1; i < in.size(); i++) acum[i] = acum[i - 1] + in.get(i).pos().distance(in.get(i - 1).pos());
        float total = acum[in.size() - 1];
        if (total < 1e-4f) return out;
        int n = Math.max(1, Math.round(total / paso));
        int seg = 1;
        for (int k = 0; k <= n; k++) {
            float dist = total * k / n;
            while (seg < in.size() - 1 && acum[seg] < dist) seg++;
            float t0 = acum[seg - 1], t1 = acum[seg];
            float t = t1 - t0 < 1e-6f ? 0f : (dist - t0) / (t1 - t0);
            Nodo n0 = in.get(seg - 1), n1 = in.get(seg);
            Vector3f pos = new Vector3f(n0.pos()).lerp(n1.pos(), t);
            Vector3f nrm = new Vector3f(n0.normal()).lerp(n1.normal(), t);
            if (nrm.lengthSquared() < 1e-8f) nrm.set(n0.normal());
            out.add(new Nodo(pos, nrm.normalize()));
        }
        return out;
    }

    // ── dibujo ───────────────────────────────────────────────────────────

    private static void emitir(Correa c, List<Nodo> nodos, float w, float espesor, MatrixStack.Entry e,
                               VertexConsumerProvider vcp, int luz) {
        int banda = c.colores().get(0), borde = c.colores().get(1), herraje = c.colores().get(2);
        int n = nodos.size();
        Vector3f[] t = new Vector3f[n], nn = new Vector3f[n], bb = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            Vector3f d = new Vector3f(nodos.get(Math.min(n - 1, i + 1)).pos()).sub(nodos.get(Math.max(0, i - 1)).pos());
            if (d.lengthSquared() < 1e-8f) d.set(1, 0, 0);
            t[i] = d.normalize();
            Vector3f nrm = new Vector3f(nodos.get(i).normal());
            nrm.sub(new Vector3f(t[i]).mul(nrm.dot(t[i])));
            if (nrm.lengthSquared() < 1e-6f) {
                // La tira va a lo largo de la normal: cualquier perpendicular sirve.
                nrm = Math.abs(t[i].y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
                nrm.sub(new Vector3f(t[i]).mul(nrm.dot(t[i])));
            }
            nn[i] = nrm.normalize();
            bb[i] = new Vector3f(t[i]).cross(nn[i]).normalize();
        }
        switch (c.estilo()) {
            case LISA, OJALILLOS -> {
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(texturaCorrea(c.estilo(), banda, borde)));
                cinta(vc, e, luz, nodos, bb, nn, w / 2f, 0f, 1f);
            }
            case CORDON -> {
                float wc = Math.max(0.8f, w * 0.55f);
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(SombreroRenderer.textura(banda, SombreroPatron.LISO)));
                cinta(vc, e, luz, nodos, bb, nn, wc / 2f, 0.5f, 0.5f);
                cinta(vc, e, luz, nodos, nn, bb, wc / 2f, 0.5f, 0.5f);
                // Las puntas (aglets): una caja de herraje en cada extremo.
                VertexConsumer vm = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(SombreroRenderer.textura(herraje, SombreroPatron.LISO)));
                for (int i : new int[] {0, n - 1}) {
                    if (i == 0 && (c.modo() == ModoCorrea.COLGANTE || c.modo() == ModoCorrea.ANCLADA)) continue;     // el de arriba está sujeto
                    Vector3f dir = new Vector3f(t[i]).mul(i == 0 ? -1f : 1f);
                    Vector3f centro = new Vector3f(nodos.get(i).pos()).add(new Vector3f(dir).mul(0.7f));
                    caja(vm, e, luz, centro, t[i], bb[i], nn[i], 0.9f, wc * 0.65f, wc * 0.65f);
                }
            }
            case CADENA -> {
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(SombreroRenderer.textura(herraje, SombreroPatron.LISO)));
                for (int i = 0; i < n; i++) {
                    boolean par = i % 2 == 0;
                    // Eslabones alternados 90°: uno de plano, el otro de canto.
                    caja(vc, e, luz, nodos.get(i).pos(), t[i], bb[i], nn[i], w * 0.85f,
                            par ? w * 0.5f : w * 0.22f, par ? w * 0.22f : w * 0.5f);
                }
            }
            case CADENA_FINA -> {
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(SombreroRenderer.textura(herraje, SombreroPatron.LISO)));
                float lado = Math.max(1f, w * 0.75f) / 2f;
                for (int i = 0; i < n; i++) {
                    caja(vc, e, luz, nodos.get(i).pos(), t[i], bb[i], nn[i], lado, lado, lado);
                }
            }
        }
    }

    /** Una cinta de ancho 2·{@code medio} siguiendo los nodos; {@code lados} es el vector de costado de cada nodo. */
    private static void cinta(VertexConsumer vc, MatrixStack.Entry e, int luz, List<Nodo> nodos, Vector3f[] lados,
                              Vector3f[] normales, float medio, float u0, float u1) {
        for (int i = 0; i + 1 < nodos.size(); i++) {
            Vector3f a = nodos.get(i).pos(), b = nodos.get(i + 1).pos();
            float ua = u0 == u1 ? u0 : 0f, ub = u0 == u1 ? u1 : 1f;
            vertice(vc, e, luz, new Vector3f(a).add(new Vector3f(lados[i]).mul(medio)), normales[i], ua, 0f, u0 == u1);
            vertice(vc, e, luz, new Vector3f(a).sub(new Vector3f(lados[i]).mul(medio)), normales[i], ub, 0f, u0 == u1);
            vertice(vc, e, luz, new Vector3f(b).sub(new Vector3f(lados[i + 1]).mul(medio)), normales[i + 1], ub, 1f, u0 == u1);
            vertice(vc, e, luz, new Vector3f(b).add(new Vector3f(lados[i + 1]).mul(medio)), normales[i + 1], ua, 1f, u0 == u1);
        }
    }

    private static void vertice(VertexConsumer vc, MatrixStack.Entry e, int luz, Vector3f p, Vector3f nrm,
                                float u, float v, boolean plano) {
        // Textura lisa: todos los vértices leen el mismo texel (el centro del lienzo).
        vc.vertex(e.getPositionMatrix(), p.x / 16f, p.y / 16f, p.z / 16f)
                .color(0xFFFFFFFF)
                .texture(plano ? 0.5f : u, plano ? 0.5f : v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(luz)
                .normal(e, nrm.x, nrm.y, nrm.z);
    }

    /** Una caja orientada (ejes unitarios {@code ex}, {@code ey}, {@code ez}; medidas medias {@code hx}, {@code hy}, {@code hz}) en px. */
    private static void caja(VertexConsumer vc, MatrixStack.Entry e, int luz, Vector3f centro, Vector3f ex, Vector3f ey,
                             Vector3f ez, float hx, float hy, float hz) {
        Vector3f[] ejes = {ex, ey, ez};
        float[] h = {hx, hy, hz};
        for (int ax = 0; ax < 3; ax++) {
            int u = (ax + 1) % 3, v = (ax + 2) % 3;
            for (int s = -1; s <= 1; s += 2) {
                Vector3f cara = new Vector3f(centro).add(new Vector3f(ejes[ax]).mul(s * h[ax]));
                Vector3f nrm = new Vector3f(ejes[ax]).mul(s);
                Vector3f du = new Vector3f(ejes[u]).mul(h[u]), dv = new Vector3f(ejes[v]).mul(h[v]);
                // Sentido antihorario visto desde afuera (el layer no descarta caras, pero la luz lo agradece).
                int[][] esq = s > 0 ? new int[][] {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}} : new int[][] {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
                for (int[] q : esq) {
                    Vector3f p = new Vector3f(cara).add(new Vector3f(du).mul(q[0])).add(new Vector3f(dv).mul(q[1]));
                    vertice(vc, e, luz, p, nrm, 0.5f, 0.5f, true);
                }
            }
        }
    }

    // ── texturas de las cintas ───────────────────────────────────────────

    private static final Map<String, Identifier> TEXTURAS = new HashMap<>();

    /** 16x16: la banda al centro, el borde a los lados, puntadas claras y (ojalillos) dos agujeros. */
    private static Identifier texturaCorrea(EstiloCorrea estilo, int banda, int borde) {
        String clave = estilo.clave + "_" + Integer.toHexString(banda) + "_" + Integer.toHexString(borde);
        Identifier id = TEXTURAS.get(clave);
        if (id != null) return id;
        NativeImage img = new NativeImage(16, 16, true);
        int cb = abgr(banda), ce = abgr(borde), cp = abgr(SombreroPatron.tonoDeContraste(banda)), co = abgr(0x201408);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int col = (x < 2 || x > 13) ? ce : cb;
                if ((x == 3 || x == 12) && y % 4 < 2) col = cp;
                if (estilo == EstiloCorrea.OJALILLOS && x >= 7 && x <= 8 && ((y >= 3 && y <= 4) || (y >= 11 && y <= 12))) col = co;
                img.setColor(x, y, col);
            }
        }
        id = Identifier.of("modamod", "dynamic/correa_" + clave);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
        TEXTURAS.put(clave, id);
        return id;
    }

    private static int abgr(int rgb) {
        return 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
    }
}
