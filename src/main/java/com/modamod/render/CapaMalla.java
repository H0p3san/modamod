package com.modamod.render;

import com.modamod.item.CapaItem;
import com.modamod.item.CapaRuedo;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * La capa del mod (2026-09-29, "capas... como las capas vanilla (misma
 * dinamica de tela) pero usable y personalizable").
 *
 * <h2>Dinámica</h2>
 * La MISMA cuenta que {@code CapeFeatureRenderer} de la 1.21.1: se inclina
 * hacia atrás según la velocidad (0..150°), sube y baja con los saltos
 * (-6..32), se balancea de costado (±20°), acompaña el paso y suma 25°
 * agachado. {@link #giros} la reproduce paso a paso.
 *
 * <h2>Tela</h2>
 * El paño usa el mismo mapeo que el cuboide de la capa vanilla (10x16x1 en
 * uv 0,0): el EXTERIOR es la cara del frente del cuboide (u 1..11, v 1..17,
 * la que queda mirando hacia atrás), el FORRO la de atrás (u 12..22). El
 * largo real se estira sobre esos 16 de alto. Capucha y cuello alto sacan la
 * tela de una segunda caja (12x8x2 en uv 24,0; ver {@code PatronGenerador}).
 *
 * <p>Coordenadas en píxeles, en el espacio del cuboide vanilla (antes de los
 * giros): x -5..5, y hacia abajo desde los hombros, exterior en z=-1 y forro
 * en z=0.
 */
public final class CapaMalla {

    private CapaMalla() {}

    private static final int COLS = 10, FILAS = 8;

    /**
     * Lo que la capa vanilla lee del jugador para moverse, ya acotado igual
     * que en {@code CapeFeatureRenderer}: {@code atras} = velocidad hacia
     * adelante (0..150), {@code vertical} = caída/subida (-6..32, sin el paso),
     * {@code lado} = velocidad de costado (-20..20), {@code paso} = el vaivén
     * de la caminata (-32..32). También lo usa la pollera ({@link PolleraMalla}).
     */
    public record Movimiento(float atras, float vertical, float lado, float paso) {
        public static final Movimiento QUIETO = new Movimiento(0f, 0f, 0f, 0f);
    }

    public static Movimiento movimiento(AbstractClientPlayerEntity p, float h) {
        double d = MathHelper.lerp(h, p.prevCapeX, p.capeX) - MathHelper.lerp(h, p.prevX, p.getX());
        double e = MathHelper.lerp(h, p.prevCapeY, p.capeY) - MathHelper.lerp(h, p.prevY, p.getY());
        double m = MathHelper.lerp(h, p.prevCapeZ, p.capeZ) - MathHelper.lerp(h, p.prevZ, p.getZ());
        float n = MathHelper.lerpAngleDegrees(h, p.prevBodyYaw, p.bodyYaw);
        double o = MathHelper.sin(n * 0.017453292F);
        double q0 = -MathHelper.cos(n * 0.017453292F);
        float q = MathHelper.clamp((float) e * 10.0F, -6.0F, 32.0F);
        float r = MathHelper.clamp((float) (d * o + m * q0) * 100.0F, 0.0F, 150.0F);
        float s = MathHelper.clamp((float) (d * q0 - m * o) * 100.0F, -20.0F, 20.0F);
        float t = MathHelper.lerp(h, p.prevStrideDistance, p.strideDistance);
        float paso = MathHelper.sin(MathHelper.lerp(h, p.prevHorizontalSpeed, p.horizontalSpeed) * 6.0F) * 32.0F * t;
        return new Movimiento(r, q, s, paso);
    }

    // ── la tela doblada ─────────────────────────────────────────────────
    // 2026-09-30, "es una placa tiesa, no hay render de tela": la capa
    // vanilla es un cuboide rígido que gira entero desde los hombros, y esta
    // la copiaba. Ahora el paño es una cadena de TRAMOS tramos: arriba sigue a
    // la espalda y cada tramo se inclina un poco más que el anterior (la tela
    // se curva y el ruedo es lo que más se levanta), con una onda que baja
    // por la tela al moverse, un vaivén suave quieta, el balanceo de costado,
    // y cada tramo se abre lo necesario para no atravesar las piernas.
    // Sin estado: todo sale de la inercia de la capa vanilla y del tiempo.

    private static final int TRAMOS = 16;
    private static final float[] NODO_Y = new float[TRAMOS + 1], NODO_Z = new float[TRAMOS + 1], NODO_A = new float[TRAMOS + 1];
    private static float tramo = 1f, corrimientoLado = 0f;
    private static final float[] MUESTRAS_X = {-4.5f, -3f, -1.5f, 0f, 1.5f, 3f, 4.5f};

    /**
     * Arma la cadena para este frame. Coordenadas en píxeles en el marco de la
     * capa (el de {@code CapeFeatureRenderer} después de su corrimiento de
     * 2 px hacia atrás): y hacia abajo, z hacia atrás.
     *
     * @param piernas las piernas llevadas a este marco, o null
     */
    private static void doblar(float largo, Movimiento mv, boolean agachado, float tiempo,
                               @Nullable PolleraMalla.Piernas piernas, float piso) {
        float total = 6f + mv.atras() / 2f + mv.vertical() + mv.paso() + (agachado ? 25f : 0f);
        float onda = Math.min(1f, mv.atras() / 60f) * 9f;
        tramo = largo / TRAMOS;
        corrimientoLado = (float) Math.sin(Math.toRadians(mv.lado() / 2f)) * 0.9f;
        NODO_Y[0] = 0f;
        NODO_Z[0] = 0f;
        Vector3f v = new Vector3f();
        for (int k = 0; k < TRAMOS; k++) {
            float t = (k + 0.5f) / TRAMOS;
            float grados = 6f + (total - 6f) * (0.3f + 0.9f * t)
                    + onda * t * (float) Math.sin(tiempo * 0.45f - t * 6f)
                    + 1.8f * t * (float) Math.sin(tiempo * 0.07f - t * 2.5f);
            float a = (float) Math.toRadians(Math.max(2f, Math.min(160f, grados)));
            // Se abre de a 3° hasta que ningún punto del borde del tramo quede adentro de una pierna.
            for (int intento = 0; piernas != null && intento < 40; intento++) {
                float y = NODO_Y[k] + tramo * (float) Math.cos(a), z = NODO_Z[k] + tramo * (float) Math.sin(a);
                boolean choca = false;
                for (float x : MUESTRAS_X) {
                    // Con los brazos también (2026-10-25, "la capelet por ahi es atravesada un poquito por los brazos").
                    for (PolleraMalla.Piernas c = piernas; c != null && !choca; c = c.brazos) {
                        for (int i = 0; i < 2 && !choca; i++) {
                            c.aLocal[i].transformPosition(v.set(x + corrimientoLado * (k + 1) * tramo, y, z));
                            choca = v.y > 0.5f && v.y < c.hasta && PolleraMalla.dentroDePierna(v.x, v.z, c.aire);
                        }
                    }
                    if (choca) break;
                }
                if (!choca) break;
                a += (float) Math.toRadians(3f);
            }
            float yn = NODO_Y[k] + tramo * (float) Math.cos(a), zn = NODO_Z[k] + tramo * (float) Math.sin(a);
            if (yn > piso) {
                // Ruedo con cola (2026-10-04): la tela que pasaría del piso se apoya en él y sigue hacia atrás.
                float dy = Math.max(0f, piso - NODO_Y[k]);
                float dz = (float) Math.sqrt(Math.max(0f, tramo * tramo - dy * dy));
                a = (float) Math.atan2(dz, dy);
                yn = NODO_Y[k] + dy;
                zn = NODO_Z[k] + dz;
            }
            NODO_A[k] = a;
            NODO_Y[k + 1] = yn;
            NODO_Z[k + 1] = zn;
        }
        NODO_A[TRAMOS] = NODO_A[TRAMOS - 1];
    }

    /**
     * Local del cuboide vanilla (x -5..5, y = distancia bajando por la tela,
     * z -1 exterior .. 0 forro) → marco de la capa, doblado. Devuelve x, y, z.
     */
    private static void doblado(float lx, float ly, float lz, float[] salida, float[] normal) {
        float d = Math.max(0f, ly) / tramo;
        int k = Math.min(TRAMOS - 1, (int) d);
        float f = Math.min(1f, d - k);
        float cy = NODO_Y[k] + (NODO_Y[k + 1] - NODO_Y[k]) * f;
        float cz = NODO_Z[k] + (NODO_Z[k + 1] - NODO_Z[k]) * f;
        // El ángulo, parejo entre tramos (así la tela no se quiebra en las uniones).
        float ak = k == 0 ? NODO_A[0] : (NODO_A[k - 1] + NODO_A[k]) / 2f;
        float ak1 = (NODO_A[k] + NODO_A[Math.min(TRAMOS, k + 1)]) / 2f;
        float a = ak + (ak1 - ak) * f;
        float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
        // Vuelta de 180° de la capa vanilla: x y z cambian de signo; w = cuánto se aleja de la espalda.
        float w = -lz;
        salida[0] = -lx + corrimientoLado * ly;
        salida[1] = cy - w * sin;
        salida[2] = cz + w * cos;
        if (normal != null) {
            float ny = normal[1], nz = -normal[2];
            normal[0] = -normal[0];
            normal[1] = ny * cos - nz * sin;
            normal[2] = ny * sin + nz * cos;
        }
    }

    /**
     * El paño (exterior + forro + cantos) y la capucha, doblados como tela.
     * {@code matrices} ya tiene el corrimiento de la capa (2 px hacia atrás, y
     * el de agachado), sin giros: los pone {@link #doblar} vértice por vértice.
     */
    public static void dibujarPano(MatrixStack matrices, VertexConsumer vc, int luz, ItemStack stack,
                                   Movimiento mv, boolean agachado, float tiempo,
                                   @Nullable PolleraMalla.Piernas piernas) {
        dibujarPano(matrices, vc, luz, stack, mv, agachado, tiempo, piernas, false);
    }

    /** @param sinCapucha sin uso desde 2026-10-24 (la capucha caída la dibuja CuelloYCapucha, que ya respeta el hoodie) */
    public static void dibujarPano(MatrixStack matrices, VertexConsumer vc, int luz, ItemStack stack,
                                   Movimiento mv, boolean agachado, float tiempo,
                                   @Nullable PolleraMalla.Piernas piernas, boolean sinCapucha) {
        dibujarPano(matrices, vc, luz, stack, mv, agachado, tiempo, piernas, sinCapucha, true);
    }

    /** @param conCola false sentado, montado, nadando, durmiendo o gateando: la cola no se apoya en ningún piso */
    public static void dibujarPano(MatrixStack matrices, VertexConsumer vc, int luz, ItemStack stack,
                                   Movimiento mv, boolean agachado, float tiempo,
                                   @Nullable PolleraMalla.Piernas piernas, boolean sinCapucha, boolean conCola) {
        MatrixStack.Entry e = matrices.peek();
        CapaRuedo ruedo = CapaItem.ruedo(stack);
        float cola = ruedo == CapaRuedo.COLA && conCola ? CapaRuedo.COLA_PIXELES : 0f;
        float largo = CapaItem.largo(stack).pixeles + cola;
        // El piso en el marco de la capa: los pies están 24 px debajo de los hombros (menos lo que baja agachado).
        float piso = cola > 0f ? 24f - (agachado ? 1.85f : 0f) - 0.03f : Float.MAX_VALUE;
        doblar(largo, mv, agachado, tiempo, piernas, piso);
        boolean redondeado = ruedo == CapaRuedo.REDONDEADO;
        // Largo de cada columna: con ruedo redondeado las puntas suben en arco.
        float[] l = new float[COLS + 1];
        for (int c = 0; c <= COLS; c++) {
            float x = -5f + c;
            float k = x / 5f;
            float sube = redondeado ? Math.min(largo * 0.35f, 4.5f) * (1f - (float) Math.sqrt(Math.max(0f, 1f - k * k))) : 0f;
            l[c] = largo - sube;
        }
        for (int f = 0; f < FILAS; f++) {
            float t0 = f / (float) FILAS, t1 = (f + 1) / (float) FILAS;
            for (int c = 0; c < COLS; c++) {
                float xa = -5f + c, xb = xa + 1f;
                float ya0 = t0 * l[c], yb0 = t0 * l[c + 1], ya1 = t1 * l[c], yb1 = t1 * l[c + 1];
                float v0 = (1f + 16f * t0) / 64f, v1 = (1f + 16f * t1) / 64f;
                // Exterior (z = -1), u crece con x como la cara norte del cuboide.
                float ua = (1f + (xa + 5f)) / 64f, ub = (1f + (xb + 5f)) / 64f;
                quad(vc, e, luz,
                        xb, yb0, -1, ub, v0, xa, ya0, -1, ua, v0, xa, ya1, -1, ua, v1, xb, yb1, -1, ub, v1, 0, 0, -1);
                // Forro (z = 0), u decrece con x como la cara sur.
                float fa = (12f + (5f - xa)) / 64f, fb = (12f + (5f - xb)) / 64f;
                quad(vc, e, luz,
                        xa, ya0, 0, fa, v0, xb, yb0, 0, fb, v0, xb, yb1, 0, fb, v1, xa, ya1, 0, fa, v1, 0, 0, 1);
            }
        }
        // Cantos de los costados y del ruedo, con la columna del costado de la textura.
        for (int f = 0; f < FILAS; f++) {
            float t0 = f / (float) FILAS, t1 = (f + 1) / (float) FILAS;
            float v0 = (1f + 16f * t0) / 64f, v1 = (1f + 16f * t1) / 64f;
            quad(vc, e, luz, -5, t0 * l[0], 0, 0, v0, -5, t0 * l[0], -1, 1 / 64f, v0,
                    -5, t1 * l[0], -1, 1 / 64f, v1, -5, t1 * l[0], 0, 0, v1, -1, 0, 0);
            quad(vc, e, luz, 5, t0 * l[COLS], -1, 11 / 64f, v0, 5, t0 * l[COLS], 0, 12 / 64f, v0,
                    5, t1 * l[COLS], 0, 12 / 64f, v1, 5, t1 * l[COLS], -1, 11 / 64f, v1, 1, 0, 0);
        }
        for (int c = 0; c < COLS; c++) {
            float xa = -5f + c, xb = xa + 1f;
            float ua = (11f + (xa + 5f)) / 64f, ub = (11f + (xb + 5f)) / 64f;
            quad(vc, e, luz, xa, l[c], -1, ua, 0, xb, l[c + 1], -1, ub, 0,
                    xb, l[c + 1], 0, ub, 1 / 64f, xa, l[c], 0, ua, 1 / 64f, 0, 1, 0);
        }
        // Con la capucha puesta (tecla, 2026-09-30) la dibuja CuelloYCapucha en la cabeza.
        // La capucha caída ya no es una bolsa del paño: la dibuja CuelloYCapucha con la de Blockbench (2026-10-24).
    }

    /**
     * Cuello alto: media vuelta parada detrás de la nuca, de 3.5 px de alto
     * sobre los hombros. Va FUERA de {@link #giros} (solo el corrimiento de
     * la capa y el giro de 180°): no se inclina con la tela. Tela: la franja
     * de la caja de detalles (u 24..40, v 2..6).
     */
    public static void dibujarCuello(MatrixStack matrices, VertexConsumer vc, int luz) {
        sinDoblar = true;
        try {
            dibujarCuelloSinDoblar(matrices, vc, luz);
        } finally {
            sinDoblar = false;
        }
    }

    private static void dibujarCuelloSinDoblar(MatrixStack matrices, VertexConsumer vc, int luz) {
        MatrixStack.Entry e = matrices.peek();
        int n = 12;
        // 2026-09-30, "el cuello alto de la capa hay q abrirlo porque se esconde
        // en la cabeza": antes era media vuelta de radio 4.7 centrada en la
        // NUCA (z = 2 en este espacio, ver translate de giros), así que pasaba
        // por el medio de la cabeza (8 de profundidad: su centro queda 4 px
        // adelante de la nuca). Ahora se centra en la cabeza, rodea la parte
        // de atrás por fuera del sombrero de la skin (radio 5.3 abajo) y se
        // abre hacia arriba (6.6), como un cuello de capa de verdad.
        float centro = 2f - 4f, abajo = 5.3f, arriba = 6.6f, alto = 3.5f;
        for (int k = 0; k < n; k++) {
            double f0 = Math.PI * k / n, f1 = Math.PI * (k + 1) / n;
            float c0 = (float) Math.cos(f0), s0 = (float) Math.sin(f0);
            float c1 = (float) Math.cos(f1), s1 = (float) Math.sin(f1);
            float u0 = (24f + 16f * k / n) / 64f, u1 = (24f + 16f * (k + 1) / n) / 64f;
            float nx = (float) Math.cos((f0 + f1) / 2), nz = (float) Math.sin((f0 + f1) / 2);
            quad(vc, e, luz, arriba * c0, -alto, centro + arriba * s0, u0, 2 / 64f,
                    arriba * c1, -alto, centro + arriba * s1, u1, 2 / 64f,
                    abajo * c1, 0.3f, centro + abajo * s1, u1, 6 / 64f,
                    abajo * c0, 0.3f, centro + abajo * s0, u0, 6 / 64f, nx, 0, nz);
        }
    }


    // ── embozo ──────────────────────────────────────────────────────────
    // 2026-10-11, "un molde de embozo para la capa que cubra los hombros y un molde para que la capa recorra todo el
    // borde del embozo cerrando por el frente y que abra un poco con la tecla de abrir el frente": una esclavina de
    // malla en el marco del torso (origen en el centro del torso a la altura de los hombros, y hacia abajo, frente en
    // −z). Sube desde el cuello, pasa por encima de los hombros y baja por los costados hasta {@code profundidad}.
    // Tela: el exterior de la capa; el borde, la cara del frente de la caja de detalles (zona Detalles de Tintes).

    /** (fracción del radio, y) del tope del embozo, del cuello hacia el hombro. */
    private static final float[][] EMBOZO_PERFIL = {{0.50f, -0.6f}, {0.68f, -1.4f}, {0.84f, -1.65f}, {0.96f, -1.4f}, {1.00f, -0.5f}};
    private static final int EMBOZO_PASOS = 36;
    /** Cuánto se abre el ruedo cuando llega al piso, en px (2026-10-23); empieza a la altura de la cadera. */
    private static final float EMBOZO_VUELO = 2f;

    /**
     * @param profundidad cuánto baja el borde de abajo del embozo, en px
     * @param borde       con cinta alrededor del contorno (abajo y a lo largo del cierre del frente)
     * @param abierto     el frente abierto un poco (la tecla de abrir el frente)
     * @param extra       cuánto más se aleja del cuerpo por la ropa gruesa que lleve debajo
     * @param busto       la superficie del busto en el marco del torso, o null (2026-10-25)
     * @param cuerpo      piernas y brazos en el marco del torso, para que la tela choque con ellos (2026-10-23)
     */
    public static void dibujarEmbozo(MatrixStack matrices, VertexConsumer vc, int luz, float profundidad, boolean borde,
                                     boolean abierto, float extra,
                                     @Nullable java.util.function.BiFunction<Float, Float, com.modamod.render.relieve.BustoRender.Punto> busto,
                                     PolleraMalla.Piernas... cuerpo) {
        sinDoblar = true;
        try {
            embozo(matrices.peek(), vc, luz, profundidad, borde, abierto, extra, busto, cuerpo);
        } finally {
            sinDoblar = false;
        }
    }

    private static void embozo(MatrixStack.Entry e, VertexConsumer vc, int luz, float profundidad, boolean borde,
                               boolean abierto, float extra,
                               @Nullable java.util.function.BiFunction<Float, Float, com.modamod.render.relieve.BustoRender.Punto> busto,
                               PolleraMalla.Piernas[] cuerpo) {
        float rx = 9.4f + extra, rzf = 3.6f + extra, rzb = 4.4f + extra;
        // El perfil del hombro y después una fila cada ~1,5 px hasta el ruedo (2026-10-23, para que doble al chocar).
        int np = EMBOZO_PERFIL.length;
        float yHombro = EMBOZO_PERFIL[np - 1][1], yFin = Math.max(profundidad, 1.2f);
        int bajada = Math.max(1, (int) Math.ceil((yFin - yHombro) / 1.5f));
        int n = np + bajada;
        float[] pa = new float[n], py = new float[n], sv = new float[n];
        for (int i = 0; i < np; i++) {
            pa[i] = EMBOZO_PERFIL[i][0];
            py[i] = EMBOZO_PERFIL[i][1];
        }
        for (int i = np; i < n; i++) {
            py[i] = yHombro + (yFin - yHombro) * (i - np + 1) / bajada;
            pa[i] = 1f + EMBOZO_VUELO / rx * MathHelper.clamp((py[i] - 12f) / 11.5f, 0f, 1f);
        }
        for (int i = 1; i < n; i++) {
            sv[i] = sv[i - 1] + (float) Math.hypot((pa[i] - pa[i - 1]) * rx, py[i] - py[i - 1]);
        }
        for (int i = 1; i < n; i++) sv[i] /= sv[n - 1];
        // Abierto (2026-10-23, "que caiga por el hombro hacia los laterales y atras directamente"): apenas abre en el
        // cuello y la abertura crece hacia abajo hasta ~70° de cada lado a los 11,5 px; los bordes bajan en diagonal.
        float[][] t = new float[n][EMBOZO_PASOS + 1];
        float[] gap = new float[n];
        for (int k = 0; k < n; k++) {
            gap[k] = abierto ? 0.30f + 0.92f * MathHelper.clamp((py[k] + 0.5f) / 12f, 0f, 1f) : 0.03f;
            float vuelta = (float) (2 * Math.PI) - 2 * gap[k];
            for (int j = 0; j <= EMBOZO_PASOS; j++) t[k][j] = gap[k] + vuelta * j / EMBOZO_PASOS;
        }
        float[][][] p = new float[n][EMBOZO_PASOS + 1][];
        for (int k = 0; k < n; k++) {
            for (int j = 0; j <= EMBOZO_PASOS; j++) p[k][j] = embozoPunto(pa[k], py[k], t[k][j], rx, rzf, rzb, 0f, k < np - 1);
        }
        float[][][] despl = chocarEmbozo(p, cuerpo);
        // Por encima del busto (2026-10-25, "mira captura sigen dos agujeritos": las puntas del busto asomaban por el frente).
        if (busto != null) {
            for (int k = 1; k < n; k++) {
                for (int j = 0; j <= EMBOZO_PASOS; j++) {
                    float[] q = p[k][j], d = despl[k][j];
                    if (q[2] + d[2] >= 0f) continue;
                    com.modamod.render.relieve.BustoRender.Punto s = busto.apply(q[0] + d[0], q[1] + d[1]);
                    float tope = s == null ? 0f : s.pos().z - 0.35f;
                    if (s != null && q[2] + d[2] > tope) d[2] = tope - q[2];
                }
            }
        }
        for (int k = 0; k < n - 1; k++) {
            for (int j = 0; j < EMBOZO_PASOS; j++) {
                float u0 = (1f + 10f * j / EMBOZO_PASOS) / 64f, u1 = (1f + 10f * (j + 1) / EMBOZO_PASOS) / 64f;
                float v0 = (1f + 16f * sv[k]) / 64f, v1 = (1f + 16f * sv[k + 1]) / 64f;
                embozoQuad(vc, e, luz, p[k][j], u0, v0, p[k][j + 1], u1, v0, p[k + 1][j + 1], u1, v1, p[k + 1][j], u0, v1);
            }
        }
        if (!borde) return;
        // Cinta del ruedo: una franja de 1,4 px sobre el borde de abajo, corrida igual que la tela del ruedo.
        float yb = py[n - 1], ya = Math.max(py[n - 2] + 0.2f, yb - 1.4f), ab = pa[n - 1];
        for (int j = 0; j < EMBOZO_PASOS; j++) {
            float[] tb = t[n - 1];
            float[] a = conDespl(embozoPunto(ab, ya, tb[j], rx, rzf, rzb, 0.12f, false), despl[n - 1][j], cuerpo);
            float[] b = conDespl(embozoPunto(ab, ya, tb[j + 1], rx, rzf, rzb, 0.12f, false), despl[n - 1][j + 1], cuerpo);
            float[] c = conDespl(embozoPunto(ab, yb, tb[j + 1], rx, rzf, rzb, 0.12f, false), despl[n - 1][j + 1], cuerpo);
            float[] d = conDespl(embozoPunto(ab, yb, tb[j], rx, rzf, rzb, 0.12f, false), despl[n - 1][j], cuerpo);
            float u0 = (26f + 12f * j / EMBOZO_PASOS) / 64f, u1 = (26f + 12f * (j + 1) / EMBOZO_PASOS) / 64f;
            embozoQuad(vc, e, luz, a, u0, 2 / 64f, b, u1, 2 / 64f, c, u1, 4 / 64f, d, u0, 4 / 64f);
        }
        // Cinta del cierre: una a cada lado del frente, del cuello al ruedo (por eso cierra el frente).
        float dt = 1.4f / rx;
        for (int lado = 0; lado < 2; lado++) {
            int col = lado == 0 ? 0 : EMBOZO_PASOS;
            for (int k = 0; k < n - 1; k++) {
                float b0 = t[k][col], b1 = t[k + 1][col];
                float i0 = lado == 0 ? b0 + dt : b0 - dt, i1 = lado == 0 ? b1 + dt : b1 - dt;
                float[] a = conDespl(embozoPunto(pa[k], py[k], b0, rx, rzf, rzb, 0.12f, k < np - 1), despl[k][col], cuerpo);
                float[] b = conDespl(embozoPunto(pa[k], py[k], i0, rx, rzf, rzb, 0.12f, k < np - 1), despl[k][col], cuerpo);
                float[] c = conDespl(embozoPunto(pa[k + 1], py[k + 1], i1, rx, rzf, rzb, 0.12f, k + 1 < np - 1), despl[k + 1][col], cuerpo);
                float[] d = conDespl(embozoPunto(pa[k + 1], py[k + 1], b1, rx, rzf, rzb, 0.12f, k + 1 < np - 1), despl[k + 1][col], cuerpo);
                float v0 = (2f + 8f * sv[k]) / 64f, v1 = (2f + 8f * sv[k + 1]) / 64f;
                embozoQuad(vc, e, luz, a, 26 / 64f, v0, b, 38 / 64f, v0, c, 38 / 64f, v1, d, 26 / 64f, v1);
            }
        }
    }

    /**
     * Saca la tela del embozo de piernas y brazos, como la pollera (PolleraMalla#chocarConPiernas): empuja, suaviza los
     * corrimientos con los vecinos para que no quede un pico y vuelve a empujar. Devuelve el corrimiento de cada punto
     * (para llevar las cintas con la tela). La fila del cuello no se toca.
     */
    private static float[][][] chocarEmbozo(float[][][] p, PolleraMalla.Piernas[] cuerpo) {
        int n = p.length, m = p[0].length;
        float[][][] despl = new float[n][m][3];
        if (cuerpo.length == 0) return despl;
        float[][][] crudo = new float[n][m][];
        boolean alguno = false;
        for (int k = 1; k < n; k++) {
            for (int j = 0; j < m; j++) {
                float[] q = p[k][j], antes = q.clone();
                for (PolleraMalla.Piernas c : cuerpo) PolleraMalla.empujar(q, c);
                if (q[0] != antes[0] || q[1] != antes[1] || q[2] != antes[2]) {
                    crudo[k][j] = new float[]{q[0] - antes[0], q[1] - antes[1], q[2] - antes[2]};
                    alguno = true;
                }
                System.arraycopy(antes, 0, q, 0, 3);
            }
        }
        if (!alguno) return despl;
        for (int k = 1; k < n; k++) {
            for (int j = 0; j < m; j++) {
                float sx = 0, sy = 0, sz = 0, w = 0;
                for (int dk = -1; dk <= 1; dk++) {
                    int kk = k + dk;
                    if (kk < 1 || kk >= n) continue;
                    for (int dj = -2; dj <= 2; dj++) {
                        int jj = j + dj;
                        if (jj < 0 || jj >= m) continue;
                        float peso = (3 - Math.abs(dj)) * (dk == 0 ? 2 : 1);
                        w += peso;
                        float[] d = crudo[kk][jj];
                        if (d == null) continue;
                        sx += d[0] * peso;
                        sy += d[1] * peso;
                        sz += d[2] * peso;
                    }
                }
                float[] q = p[k][j], antes = q.clone();
                q[0] += sx / w;
                q[1] += sy / w;
                q[2] += sz / w;
                for (PolleraMalla.Piernas c : cuerpo) PolleraMalla.empujar(q, c);
                despl[k][j] = new float[]{q[0] - antes[0], q[1] - antes[1], q[2] - antes[2]};
            }
        }
        return despl;
    }

    /** Un punto de las cintas corrido como la tela que tiene debajo, y fuera del cuerpo por las dudas. */
    private static float[] conDespl(float[] q, float[] d, PolleraMalla.Piernas[] cuerpo) {
        q[0] += d[0];
        q[1] += d[1];
        q[2] += d[2];
        for (PolleraMalla.Piernas c : cuerpo) PolleraMalla.empujar(q, c);
        return q;
    }

    /** Un punto del embozo: fracción {@code a} del radio, altura {@code y}, ángulo {@code t} desde el frente (0 = frente). */
    private static float[] embozoPunto(float a, float y, float t, float rx, float rzf, float rzb, float off, boolean tope) {
        float s = (float) Math.sin(t), c = (float) Math.cos(t);
        float zr = c > 0f ? rzf : rzb;
        return new float[]{(rx * a + off) * s, tope ? y - off : y, -(zr * a + off) * c};
    }

    private static void embozoQuad(VertexConsumer vc, MatrixStack.Entry e, int luz,
                                   float[] a, float ua, float va, float[] b, float ub, float vb,
                                   float[] c, float uc, float vc2, float[] d, float ud, float vd) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float wx = d[0] - a[0], wy = d[1] - a[1], wz = d[2] - a[2];
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        // Hacia afuera del eje del embozo (que está unos 4 px bajo los hombros).
        if (nx * a[0] + ny * (a[1] - 4f) + nz * a[2] < 0f) { nx = -nx; ny = -ny; nz = -nz; }
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) { nx = 0f; ny = -1f; nz = 0f; l = 1f; }
        quad(vc, e, luz, a[0], a[1], a[2], ua, va, b[0], b[1], b[2], ub, vb, c[0], c[1], c[2], uc, vc2,
                d[0], d[1], d[2], ud, vd, nx / l, ny / l, nz / l);
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry e, int luz,
                             float x0, float y0, float z0, float u0, float v0,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float nx, float ny, float nz) {
        v(vc, e, x0, y0, z0, u0, v0, luz, nx, ny, nz);
        v(vc, e, x1, y1, z1, u1, v1, luz, nx, ny, nz);
        v(vc, e, x2, y2, z2, u2, v2, luz, nx, ny, nz);
        v(vc, e, x3, y3, z3, u3, v3, luz, nx, ny, nz);
    }

    private static final float[] PUNTO = new float[3], NORMAL = new float[3];
    /** Mientras se dibuja el cuello (que no se dobla con la tela), los vértices van tal cual. */
    private static boolean sinDoblar = false;

    private static void v(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, float u, float vv,
                          int luz, float nx, float ny, float nz) {
        if (!sinDoblar) {
            NORMAL[0] = nx;
            NORMAL[1] = ny;
            NORMAL[2] = nz;
            doblado(x, y, z, PUNTO, NORMAL);
            x = PUNTO[0];
            y = PUNTO[1];
            z = PUNTO[2];
            nx = NORMAL[0];
            ny = NORMAL[1];
            nz = NORMAL[2];
        }
        if (MallaCapturada.grabando != null) {
            MallaCapturada.grabando.agregar(e.getPositionMatrix(), e.getNormalMatrix(),
                    x / 16f, y / 16f, z / 16f, u, vv, nx, ny, nz);
        }
        vc.vertex(e.getPositionMatrix(), x / 16f, y / 16f, z / 16f)
                .color(0xFFFFFFFF)
                .texture(u, vv)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(luz)
                .normal(e, nx, ny, nz);
    }
}
