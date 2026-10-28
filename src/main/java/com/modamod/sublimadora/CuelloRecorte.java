package com.modamod.sublimadora;

import net.minecraft.client.texture.NativeImage;

/**
 * Los cuellos que se recortan en runtime (2026-10-04, "hagamos el cambio del sistema del cuello"): cuadrado y corazón
 * no tienen un archivo por corte — se parte de la textura del cuello REDONDO del mismo largo y se le borra (alfa 0)
 * el escote del frente y el agujero de la tapa de arriba, como hacía {@code tools/generar_cuellos.py}. Sumar un cuello
 * nuevo es agregar su forma acá, sin archivos. Las coordenadas son las del atlas de la skin (64 de ancho), así que
 * sirve a cualquier escala de textura.
 *
 * <p>Formas (texeles del frente del torso, x de 0 a 8, y hacia abajo): el cuadrado va de x 2 a 6 hasta 2,75 de
 * hondo (antes 1,75); el corazón son dos lóbulos que siguen la parte de arriba de cada pecho (centros en x 2 y 6, radio 2) y se
 * juntan en una punta al centro, de x 1 a 7, con los puntos más altos de la tela en y 3,0 y la punta en 5,0
 * ({@link #CORAZON_BAJA}; 2026-10-04, "lo bajaria para que empiecen a cubrir justo encima del medio de la teta").
 * La tapa de arriba tiene el mismo ancho que el corte del frente, las 4 filas de profundidad.
 */
public final class CuelloRecorte {

    private CuelloRecorte() {}

    /** Cuánto baja el escote cuadrado y el redondo (texeles del frente). */
    private static final float CUADRADO_HONDO = 2.75f, REDONDO_HONDO = 1.5f, REDONDO_MEDIO = 2.2f;

    /** Dónde queda la punta del medio del corazón (el borde de los lóbulos está 2 por arriba). */
    private static final float CORAZON_BAJA = 5.0f;

    public static boolean recortaEnRuntime(Variante.Cuello cuello) {
        return cuello == Variante.Cuello.CUADRADO || cuello == Variante.Cuello.CORAZON || cuello == Variante.Cuello.CAMISA
                || cuello == Variante.Cuello.REDONDO;
    }

    /** x, y en texeles del frente: ¿ese punto queda recortado? */
    private static boolean agujero(Variante.Cuello cuello, float x, float y) {
        switch (cuello) {
            case CUADRADO:
                // Bajado (2026-10-08, "bajarle un poco mas el cuello cuadrado y el redondo"): de 1,75 a 2,75 de hondo.
                return x >= 2f && x <= 6f && y < CUADRADO_HONDO;
            case REDONDO: {
                // El redondo de fábrica solo abre la tapa de arriba y el frente queda entero: se le suma un escote de
                // media elipse, de 4,4 de ancho (como la tapa) y REDONDO_HONDO de hondo.
                float dx = (x - 4f) / REDONDO_MEDIO;
                return Math.abs(dx) < 1f && y < REDONDO_HONDO * (float) Math.sqrt(1f - dx * dx);
            }
            case CAMISA:
                // V chica (2026-10-07): 3 texeles de ancho arriba, punta a 3 de hondo.
                return y < 3f && Math.abs(x - 4f) < 1.5f * (1f - y / 3f);
            case CORAZON: {
                if (x < 1f || x > 7f) return false;
                float cx = x <= 4f ? 2f : 6f;
                float borde = CORAZON_BAJA - (float) Math.sqrt(Math.max(0f, 4f - (x - cx) * (x - cx)));
                return y < borde;
            }
            default:
                return false;
        }
    }

    /** Ancho (texeles, de 0 a 8) del agujero de la tapa de arriba. */
    private static float[] anchoTapa(Variante.Cuello cuello) {
        return cuello == Variante.Cuello.CORAZON ? new float[]{1f, 7f}
                : cuello == Variante.Cuello.CAMISA ? new float[]{2.5f, 5.5f} : new float[]{2f, 6f};
    }

    /** Recorta {@code img} (la textura del cuello redondo, del atlas de la skin) con el escote de {@code cuello}. */
    public static void aplicar(NativeImage img, Variante.Cuello cuello) {
        if (!recortaEnRuntime(cuello)) return;
        float k = img.getWidth() / 64f;                // píxeles por texel de la skin
        int x0 = Math.round(20 * k), y0 = Math.round(20 * k);   // esquina del frente del torso
        int ancho = Math.round(8 * k);
        // La tapa de arriba: x como el frente, y de 16 a 20 (el redondo ya trae la suya, de 2 filas).
        float[] tapa = anchoTapa(cuello);
        for (int y = Math.round(16 * k); cuello != Variante.Cuello.REDONDO && y < y0; y++) {
            for (int x = x0; x < x0 + ancho; x++) {
                float tx = (x - x0 + 0.5f) / k;
                if (tx >= tapa[0] && tx <= tapa[1]) img.setColor(x, y, 0);
            }
        }
        // El frente: la forma, con un texel de margen de abajo.
        int alto = Math.round(6 * k);
        for (int y = y0; y < y0 + alto && y < img.getHeight(); y++) {
            for (int x = x0; x < x0 + ancho; x++) {
                if (agujero(cuello, (x - x0 + 0.5f) / k, (y - y0 + 0.5f) / k)) img.setColor(x, y, 0);
            }
        }
    }
}
