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
                || cuello == Variante.Cuello.REDONDO || deHombros(cuello);
    }

    public static boolean deHombros(Variante.Cuello cuello) {
        return cuello == Variante.Cuello.HOMBROS || cuello == Variante.Cuello.HOMBRO_IZQ
                || cuello == Variante.Cuello.HOMBRO_DER;
    }

    /** Cuántas filas por debajo del hombro empieza la tela en los caídos (torso, costado y manga). */
    private static final float HOMBROS_HONDO = 2f;
    /** En los de un solo hombro, a qué parte del ancho (desde el hombro caído) la diagonal llega al hombro tapado. */
    private static final float HOMBRO_DIAGONAL = 0.85f;

    /** Hondo del corte del torso en {@code u} (0 = hombro derecho del jugador, 1 = izquierdo). */
    private static float hondoTorso(Variante.Cuello c, float u) {
        return switch (c) {
            case HOMBROS -> HOMBROS_HONDO;
            case HOMBRO_DER -> HOMBROS_HONDO * Math.max(0f, 1f - u / HOMBRO_DIAGONAL);
            case HOMBRO_IZQ -> HOMBROS_HONDO * Math.max(0f, 1f - (1f - u) / HOMBRO_DIAGONAL);
            default -> 0f;
        };
    }

    /** ¿Se saca la tapa de arriba del torso en {@code u}? El de un hombro deja la mitad del hombro tapado. */
    private static boolean tapaTorso(Variante.Cuello c, float u) {
        return c == Variante.Cuello.HOMBROS || (c == Variante.Cuello.HOMBRO_DER ? u < 0.6f : u > 0.4f);
    }

    /**
     * Recorta una caja del layout de la skin (origen {@code u0, v0}, ancho {@code w}, fondo {@code d}): saca la tapa
     * de arriba donde {@code tapa(u)} y, en las 4 caras, las filas de arriba hasta {@code hondo(u)}. {@code u} va de
     * la derecha del jugador (0) a la izquierda (1) en todas las caras.
     */
    private static void caja(NativeImage img, float k, int u0, int v0, int w, int d,
                             java.util.function.DoublePredicate tapa, java.util.function.DoubleUnaryOperator hondo) {
        // Tapa: (u0+d, v0), w x d; su x va como el frente.
        for (int y = Math.round(v0 * k); y < Math.round((v0 + d) * k); y++) {
            for (int x = Math.round((u0 + d) * k); x < Math.round((u0 + d + w) * k); x++) {
                if (tapa.test(((x + 0.5f) / k - u0 - d) / w)) img.setColor(x, y, 0);
            }
        }
        // Caras, en la fila v0+d: costado derecho (u=0), frente, costado izquierdo (u=1), espalda (u al revés).
        int[][] caras = {{u0, d, 0}, {u0 + d, w, 1}, {u0 + d + w, d, 2}, {u0 + 2 * d + w, w, 3}};
        int fy = v0 + d;
        for (int[] c : caras) {
            for (int x = Math.round(c[0] * k); x < Math.round((c[0] + c[1]) * k); x++) {
                float t = ((x + 0.5f) / k - c[0]) / c[1];
                float u = c[2] == 0 ? 0f : c[2] == 2 ? 1f : c[2] == 1 ? t : 1f - t;
                float h = (float) hondo.applyAsDouble(u);
                for (int y = Math.round(fy * k); y < Math.round((fy + h) * k) && y < img.getHeight(); y++) {
                    img.setColor(x, y, 0);
                }
            }
        }
    }

    /** Los caídos de hombros: torso (base y capa de afuera) y la manga del hombro que queda al aire. */
    private static void recortarHombros(NativeImage img, Variante.Cuello c, float k) {
        for (int v : new int[]{16, 32}) caja(img, k, 16, v, 8, 4, u -> tapaTorso(c, (float) u), u -> hondoTorso(c, (float) u));
        // shortcut: la tapa del brazo es la de 4 de ancho; en brazos finos (3) se lleva 1 columna del puño, que en la
        // manga no se ve. Si alguna vez se nota, pasar el ancho del brazo desde quien compone la textura.
        java.util.function.DoublePredicate todo = u -> true;
        java.util.function.DoubleUnaryOperator parejo = u -> HOMBROS_HONDO;
        if (c != Variante.Cuello.HOMBRO_IZQ) {   // brazo derecho
            caja(img, k, 40, 16, 4, 4, todo, parejo);
            caja(img, k, 40, 32, 4, 4, todo, parejo);
        }
        if (c != Variante.Cuello.HOMBRO_DER) {   // brazo izquierdo
            caja(img, k, 32, 48, 4, 4, todo, parejo);
            caja(img, k, 48, 48, 4, 4, todo, parejo);
        }
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
        if (deHombros(cuello)) {
            recortarHombros(img, cuello, k);
            return;
        }
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
