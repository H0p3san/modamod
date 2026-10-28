package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Silueta del delantal (2026-10-11, "quizas recortes de formas?" → "las formas entran como piña"): se recorta sobre la
 * tela de la malla (texeles transparentes), así que lleva patrones, estampas y teñido igual. Todas las medidas están
 * en píxeles del modelo: {@code s} es el corrimiento lateral desde el centro del frente y {@code y} la distancia desde
 * el borde de arriba del delantal. Por ordinal: nuevos al final.
 */
public enum DelantalForma implements StringIdentifiable {
    /** Rectángulo parejo, como un delantal de cocina. */
    RECTO("recto"),
    /** Más angosto arriba y abierto hacia el ruedo. */
    TRAPECIO("trapecio"),
    /** Babero angosto arriba que se abre en la falda. */
    PETO("peto"),
    /** Rectángulo con el borde de arriba en dos lóbulos. */
    CORAZON("corazon"),
    /** Rectángulo con el ruedo en festones. */
    FESTON("feston");

    public final String clave;

    DelantalForma(String clave) { this.clave = clave; }

    /** Media anchura máxima del paño: lo que cubre la malla a cada lado del centro. */
    public static final float MEDIO_ANCHO = 5.8f;
    /** Radio de las esquinas de abajo cuando son redondeadas. */
    public static final float RADIO_ESQUINA = 2.4f;

    /** Media anchura de la silueta a la altura {@code y} (px desde el borde de arriba). */
    public float medio(float y, float largo) { return medio(y, largo, MEDIO_ANCHO); }

    /** Igual, pero sin pasar de {@code cap} px a cada lado (la cobertura del delantal). */
    public float medio(float y, float largo, float cap) { return Math.min(cap, medioBase(y, largo)); }

    private float medioBase(float y, float largo) {
        return switch (this) {
            case TRAPECIO -> 3.9f + (MEDIO_ANCHO - 3.9f) * Math.min(1f, y / Math.max(1f, largo));
            case PETO -> {
                float yb = Math.min(largo - 2f, Math.max(3.5f, largo * 0.32f));
                float t = Math.max(0f, Math.min(1f, (y - yb) / 2.2f));
                t = t * t * (3f - 2f * t);
                yield 2.9f + (MEDIO_ANCHO - 2.9f) * t;
            }
            default -> MEDIO_ANCHO;
        };
    }

    /** Dónde empieza la silueta por arriba, a {@code a} px del centro (0 salvo en el corazón, con sus dos lóbulos). */
    public float tope(float a) {
        if (this != CORAZON) return 0f;
        float d = a - 2.0f;
        return a > 4.0f ? 2.0f : 2.0f - (float) Math.sqrt(Math.max(0f, 4f - d * d));
    }

    /** ¿El punto (s, y) cae adentro de la silueta de un delantal de {@code largo} px? */
    public boolean contiene(float s, float y, float largo, boolean esquinasRedondas) {
        return contiene(s, y, largo, esquinasRedondas, MEDIO_ANCHO);
    }

    public boolean contiene(float s, float y, float largo, boolean esquinasRedondas, float cap) {
        if (y < 0f || y > largo) return false;
        float a = Math.abs(s);
        float medio = switch (this) {
            case TRAPECIO -> 3.9f + (MEDIO_ANCHO - 3.9f) * Math.min(1f, y / Math.max(1f, largo));
            case PETO -> {
                float yb = Math.min(largo - 2f, Math.max(3.5f, largo * 0.32f));
                float t = Math.max(0f, Math.min(1f, (y - yb) / 2.2f));
                t = t * t * (3f - 2f * t);
                yield 2.9f + (MEDIO_ANCHO - 2.9f) * t;
            }
            default -> MEDIO_ANCHO;
        };
        medio = Math.min(medio, cap);
        if (a > medio) return false;
        if (this == CORAZON) {
            float d = a - 2.0f;
            float tope = a > 4.0f ? 2.0f : 2.0f - (float) Math.sqrt(Math.max(0f, 4f - d * d));
            if (y < tope) return false;
        }
        if (this == FESTON) {
            float fase = ((s + 12f) % 2.4f) / 2.4f * 2f - 1f;
            float caida = 1.5f * (1f - (float) Math.sqrt(Math.max(0f, 1f - fase * fase)));
            if (y > largo - caida) return false;
        }
        if (esquinasRedondas && this != FESTON) {
            float r = RADIO_ESQUINA;
            float cx = medio - r, cy = largo - r;
            if (a > cx && y > cy) {
                float dx = a - cx, dy = y - cy;
                if (dx * dx + dy * dy > r * r) return false;
            }
        }
        return true;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.delantal.forma." + clave; }
}
