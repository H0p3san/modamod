package com.modamod.render;

import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tela blanda de los apliques (2026-10-04, "alguna forma de hacer que los
 * apliques sean tela blanda afectada por el movimiento, lo pondria como
 * configurable por aplique"): dos resortes amortiguados por quien se dibuja
 * (uno BLANDO, para cintas y colas, y otro FIRME, para alas y pétalos), cada
 * uno con 3 ejes. Los empuja la inercia de quien lleva el aplique — subir y
 * caer, arrancar y frenar, girar, cada paso — y {@link ApliqueRenderer} lo
 * convierte en giros de los huesos del modelo según su nombre y cuánta
 * {@code blandura} se le puso a ese aplique (0 = rígido).
 *
 * <p>Solo cliente y sin estado en el servidor (como {@code FisicaBusto}):
 * cada cliente simula a quien ve, con el reloj real. Hay tres fuentes de
 * movimiento ({@link Modo}):
 * <ul>
 *   <li>{@code REAL}: lo que hace la entidad en el mundo;</li>
 *   <li>{@code QUIETO}: nada empuja (las vistas previas de las máquinas);</li>
 *   <li>{@code SACUDIDA}: un vaivén fuerte inventado, para verla en la Mesa
 *       de estilado; {@code BRISA}: uno suave, para el Maniquí.</li>
 * </ul>
 *
 * <p>Los desplazamientos están en el espacio del modelo del jugador (el de
 * {@code ModelPart}: x hacia su izquierda, y hacia los pies, -z al frente) y
 * valen de -1 a 1; la tela se queda ATRÁS de lo que la empuja (si el cuerpo
 * acelera hacia adelante, la tela cuelga hacia atrás).
 */
public final class FisicaApliques {

    private FisicaApliques() {}

    public enum Modo { REAL, QUIETO, SACUDIDA, BRISA }

    /** Lo que el aplique lee este cuadro: blando y firme (x, y, z) en el espacio del modelo. null = rígido. */
    public record Desplazamiento(Vector3f blando, Vector3f firme) {}

    /** El del cuadro que se está dibujando (lo pone {@link #preparar}; null = nada se mueve). */
    @Nullable
    public static Desplazamiento actual;

    /** Lo que se simula en la vista previa de la Mesa de estilado (lo fija su pantalla mientras dibuja). */
    public static Modo modoVistaPrevia = Modo.QUIETO;
    /**
     * Cuánto se exagera lo que se ve en este cuadro (2026-10-04, "el sacudir tiene que tener un overhaul porque se
     * mueve muy sutil como para ver cómo pendulea"): 1 normal, más en {@link Modo#SACUDIDA}. Lo leen los giros de
     * los huesos y el nodo de oscilación de {@code ApliqueRenderer}.
     */
    public static float exageracion = 1f;

    /** La clave de la vista previa (los estados de entidades reales no se tocan). */
    public static final Object CLAVE_VISTA_PREVIA = new Object();

    // Resortes: rigidez (1/s²) y amortiguación (1/s).
    private static final float K_BLANDO = 45f, K_FIRME = 120f;
    private static final float C_BLANDO = 2f * 0.2f * (float) Math.sqrt(K_BLANDO);
    private static final float C_FIRME = 2f * 0.28f * (float) Math.sqrt(K_FIRME);
    private static final float PASO = 1f / 240f;
    /** De aceleración (bloques/s²) a fuerza del resorte. */
    private static final float GANANCIA = 1.5f;

    private static final class Estado {
        final float[] pb = new float[3], vb = new float[3];
        final float[] pf = new float[3], vf = new float[3];
        double x = Double.NaN, z, y, vx, vz, vy;
        float yaw = Float.NaN, vYaw;
        long nanos;
        float resto;
        Desplazamiento ultimo = new Desplazamiento(new Vector3f(), new Vector3f());
    }

    private static final Map<Object, Estado> ESTADOS = new WeakHashMap<>();

    /**
     * Deja en {@link #actual} el desplazamiento de este cuadro para {@code clave}
     * (la entidad, la vista previa o el maniquí). {@code entidad} solo hace
     * falta en el modo {@code REAL}.
     */
    public static void preparar(Object clave, @Nullable LivingEntity entidad, float tickDelta, Modo modo) {
        Estado e = ESTADOS.computeIfAbsent(clave, k -> new Estado());
        exageracion = modo == Modo.SACUDIDA ? 2.5f : 1f;
        long ahora = System.nanoTime();
        float dt = e.nanos == 0 ? 0f : Math.min(0.1f, (ahora - e.nanos) / 1e9f);
        e.nanos = ahora;
        if (dt <= 1e-4f) {                 // el mismo cuadro dibujado dos veces: no se integra de nuevo
            actual = e.ultimo;
            return;
        }

        float fx = 0f, fy = 0f, fz = 0f;   // fuerza sobre la tela (ya en el espacio del modelo)
        switch (modo) {
            case REAL -> {
                if (entidad != null) {
                    float[] f = fuerzaReal(e, entidad, tickDelta, dt);
                    fx = f[0];
                    fy = f[1];
                    fz = f[2];
                }
            }
            case SACUDIDA -> {
                // Sacudones cada 2,4 s, alternando de lado: un empujón fuerte de 0,35 s y después se suelta, así se
                // ve cómo el aplique pendulea y se va frenando (el resorte blando tarda ~2 s en asentarse).
                double t = (ahora % 1_000_000_000_000L) / 1e9;
                double ciclo = Math.floor(t / 2.4), fase = t - ciclo * 2.4;
                float golpe = fase < 0.35 ? 170f * (float) Math.sin(Math.PI * fase / 0.35) : 0f;
                float lado = ciclo % 2 == 0 ? 1f : -1f;
                fx = lado * golpe;
                fy = golpe * 0.35f * (ciclo % 3 == 0 ? 1f : -1f);
                fz = golpe * 0.55f * (ciclo % 4 < 2 ? 1f : -1f);
            }
            case BRISA -> {
                float t = ahora / 1e9f;
                fx = 5f * (float) Math.sin(t * 2.2f);
                fy = 2.5f * (float) Math.sin(t * 3.1f + 1f);
                fz = 4f * (float) Math.sin(t * 1.7f + 2f);
            }
            default -> { }
        }
        float[] fuerza = { fx, fy, fz };

        e.resto += dt;
        while (e.resto >= PASO) {
            e.resto -= PASO;
            for (int i = 0; i < 3; i++) {
                e.vb[i] += (-K_BLANDO * e.pb[i] - C_BLANDO * e.vb[i] + fuerza[i]) * PASO;
                e.pb[i] += e.vb[i] * PASO;
                e.vf[i] += (-K_FIRME * e.pf[i] - C_FIRME * e.vf[i] + fuerza[i]) * PASO;
                e.pf[i] += e.vf[i] * PASO;
                if (Math.abs(e.pb[i]) > 1f) { e.pb[i] = Math.signum(e.pb[i]); e.vb[i] *= -0.3f; }
                if (Math.abs(e.pf[i]) > 1f) { e.pf[i] = Math.signum(e.pf[i]); e.vf[i] *= -0.3f; }
            }
        }
        e.ultimo = new Desplazamiento(new Vector3f(e.pb[0], e.pb[1], e.pb[2]), new Vector3f(e.pf[0], e.pf[1], e.pf[2]));
        if (modo == Modo.SACUDIDA) {            // lo que ve el aplique: más allá del tope del resorte
            e.ultimo.blando().mul(exageracion);
            e.ultimo.firme().mul(exageracion);
        }
        actual = e.ultimo;
    }

    /** La inercia de {@code entidad}: la fuerza en x (lado), y (vertical) y z (adelante-atrás) del modelo. */
    private static float[] fuerzaReal(Estado e, LivingEntity entidad, float tickDelta, float dt) {
        var pos = entidad.getLerpedPos(tickDelta);
        float yaw = entidad.getBodyYaw();
        if (Double.isNaN(e.x)) {
            e.x = pos.x;
            e.y = pos.y;
            e.z = pos.z;
            e.yaw = yaw;
            return new float[3];
        }
        double vx = (pos.x - e.x) / dt, vy = (pos.y - e.y) / dt, vz = (pos.z - e.z) / dt;
        float ax = (float) Math.max(-60, Math.min(60, (vx - e.vx) / dt));
        float ay = (float) Math.max(-60, Math.min(60, (vy - e.vy) / dt));
        float az = (float) Math.max(-60, Math.min(60, (vz - e.vz) / dt));
        e.x = pos.x; e.y = pos.y; e.z = pos.z;
        e.vx = vx; e.vy = vy; e.vz = vz;
        float vYaw = wrap(yaw - e.yaw) / dt;
        float aYaw = Math.max(-6000f, Math.min(6000f, (vYaw - e.vYaw) / dt));
        e.yaw = yaw;
        e.vYaw = vYaw;

        // De la aceleración del mundo a los ejes del cuerpo: adelante = (-sen yaw, cos yaw) del mundo.
        double rad = Math.toRadians(yaw);
        float adelante = (float) (-Math.sin(rad) * ax + Math.cos(rad) * az);
        float izquierda = (float) (Math.cos(rad) * ax + Math.sin(rad) * az);

        // Cada paso: dos golpecitos por ciclo de piernas.
        float fase = entidad.limbAnimator.getPos(tickDelta) * 0.6662f * 2f;
        float caminar = Math.min(1f, entidad.limbAnimator.getSpeed(tickDelta));

        // La tela se queda atrás: adelante → +z, izquierda → -x, subir → +y (hacia los pies).
        float fx = -izquierda * GANANCIA - aYaw * 0.004f * GANANCIA;
        float fy = ay * GANANCIA - caminar * 22f * (float) Math.sin(fase);
        float fz = adelante * GANANCIA;
        return new float[] { fx, fy, fz };
    }

    private static float wrap(float grados) {
        grados %= 360f;
        if (grados >= 180f) grados -= 360f;
        if (grados < -180f) grados += 360f;
        return grados;
    }
}
