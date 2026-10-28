package com.modamod.render.relieve;

import com.modamod.garment.Parte;

/**
 * Alturas (en px de skin, hacia afuera de cada cara) de los 4 costados de
 * torso, brazos y piernas — 2026-10-01, relieve: "si es un cuerpo musculoso
 * q tenga pectorales y se vean bien marcaditos en la capa de volumen sobre
 * todo en calce pegado y ajustado".
 *
 * <p>Cada cara es una grilla de {@link #R} celdas por px de skin, 12 px de
 * alto. Las caras van en el orden de la franja de la skin: 0 = la primera
 * columna (el costado −x: el derecho del cuerpo, el de afuera del brazo y la
 * pierna derechos), 1 = frente, 2 = el costado +x, 3 = espalda. Dentro de
 * cada cara {@code fu} va como la textura (0 = borde izquierdo de la cara en
 * la skin) y {@code fv} de arriba (0) hacia abajo (1). La cabeza y las tapas
 * no llevan relieve.
 *
 * <p>Se calcula una vez (por cuerpo, calce, prenda...) y después solo se lee:
 * así las capas de ropa pueden "envolver" a la de abajo sin recalcular la
 * cadena entera en cada vértice ({@link RelieveTela}).
 */
public final class MapaRelieve {

    /** Celdas por px de skin. */
    public static final int R = 4;
    static final int FILAS_PX = 12;

    /** Sin relieve: la caja de siempre. */
    public static final MapaRelieve PLANO = new MapaRelieve("plano", false);

    @FunctionalInterface
    public interface Funcion {
        /** Altura en px de skin. {@code anchoPx}: ancho de la cara (para pasar de fracción a px). */
        float altura(Parte parte, int cara, float fu, float fv, int anchoPx);
    }

    public final String clave;
    public final boolean slim;
    /** [parte][cara] → celdas (fila mayor), o null si la cara queda plana. */
    private final float[][][] celdas = new float[5][4][];

    private MapaRelieve(String clave, boolean slim) {
        this.clave = clave;
        this.slim = slim;
    }

    public static MapaRelieve generar(String clave, boolean slim, Funcion f) {
        MapaRelieve m = new MapaRelieve(clave, slim);
        for (Parte parte : Parte.values()) {
            int i = indice(parte);
            if (i < 0) continue;
            for (int cara = 0; cara < 4; cara++) {
                int ancho = anchoPx(parte, cara, slim);
                int nx = ancho * R, ny = FILAS_PX * R;
                float[] g = new float[nx * ny];
                boolean algo = false;
                for (int y = 0; y < ny; y++) {
                    for (int x = 0; x < nx; x++) {
                        float h = f.altura(parte, cara, (x + 0.5f) / nx, (y + 0.5f) / ny, ancho);
                        g[y * nx + x] = h;
                        if (Math.abs(h) > 1e-3f) algo = true;
                    }
                }
                m.celdas[i][cara] = algo ? g : null;
            }
        }
        return m;
    }

    public boolean plana(Parte parte, int cara) {
        int i = indice(parte);
        return i < 0 || celdas[i][cara] == null;
    }

    /** Altura (px) en la fracción (fu, fv) de la cara, bilineal entre celdas. */
    public float altura(Parte parte, int cara, float fu, float fv) {
        int i = indice(parte);
        if (i < 0 || cara < 0 || cara > 3) return 0f;
        float[] g = celdas[i][cara];
        if (g == null) return 0f;
        int nx = anchoPx(parte, cara, slim) * R, ny = FILAS_PX * R;
        float x = clamp(fu, 0f, 1f) * nx - 0.5f, y = clamp(fv, 0f, 1f) * ny - 0.5f;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float tx = x - x0, ty = y - y0;
        int xa = Math.max(0, Math.min(nx - 1, x0)), xb = Math.max(0, Math.min(nx - 1, x0 + 1));
        int ya = Math.max(0, Math.min(ny - 1, y0)), yb = Math.max(0, Math.min(ny - 1, y0 + 1));
        float a = g[ya * nx + xa] + (g[ya * nx + xb] - g[ya * nx + xa]) * tx;
        float b = g[yb * nx + xa] + (g[yb * nx + xb] - g[yb * nx + xa]) * tx;
        return a + (b - a) * ty;
    }

    // ── geometría de la skin ────────────────────────────────────────────────

    static int indice(Parte parte) {
        return switch (parte) {
            case TORSO -> 0;
            case BRAZO_DER -> 1;
            case BRAZO_IZQ -> 2;
            case PIERNA_DER -> 3;
            case PIERNA_IZQ -> 4;
            default -> -1;
        };
    }

    /** Ancho (px) de la cara de frente/espalda de la parte. */
    static int anchoFrente(Parte parte, boolean slim) {
        return switch (parte) {
            case TORSO -> 8;
            case BRAZO_DER, BRAZO_IZQ -> slim ? 3 : 4;
            default -> 4;
        };
    }

    public static int anchoPx(Parte parte, int cara, boolean slim) {
        return cara % 2 == 0 ? 4 : anchoFrente(parte, slim);
    }

    /** Esquina de arriba a la izquierda de la caja de la parte en la skin (px). */
    static int[] origenSkin(Parte parte) {
        return switch (parte) {
            case TORSO -> new int[]{16, 16};
            case BRAZO_DER -> new int[]{40, 16};
            case BRAZO_IZQ -> new int[]{32, 48};
            case PIERNA_DER -> new int[]{0, 16};
            case PIERNA_IZQ -> new int[]{16, 48};
            default -> null;
        };
    }

    /** x (px de skin) donde empieza cada cara: 4 de costado, ancho de frente... */
    static int inicioCara(Parte parte, int cara, boolean slim) {
        int x0 = origenSkin(parte)[0], w = anchoFrente(parte, slim);
        return switch (cara) {
            case 0 -> x0;
            case 1 -> x0 + 4;
            case 2 -> x0 + 4 + w;
            default -> x0 + 8 + w;
        };
    }

    /** La cara que contiene la columna {@code uPx} de la skin, o −1. */
    static int caraEn(Parte parte, float uPx, boolean slim) {
        for (int c = 0; c < 4; c++) {
            int x = inicioCara(parte, c, slim);
            if (uPx >= x && uPx < x + anchoPx(parte, c, slim)) return c;
        }
        return -1;
    }

    /** Fila de arriba de los costados en la skin (px). */
    static int arribaCostados(Parte parte) {
        return origenSkin(parte)[1] + 4;
    }

    static float clamp(float v, float a, float b) {
        return v < a ? a : Math.min(b, v);
    }
}
