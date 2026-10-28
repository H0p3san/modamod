package com.modamod.render.relieve;

import net.minecraft.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Rebote del busto (2026-10-01, "pechos de distintos tamaños con fisicas
 * estilo resorte"): un resorte amortiguado por entidad, en px de skin, que
 * empujan
 * <ul>
 *   <li>la aceleración vertical del cuerpo (saltar, caer, aterrizar,
 *       agacharse): el busto se queda atrás y después rebota;</li>
 *   <li>cada paso al caminar o correr (dos golpecitos por ciclo de piernas);</li>
 *   <li>los giros del cuerpo, de costado (y el twirl).</li>
 * </ul>
 * Solo cliente, sin estado en el servidor: cada cliente simula a quien ve.
 * Corre con el reloj real (no con los ticks), así que es suave a cualquier
 * cantidad de cuadros. Más busto = más lento y más amplio.
 *
 * <p>El resultado ({@link RelieveRender.Rebote}) corre la zona del busto en
 * el mapa de relieve del frente del torso — en el cuerpo y en todas las
 * telas de encima por igual, así la ropa rebota con él.
 */
public final class FisicaBusto {

    private FisicaBusto() {}

    /** Paso fijo de la integración (s). */
    private static final float PASO = 1f / 240f;
    /** Tope del corrimiento (px). */
    private static final float TOPE_Y = 0.9f, TOPE_X = 0.6f, TOPE_COLA = 0.6f;

    private static final class Estado {
        float y, vy, x, vx;
        /** La cola (2026-10-02, "el trasero... tener tambien fisica"): vertical común y alternado por paso. */
        float cy, vcy, ca, vca;
        float cola;
        double yMundo = Double.NaN, vyMundo;
        float yaw = Float.NaN, vYaw;
        long nanos;
        float resto;
    }

    private static final Map<LivingEntity, Estado> ESTADOS = new WeakHashMap<>();

    /** El rebote de este cuadro para {@code entidad} con este busto (unidades de {@code RelieveCuerpo}). */
    public static RelieveRender.Rebote de(LivingEntity entidad, float tickDelta, float busto) {
        Estado e = ESTADOS.computeIfAbsent(entidad, k -> new Estado());
        long ahora = System.nanoTime();
        float dt = e.nanos == 0 ? 0f : Math.min(0.1f, (ahora - e.nanos) / 1e9f);
        e.nanos = ahora;

        double yMundo = entidad.getLerpedPos(tickDelta).y;
        float yaw = entidad.getBodyYaw();
        if (dt <= 1e-4f || Double.isNaN(e.yMundo)) {
            // Primer cuadro (o el mismo cuadro dibujado dos veces: GUI + mundo).
            if (Double.isNaN(e.yMundo)) {
                e.yMundo = yMundo;
                e.yaw = yaw;
            }
            return rebote(e, busto);
        }

        // Aceleración vertical del cuerpo (bloques/s²) y giro (°/s²).
        double vyMundo = (yMundo - e.yMundo) / dt;
        float ay = (float) Math.max(-60, Math.min(60, (vyMundo - e.vyMundo) / dt));
        e.yMundo = yMundo;
        e.vyMundo = vyMundo;
        float vYaw = wrap(yaw - e.yaw) / dt;
        float aYaw = Math.max(-6000f, Math.min(6000f, (vYaw - e.vYaw) / dt));
        e.yaw = yaw;
        e.vYaw = vYaw;

        // Pasos: dos golpecitos por ciclo de piernas, según cuánto camina.
        float fase = entidad.limbAnimator.getPos(tickDelta) * 0.6662f * 2f;
        float caminar = Math.min(1f, entidad.limbAnimator.getSpeed(tickDelta));

        float masa = 0.6f + 0.12f * busto;
        float k = Math.max(60f, 150f - 14f * busto);          // rigidez (1/s²)
        // Amortiguación 0,45 (era 0,13; 2026-10-04, "las tetas quedan rebotando mucho tiempo"): rebota una o dos veces
        float c = 2f * 0.45f * (float) Math.sqrt(k);
        // Empuje externo (px/s²): el cuerpo sube → el busto se queda abajo (+y es hacia abajo).
        float empujeY = (ay * 16f * 0.12f - caminar * 30f * (float) Math.sin(fase)) * masa;
        float empujeX = -aYaw * 0.004f * masa;

        // La cola: más pesada y un poco más blanda; cada paso empuja una nalga
        // para arriba y la otra para abajo (sen de medio ciclo de piernas).
        float masaC = 0.7f + 0.15f * e.cola;
        float kc = Math.max(55f, 130f - 12f * e.cola);
        float cc = 2f * 0.45f * (float) Math.sqrt(kc);
        float empujeCY = (ay * 16f * 0.1f - caminar * 18f * (float) Math.sin(fase + 0.6f)) * masaC;
        float empujeCA = caminar * 26f * (float) Math.sin(fase * 0.5f) * masaC;

        e.resto += dt;
        while (e.resto >= PASO) {
            e.vcy += (-kc * e.cy - cc * e.vcy + empujeCY) * PASO;
            e.cy += e.vcy * PASO;
            e.vca += (-kc * e.ca - cc * e.vca + empujeCA) * PASO;
            e.ca += e.vca * PASO;
            if (Math.abs(e.cy) > TOPE_COLA) { e.cy = Math.signum(e.cy) * TOPE_COLA; e.vcy *= -0.3f; }
            if (Math.abs(e.ca) > TOPE_COLA) { e.ca = Math.signum(e.ca) * TOPE_COLA; e.vca *= -0.3f; }
            e.resto -= PASO;
            e.vy += (-k * e.y - c * e.vy + empujeY) * PASO;
            e.y += e.vy * PASO;
            e.vx += (-k * e.x - c * e.vx + empujeX) * PASO;
            e.x += e.vx * PASO;
            if (Math.abs(e.y) > TOPE_Y) { e.y = Math.signum(e.y) * TOPE_Y; e.vy *= -0.3f; }
            if (Math.abs(e.x) > TOPE_X) { e.x = Math.signum(e.x) * TOPE_X; e.vx *= -0.3f; }
        }
        return rebote(e, busto);
    }

    /**
     * El rebote de la cola de este cuadro (lo calcula {@link #de}, que se llama
     * antes): dy = la vertical común, dx = lo alternado entre las dos nalgas.
     * {@code cola} = cuánto sale (px): más cola, más lenta y amplia.
     */
    public static RelieveRender.Rebote cola(LivingEntity entidad, float cola) {
        Estado e = ESTADOS.get(entidad);
        if (e == null) return null;
        e.cola = cola;
        return new RelieveRender.Rebote(cola, e.ca / 8f, e.cy / 12f);
    }

    private static RelieveRender.Rebote rebote(Estado e, float busto) {
        // De px a fracción de la cara del frente del torso (8 x 12).
        return new RelieveRender.Rebote(busto, e.x / 8f, e.y / 12f);
    }

    private static float wrap(float grados) {
        grados %= 360f;
        if (grados >= 180f) grados -= 360f;
        if (grados < -180f) grados += 360f;
        return grados;
    }
}
