package com.modamod.render.relieve;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * El busto como geometría propia (2026-10-01, "cambiaria los pechos buscando
 * mas la estrategia del mod only jugs"): volumen real que sale del pecho, con
 * la textura de esa zona (la piel, o la prenda de encima con su patrón y sus
 * fotos). Reemplaza al busto del relieve, que se superponía con lo pintado en
 * las máscaras.
 *
 * <p>Forma (2026-10-02, "me gustan mas los pechos con volumen real pero me
 * gustaria que fueran mas redondeados"): dos cúpulas — media elipse de frente,
 * más llena abajo que arriba — que nacen pegadas al pecho. La caída, la
 * apertura hacia los costados y el rebote ({@link FisicaBusto}) no giran la
 * cúpula: la deforman según lo que sobresale cada punto, así el borde queda
 * siempre apoyado en el pecho, sin rendijas.
 *
 * <p>Quién la dibuja:
 * <ul>
 *   <li>{@code GarmentFeatureRenderer}: la piel y cada pieza del torso que
 *       tape el pecho, inflada lo que esa tela está por fuera de la de abajo
 *       (las telas holgadas caen más abajo, como una carpa);</li>
 *   <li>{@code mixin/BustoEnModelosMixin} (2026-10-02, "armar un algo que
 *       adapte las prendas y armaduras de cualquier mod (quizas con armaduras
 *       pueden mantenerse rigidas"): cualquier modelo humanoide que se dibuje
 *       sobre el jugador (armaduras, cosméticos, ropa de otros mods), con las
 *       UV de su propio torso ({@link Uv#deCaraDelTorso}); las armaduras,
 *       rígidas.</li>
 * </ul>
 * Los apliques del pecho se apoyan en la cúpula ({@link #sobreBusto}) y la
 * Mesa de estilado los apunta con {@link #rayo}.
 *
 * <p>Coordenadas: las del torso del modelo (px, Y hacia abajo, el frente en
 * −Z), después de {@code ModelPart.rotate} del torso.
 */
public final class BustoRender {

    private BustoRender() {}

    /** El busto del jugador que se está dibujando. */
    public record Busto(float talle, float sujecion, @Nullable RelieveRender.Rebote rebote, boolean cuadrado,
                        float cola, @Nullable RelieveRender.Rebote reboteCola) {
        /** Con la cola que corresponde a este talle (2026-10-02, "el trasero deberia crecer con el busto"). */
        public Busto(float talle, float sujecion, @Nullable RelieveRender.Rebote rebote, boolean cuadrado) {
            this(talle, sujecion, rebote, cuadrado, RelieveCuerpo.colaDeTalle(talle, 0.5f), null);
        }

        public Busto(float talle, float sujecion, @Nullable RelieveRender.Rebote rebote) {
            this(talle, sujecion, rebote, false);
        }
    }

    /** El de la ropa del mod (lo pone {@code GarmentFeatureRenderer} mientras dibuja). */
    @Nullable
    public static Busto actual;

    /**
     * Fila del torso donde arranca el busto (px desde los hombros): bien
     * arriba, para que baje en pendiente hasta la punta (2026-10-02, "integrar
     * el inicio del pecho mas arriba para que caiga hasta la punta mejor").
     */
    private static final float ARRIBA = 0.8f;
    /** Anillos (del centro al borde) y gajos alrededor de cada cúpula. */
    private static final int ANILLOS = 7, GAJOS = 18;
    /**
     * Exponente del perfil: 2 = media elipse; más, más lleno hasta el borde.
     * Abajo es redondo ({@link #PERFIL_ABAJO}); hacia arriba baja a
     * {@link #PERFIL_ARRIBA}, una pendiente suave que nace del pecho.
     */
    private static final float PERFIL_ABAJO = 2.4f, PERFIL_ARRIBA = 1.45f;

    /** ¿Una pieza que cubre las filas [desde, hasta) del torso tapa el busto? */
    public static boolean cubre(int desde, int hasta) {
        return desde <= 2 && hasta >= 5;
    }

    /** De dónde sale la textura: un punto (x, y) del frente del torso → UV normalizadas. */
    public interface Uv {
        float u(float x);
        float v(float y);

        /** El layout de la skin (piel y telas del mod): frente del torso en u 20..28, v desde {@code fila}. */
        static Uv skin(float fila) {
            return new Uv() {
                public float u(float x) { return Math.max(20f, Math.min(28f, 24f + x)) / 64f; }
                public float v(float y) { return Math.max(fila, Math.min(fila + 12f, fila + y)) / 64f; }
            };
        }

        /**
         * La espalda del torso en el layout de la skin (u 32..40, v desde
         * {@code fila}), en x del MUNDO: x = +4 es u 32 (la espalda se ve desde atrás).
         */
        static Uv skinEspalda(float fila) {
            return new Uv() {
                public float u(float x) { return Math.max(32f, Math.min(40f, 36f - x)) / 64f; }
                public float v(float y) { return Math.max(fila, Math.min(fila + 12f, fila + y)) / 64f; }
            };
        }

        /** Esta misma, con x espejada (el marco de la cola mira a +Z: x local = −x del mundo). */
        default Uv espejada() {
            Uv base = this;
            return new Uv() {
                public float u(float x) { return base.u(-x); }
                public float v(float y) { return base.v(y); }
            };
        }

        /**
         * Las UV de la cara del frente del torso de cualquier modelo (otro mod,
         * otra textura, otro tamaño): lineales entre los bordes de esa cara.
         * {@code x0..x1}/{@code y0..y1} son los bordes de la cara en px y
         * {@code u0..u1}/{@code v0..v1} sus UV en esos bordes (pueden ir al revés).
         */
        static Uv deCaraDelTorso(float x0, float x1, float u0, float u1, float y0, float y1, float v0, float v1) {
            return new Uv() {
                public float u(float x) {
                    float k = Math.max(0f, Math.min(1f, (x - x0) / (x1 - x0)));
                    return u0 + (u1 - u0) * k;
                }
                public float v(float y) {
                    float k = Math.max(0f, Math.min(1f, (y - y0) / (y1 - y0)));
                    return v0 + (v1 - v0) * k;
                }
            };
        }
    }

    /** La forma de una cúpula ya calculada para este talle, inflado y rebote. */
    private record Forma(float cx, float cy, float rx, float rArriba, float rAbajo, float hondo,
                         float caida, float apertura, float dx, float dy, float plano, int lado) {

        /** Punto de la cúpula en el anillo {@code r} (0 centro .. 1 borde) y el ángulo {@code a}. */
        Vector3f punto(float r, double a) {
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float x = cx + rx * r * c;
            float y = cy + (s > 0 ? rAbajo : rArriba) * r * s;
            float sale = sale(r, a);
            float k = hondo <= 1e-4f ? 0f : sale / hondo;          // 0 en el borde, 1 en la punta
            // Lo que sobresale cae, se abre hacia afuera y rebota; la base no se mueve.
            y += caida * sale + dy * k;
            x += lado * apertura * sale + dx * k;
            return new Vector3f(x, y, plano - sale);
        }

        /**
         * Dónde se ve el punto de frente con la caída y la apertura pero sin el
         * rebote: de ahí salen las UV (2026-10-02, "la ropa interior... deforma
         * por ahi y el bretel del pecho no coincide con la tapa"): proyectadas
         * de frente sobre la forma ya caída, un bretel sigue derecho y empalma
         * con el del pecho plano; el rebote no hace correr la textura.
         */
        float[] vistoDeFrente(float r, double a) {
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float sale = sale(r, a);
            float x = cx + rx * r * c + lado * apertura * sale;
            float y = cy + (s > 0 ? rAbajo : rArriba) * r * s + caida * sale;
            return new float[]{x, y};
        }

        /** Cuánto sobresale en el anillo {@code r} hacia el ángulo {@code a}: 0 en el borde. */
        float sale(float r, double a) {
            // Arriba (sen < 0, Y hacia abajo) en pendiente; abajo redondo.
            float arriba = (float) Math.max(0.0, -Math.sin(a));
            float p = PERFIL_ABAJO + (PERFIL_ARRIBA - PERFIL_ABAJO) * arriba;
            return hondo * (float) Math.pow(Math.max(0.0, 1.0 - Math.pow(Math.min(1f, r), p)), 1.0 / p);
        }

        /** El punto de la base (sin caída ni rebote) del anillo {@code r} y el ángulo {@code a}. */
        float baseX(float r, double a) { return cx + rx * r * (float) Math.cos(a); }
        float baseY(float r, double a) {
            float s = (float) Math.sin(a);
            return cy + (s > 0 ? rAbajo : rArriba) * r * s;
        }

        /** (r, ángulo) del punto de la base (x, y); r > 1 = afuera de la cúpula. */
        float[] polar(float x, float y) {
            float u = (x - cx) / rx;
            float dyb = y - cy;
            float v = dyb / (dyb > 0 ? rAbajo : rArriba);
            return new float[]{(float) Math.sqrt(u * u + v * v), (float) Math.atan2(v, u)};
        }

        Vector3f normal(float r, double a) {
            float er = 0.02f;
            float rr = Math.max(r, 0.02f);
            Vector3f dr = punto(Math.min(1f, r + er), a).sub(punto(Math.max(0f, r - er), a));
            Vector3f da = punto(rr, a + 0.05).sub(punto(rr, a - 0.05));
            Vector3f nn = dr.cross(da);
            if (nn.z > 0) nn.negate();
            if (nn.lengthSquared() < 1e-10f) nn.set(0, 0, -1);
            return nn.normalize();
        }
    }

    /**
     * Las dos cúpulas (derecha, izquierda) de este busto con este inflado.
     *
     * <p>{@code piel}: la cúpula del cuerpo (o de la skin), que con un inflado
     * negativo se achica por dentro de la tela más ajustada. Si no, es una
     * TELA (2026-10-02, "con el hoodie se deberian tapar en vez de verse
     * gigantes"): cubre la punta del busto apenas por fuera (más afuera
     * cuanto más holgada, así las capas no se pisan) y se abre más ancha en la
     * base; lo que sobresale de su plano es solo lo que el busto pasa de la
     * holgura de la prenda. Antes la cúpula de la tela se inflaba entera y una
     * prenda Oversize sumaba su holgura al busto.
     */
    private static Forma[] formas(Busto b, float inflado, float carpa, boolean rigido, boolean piel) {
        float t = b.talle() * b.sujecion();
        if (t <= 0.05f) return new Forma[0];
        float dy = 0f, dx = 0f;
        if (b.rebote() != null && !rigido) {
            dy = b.rebote().dy() * 12f;
            dx = b.rebote().dx() * 8f;
        }
        float cuerpo = 0.55f + 0.5f * t;                      // lo que sale el busto de la piel
        float hondo;
        float abrir = Math.max(0f, inflado);
        if (piel) {
            // La punta queda en su lugar aunque el plano salga (la piel por delante de la
            // 2.ª capa plana); con inflado negativo se achica por dentro de la tela.
            hondo = cuerpo - Math.max(0f, inflado);
            abrir = 0f;
        } else {
            float punta = Math.max(cuerpo + 0.06f + 0.12f * abrir, inflado);
            hondo = Math.max(0f, punta - inflado);             // por delante del plano de la tela
        }
        float rx = Math.min(2.6f, 1.95f + 0.07f * t) + 0.5f * abrir;
        float rArriba = 2.3f + 0.16f * t + 0.5f * abrir;
        float rAbajo = 1.3f + 0.22f * t + abrir + carpa;
        float caida = (0.06f + 0.025f * t) * (0.6f + 0.4f * b.sujecion());
        float apertura = 0.04f + 0.012f * t;
        if (rigido) {
            // Armadura: un peto moldeado, firme (2026-10-02, "con armaduras pueden mantenerse rigidas").
            caida *= 0.25f;
            apertura *= 0.5f;
        }
        Forma[] f = new Forma[2];
        for (int i = 0; i < 2; i++) {
            int lado = i == 0 ? -1 : 1;
            f[i] = new Forma(lado * 2f, ARRIBA + 2.3f + 0.16f * t, rx, rArriba, rAbajo, hondo,
                    caida, apertura, dx, dy * 0.6f, -2f - inflado, lado);
        }
        return f;
    }

    /**
     * Desde qué carpa la tela holgada ya no marca dos cúpulas sino un solo
     * manto (2026-10-02, "se marca demasiado en el hoodie no se deberian ver
     * asi como dos tetas"): Suelto y Oversize. Normal sigue marcando las dos.
     */
    private static final float CARPA_MANTO = 1f;
    /** Celdas por px de la grilla del manto. */
    private static final int PASO = 2;

    /**
     * Peso (0..1) con que una prenda de arriba tiene que acompañar el busto a la altura {@code y} del torso (px desde el hombro):
     * 1 en toda la cupula (de su centro, {@code ARRIBA + 2,3 + 0,16·t}, hasta sus radios) y bajando hasta 0 un poco mas alla.
     * Una rampa lineal por el medio dejaba los bordes de las cupulas, que son casi planos, atravesando la prenda (2026-10-19).
     */
    public static float pesoDeAltura(Busto b, float y) {
        float t = b.talle() * b.sujecion();
        float cy = ARRIBA + 2.3f + 0.16f * t, ra = 2.3f + 0.16f * t, rb = 1.3f + 0.22f * t;
        float d = y < cy ? (cy - y) / ra : (y - cy) / rb;      // 1 = el borde de la cupula
        return d <= 1f ? 1f : Math.max(0f, 1f - (d - 1f) / 0.6f);
    }

    /** ¿Una tela con esta carpa tapa el busto con un manto (Suelto, Oversize)? */
    public static boolean esManto(float carpa) {
        return carpa >= CARPA_MANTO;
    }

    /** La carpa de la tela de cada calce: cuánto más abajo cae debajo del busto (px). */
    public static float carpaDe(@Nullable com.modamod.item.Calce calce) {
        if (calce == null) return 0f;
        return switch (calce) {
            case NORMAL -> 0.4f;
            case SUELTO -> 1.5f;
            case OVERSIZE -> 2.5f;
            default -> 0f;
        };
    }

    /**
     * Cuánto sale la tela por fuera del cuerpo en cada fila del torso (la
     * dilatación de la pieza, con su caída hacia el ruedo): el manto se apoya
     * en esa superficie y termina en el ruedo de la pieza.
     */
    public record Tela(float[] fila, int desde, int hasta, int abertura) {
        /** Sin apertura en el frente. */
        public Tela(float[] fila, int desde, int hasta) { this(fila, desde, hasta, 0); }

        float en(float y) {
            if (hasta - desde <= 1) return fila[Math.max(0, desde)];
            float yy = Math.max(desde + 0.5f, Math.min(hasta - 0.5f, y)) - 0.5f;
            int f0 = (int) Math.floor(yy), f1 = Math.min(f0 + 1, hasta - 1);
            float k = yy - f0;
            return fila[f0] + (fila[f1] - fila[f0]) * k;
        }

        static Tela uniforme(float inflado) {
            float[] f = new float[12];
            java.util.Arrays.fill(f, inflado);
            return new Tela(f, 0, 12, 0);
        }
    }

    /**
     * Un vértice de la malla: posición, normal, de qué punto del frente sale
     * la textura ({@code ux, uy}) y qué punto del frente PLANO tiene debajo
     * ({@code bx, by}: con eso se apoyan y se apuntan los apliques).
     */
    private record Vert(Vector3f pos, Vector3f normal, float ux, float uy, float bx, float by) {}

    /**
     * La malla del busto (cuadriláteros), según el estilo y la tela:
     * <ul>
     *   <li>cajas, si el busto es cuadrado (2026-10-02, "agreguemos un
     *       selector de tetas cuadradas en el selector de skin");</li>
     *   <li>un manto, si es una tela holgada (carpa ≥ {@link #CARPA_MANTO});</li>
     *   <li>si no, las dos cúpulas.</li>
     * </ul>
     */
    private static List<Vert[]> malla(Busto b, float inflado, float carpa, boolean rigido, boolean piel,
                                      @Nullable Tela tela, float fondo) {
        if (b.cuadrado()) return comprimirAbajo(cajas(b, inflado, carpa, rigido, piel), fondo);
        Forma[] fs = formas(b, inflado, piel || carpa < CARPA_MANTO ? carpa : 0f, rigido, piel);
        if (fs.length == 0) return List.of();
        if (!piel && carpa >= CARPA_MANTO) return manto(fs, inflado, carpa, tela == null ? Tela.uniforme(inflado) : tela);
        List<Vert[]> q = new ArrayList<>();
        for (Forma f : fs) cupula(f, q);
        return comprimirAbajo(q, fondo);
    }

    /**
     * Si la textura de lo que cubre el busto termina antes que el busto
     * ({@code fondo}: la última fila con tela — el corpiño de la ropa
     * interior, un croptop), la mitad de abajo de la textura se aprieta hasta
     * esa fila (2026-10-02, "la parte de abajo de los senos no esta cubierta
     * por el corpiño"): el corpiño cubre el pecho entero y su borde queda en
     * el pliegue de abajo, como uno de verdad. Sin eso, lo que el busto baja
     * por debajo del corpiño plano mostraba la piel.
     */
    private static List<Vert[]> comprimirAbajo(List<Vert[]> quads, float fondo) {
        if (quads.isEmpty() || fondo >= 12f) return quads;
        float arriba = Float.MAX_VALUE, abajo = -Float.MAX_VALUE;
        for (Vert[] q : quads) for (Vert v : q) { arriba = Math.min(arriba, v.uy()); abajo = Math.max(abajo, v.uy()); }
        float fin = fondo - 0.05f;
        if (abajo <= fin) return quads;
        // Se aprieta la mitad de abajo (desde el medio del busto); si el corpiño
        // termina más arriba del medio, desde un poco antes de su borde.
        float desde = Math.min((arriba + abajo) * 0.5f, fin - 0.5f);
        float k = (fin - desde) / (abajo - desde);
        List<Vert[]> salida = new ArrayList<>(quads.size());
        for (Vert[] q : quads) {
            Vert[] n = new Vert[q.length];
            for (int i = 0; i < q.length; i++) {
                Vert v = q[i];
                float uy = v.uy() <= desde ? v.uy() : desde + (v.uy() - desde) * k;
                n[i] = new Vert(v.pos(), v.normal(), v.ux(), uy, v.bx(), v.by());
            }
            salida.add(n);
        }
        return salida;
    }

    /**
     * La piel y la ropa del mod (layout de la skin).
     *
     * @param inflado  cuánto sale la tela por fuera del cuerpo en el pecho (px; 0 = la piel)
     * @param carpa    cuánto más abajo cae la tela holgada debajo del busto (px)
     * @param filaSkin fila de la skin donde empieza el frente del torso: 20 (la
     *                 piel y todas las telas) o 36 (la segunda capa de la skin)
     * @param tela     la dilatación de la pieza por fila (para el manto); null = uniforme
     */
    public static void dibujar(Busto b, MatrixStack matrices, VertexConsumer vc, int luz, int ov,
                               float inflado, float carpa, float filaSkin, boolean piel, @Nullable Tela tela) {
        dibujar(b, matrices, vc, luz, ov, inflado, carpa, filaSkin, piel, tela, tela == null ? 12f : tela.hasta());
    }

    /** @param fondo última fila del torso con textura de lo que cubre el busto (ver {@link #comprimirAbajo}) */
    public static void dibujar(Busto b, MatrixStack matrices, VertexConsumer vc, int luz, int ov,
                               float inflado, float carpa, float filaSkin, boolean piel, @Nullable Tela tela,
                               float fondo) {
        dibujar(b, matrices, vc, luz, ov, 0xFFFFFFFF, Uv.skin(filaSkin), inflado, carpa, false, piel, tela, fondo);
    }

    public static void dibujar(Busto b, MatrixStack matrices, VertexConsumer vc, int luz, int ov,
                               float inflado, float carpa, float filaSkin, boolean piel) {
        dibujar(b, matrices, vc, luz, ov, inflado, carpa, filaSkin, piel, null);
    }

    /** Con cualquier mapeo de textura, color y rigidez (modelos de otros mods y armaduras: tela, no piel). */
    public static void dibujar(Busto b, MatrixStack matrices, VertexConsumer vc, int luz, int ov, int color,
                               Uv uv, float inflado, float carpa, boolean rigido) {
        dibujar(b, matrices, vc, luz, ov, color, uv, inflado, carpa, rigido, false, null, 12f);
    }

    private static void dibujar(Busto b, MatrixStack matrices, VertexConsumer vc, int luz, int ov, int color,
                                Uv uv, float inflado, float carpa, boolean rigido, boolean piel, @Nullable Tela tela,
                                float fondo) {
        emitir(malla(b, inflado, carpa, rigido, piel, tela, fondo), matrices, vc, luz, ov, color, uv);
    }

    private static void emitir(List<Vert[]> malla, MatrixStack matrices, VertexConsumer vc, int luz, int ov, int color,
                               Uv uv) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        Matrix3f n = matrices.peek().getNormalMatrix();
        for (Vert[] q : malla) {
            for (Vert v : q) vertice(m, n, vc, luz, ov, color, v.pos(), uv.u(v.ux()), uv.v(v.uy()), v.normal());
        }
    }

    // ── cúpulas ──────────────────────────────────────────────────────────

    private static void cupula(Forma f, List<Vert[]> salida) {
        Vert[][] g = new Vert[ANILLOS + 1][GAJOS + 1];
        for (int i = 0; i <= ANILLOS; i++) {
            float r = i / (float) ANILLOS;
            for (int j = 0; j <= GAJOS; j++) {
                double a = Math.PI * 2 * j / GAJOS;
                float[] frente = f.vistoDeFrente(r, a);
                g[i][j] = new Vert(f.punto(r, a), f.normal(r, a), frente[0], frente[1], f.baseX(r, a), f.baseY(r, a));
            }
        }
        for (int i = 0; i < ANILLOS; i++) {
            for (int j = 0; j < GAJOS; j++) {
                salida.add(new Vert[]{g[i][j], g[i + 1][j], g[i + 1][j + 1], g[i][j + 1]});
            }
        }
    }

    // ── manto ────────────────────────────────────────────────────────────

    /**
     * Una tela holgada sobre el busto (2026-10-02, "se marca demasiado en el
     * hoodie no se deberian ver asi como dos tetas"): la tela no entra entre
     * los pechos ni los envuelve uno por uno; queda tirante de una punta a la
     * otra y cae derecho desde las puntas hasta el ruedo, como un estante.
     *
     * <p>Un campo de alturas sobre el frente del torso: se calcan las dos
     * cúpulas (con su caída y su rebote) y se tensa la tela por filas y por
     * columnas (envolvente cóncava, {@link #tensar}), con los bordes pegados a
     * la superficie de la pieza ({@link Tela}). La textura va proyectada de
     * frente: el estampado de la prenda no se corre.
     */
    private static List<Vert[]> manto(Forma[] fs, float inflado, float carpa, Tela tela) {
        float y0 = tela.desde(), y1 = tela.hasta();
        int nx = 8 * PASO, ny = Math.round((y1 - y0) * PASO);
        if (ny < 2) return List.of();
        float[][] h = new float[nx + 1][ny + 1];
        int anillos = ANILLOS * 4, gajos = GAJOS * 3;
        for (Forma f : fs) {
            for (int i = 0; i <= anillos; i++) {
                float r = i / (float) anillos;
                for (int j = 0; j < gajos; j++) {
                    Vector3f p = f.punto(r, Math.PI * 2 * j / gajos);
                    int ix = Math.round((p.x + 4f) * PASO), iy = Math.round((p.y - y0) * PASO);
                    if (ix < 0 || ix > nx || iy < 0 || iy > ny) continue;
                    // Lo que sale por delante de la superficie de la pieza en esa fila.
                    float sale = (f.plano() - p.z) - (tela.en(p.y) - inflado);
                    if (sale > h[ix][iy]) h[ix][iy] = sale;
                }
            }
        }
        // Bordes pegados a la pieza: así no quedan rendijas con los costados ni con el ruedo.
        for (int ix = 0; ix <= nx; ix++) { h[ix][0] = 0f; h[ix][ny] = 0f; }
        for (int iy = 0; iy <= ny; iy++) { h[0][iy] = 0f; h[nx][iy] = 0f; }
        // Frente abierto (2026-10-08, "cortar el manto del busto en el centro de la apertura"): la franja del medio no
        // lleva tela, y cada mitad se tensa por separado, pegada a la pieza en el canto de la apertura.
        int corte = tela.abertura() > 0 ? Math.round(tela.abertura() * 0.5f * PASO) : 0;
        int mitad = nx / 2;
        int izqFin = mitad - corte, derIni = mitad + corte;
        if (corte > 0) for (int iy = 0; iy <= ny; iy++) {
            for (int ix = izqFin; ix <= derIni; ix++) h[ix][iy] = 0f;
        }
        float[] fila = new float[nx + 1];
        for (int iy = 0; iy <= ny; iy++) {
            if (corte > 0) {
                float[] izq = new float[izqFin + 1], der = new float[nx - derIni + 1];
                for (int ix = 0; ix <= izqFin; ix++) izq[ix] = h[ix][iy];
                for (int ix = derIni; ix <= nx; ix++) der[ix - derIni] = h[ix][iy];
                tensar(izq);
                tensar(der);
                for (int ix = 0; ix <= izqFin; ix++) h[ix][iy] = izq[ix];
                for (int ix = derIni; ix <= nx; ix++) h[ix][iy] = der[ix - derIni];
                continue;
            }
            for (int ix = 0; ix <= nx; ix++) fila[ix] = h[ix][iy];
            tensar(fila);
            for (int ix = 0; ix <= nx; ix++) h[ix][iy] = fila[ix];
        }
        for (int ix = 0; ix <= nx; ix++) tensar(h[ix]);
        // Cuanto más holgada, más chato: la tela sobrante se lo come (Suelto ~0.88, Oversize ~0.65).
        float chato = 1f - 0.35f * Math.max(0f, Math.min(1f, (carpa - CARPA_MANTO) / 1.5f));

        Vert[][] g = new Vert[nx + 1][ny + 1];
        float[][] z = new float[nx + 1][ny + 1];
        for (int ix = 0; ix <= nx; ix++) {
            for (int iy = 0; iy <= ny; iy++) {
                h[ix][iy] *= chato;
                z[ix][iy] = -2f - tela.en(y0 + iy / (float) PASO) - h[ix][iy];
            }
        }
        for (int ix = 0; ix <= nx; ix++) {
            for (int iy = 0; iy <= ny; iy++) {
                float x = -4f + ix / (float) PASO, y = y0 + iy / (float) PASO;
                int a = Math.max(0, ix - 1), c = Math.min(nx, ix + 1);
                int d = Math.max(0, iy - 1), e = Math.min(ny, iy + 1);
                float fx = (z[c][iy] - z[a][iy]) / ((c - a) / (float) PASO);
                float fy = (z[ix][e] - z[ix][d]) / ((e - d) / (float) PASO);
                Vector3f nor = new Vector3f(fx, fy, -1f).normalize();
                g[ix][iy] = new Vert(new Vector3f(x, y, z[ix][iy]), nor, x, y, x, y);
            }
        }
        List<Vert[]> q = new ArrayList<>();
        for (int ix = 0; ix < nx; ix++) {
            for (int iy = 0; iy < ny; iy++) {
                // Lo que queda pegado a la pieza ya lo dibuja la pieza (y así no pelean en Z).
                if (corte > 0 && ix >= izqFin && ix + 1 <= derIni) continue; // la apertura
                if (h[ix][iy] <= 1e-4f && h[ix + 1][iy] <= 1e-4f && h[ix + 1][iy + 1] <= 1e-4f && h[ix][iy + 1] <= 1e-4f) continue;
                q.add(new Vert[]{g[ix][iy], g[ix + 1][iy], g[ix + 1][iy + 1], g[ix][iy + 1]});
            }
        }
        return q;
    }

    /** La envolvente cóncava de arriba de {@code h} (puntos equiespaciados): la tela tirante. */
    private static void tensar(float[] h) {
        int n = h.length;
        int[] pila = new int[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2) {
                int a = pila[k - 2], b = pila[k - 1];
                // b queda por debajo (o sobre) la recta a→i: sobra.
                if ((h[b] - h[a]) * (i - a) <= (h[i] - h[a]) * (b - a)) k--;
                else break;
            }
            pila[k++] = i;
        }
        for (int s = 0; s + 1 < k; s++) {
            int a = pila[s], b = pila[s + 1];
            for (int i = a + 1; i < b; i++) h[i] = h[a] + (h[b] - h[a]) * (i - a) / (float) (b - a);
        }
    }

    // ── cajas ────────────────────────────────────────────────────────────

    /**
     * El busto cuadrado (2026-10-02, "agreguemos un selector de tetas
     * cuadradas en el selector de skin"): una caja por pecho, de 4 px de ancho
     * (la mitad del torso), que sale del pecho y se inclina hacia abajo con la
     * caída, al estilo cúbico de Minecraft. Mismo talle, sujeción y rebote que
     * las cúpulas. Las telas la envuelven apenas por fuera; las holgadas
     * (carpa ≥ {@link #CARPA_MANTO}) en una sola caja que cae en carpa hasta
     * el pecho. La textura va proyectada de frente: el frente lleva el pecho,
     * la tapa y los costados estiran la fila o la columna del borde.
     */
    private static List<Vert[]> cajas(Busto b, float inflado, float carpa, boolean rigido, boolean piel) {
        float t = b.talle() * b.sujecion();
        if (t <= 0.05f) return List.of();
        float cuerpo = 0.55f + 0.5f * t;
        float abrir = Math.max(0f, inflado);
        float hondo;
        if (piel) {
            hondo = cuerpo - Math.max(0f, inflado);
            abrir = 0f;
        } else {
            hondo = Math.max(0f, Math.max(cuerpo + 0.06f + 0.12f * abrir, inflado) - inflado);
        }
        if (hondo <= 0.01f) return List.of();
        float dy = 0f, dx = 0f;
        if (b.rebote() != null && !rigido) {
            dy = b.rebote().dy() * 12f * 0.6f;
            dx = b.rebote().dx() * 8f;
        }
        float plano = -2f - inflado;
        // Como el Female Gender Mod (2026-10-04, "investiga como hace el jenys mod y el female gender mod... porque
        // tus tetas salieron horribles"): una cuña que cuelga de su arista de arriba, pegada al pecho; la cara de
        // adelante baja en pendiente hasta la punta y la de abajo vuelve al pecho.
        float arriba = ARRIBA - 0.25f * abrir;
        float largo = 4f + 0.4f * t + 0.3f * abrir;
        // Cada capa es más ancha que la de abajo (2026-10-04, "piel con remera, en el lado del costado": los
        // triángulos de los costados de la piel y de la tela caían en el mismo plano x = ±4 y se peleaban): la
        // tela sale por el costado lo que está inflada y la rendija del medio se le cierra de a poco.
        float lado = Math.max(0f, inflado) + 0.02f * Math.max(0f, inflado);
        float rendija = Math.max(0.02f, SEPARACION - 0.5f * Math.max(0f, inflado));
        List<Vert[]> q = new ArrayList<>();
        if (!piel && carpa >= CARPA_MANTO) {
            float chato = 1f - 0.35f * Math.max(0f, Math.min(1f, (carpa - CARPA_MANTO) / 1.5f));
            cuna(q, -4f - lado, 4f + lado, arriba, largo, plano, hondo * chato, 0f, dy, carpa * 1.5f, rigido);
        } else {
            float colgar = piel ? 0f : carpa;
            // Una rendija entre los dos (2026-10-04, "q se note la division de los pechos").
            cuna(q, -4f - lado, -rendija, arriba, largo, plano, hondo, dx, dy, colgar, rigido);
            cuna(q, rendija, 4f + lado, arriba, largo, plano, hondo, dx, dy, colgar, rigido);
        }
        return q;
    }

    /** Mitad de la rendija entre los dos pechos cuadrados (px). */
    private static final float SEPARACION = 0.25f;

    /**
     * Una cuña de x0..x1 (la forma del Female Gender Mod: una caja que cuelga de su arista de arriba, pegada al
     * pecho, girada hacia adelante; 2026-10-04, "dos cubos inclinados en que lados a bajan suave por el pecho
     * hasta la punta y de ahí lado b se inserta en el pecho"). Perfil: A (arriba, en el plano del pecho) →
     * B (la punta, {@code hondo} px por delante) → C (abajo, otra vez en el plano). Se dibujan el frente A-B, la
     * base B-C y los dos costados triangulares; la tapa y el fondo quedan adentro del pecho.
     *
     * @param largo  de A a C, sobre el pecho (px); {@code colgar} baja C (la tela cae en carpa hasta el pecho)
     * @param hondo  cuánto sale la punta
     * @param rigido armaduras: menos inclinadas
     */
    private static void cuna(List<Vert[]> q, float x0, float x1, float arriba, float largo, float plano, float hondo,
                             float dx, float dy, float colgar, boolean rigido) {
        if (x1 - x0 < 0.05f || hondo <= 0.01f) return;
        // La punta sale hondo = alto·sen θ y C queda a alto/cos θ: con alto = largo·cos θ, hondo = largo·sen θ·cos θ.
        float s2 = Math.min(1f, 2f * hondo / largo);
        float giro = Math.min((float) Math.toRadians(42f), 0.5f * (float) Math.asin(s2)) * (rigido ? 0.6f : 1f)
                + dy * 0.04f;
        giro = Math.max(0.05f, giro);
        float co = (float) Math.cos(giro), si = (float) Math.sin(giro);
        float alto = largo * co;
        float yB = arriba + alto * co, zB = plano - alto * si, yC = arriba + largo + colgar;
        float[] xs = {x0, x1};
        Vector3f[] a = new Vector3f[2], b = new Vector3f[2], c = new Vector3f[2];
        float[][] uvA = new float[2][], uvB = new float[2][], uvC = new float[2][];
        for (int k = 0; k < 2; k++) {
            a[k] = new Vector3f(xs[k] + dx, arriba + dy, plano);
            b[k] = new Vector3f(xs[k] + dx, yB + dy, zB);
            c[k] = new Vector3f(xs[k] + dx, yC + dy, plano);
            // Textura de frente, sin el rebote: lo que se ve desde adelante.
            uvA[k] = new float[]{xs[k], arriba};
            uvB[k] = new float[]{xs[k], yB};
            uvC[k] = new float[]{xs[k], yC};
        }
        // Frente (A-B) y base (B-C).
        quad(q, new Vector3f(0, -0.5f, -1), new Vector3f[]{a[0], a[1], b[1], b[0]}, new float[][]{uvA[0], uvA[1], uvB[1], uvB[0]});
        quad(q, new Vector3f(0, 1, -0.5f), new Vector3f[]{b[0], b[1], c[1], c[0]}, new float[][]{uvB[0], uvB[1], uvC[1], uvC[0]});
        // Costados: el triángulo A-B-C (el último vértice se repite).
        quad(q, new Vector3f(-1, 0, 0), new Vector3f[]{a[0], b[0], c[0], c[0]}, new float[][]{uvA[0], uvB[0], uvC[0], uvC[0]});
        quad(q, new Vector3f(1, 0, 0), new Vector3f[]{a[1], b[1], c[1], c[1]}, new float[][]{uvA[1], uvB[1], uvC[1], uvC[1]});
    }

    /** Un cuadrilátero (o triángulo con el último vértice repetido) con la normal sacada de sus vértices, hacia {@code afuera}. */
    private static void quad(List<Vert[]> q, Vector3f afuera, Vector3f[] p, float[][] uv) {
        Vector3f nor = new Vector3f(p[2]).sub(p[0]).cross(new Vector3f(p[3]).sub(p[1]));
        if (nor.lengthSquared() < 1e-10f) return;
        boolean alReves = nor.dot(afuera) > 0;
        if (nor.dot(afuera) < 0) nor.negate();
        nor.normalize();
        Vert[] v = new Vert[4];
        for (int i = 0; i < 4; i++) v[i] = new Vert(p[i], nor, uv[i][0], uv[i][1], uv[i][0], uv[i][1]);
        // Las cúpulas giran con cruz(lado1, lado2) hacia ADENTRO.
        q.add(alReves ? new Vert[]{v[3], v[2], v[1], v[0]} : v);
    }

    private static void vertice(Matrix4f m, Matrix3f n, VertexConsumer vc, int luz, int ov, int color,
                                Vector3f p, float u, float v, Vector3f normal) {
        Vector3f w = m.transformPosition(p.x / 16f, p.y / 16f, p.z / 16f, new Vector3f());
        Vector3f nn = n.transform(new Vector3f(normal)).normalize();
        vc.vertex(w.x, w.y, w.z, color, u, v, ov, luz, nn.x, nn.y, nn.z);
    }

    // ── cola ─────────────────────────────────────────────────────────────

    /**
     * Las dos nalgas (2026-10-02, "el trasero deberia crecer con el busto y
     * tener tambien fisica"): las mismas cúpulas que el busto, en la espalda
     * del torso (filas ~8 a 12), con su propio resorte ({@link Busto#reboteCola}:
     * rebotan alternadas con cada paso). Crecen con el talle del busto
     * ({@code RelieveCuerpo.colaDe}). Reemplazan a las nalgas del relieve
     * cuando hay busto.
     *
     * <p>Se arman en un marco espejado que mira a +Z (x y z dados vuelta, el
     * giro de 180° alrededor de Y): así sirve toda la maquinaria del busto
     * tal cual — piel/tela, carpa, cajas.
     */
    private static Forma[] formasCola(Busto b, float inflado, float carpa, boolean rigido, boolean piel) {
        float cuerpo = b.cola();
        if (cuerpo <= 0.05f) return new Forma[0];
        float dy = 0f, alterno = 0f;
        if (b.reboteCola() != null && !rigido) {
            dy = b.reboteCola().dy() * 12f;
            alterno = b.reboteCola().dx() * 8f;
        }
        float abrir = Math.max(0f, inflado);
        float hondo;
        if (piel) {
            hondo = cuerpo - Math.max(0f, inflado);
            abrir = 0f;
        } else {
            float punta = Math.max(cuerpo + 0.06f + 0.12f * abrir, inflado);
            hondo = Math.max(0f, punta - inflado);
        }
        float rx = Math.min(2.4f, 1.9f + 0.12f * cuerpo) + 0.4f * abrir;
        float rArriba = 1.9f + 0.25f * cuerpo + 0.3f * abrir;
        float rAbajo = 1.5f + 0.1f * cuerpo + 0.4f * abrir + 0.5f * carpa;
        float caida = (0.05f + 0.02f * cuerpo) * (rigido ? 0.25f : 1f);
        float apertura = 0.03f * (rigido ? 0.5f : 1f);
        float cy = 10.1f - 0.12f * cuerpo;
        Forma[] f = new Forma[2];
        for (int i = 0; i < 2; i++) {
            int lado = i == 0 ? -1 : 1;
            // Cada paso sube una y baja la otra.
            f[i] = new Forma(lado * 2f, cy, rx, rArriba, rAbajo, hondo, caida, apertura, 0f,
                    (dy + lado * alterno) * 0.6f, -2f - inflado, lado);
        }
        return f;
    }

    /** ¿Una pieza que cubre las filas [desde, hasta) del torso pasa por la cola? */
    public static boolean cubreCola(int desde, int hasta) {
        return hasta > 8 && desde < 12;
    }

    private static List<Vert[]> mallaCola(Busto b, float inflado, float carpa, boolean rigido, boolean piel) {
        return mallaCola(b, inflado, carpa, rigido, piel, 0f);
    }

    /** @param techo primera fila de la ropa interior de abajo en la espalda (0 = sin ver, ver {@link #comprimirArriba}) */
    private static List<Vert[]> mallaCola(Busto b, float inflado, float carpa, boolean rigido, boolean piel, float techo) {
        if (b.cuadrado()) return comprimirArriba(cajasCola(b, inflado, carpa, rigido, piel), techo);
        List<Vert[]> q = new ArrayList<>();
        for (Forma f : formasCola(b, inflado, carpa, rigido, piel)) cupula(f, q);
        return comprimirArriba(q, techo);
    }

    /**
     * Como {@link #comprimirAbajo}, para arriba (2026-10-02, "acomoda la ropa
     * interior a las nalgas"): la bombacha plana empieza en la fila 9 de la
     * espalda (el boxer en la 8) y la cola arranca más arriba, así que la
     * mitad de arriba de la textura se aprieta desde {@code techo}: la ropa
     * interior cubre la cola entera y su elástico queda donde la cola nace.
     */
    private static List<Vert[]> comprimirArriba(List<Vert[]> quads, float techo) {
        if (quads.isEmpty() || techo <= 0f) return quads;
        float arriba = Float.MAX_VALUE, abajo = -Float.MAX_VALUE;
        for (Vert[] q : quads) for (Vert v : q) { arriba = Math.min(arriba, v.uy()); abajo = Math.max(abajo, v.uy()); }
        float inicio = techo + 0.05f;
        if (arriba >= inicio) return quads;
        float hasta = Math.max((arriba + abajo) * 0.5f, inicio + 0.5f);
        float k = (hasta - inicio) / (hasta - arriba);
        List<Vert[]> salida = new ArrayList<>(quads.size());
        for (Vert[] q : quads) {
            Vert[] n = new Vert[q.length];
            for (int i = 0; i < q.length; i++) {
                Vert v = q[i];
                float uy = v.uy() >= hasta ? v.uy() : hasta - (hasta - v.uy()) * k;
                n[i] = new Vert(v.pos(), v.normal(), v.ux(), uy, v.bx(), v.by());
            }
            salida.add(n);
        }
        return salida;
    }

    /** La cola cuadrada: una caja por nalga, de la fila 8 hasta abajo del torso. */
    private static List<Vert[]> cajasCola(Busto b, float inflado, float carpa, boolean rigido, boolean piel) {
        float cuerpo = b.cola();
        if (cuerpo <= 0.05f) return List.of();
        float abrir = Math.max(0f, inflado);
        float hondo = piel ? cuerpo - Math.max(0f, inflado)
                : Math.max(0f, Math.max(cuerpo + 0.06f + 0.12f * abrir, inflado) - inflado);
        if (hondo <= 0.01f) return List.of();
        float dy = 0f, alterno = 0f;
        if (b.reboteCola() != null && !rigido) {
            dy = b.reboteCola().dy() * 12f * 0.6f;
            alterno = b.reboteCola().dx() * 8f * 0.6f;
        }
        float plano = -2f - inflado;
        float arriba = 8.2f - 0.2f * abrir, largo = 3.8f + 0.4f * abrir;
        float colgar = piel ? 0f : carpa * 0.5f;
        // Las mismas cuñas que el busto (2026-10-04, "quizas aplicable a las nalgas"), con una rendija más fina.
        List<Vert[]> q = new ArrayList<>();
        float lado = 1.02f * Math.max(0f, inflado);
        float rendija = Math.max(0.02f, SEPARACION * 0.6f - 0.5f * Math.max(0f, inflado));
        cuna(q, -4f - lado, -rendija, arriba, largo, plano, hondo, 0f, dy - alterno, colgar, rigido);
        cuna(q, rendija, 4f + lado, arriba, largo, plano, hondo, 0f, dy + alterno, colgar, rigido);
        return q;
    }

    /**
     * La cola con {@code uv} en x del MUNDO (como la da {@link Uv#skinEspalda}
     * o la cara de atrás de otro modelo), en el marco del torso.
     */
    public static void dibujarCola(Busto b, MatrixStack matrices, VertexConsumer vc, int luz, int ov, int color,
                                   Uv uv, float inflado, float carpa, boolean rigido, boolean piel) {
        dibujarCola(b, matrices, vc, luz, ov, color, uv, inflado, carpa, rigido, piel, 0f);
    }

    private static void dibujarCola(Busto b, MatrixStack matrices, VertexConsumer vc, int luz, int ov, int color,
                                    Uv uv, float inflado, float carpa, boolean rigido, boolean piel, float techo) {
        List<Vert[]> malla = mallaCola(b, inflado, carpa, rigido, piel, techo);
        if (malla.isEmpty()) return;
        matrices.push();
        // Marco espejado: el "frente" de la cola es la espalda del torso.
        matrices.scale(-1f, 1f, -1f);
        emitir(malla, matrices, vc, luz, ov, color, uv.espejada());
        matrices.pop();
    }

    /** Con el layout de la skin (piel o telas del mod). */
    public static void dibujarCola(Busto b, MatrixStack matrices, VertexConsumer vc, int luz, int ov,
                                   float inflado, float carpa, float filaSkin, boolean piel) {
        dibujarCola(b, matrices, vc, luz, ov, inflado, carpa, filaSkin, piel, 0f);
    }

    /** @param techo primera fila de la ropa interior de abajo en la espalda (0 = no la hay) */
    public static void dibujarCola(Busto b, MatrixStack matrices, VertexConsumer vc, int luz, int ov,
                                   float inflado, float carpa, float filaSkin, boolean piel, float techo) {
        dibujarCola(b, matrices, vc, luz, ov, 0xFFFFFFFF, Uv.skinEspalda(filaSkin), inflado, carpa, false, piel, techo);
    }

    /**
     * Dónde queda, sobre la cola, el punto (x, y) de la espalda plana del
     * torso (los apliques de la espalda): como {@link #sobreBusto}, en
     * coordenadas del torso. Null si no cae sobre la cola.
     */
    @Nullable
    public static Punto sobreCola(Busto b, float x, float y, float inflado, float carpa) {
        Punto p = sobre(mallaCola(b, inflado, carpa, false, false), -x, y);
        if (p == null) return null;
        return new Punto(new Vector3f(-p.pos().x, p.pos().y, -p.pos().z),
                new Vector3f(-p.normal().x, p.normal().y, -p.normal().z));
    }

    /** {@link #rayo} contra la cola: {t, x, y} con (x, y) el punto de la espalda plana de debajo. */
    @Nullable
    public static float[] rayoCola(Busto b, Vector3f origen, Vector3f dir, float inflado, float carpa) {
        float[] h = rayo(mallaCola(b, inflado, carpa, false, false),
                new Vector3f(-origen.x, origen.y, -origen.z), new Vector3f(-dir.x, dir.y, -dir.z));
        if (h != null) h[1] = -h[1];
        return h;
    }

    // ── apliques ─────────────────────────────────────────────────────────

    /** Un punto de la superficie del busto y su normal (px, coordenadas del torso). */
    public record Punto(Vector3f pos, Vector3f normal) {}

    /**
     * Dónde queda, sobre el busto, el punto (x, y) del frente plano del torso
     * (2026-10-02, "hay que contemplar los pechos para los apliques"): el
     * aplique se guarda en el frente plano y acá se apoya en el busto (y
     * rebota con él). Null si el punto no cae sobre el busto — o si no hay
     * busto: la prenda sigue sirviendo igual sin él.
     */
    @Nullable
    public static Punto sobreBusto(Busto b, float x, float y, float inflado) {
        return sobreBusto(b, x, y, inflado, 0f);
    }

    /** Con la carpa de la tela (las holgadas son un manto, ver {@link #carpaDe}). */
    @Nullable
    public static Punto sobreBusto(Busto b, float x, float y, float inflado, float carpa) {
        return sobre(malla(b, inflado, carpa, false, false, null, 12f), x, y);
    }

    /**
     * La superficie del busto para muchas consultas seguidas (la malla se arma una sola vez): de (x, y) del frente plano
     * al punto de la cúpula, o null. La usa el embozo de la capa para pasar por encima (2026-10-25).
     */
    public static java.util.function.BiFunction<Float, Float, Punto> superficie(Busto b, float inflado) {
        List<Vert[]> m = malla(b, inflado, 0f, false, false, null, 12f);
        return (x, y) -> sobre(m, x, y);
    }

    /** El punto de la malla que tiene debajo el punto (x, y) del frente plano (el de más adelante), o null. */
    @Nullable
    private static Punto sobre(List<Vert[]> malla, float x, float y) {
        Punto mejor = null;
        for (Vert[] q : malla) {
            for (int[] tri : TRIANGULOS) {
                Vert a = q[tri[0]], c = q[tri[1]], d = q[tri[2]];
                // Baricéntricas del punto en el triángulo del frente plano (las caras de costado se aplastan: se saltean).
                float e1x = c.bx() - a.bx(), e1y = c.by() - a.by(), e2x = d.bx() - a.bx(), e2y = d.by() - a.by();
                float det = e1x * e2y - e2x * e1y;
                if (Math.abs(det) < 1e-6f) continue;
                float px = x - a.bx(), py = y - a.by();
                float w1 = (px * e2y - e2x * py) / det, w2 = (e1x * py - px * e1y) / det;
                if (w1 < -1e-4f || w2 < -1e-4f || w1 + w2 > 1f + 1e-4f) continue;
                float w0 = 1f - w1 - w2;
                Vector3f pos = new Vector3f(a.pos()).mul(w0).add(new Vector3f(c.pos()).mul(w1)).add(new Vector3f(d.pos()).mul(w2));
                if (mejor != null && pos.z >= mejor.pos().z) continue;     // el más de adelante
                Vector3f nor = new Vector3f(a.normal()).mul(w0).add(new Vector3f(c.normal()).mul(w1))
                        .add(new Vector3f(d.normal()).mul(w2));
                if (nor.lengthSquared() < 1e-10f) nor.set(0, 0, -1);
                mejor = new Punto(pos, nor.normalize());
            }
        }
        return mejor;
    }

    private static final int[][] TRIANGULOS = {{0, 1, 2}, {0, 2, 3}};

    /**
     * Un rayo contra el busto, en px del torso (2026-10-02, "se puede las
     * dos? cosa que si armo la prenda sin pechos despues siga sirviendo?"):
     * devuelve {t, x, y} del choque más cercano al origen (t en el largo de
     * {@code dir}), con (x, y) el punto del frente PLANO que está debajo — eso
     * es lo que se guarda en el aplique. Null si no le pega.
     */
    @Nullable
    public static float[] rayo(Busto b, Vector3f origen, Vector3f dir, float inflado) {
        return rayo(b, origen, dir, inflado, 0f);
    }

    /** Con la carpa de la tela (las holgadas son un manto, ver {@link #carpaDe}). */
    @Nullable
    public static float[] rayo(Busto b, Vector3f origen, Vector3f dir, float inflado, float carpa) {
        return rayo(malla(b, inflado, carpa, false, false, null, 12f), origen, dir);
    }

    @Nullable
    private static float[] rayo(List<Vert[]> malla, Vector3f origen, Vector3f dir) {
        float[] mejor = null;
        for (Vert[] q : malla) {
            for (int[] tri : TRIANGULOS) {
                Vert a = q[tri[0]], c = q[tri[1]], d = q[tri[2]];
                float[] k = triangulo(origen, dir, a.pos(), c.pos(), d.pos());
                if (k == null || (mejor != null && k[0] >= mejor[0])) continue;
                float w1 = k[1], w2 = k[2], w0 = 1f - w1 - w2;
                mejor = new float[]{k[0], w0 * a.bx() + w1 * c.bx() + w2 * d.bx(), w0 * a.by() + w1 * c.by() + w2 * d.by()};
            }
        }
        return mejor;
    }

    /** Möller–Trumbore: {t, u, v} o null. */
    @Nullable
    private static float[] triangulo(Vector3f o, Vector3f d, Vector3f a, Vector3f b, Vector3f c) {
        Vector3f e1 = new Vector3f(b).sub(a), e2 = new Vector3f(c).sub(a);
        Vector3f p = new Vector3f(d).cross(e2);
        float det = e1.dot(p);
        if (Math.abs(det) < 1e-9f) return null;
        float inv = 1f / det;
        Vector3f s = new Vector3f(o).sub(a);
        float u = s.dot(p) * inv;
        if (u < 0f || u > 1f) return null;
        Vector3f q = new Vector3f(s).cross(e1);
        float v = d.dot(q) * inv;
        if (v < 0f || u + v > 1f) return null;
        float t = e2.dot(q) * inv;
        return t < 0f ? null : new float[]{t, u, v};
    }
}
