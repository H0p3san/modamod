package com.modamod.maniqui;

/**
 * Poses del Maniquí (2026-09-30, "el maniqui que tenga poses": básicas,
 * pasarela, sentado y pose libre por partes). Cada pose es un juego de
 * {@link #ANGULOS} ángulos en grados; elegir una copia sus ángulos al
 * maniquí, y mover cualquier slider de la pantalla lo pasa a {@link #LIBRE}.
 *
 * <p>Orden de los ángulos (el mismo que los sliders):
 * <ol start="0">
 *   <li>cabeza: arriba/abajo (negativo = mira arriba)</li>
 *   <li>cabeza: girar</li>
 *   <li>brazo derecho: adelante/atrás (negativo = adelante)</li>
 *   <li>brazo derecho: abrir (hacia el costado)</li>
 *   <li>brazo izquierdo: adelante/atrás</li>
 *   <li>brazo izquierdo: abrir</li>
 *   <li>pierna derecha: adelante/atrás</li>
 *   <li>pierna derecha: abrir</li>
 *   <li>pierna izquierda: adelante/atrás</li>
 *   <li>pierna izquierda: abrir</li>
 * </ol>
 * "Abrir" es positivo hacia afuera de los dos lados: el renderer le invierte
 * el signo al lado izquierdo.
 *
 * <p>Se guarda por nombre ({@link #name()}), no por ordinal: agregar poses
 * en el medio no rompe maniquíes ya puestos.
 */
public enum PoseManiqui {
    PARADO(new float[] { 0, 0, 0, 5, 0, 5, 0, 0, 0, 0 }),
    // Sin codos no hay jarra de verdad: brazos abiertos y un poco adelante.
    JARRA(new float[] { 0, 0, -12, 32, -12, 32, 0, 4, 0, 4 }),
    SALUDO(new float[] { -5, 12, -165, 20, 0, 5, 0, 0, 0, 0 }),
    ABIERTOS(new float[] { 0, 0, 0, 85, 0, 85, 0, 5, 0, 5 }),
    // Paso congelado: brazo contrario a la pierna.
    PASARELA(new float[] { 4, -8, 25, 4, -25, 4, -20, 0, 20, 0 }),
    // Sentado sobre el plato: piernas hacia adelante y manos sobre las rodillas.
    SENTADO(new float[] { 5, 0, -40, 6, -40, 6, -88, 7, -88, 7 }),
    LIBRE(null);

    public static final int ANGULOS = 10;

    /** Rango de cada slider, en grados: {min, max}. */
    public static final float[][] RANGO = {
            { -60, 60 }, { -80, 80 },
            { -180, 90 }, { -10, 120 }, { -180, 90 }, { -10, 120 },
            { -100, 60 }, { -10, 60 }, { -100, 60 }, { -10, 60 },
    };

    /** Cuánto baja la figura sentada para apoyarse en el plato (px de skin, ya a la escala del maniquí). */
    public static final float BAJADA_SENTADO = 6.0F;

    private final float[] angulos;

    PoseManiqui(float[] angulos) {
        this.angulos = angulos;
    }

    /** Copia de los ángulos de la pose, o null para {@link #LIBRE}. */
    public float[] angulos() {
        return angulos == null ? null : angulos.clone();
    }

    public String traduccion() {
        return "modamod.maniqui.pose." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** La siguiente pose con ángulos propios (salta LIBRE). */
    public PoseManiqui siguiente() {
        PoseManiqui[] v = values();
        PoseManiqui p = v[(ordinal() + 1) % v.length];
        return p == LIBRE ? v[0] : p;
    }

    public static PoseManiqui porNombre(String nombre) {
        for (PoseManiqui p : values()) if (p.name().equals(nombre)) return p;
        return PARADO;
    }
}
