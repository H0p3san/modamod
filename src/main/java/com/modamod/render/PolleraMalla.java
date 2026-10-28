package com.modamod.render;

import com.modamod.item.PolleraBorde;
import com.modamod.item.PolleraForma;
import com.modamod.item.PolleraLargo;
import com.modamod.item.PolleraVolado;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * La pollera como malla propia — quinta vuelta (2026-09-29, "resolveme la
 * pollera que se ve horrible a veces y quiero poder hacerle distintos
 * largos y teñirla y sublimarla"). Reemplaza a {@link PolleraGeometria}
 * (20 gajos rígidos): los gajos no se podían afinar ni doblar, las piernas
 * los atravesaban al caminar y todos compartían un rinconcito de 1.8x9 de
 * textura, así que no entraba ningún patrón ni foto.
 *
 * <h2>Forma</h2>
 * Un tubo de {@link #COLUMNAS}x{@link #FILAS} cuadriláteros que nace en la
 * cintura (fila {@link #Y_CINTURA} del torso) con la sección del torso (caja
 * de esquinas redondeadas) y se abre hacia el ruedo, cada vez más redondo.
 * {@link PolleraForma#TABLEADA} le suma un zigzag radial (pliegues en V,
 * {@link #TABLAS} tablas) que crece hacia abajo — la idea del pliegue en V
 * de la vieja de gajos, pero continua.
 *
 * <h2>Tela</h2>
 * El perímetro recorre la franja de caras de la caja del TORSO de la skin
 * (u 16..40: costado derecho, frente, costado izquierdo, espalda — el mismo
 * orden y sentido que la caja) y el largo va de v 20 a 32, repartidos por
 * largo de tela de la pollera quieta (ver {@link Perfil}, 2026-10-01). Así la textura
 * de la pollera tiene el layout de una caja de torso y la tiñen, estampan y
 * perforan las mismas rutinas que al resto de las prendas.
 *
 * <h2>Piernas y movimiento</h2>
 * La tela choca con las piernas ({@link #chocarConPiernas}, contra su caja
 * real) y se mueve con el cuerpo (el modo rígido de prueba se sacó el
 * 2026-10-02, "sacar la pollera rigida"). Cuelga hacia abajo aunque el
 * torso se incline ({@link #colgar}). El movimiento (2026-09-30, "habria que animarlas segun
 * el movimiento") usa la inercia de la capa vanilla ({@link CapaMalla#movimiento}):
 * el ruedo queda atrás al caminar/correr, se abre al caer, se achica al
 * saltar, se balancea de costado y se retuerce un poco con cada paso. Todo
 * crece hacia el ruedo (la cintura no se mueve).
 *
 * <p>Coordenadas: las del torso del jugador (píxeles, Y hacia abajo, el
 * frente en -Z), dibujadas después de {@code ModelPart.rotate} del torso.
 */
public final class PolleraMalla {

    private PolleraMalla() {}

    private static final float Y_CINTURA = 9f;
    /** Cintura más alta posible (px bajo el hombro): pollera subida hasta el pecho (2026-10-08). */
    public static final float CINTURA_MIN = 3f, CINTURA_MAX = 11f;
    private static final int COLUMNAS = 48;
    /** 24 filas (2026-10-05, volados): tres filas de volado necesitan resolución para que se lea el borde de cada una. */
    static final int FILAS = 24;

    /** Vértices de la malla que se arma en {@code dibujar}, reusados cuadro a cuadro (hilo de render, sin reentrada). */
    private static final float[][][] POOL_VERTICES = new float[FILAS + 1][COLUMNAS + 1][5];
    private static final int TABLAS = 12;
    /** Largo del perímetro de la caja del torso en la textura (4+8+4+8). */
    private static final float PERIMETRO = 24f;
    /** Columnas del centro del frente (s = 8) y de la espalda (s = 20). */
    private static final int COL_FRENTE = COLUMNAS / 3, COL_ESPALDA = COLUMNAS * 5 / 6;

    /**
     * Las dos piernas del modelo, para que la tela choque con ellas
     * (2026-09-30, "hay forma de hacer q la tela de la pollera colisione con
     * las piernas?" → opción A, colisión geométrica sin estado).
     * Cada pierna es su caja real de 4x12x4 con la pose de este frame (pivote
     * y giros de {@link ModelPart}, agachada incluida), llevada al marco del
     * torso donde vive la malla.
     */
    public static final class Piernas {
        final Matrix4f[] aLocal = new Matrix4f[2];
        final Matrix4f[] aTorso = new Matrix4f[2];
        /** Hasta dónde choca a lo largo del miembro (12,5 = la pierna entera). */
        final float hasta;
        /** Lo que se agranda la caja de cada lado (px), sobre la de la pierna. */
        final float aire;
        /** Los brazos, si la tela también choca con ellos (el paño de la capa, 2026-10-25), o null. */
        @Nullable Piernas brazos;

        public Piernas(ModelPart torso, ModelPart der, ModelPart izq) {
            this(marco(torso), der, izq);
        }

        /**
         * Con un marco cualquiera en vez del torso (2026-09-30, la capa): {@code base}
         * lleva de ese marco al del modelo, en píxeles.
         */
        public Piernas(Matrix4f base, ModelPart der, ModelPart izq) {
            this(base, marco(der), marco(izq), 12.5f, 0f);
        }

        private Piernas(Matrix4f base, Matrix4f der, Matrix4f izq, float hasta, float aire) {
            this.hasta = hasta;
            this.aire = aire;
            Matrix4f[] miembros = {der, izq};
            for (int i = 0; i < 2; i++) {
                aTorso[i] = base.invert(new Matrix4f()).mul(miembros[i]);
                aLocal[i] = aTorso[i].invert(new Matrix4f());
            }
        }

        /**
         * Los brazos con la misma caja (2026-10-23, el embozo largo "que colisione con el cuerpo como las polleras"):
         * corrida al centro de cada brazo. Desde 1 px bajo el tope del brazo y 0,3 px más ancha (2026-10-25, "la capelet
         * por ahi es atravesada un poquito por los brazos": al balancearse, el brazo salía por arriba de la caja, que
         * arrancaba 2 px más abajo).
         */
        public static Piernas brazos(ModelPart torso, ModelPart der, ModelPart izq, float aire) {
            return brazos(marco(torso), der, izq, aire);
        }

        /** {@code aire}: lo que se agranda la caja sobre la de la pierna (que ya es 0,55 px más ancha que el brazo). */
        public static Piernas brazos(Matrix4f base, ModelPart der, ModelPart izq, float aire) {
            return new Piernas(base, marco(der).translate(-1f, -1.5f, 0f), marco(izq).translate(1f, -1.5f, 0f), 11.5f, aire);
        }

        /** Estas piernas y además {@code b} (los brazos, en el mismo marco). */
        public Piernas conBrazos(Piernas b) {
            brazos = b;
            return this;
        }

        /** Lo mismo que {@code ModelPart#rotate}, en píxeles. */
        private static Matrix4f marco(ModelPart parte) {
            return new Matrix4f().translation(parte.pivotX, parte.pivotY, parte.pivotZ)
                    .rotateZYX(parte.roll, parte.yaw, parte.pitch);
        }
    }

    /** Media caja de la pierna (2) + la tela del pantalón/medias y un poco de aire. */
    private static final float MEDIO_ANCHO_PIERNA = 2.55f;
    /** Radio de las esquinas de la sección de la pierna (así la tela no se quiebra en la arista). */
    private static final float ESQUINA_PIERNA = 1.2f;

    /**
     * @param dil     dilatación de la tela (calce): la cintura sale un poco por fuera del torso
     * @param piernas las piernas contra las que choca la tela, o null (sin colisión)
     */
    public static void dibujar(MatrixStack matrices, VertexConsumer vc, int luz, PolleraForma forma,
                               PolleraLargo largo, float dil, @Nullable Piernas piernas,
                               CapaMalla.Movimiento mov, float twirl) {
        dibujar(matrices, vc, luz, forma, largo, null, null, null, dil, piernas, mov, twirl, 0f, 0f, Y_CINTURA);
    }

    /**
     * @param cola        cuánto sale la cola por detrás (px, ver {@code BustoRender.formasCola}); la tela pasa por fuera
     * @param inclinacion el {@code pitch} del torso (agachado ~0.5): la tela cuelga igual hacia abajo
     */
    public static void dibujar(MatrixStack matrices, VertexConsumer vc, int luz, PolleraForma forma,
                               PolleraLargo largo, @Nullable PolleraVolado voladoRuedo, @Nullable PolleraVolado voladoTodo,
                               com.modamod.item.Ruedo hem, float dil, @Nullable Piernas piernas,
                               CapaMalla.Movimiento mov, float twirl, float cola, float inclinacion, float cintura) {
        dibujar(matrices, vc, luz, forma, largo, voladoRuedo, voladoTodo, hem, dil, piernas, mov, twirl, cola, inclinacion,
                cintura, Float.NaN, Float.NaN, 0, null, Float.NaN, -1, -1, null, Float.NaN, Float.NaN);
    }

    /**
     * El faldón de un Top (2026-10-11, "las chaquetas de malla": una falda de malla que cuelga de la cintura, con el frente
     * abierto): la misma malla pero con la tela del torso del Top, tomando de la textura las filas {@code vDesde..vHasta}
     * (px de la textura de 64, v absoluta) de arriba a abajo, y sin los {@code hueco} cuadrados de cada lado del centro
     * del frente (0 = cerrada). Las UV de arriba a abajo son lineales: no hay perfil de largo de tela.
     */
    public static void dibujarFaldon(MatrixStack matrices, VertexConsumer vc, int luz, PolleraForma forma,
                                     PolleraLargo largo, float dil, @Nullable Piernas piernas, CapaMalla.Movimiento mov,
                                     float twirl, float cola, float inclinacion, float cintura,
                                     float vDesde, float vHasta, int hueco, @Nullable float[] extraPorFila,
                                     float mitadX, float mitadZ) {
        dibujar(matrices, vc, luz, forma, largo, null, null, com.modamod.item.Ruedo.RECTO, dil, piernas, mov, twirl, cola,
                inclinacion, cintura, vDesde, vHasta, hueco, extraPorFila, Float.NaN, -1, -1, null, mitadX, mitadZ);
    }

    private static void dibujar(MatrixStack matrices, VertexConsumer vc, int luz, PolleraForma forma,
                                PolleraLargo largo, @Nullable PolleraVolado voladoRuedo, @Nullable PolleraVolado voladoTodo,
                                com.modamod.item.Ruedo hem, float dil, @Nullable Piernas piernas,
                                CapaMalla.Movimiento mov, float twirl, float cola, float inclinacion, float cintura,
                                float vDesde, float vHasta, int hueco, @Nullable float[] extraPorFila,
                                float lPx, int colDesde, int colHasta, @Nullable AdornoDelantal adorno,
                                float mitadX, float mitadZ) {
        // Los vértices se reusan entre cuadros (la malla se arma y se dibuja completa dentro de esta llamada).
        float[][][] p = POOL_VERTICES;
        PolleraBorde borde = hem.borde();
        float l = Float.isNaN(lPx) ? largo.pixeles : lPx;
        // Nunca más pegada que Normal: la cintura (sección casi recta) tiene
        // que pasar por fuera de las esquinas del torso.
        float holgura = Math.max(dil, 0.25f);
        float a0 = 4f + holgura + 0.1f, b0 = 2f + holgura + 0.1f;
        // El faldón de un abrigo (2026-10-18): la mitad de ancho de arriba es la del ruedo del abrigo, para que sea su continuación.
        if (!Float.isNaN(mitadX)) {
            a0 = mitadX;
            b0 = mitadZ;
        }
        // Cuánto se mueve el ruedo (en px, a t = 1): hacia atrás con la
        // velocidad, de costado con el giro, y cuánto se abre al caer.
        float atras = l * 0.5f * Math.min(1f, mov.atras() / 80f);
        float costado = -l * 0.3f * mov.lado() / 20f;
        float abrir = mov.vertical() > 0f ? 0.6f * Math.min(1f, mov.vertical() / 20f)
                : -0.12f * Math.min(1f, -mov.vertical() / 6f);
        float giro = 0.18f * mov.paso() / 32f;
        // Cuánto se mueve según la forma (2026-10-05): el tubo casi no vuela, el globo un poco menos que la campana
        // y la circular más.
        float movimiento = colDesde >= 0 ? 0.4f : movimientoDe(forma);
        // Twirl (2026-09-30, "que se abran y giren"): la campana se abre casi
        // horizontal a mitad de la vuelta y el ruedo queda atrasado respecto
        // del giro del cuerpo (la tela arrastra). Vale también en rígida.
        if (twirl >= 0f) {
            float fuerza = (float) Math.sin(Math.PI * twirl);
            abrir = Math.max(abrir, 1.3f * fuerza);
            giro += 0.7f * fuerza;
            atras *= 1f - fuerza;
        }
        atras *= movimiento;
        costado *= movimiento;
        // Delantal (2026-10-12, "se abre mucho sobre la falda"): casi no se abre al caer ni al girar, cuelga derecho sobre la pollera.
        abrir *= colDesde >= 0 ? 0.2f : movimiento;
        giro *= movimiento;

        // La tela sale del perfil QUIETO (no del que se mueve): así el dibujo
        // no "nada" sobre la pollera cuando la tela se balancea.
        Perfil perfil = perfil(forma, largo, cola);
        for (int f = 0; f <= FILAS; f++) {
            float t = f / (float) FILAS;
            float y = cintura + t * l;
            float peso = (float) Math.pow(t, 1.6);
            float cosG = (float) Math.cos(giro * t), sinG = (float) Math.sin(giro * t);
            float[][] anillo = anillo(t, a0, b0, l, abrir, forma, voladoRuedo, voladoTodo);
            for (int c = 0; c <= COLUMNAS; c++) {
                float x = anillo[c][0], z = anillo[c][1];
                // Ruedo ajustado / campana (2026-10-08, "un ruedo ajustado que podria servir hasta para polleras"): las
                // filas de abajo se cierran contra la pierna o se abren hacia afuera, más cuanto más abajo.
                if (t > 0.8f && (hem == com.modamod.item.Ruedo.AJUSTADO || hem == com.modamod.item.Ruedo.CAMPANA)) {
                    float w = (t - 0.8f) / 0.2f;
                    float k = hem == com.modamod.item.Ruedo.AJUSTADO ? 1f - 0.35f * w * w : 1f + 0.5f * w * w;
                    x *= k;
                    z *= k;
                }
                // Faldón sobre una pollera (2026-10-11, "el faldon tiene que colisionar con la pollera"): esta fila se aleja
                // del eje lo que haga falta para pasar por fuera de la pollera que lleva debajo.
                if (extraPorFila != null && extraPorFila[f] > 0.01f) {
                    float rr = (float) Math.hypot(x, z);
                    if (rr > 0.5f) {
                        float k = (rr + extraPorFila[f]) / rr;
                        x *= k;
                        z *= k;
                    }
                }
                // Vaivén del paso: la tela se retuerce un poco alrededor del eje del cuerpo.
                float xg = x * cosG - z * sinG, zg = x * sinG + z * cosG;
                x = xg;
                z = zg;
                float yy = y;
                // Borde decorativo del ruedo (2026-10-05): las filas de abajo bajan según el dibujo, más cuanto más abajo.
                if (borde != null && t > 0.8f) {
                    float w = (t - 0.8f) / 0.2f;
                    yy += borde.baja(((c - COL_FRENTE + COLUMNAS) % COLUMNAS) / (float) COLUMNAS) * w * w;
                }
                // Inercia: el ruedo se queda atrás (+Z) y de costado; al irse
                // para atrás/afuera también sube un poco (la tela no se estira).
                float dz = atras * peso, dx = costado * peso;
                z += dz;
                x += dx;
                yy -= (dz * 0.35f + Math.abs(dx) * 0.2f + abrir * l * 0.2f * peso);
                float[] v = p[f][c];
                v[0] = x;
                v[1] = yy;
                v[2] = z;
                v[3] = perfil.u[f][c] / 64f;
                v[4] = (Float.isNaN(vDesde) ? perfil.v[f] : vDesde + (vHasta - vDesde) * t) / 64f;
            }
        }

        if (cola > 0.05f) pasarPorFueraDeLaCola(p, cola);
        if (Math.abs(inclinacion) > 1e-3f) colgar(p, inclinacion, cintura);
        if (piernas != null) chocarConPiernas(p, piernas);

        MatrixStack.Entry e = matrices.peek();
        int ov = OverlayTexture.DEFAULT_UV;
        for (int f = 0; f < FILAS; f++) {
            for (int c = 0; c < COLUMNAS; c++) {
                // Frente abierto (faldón de un Top): sin los cuadrados de cada lado del centro del frente.
                if (hueco > 0 && Math.abs(c + 0.5f - COL_FRENTE) < hueco) continue;
                // Delantal: solo el arco del frente.
                if (colDesde >= 0 && (c < colDesde || c >= colHasta)) continue;
                emitir(vc, e, p, f, c, luz, ov);
                emitir(vc, e, p, f, c + 1, luz, ov);
                emitir(vc, e, p, f + 1, c + 1, luz, ov);
                emitir(vc, e, p, f + 1, c, luz, ov);
            }
        }
        if (adorno != null) adornar(p, adorno, e, vc, luz, ov);
    }

    /** Los volados de los costados y del borde de arriba del delantal (2026-10-11) y la silueta a la que se pegan. */
    public record AdornoDelantal(com.modamod.item.DelantalForma forma, float largo, @Nullable PolleraVolado lateral,
                                 @Nullable PolleraVolado superior, float cap,
                                 boolean pieza, @Nullable com.modamod.item.DelantalTirantes tirantes, float dil,
                                 float bustoExtra) {
        public AdornoDelantal(com.modamod.item.DelantalForma forma, float largo, @Nullable PolleraVolado lateral,
                              @Nullable PolleraVolado superior, float cap) {
            this(forma, largo, lateral, superior, cap, false, null, 0f, 0f);
        }
    }

    private static final int SUB = FILAS * 2, TRAMOS = 3;

    /** Un punto de la malla: fila fraccionaria {@code rowF} (0..FILAS) y arco {@code arcPx} desde el centro del frente (px). */
    static float[] punto(float[][][] p, float rowF, float arcPx) {
        float rf = Math.max(0f, Math.min(FILAS, rowF));
        int f0 = Math.min(FILAS - 1, (int) rf);
        float tf = rf - f0;
        float[] a = enFila(p[f0], 24f + arcPx), b = enFila(p[f0 + 1], 24f + arcPx);
        return new float[]{a[0] + (b[0] - a[0]) * tf, a[1] + (b[1] - a[1]) * tf, a[2] + (b[2] - a[2]) * tf};
    }

    private static float[] enFila(float[][] fila, float uObj) {
        int c0 = COL_FRENTE - DELANTAL_COLUMNAS, c1 = COL_FRENTE + DELANTAL_COLUMNAS;
        int c = c0;
        while (c < c1 - 1 && fila[c + 1][3] * 64f < uObj) c++;
        float u0 = fila[c][3] * 64f, u1 = fila[c + 1][3] * 64f;
        float t = Math.abs(u1 - u0) < 1e-4f ? 0f : (uObj - u0) / (u1 - u0);
        return new float[]{fila[c][0] + (fila[c + 1][0] - fila[c][0]) * t, fila[c][1] + (fila[c + 1][1] - fila[c][1]) * t,
                fila[c][2] + (fila[c + 1][2] - fila[c][2]) * t};
    }

    private static float[] unitario(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return l < 1e-5f ? new float[]{0f, 0f, -1f} : new float[]{x / l, y / l, z / l};
    }

    /**
     * Los volados del delantal: tiras de malla que salen del borde de la silueta — a los costados hacia afuera, arriba
     * hacia arriba — y ondulan como los de la pollera (recto = ondas finas, circular = pocas y grandes). Toman el
     * texel del borde de la silueta, así llevan su color y dibujo.
     */
    private static void adornar(float[][][] p, AdornoDelantal ad, MatrixStack.Entry e, VertexConsumer vc, int luz, int ov) {
        float largo = ad.largo();
        if (ad.pieza()) DelantalPieza.dibujar(p, ad, e, vc, luz, ov);
        if (ad.lateral() != null) {
            boolean circ = ad.lateral() == PolleraVolado.CIRCULAR;
            float L = circ ? 3.0f : 2.0f, amp = circ ? 0.8f : 0.3f, per = circ ? 2.7f : 1.0f;
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float[][][] g = new float[SUB + 1][TRAMOS + 1][];
                for (int j = 0; j <= SUB; j++) {
                    float y = j / (float) SUB * largo;
                    float arc = sgn * Math.max(0.5f, ad.forma().medio(y, largo, ad.cap()) - 0.15f);
                    float rowF = y / largo * FILAS;
                    float[] b = punto(p, rowF, arc), dentro = punto(p, rowF, arc - sgn * 0.6f);
                    float[] d = unitario(b[0] - dentro[0], b[1] - dentro[1], b[2] - dentro[2]);
                    float[] n = unitario(b[0], 0f, b[2]);
                    for (int k = 0; k <= TRAMOS; k++) {
                        float t = k / (float) TRAMOS;
                        float onda = amp * t * (float) Math.sin(2.0 * Math.PI * y / per);
                        g[j][k] = new float[]{b[0] + d[0] * L * t + n[0] * onda, b[1] + d[1] * L * t + n[1] * onda,
                                b[2] + d[2] * L * t + n[2] * onda, (24f + arc) / 64f, (20f + y) / 64f};
                    }
                }
                emitirTira(g, e, vc, luz, ov);
            }
        }
        if (ad.superior() != null) {
            boolean circ = ad.superior() == PolleraVolado.CIRCULAR;
            float L = circ ? 3.0f : 2.0f, amp = circ ? 0.8f : 0.3f, per = circ ? 2.7f : 1.0f;
            float m = Math.max(0.5f, ad.forma().medio(0f, largo, ad.cap()) - 0.15f);
            float[][][] g = new float[SUB + 1][TRAMOS + 1][];
            for (int i = 0; i <= SUB; i++) {
                float s = -m + 2f * m * i / SUB;
                float y = ad.forma().tope(Math.abs(s)) + 0.3f;
                float rowF = y / largo * FILAS;
                float[] b = punto(p, rowF, s), abajo = punto(p, rowF + 0.6f, s);
                float[] d = unitario(b[0] - abajo[0], b[1] - abajo[1], b[2] - abajo[2]);
                float[] n = unitario(b[0], 0f, b[2]);
                for (int k = 0; k <= TRAMOS; k++) {
                    float t = k / (float) TRAMOS;
                    float onda = amp * t * (float) Math.sin(2.0 * Math.PI * s / per);
                    g[i][k] = new float[]{b[0] + d[0] * L * t + n[0] * onda, b[1] + d[1] * L * t + n[1] * onda,
                            b[2] + d[2] * L * t + n[2] * onda, (24f + s) / 64f, (20f + y) / 64f};
                }
            }
            emitirTira(g, e, vc, luz, ov);
        }
    }

    static void emitirTira(float[][][] g, MatrixStack.Entry e, VertexConsumer vc, int luz, int ov) {
        for (int j = 0; j < g.length - 1; j++) {
            for (int k = 0; k < g[j].length - 1; k++) {
                float[][] q = {g[j][k], g[j + 1][k], g[j + 1][k + 1], g[j][k + 1]};
                float[] n = unitario(
                        (q[1][1] - q[0][1]) * (q[3][2] - q[0][2]) - (q[1][2] - q[0][2]) * (q[3][1] - q[0][1]),
                        (q[1][2] - q[0][2]) * (q[3][0] - q[0][0]) - (q[1][0] - q[0][0]) * (q[3][2] - q[0][2]),
                        (q[1][0] - q[0][0]) * (q[3][1] - q[0][1]) - (q[1][1] - q[0][1]) * (q[3][0] - q[0][0]));
                for (float[] v : q) {
                    vc.vertex(e.getPositionMatrix(), v[0] / 16f, v[1] / 16f, v[2] / 16f)
                            .color(0xFFFFFFFF)
                            .texture(v[3], v[4])
                            .overlay(ov)
                            .light(luz)
                            .normal(e, n[0], n[1], n[2]);
                }
            }
        }
    }

    /** Columnas de cada lado del centro del frente que cubre el delantal (0,5 px por columna → 6 px a cada lado). */
    public static final int DELANTAL_COLUMNAS = 12;

    /**
     * El delantal (2026-10-11, "un delantal que sea de malla"): la misma malla que la pollera, en forma de tubo casi
     * recto, pero solo el arco del frente ({@link #DELANTAL_COLUMNAS} columnas a cada lado del centro), que arranca a
     * {@code inicio} px bajo el hombro y mide {@code largoPx}. Las UV son lineales: {@code v} baja de 20 a 20 + largo
     * (1 px de modelo = 1 px de textura) y {@code u} sale del largo de arco con el centro del frente en 24.
     */
    public static void dibujarDelantal(MatrixStack matrices, VertexConsumer vc, int luz, float dil, @Nullable Piernas piernas,
                                       CapaMalla.Movimiento mov, float twirl, float cola, float inclinacion,
                                       float inicio, float largoPx, @Nullable PolleraVolado volado,
                                       com.modamod.item.Ruedo hem, @Nullable float[] extraPorFila, @Nullable AdornoDelantal adorno,
                                       int columnas) {
        dibujar(matrices, vc, luz, PolleraForma.TUBO, PolleraLargo.MAXIMO, volado, null, hem, dil, piernas, mov, twirl, cola,
                inclinacion, inicio, 20f, 20f + largoPx, 0, extraPorFila, largoPx,
                COL_FRENTE - columnas, COL_FRENTE + columnas, adorno, Float.NaN, Float.NaN);
    }

    /**
     * La tela de atrás pasa por fuera de la cola (2026-10-02, "el trasero
     * deberia crecer con el busto"): cada punto de la espalda se aleja hasta
     * la superficie de las nalgas (las mismas cúpulas que dibuja
     * {@code BustoRender}, aproximadas) más un margen; debajo de la parte más
     * saliente la tela cae derecho, como de un estante.
     */
    private static void pasarPorFueraDeLaCola(float[][][] p, float cola) {
        float cy = 10.1f - 0.12f * cola, rArriba = 1.9f + 0.25f * cola;
        float rx = Math.min(2.4f, 1.9f + 0.12f * cola);
        float arriba = cy - rArriba;
        for (float[][] fila : p) {
            for (float[] q : fila) {
                if (q[2] < 0.5f || q[1] < arriba) continue;        // solo la espalda (+Z), desde donde empieza la cola
                float y = Math.min(cy, q[1]);                       // debajo de lo más saliente, cae derecho
                // Entre las dos nalgas la tela va tirante (2026-10-02, "la pollera ahora tiene
                // un tajo"): sin esto el centro de la espalda quedaba adentro, como un tajo.
                float x = Math.max(2f, Math.abs(q[0]));
                float du = (x - 2f) / rx, dv = (y - cy) / rArriba;
                float d2 = du * du + dv * dv;
                float g = d2 < 1f ? (float) Math.sqrt(1f - d2) : 0f;
                q[2] = Math.max(q[2], 2.2f + cola * g);
            }
        }
    }

    /**
     * Cuánto sale la pollera quieta por fuera de la caja del torso a la altura
     * {@code y} (px; −1 si ahí no hay pollera): lo más que sale de los costados
     * o del frente/espalda. Lo usa la tela de encima (el hoodie, 2026-10-02,
     * "el hoodie se abre por fuera") para pasar por fuera de la pollera.
     */
    public static float holguraEn(PolleraForma forma, PolleraLargo largo, @Nullable PolleraVolado voladoRuedo,
                                  @Nullable PolleraVolado voladoTodo, float dil, float y, float cintura) {
        float l = largo.pixeles;
        if (y <= cintura || y > cintura + l + 0.5f) return -1f;
        float t = Math.min(1f, (y - cintura) / l);
        float holgura = Math.max(dil, 0.25f);
        float a0 = 4f + holgura + 0.1f, b0 = 2f + holgura + 0.1f;
        float max = 0f;
        for (float[] q : anillo(t, a0, b0, l, 0f, forma, voladoRuedo, voladoTodo)) {
            max = Math.max(max, Math.max(Math.abs(q[0]) - 4f, Math.abs(q[1]) - 2f));
        }
        return max;
    }

    /**
     * La tela cuelga hacia abajo aunque el torso se incline (2026-10-02,
     * "cuando es larga y shifteas embolsa las piernas"): agachado, el torso
     * se va para adelante y las piernas quedan derechas, así que la pollera
     * (que vive en el marco del torso) se iba para atrás y las piernas la
     * atravesaban; el choque la estiraba alrededor de cada pierna como una
     * bolsa. Ahora cada fila gira alrededor de la cintura lo contrario del
     * torso, de a poco desde la cintura (que sigue pegada) hasta el ruedo.
     */
    private static void colgar(float[][][] p, float inclinacion, float cintura) {
        for (int f = 1; f <= FILAS; f++) {
            float t = f / (float) FILAS;
            float peso = Math.min(1f, t * 2.5f);
            float a = -inclinacion * peso;
            float co = (float) Math.cos(a), si = (float) Math.sin(a);
            for (float[] q : p[f]) {
                // Como ModelPart.rotate (rotateX): y' = y·cos − z·sen, z' = y·sen + z·cos.
                float y = q[1] - cintura, z = q[2];
                q[1] = cintura + y * co - z * si;
                q[2] = y * si + z * co;
            }
        }
    }

    /**
     * Un anillo de la malla a la altura {@code t} (0 = cintura, 1 = ruedo),
     * sin movimiento: la sección redondeada que se abre hacia abajo y, en la
     * tableada, el zigzag de las tablas. Columna 0 y {@link #COLUMNAS} son
     * el mismo punto (la costura).
     */
    /**
     * Memoria de {@link #anillo0} (2026-10-12, "vamos por las seguras y despues midamos"): armar un anillo cuesta 240
     * muestras de superelipse con {@code pow}, y se pide una vez por fila (25) por pollera, delantal y faldón en cada
     * cuadro, más {@code holguraEn} desde el hoodie y el delantal. Quieto, los parámetros se repiten cuadro a cuadro.
     * Los arreglos devueltos NO se pueden modificar (están compartidos).
     */
    private record ClaveAnillo(float t, float a0, float b0, float l, float abrir, PolleraForma forma,
                               @Nullable PolleraVolado ruedo, @Nullable PolleraVolado todo) {}

    private static final java.util.HashMap<ClaveAnillo, float[][]> ANILLOS = new java.util.HashMap<>();

    private static float[][] anillo(float t, float a0, float b0, float l, float abrir, PolleraForma forma,
                                    @Nullable PolleraVolado voladoRuedo, @Nullable PolleraVolado voladoTodo) {
        ClaveAnillo clave = new ClaveAnillo(t, a0, b0, l, abrir, forma, voladoRuedo, voladoTodo);
        float[][] hecho = ANILLOS.get(clave);
        if (hecho == null) {
            // Moviéndose, `abrir` cambia cada cuadro: sin tope la memoria crecería sin parar.
            if (ANILLOS.size() > 4096) ANILLOS.clear();
            hecho = anillo0(t, a0, b0, l, abrir, forma, voladoRuedo, voladoTodo);
            ANILLOS.put(clave, hecho);
        }
        return hecho;
    }

    private static float[][] anillo0(float t, float a0, float b0, float l, float abrir, PolleraForma forma,
                                    @Nullable PolleraVolado voladoRuedo, @Nullable PolleraVolado voladoTodo) {
        boolean tableada = forma == PolleraForma.TABLEADA;
        float peso = (float) Math.pow(t, 1.6);
        float rx, rz;      // cuánto se abre la tela del torso al ruedo, en cada eje
        float n;           // exponente de la superelipse de la sección
        switch (forma) {
            case TUBO -> {
                // Recta (2026-10-05, "pollera de tubo"): casi sin vuelo, sección de caja redondeada todo el largo.
                rx = (0.25f + 0.04f * l) * t;
                rz = (0.3f + 0.05f * l) * t;
                n = 5f + 5f * (1f - t) * (1f - t);
            }
            case GLOBO -> {
                // Abombada (2026-10-05, "pollera de globo"): ancha en el medio y cerrada en el ruedo.
                float panza = (float) Math.sin(Math.PI * Math.pow(Math.min(1f, t * 1.04f), 0.8));
                rx = (1.3f + 0.2f * l) * panza + 0.3f * t;
                rz = (1.9f + 0.28f * l) * panza + 0.4f * t;
                n = 2.6f + 6f * (1f - t) * (1f - t);
            }
            case CIRCULAR -> {
                // De círculo completo (2026-10-05, "pollera circular"): se abre ya desde la cadera.
                float curva = (float) Math.pow(t, 0.9);
                rx = (1.4f + 0.31f * l) * curva;
                rz = (2.0f + 0.44f * l) * curva;
                n = 2.2f + 5f * (1f - t) * (1f - t);
            }
            default -> {
                float curva = (float) Math.pow(t, 1.15);
                rx = (0.8f + 0.18f * l) * curva;
                rz = (1.2f + 0.26f * l) * curva;
                // De caja (12, casi recta: la cintura tapa las esquinas del torso
                // ahora que no hay cinto, 2026-10-01) a casi elipse (2.4) en el ruedo.
                n = 2.4f + 9.6f * (1f - t) * (1f - t);
            }
        }
        // Volados (2026-10-05): cada fila de volado nace pegada y se abre hacia su borde de abajo, con ondas.
        float[] vol = volado(t, voladoRuedo, voladoTodo);
        float a = (a0 + rx) * (1f + abrir * peso) + vol[0], b = (b0 + rz) * (1f + abrir * peso) + vol[0];
        float[][] out = new float[COLUMNAS + 1][];
        // Columnas a igual largo de arco, con el centro del frente en COL_FRENTE (2026-10-04, "es literal un corte
        // en la tela, no enganchan las dos partes"): espaciadas por el perímetro de la caja (s parejo), la vuelta
        // de tela no repartía su u entre 16 y 40 y en el ruedo la costura leía texeles de afuera de la franja del
        // torso (transparentes). Con arco parejo la costura cae justo en u 16 = 40.
        final int muestras = 120;
        float[] arco = new float[muestras + 1];
        float[] ant = seccion(0f, a, b, n);
        for (int i = 1; i <= muestras; i++) {
            float[] q = seccion(i * PERIMETRO / muestras, a, b, n);
            arco[i] = arco[i - 1] + (float) Math.hypot(q[0] - ant[0], q[1] - ant[1]);
            ant = q;
        }
        float total = arco[muestras], arcoFrente = arco[muestras / 3];   // s = 8
        for (int c = 0; c <= COLUMNAS; c++) {
            // Mismo entero para la columna 0 y la última: la costura cierra exacta.
            int k = Math.floorMod(c - COL_FRENTE, COLUMNAS);
            float objetivo = (arcoFrente + k * total / COLUMNAS) % total;
            int i = java.util.Arrays.binarySearch(arco, objetivo);
            if (i < 0) i = -i - 2;
            i = Math.max(0, Math.min(muestras - 1, i));
            float tramo = arco[i + 1] - arco[i];
            float s = (i + (tramo <= 1e-6f ? 0f : (objetivo - arco[i]) / tramo)) * PERIMETRO / muestras;
            float[] q = seccion(s, a, b, n);
            float x = q[0], z = q[1];
            if (tableada) {
                // Zigzag: 4 columnas por tabla, picos afuera/adentro alternados.
                float fase = (c * (float) TABLAS / COLUMNAS) % 1f;
                float tri = (fase < 0.5f ? fase * 2f : 2f - fase * 2f) * 2f - 1f;
                float amp = (0.45f + 0.035f * l) * t;
                float nx = x / (a * a), nz = z / (b * b);
                float len = (float) Math.sqrt(nx * nx + nz * nz);
                if (len > 1e-4f) {
                    x += nx / len * amp * tri;
                    z += nz / len * amp * tri;
                }
            }
            if (vol[1] > 1e-4f) {
                // Ondas del volado: radiales, vol[2] por vuelta (más chicas y apretadas en el recto).
                float onda = (float) Math.sin(2.0 * Math.PI * vol[2] * c / COLUMNAS) * vol[1];
                float nx = x / (a * a), nz = z / (b * b);
                float len = (float) Math.sqrt(nx * nx + nz * nz);
                if (len > 1e-4f) {
                    x += nx / len * onda;
                    z += nz / len * onda;
                }
            }
            out[c] = new float[]{x, z};
        }
        return out;
    }

    /** Cuánto se mueve la tela de cada forma respecto de la campana (2026-10-05). */
    private static float movimientoDe(PolleraForma forma) {
        return switch (forma) {
            case TUBO -> 0.25f;
            case GLOBO -> 0.6f;
            case CIRCULAR -> 1.25f;
            default -> 1f;
        };
    }

    /**
     * Los volados a la altura {@code t} (0 = cintura, 1 = ruedo): {radio extra, amplitud de las ondas, ondas por
     * vuelta} (2026-10-05, "dos moldes de volado, uno recto y uno circular... borde inferior o toda la pollera").
     * Solo el del ruedo: una fila en el último tercio. El de toda la pollera: tres filas parejas; si hay los dos, el
     * del ruedo manda en la última fila. Cada fila arranca pegada a la tela de arriba (0,3 px) y se abre hasta su
     * borde; la fila de abajo vuelve a arrancar pegada, así queda el escalón de un volado sobre el otro.
     */
    private static float[] volado(float t, @Nullable PolleraVolado ruedo, @Nullable PolleraVolado todo) {
        PolleraVolado tipo;
        float t0, t1;
        if (todo != null) {
            int fila = Math.min(2, (int) (t * 3f));
            t0 = fila / 3f;
            t1 = (fila + 1) / 3f;
            tipo = fila == 2 && ruedo != null ? ruedo : todo;
        } else if (ruedo != null && t >= 0.68f) {
            t0 = 0.68f;
            t1 = 1f;
            tipo = ruedo;
        } else {
            return new float[]{0f, 0f, 0f};
        }
        if (t <= 0f) return new float[]{0f, 0f, 0f};
        float s = Math.min(1f, (t - t0) / (t1 - t0));
        float abrir = tipo == PolleraVolado.CIRCULAR ? 2.4f : 0.9f;
        float amp = tipo == PolleraVolado.CIRCULAR ? 0.8f : 0.3f;
        float ondas = tipo == PolleraVolado.CIRCULAR ? 6f : 16f;
        return new float[]{0.3f + abrir * (float) Math.pow(s, 1.25), amp * s, ondas};
    }

    /**
     * Cómo se reparte la tela sobre la pollera quieta (2026-10-01, "alguna
     * forma de acomodar las UVs de las polleras para que los patrones no se
     * deformen?"). Antes la textura se repartía por la posición sobre la
     * caja del torso: los costados del ruedo (casi la mitad de la vuelta)
     * recibían 1/6 de la tela cada uno y los 12 px de alto se estiraban o
     * aplastaban según el largo. Ahora:
     * <ul>
     *   <li>u: en cada anillo, proporcional al largo de tela recorrido (un
     *       px de textura mide lo mismo de frente que de costado);</li>
     *   <li>v: proporcional al largo de tela bajando desde la cintura (no a
     *       la altura), así las filas de textura miden lo mismo en toda la
     *       pollera.</li>
     * </ul>
     * Lo que queda (el ruedo es más ancho que la cintura y los 24 px de
     * textura dan la vuelta en todas las filas) lo corrige
     * {@code PatronGenerador}, que dibuja los patrones de la pollera ya
     * "desplegados" con {@link #circunferenciaEn} y {@link Perfil#largoTela}.
     *
     * <p>Sin calce (la dilatación cambia la vuelta en décimas) y con las
     * piernas abiertas, la forma de siempre.
     */
    public static final class Perfil {
        /** u (px de skin, 16..40) por fila y columna. */
        final float[][] u = new float[FILAS + 1][COLUMNAS + 1];
        /** v (px de skin, 20..32) por fila. */
        final float[] v = new float[FILAS + 1];
        /** Vuelta de cada anillo, en px de tela. */
        final float[] vuelta = new float[FILAS + 1];
        /** Fracción del largo de tela (0..1) en cada fila. */
        final float[] fraccion = new float[FILAS + 1];
        /** Largo de la tela de la cintura al ruedo, en px. */
        public float largoTela;
        /**
         * Cuántas filas de la textura (de las 12 de la franja del torso) ocupa la tela (2026-10-08, "la textura en la
         * pollera se ve muy estrechada cuando es mas corta... podemos adaptar las uvs?"): la misma densidad de
         * texeles que a lo ancho de la cintura (24 por vuelta), entre 4 y 12 filas. Una pollera corta usa solo las de
         * arriba (el contenido se recorta en vez de achatarse); una larga las estira un poco, como siempre.
         */
        public float vAlto = 12f;

        /** Vuelta (px de tela) a la fracción {@code q} (0 = cintura, 1 = ruedo) del largo de tela. */
        public float circunferenciaEn(float q) {
            if (q <= 0f) return vuelta[0];
            for (int f = 1; f <= FILAS; f++) {
                if (q <= fraccion[f]) {
                    float tramo = fraccion[f] - fraccion[f - 1];
                    float k = tramo <= 1e-6f ? 0f : (q - fraccion[f - 1]) / tramo;
                    return vuelta[f - 1] + (vuelta[f] - vuelta[f - 1]) * k;
                }
            }
            return vuelta[FILAS];
        }
    }

    private static final java.util.Map<String, Perfil> PERFILES = new java.util.HashMap<>();

    /** Siempre de la pollera lisa (sin volados): las UV de la tela no cambian con ellos. */
    public static Perfil perfil(PolleraForma forma, PolleraLargo largo) {
        return perfil(forma, largo, 0f);
    }

    /**
     * Con la cola (2026-10-07, "minimizar la deformacion de los patrones en las polleras sobre todo atras"): la tela
     * de atrás pasa por fuera de las nalgas ({@link #pasarPorFueraDeLaCola}), así que ahí mide más que la pollera sin
     * cola y el dibujo salía estirado. El perfil se calcula con esa misma deformación, por pasos de media unidad de
     * cola ({@link #pasoDeCola}), para que las UV y los patrones "desplegados" la tengan en cuenta.
     */
    public static Perfil perfil(PolleraForma forma, PolleraLargo largo, float cola) {
        int paso = pasoDeCola(cola);
        return PERFILES.computeIfAbsent(forma + "|" + largo + "|" + paso, k -> calcularPerfil(forma, largo, paso / 2f));
    }

    /** La cola en pasos de media unidad (0 si casi no hay): la clave de los perfiles y de las texturas de la pollera. */
    public static int pasoDeCola(float cola) {
        return cola > 0.05f ? Math.min(12, Math.round(cola * 2f)) : 0;
    }

    private static Perfil calcularPerfil(PolleraForma forma, PolleraLargo largo, float cola) {
        Perfil pf = new Perfil();
        float l = largo.pixeles;
        float a0 = 4.35f, b0 = 2.35f;
        float[][][] anillos = new float[FILAS + 1][][];
        for (int f = 0; f <= FILAS; f++) anillos[f] = anillo(f / (float) FILAS, a0, b0, l, 0f, forma, null, null).clone();
        if (cola > 0.05f) {
            // La misma deformación de la espalda que hace la malla al dibujar (con y, que anillo() no trae).
            float[][][] p3 = new float[FILAS + 1][COLUMNAS + 1][];
            for (int f = 0; f <= FILAS; f++) {
                float y = Y_CINTURA + f / (float) FILAS * l;
                for (int c = 0; c <= COLUMNAS; c++) p3[f][c] = new float[]{anillos[f][c][0], y, anillos[f][c][1]};
            }
            pasarPorFueraDeLaCola(p3, cola);
            for (int f = 0; f <= FILAS; f++) {
                for (int c = 0; c <= COLUMNAS; c++) anillos[f][c] = new float[]{p3[f][c][0], p3[f][c][2]};
            }
        }

        // u: largo recorrido sobre cada anillo, con el centro del frente
        // clavado en u 24 y el de la espalda en u 36 (cada mitad de la vuelta
        // = 12 px de textura): si no, el centro se correría de fila en fila y
        // las rayas verticales saldrían torcidas.
        for (int f = 0; f <= FILAS; f++) {
            float[] acum = new float[COLUMNAS + 1];
            for (int c = 1; c <= COLUMNAS; c++) {
                float dx = anillos[f][c][0] - anillos[f][c - 1][0], dz = anillos[f][c][1] - anillos[f][c - 1][1];
                acum[c] = acum[c - 1] + (float) Math.sqrt(dx * dx + dz * dz);
            }
            float total = acum[COLUMNAS];
            float frente = acum[COL_FRENTE], espalda = acum[COL_ESPALDA];
            float mitadIzq = espalda - frente, mitadDer = total - mitadIzq;
            pf.vuelta[f] = total;
            for (int c = 0; c <= COLUMNAS; c++) {
                if (c < COL_FRENTE) pf.u[f][c] = 24f - 12f * (frente - acum[c]) / mitadDer;
                else if (c <= COL_ESPALDA) pf.u[f][c] = 24f + 12f * (acum[c] - frente) / mitadIzq;
                else pf.u[f][c] = 36f + 12f * (acum[c] - espalda) / mitadDer;
                pf.u[f][c] = Math.max(16f, Math.min(40f, pf.u[f][c]));   // la franja del torso, ni un px afuera
            }
        }
        // v: largo de tela bajando (promedio de todas las columnas).
        float[] bajada = new float[FILAS + 1];
        float dy = l / FILAS;
        for (int f = 1; f <= FILAS; f++) {
            float suma = 0f;
            for (int c = 0; c < COLUMNAS; c++) {
                float dx = anillos[f][c][0] - anillos[f - 1][c][0], dz = anillos[f][c][1] - anillos[f - 1][c][1];
                suma += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            bajada[f] = bajada[f - 1] + suma / COLUMNAS;
        }
        pf.largoTela = bajada[FILAS];
        pf.vAlto = Math.max(4f, Math.min(12f, pf.largoTela * 24f / Math.max(1f, pf.vuelta[0])));
        for (int f = 0; f <= FILAS; f++) {
            pf.fraccion[f] = bajada[f] / pf.largoTela;
            pf.v[f] = 20f + pf.vAlto * pf.fraccion[f];
        }
        return pf;
    }

    /**
     * Punto del perímetro {@code s} (0..24, mismo recorrido que la franja de
     * la caja: derecho de atrás hacia adelante, frente, izquierdo, espalda),
     * redondeado a una superelipse de exponente {@code n}.
     */
    private static float[] seccion(float s, float a, float b, float n) {
        float rx, rz;
        if (s < 4f) { rx = -1f; rz = 1f - s / 2f; }
        else if (s < 12f) { rx = -1f + (s - 4f) / 4f; rz = -1f; }
        else if (s < 16f) { rx = 1f; rz = -1f + (s - 12f) / 2f; }
        else { rx = 1f - (s - 16f) / 4f; rz = 1f; }
        double th = Math.atan2(rz, rx);
        double co = Math.cos(th), si = Math.sin(th);
        float x = (float) (a * Math.signum(co) * Math.pow(Math.abs(co), 2.0 / n));
        float z = (float) (b * Math.signum(si) * Math.pow(Math.abs(si), 2.0 / n));
        return new float[]{x, z};
    }

    /**
     * Colisión con las piernas: todo punto de tela que quede adentro de una
     * pierna (caja con esquinas redondeadas, un poco inflada) sale hacia
     * AFUERA de la pollera — por el radio de la malla, no por la cara más
     * cercana, así la tela nunca se mete entre las piernas. Después se
     * suavizan los corrimientos con los vecinos (sin eso queda un pico donde
     * la pierna "pincha") y se vuelve a chocar, para que el suavizado no
     * deje nada adentro. La cintura (fila 0) no se toca.
     */
    private static void chocarConPiernas(float[][][] p, Piernas piernas) {
        float[][][] despl = new float[FILAS + 1][COLUMNAS][];
        boolean alguno = false;
        for (int f = 1; f <= FILAS; f++) {
            for (int c = 0; c < COLUMNAS; c++) {
                float[] d = empujar(p[f][c], piernas);
                despl[f][c] = d;
                alguno |= d != null;
            }
        }
        if (!alguno) return;
        for (int f = 1; f <= FILAS; f++) {
            for (int c = 0; c < COLUMNAS; c++) {
                float sx = 0, sy = 0, sz = 0, w = 0;
                for (int df = -1; df <= 1; df++) {
                    int ff = f + df;
                    if (ff < 1 || ff > FILAS) continue;
                    for (int dc = -2; dc <= 2; dc++) {
                        float[] d = despl[ff][Math.floorMod(c + dc, COLUMNAS)];
                        float peso = (3 - Math.abs(dc)) * (df == 0 ? 2 : 1);
                        w += peso;
                        if (d == null) continue;
                        sx += d[0] * peso;
                        sy += d[1] * peso;
                        sz += d[2] * peso;
                    }
                }
                if (sx == 0 && sy == 0 && sz == 0) continue;
                float[] q = p[f][c];
                q[0] += sx / w;
                q[1] += sy / w;
                q[2] += sz / w;
                empujar(q, piernas);
            }
            // La columna del final es la misma que la 0 (cierra el tubo).
            p[f][COLUMNAS][0] = p[f][0][0];
            p[f][COLUMNAS][1] = p[f][0][1];
            p[f][COLUMNAS][2] = p[f][0][2];
        }
    }

    /**
     * Saca {@code q} (x, y, z en el marco del torso; se modifica) de las dos
     * piernas. Devuelve el corrimiento aplicado, o null si no estaba adentro.
     */
    @Nullable
    static float[] empujar(float[] q, Piernas piernas) {
        float x0 = q[0], y0 = q[1], z0 = q[2];
        boolean movido = false;
        Vector3f v = new Vector3f();
        Vector3f dir = new Vector3f();
        for (int i = 0; i < 2; i++) {
            piernas.aLocal[i].transformPosition(v.set(q[0], q[1], q[2]));
            // Solo a lo largo de la pierna (arriba de la cadera la tela está en el torso).
            if (v.y < 0.5f || v.y > piernas.hasta || !dentroDePierna(v.x, v.z, piernas.aire)) continue;
            // Hacia afuera de la pollera: el radio de la malla en el torso, llevado a la pierna.
            piernas.aLocal[i].transformDirection(dir.set(q[0], 0f, q[2]));
            dir.y = 0f;
            if (dir.lengthSquared() < 1e-6f) dir.set(v.x, 0f, v.z);
            if (dir.lengthSquared() < 1e-6f) dir.set(0f, 0f, -1f);
            dir.normalize(0.2f);
            for (int k = 0; k < 60 && dentroDePierna(v.x, v.z, piernas.aire); k++) v.add(dir);
            piernas.aTorso[i].transformPosition(v);
            q[0] = v.x;
            q[1] = v.y;
            q[2] = v.z;
            movido = true;
        }
        return movido ? new float[]{q[0] - x0, q[1] - y0, q[2] - z0} : null;
    }

    /** ¿El punto (x, z) de la sección de la pierna cae adentro de la caja inflada con esquinas redondeadas? */
    static boolean dentroDePierna(float x, float z) {
        return dentroDePierna(x, z, 0f);
    }

    static boolean dentroDePierna(float x, float z, float aire) {
        float lado = MEDIO_ANCHO_PIERNA + aire - ESQUINA_PIERNA;
        float cx = Math.max(-lado, Math.min(lado, x)), cz = Math.max(-lado, Math.min(lado, z));
        float dx = x - cx, dz = z - cz;
        return dx * dx + dz * dz < ESQUINA_PIERNA * ESQUINA_PIERNA;
    }

    private static void emitir(VertexConsumer vc, MatrixStack.Entry e, float[][][] p, int f, int c, int luz, int ov) {
        float[] v = p[f][c];
        // Normal por diferencias con los vecinos (la columna 0 y la última son el mismo punto).
        float[] cIzq = p[f][c == 0 ? COLUMNAS - 1 : c - 1];
        float[] cDer = p[f][c == COLUMNAS ? 1 : c + 1];
        float[] fArr = p[Math.max(0, f - 1)][c];
        float[] fAba = p[Math.min(FILAS, f + 1)][c];
        float tx = cDer[0] - cIzq[0], ty = cDer[1] - cIzq[1], tz = cDer[2] - cIzq[2];
        float lx = fAba[0] - fArr[0], ly = fAba[1] - fArr[1], lz = fAba[2] - fArr[2];
        float nx = ty * lz - tz * ly, ny = tz * lx - tx * lz, nz = tx * ly - ty * lx;
        // Que apunte hacia afuera del tubo.
        if (nx * v[0] + nz * v[2] < 0f) { nx = -nx; ny = -ny; nz = -nz; }
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-5f) { nx /= len; ny /= len; nz /= len; } else { nx = 0; ny = 0; nz = -1; }
        if (MallaCapturada.grabando != null) {
            MallaCapturada.grabando.agregar(e.getPositionMatrix(), e.getNormalMatrix(),
                    v[0] / 16f, v[1] / 16f, v[2] / 16f, v[3], v[4], nx, ny, nz);
        }
        vc.vertex(e.getPositionMatrix(), v[0] / 16f, v[1] / 16f, v[2] / 16f)
                .color(0xFFFFFFFF)
                .texture(v[3], v[4])
                .overlay(ov)
                .light(luz)
                .normal(e, nx, ny, nz);
    }
}
