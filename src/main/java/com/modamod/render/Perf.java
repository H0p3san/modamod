package com.modamod.render;

/**
 * Medidor de rendimiento (a pedido, 2026-10-08, "herramientas de testeo de rendimiento y memoria... hud en pantalla,
 * para ver lag y consumo de recursos"). Apagado cuesta un boolean por llamada. Acumula nanosegundos por sección
 * durante el cuadro; {@link com.modamod.client.PerfHud} los pasa a un promedio móvil al final de cada cuadro.
 * Las secciones se anidan (ROPA incluye a las demás), así que no se suman entre sí.
 */
public final class Perf {

    private Perf() {}

    public enum Seccion {
        ROPA("ropa (total)"), POLLERA("pollera"), CAPA("capa"), APLIQUES("apliques"), CORREAS("correas"),
        TEXTURAS("texturas"), TRIM("acabados");

        public final String rotulo;
        Seccion(String rotulo) { this.rotulo = rotulo; }
    }

    public static volatile boolean activo = false;

    private static final long[] ACUM = new long[Seccion.values().length];
    private static final int[] LLAMADAS = new int[Seccion.values().length];

    public static long ini() {
        return activo ? System.nanoTime() : 0L;
    }

    public static void fin(Seccion s, long t0) {
        if (t0 == 0L) return;
        ACUM[s.ordinal()] += System.nanoTime() - t0;
        LLAMADAS[s.ordinal()]++;
    }

    /** Lo acumulado en el cuadro (ms) y las llamadas; los deja en cero. Solo del hilo de render. */
    public static void cerrarCuadro(double[] ms, int[] llamadas) {
        for (int i = 0; i < ACUM.length; i++) {
            ms[i] = ACUM[i] / 1_000_000.0;
            llamadas[i] = LLAMADAS[i];
            ACUM[i] = 0;
            LLAMADAS[i] = 0;
        }
    }
}
