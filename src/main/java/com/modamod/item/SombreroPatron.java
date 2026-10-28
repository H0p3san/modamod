package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Dibujo de una zona del sombrero de bruja (2026-10-05, "patrones por zona"): se pinta por código sobre la textura de la
 * zona con su color y un tono de contraste (más claro si el color es oscuro, más oscuro si es claro). Por ordinal:
 * nuevos al final.
 */
public enum SombreroPatron implements StringIdentifiable {
    LISO("liso"),
    RAYAS("rayas"),
    RAYAS_VERTICALES("rayas_verticales"),
    LUNARES("lunares"),
    CUADROS("cuadros");

    public final String clave;

    SombreroPatron(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.sombrero.patron." + clave; }

    public SombreroPatron siguiente() { return values()[(ordinal() + 1) % values().length]; }

    /** ¿El texel (x, y) lleva el tono de contraste? */
    public boolean contraste(int x, int y) {
        return switch (this) {
            case LISO -> false;
            case RAYAS -> (y / 2) % 2 == 1;
            case RAYAS_VERTICALES -> (x / 2) % 2 == 1;
            case LUNARES -> {
                int fila = y / 6;
                int xx = fila % 2 == 0 ? x : x + 3;
                yield xx % 6 < 2 && y % 6 < 2;
            }
            case CUADROS -> ((x / 2) + (y / 2)) % 2 == 1;
        };
    }

    /** El tono de contraste de un color: más oscuro si es claro, más claro si es oscuro. */
    public static int tonoDeContraste(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        if (lum > 0.5) return ((int) (r * 0.55) << 16) | ((int) (g * 0.55) << 8) | (int) (b * 0.55);
        return ((r + (int) ((255 - r) * 0.45)) << 16) | ((g + (int) ((255 - g) * 0.45)) << 8) | (b + (int) ((255 - b) * 0.45));
    }
}
