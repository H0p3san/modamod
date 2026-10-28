package com.modamod.sublimadora;

import net.minecraft.util.StringIdentifiable;

/**
 * Las formas de las máscaras de sublimación (2026-10-02, "que tenga mascaras
 * de sublimacion, cuadrado franja circulo estrella triangulo"): por dónde deja
 * pasar la foto una capa de estampa. Valores nuevos siempre al final (viajan
 * por nombre, pero el orden es el de los moldes en la pestaña).
 */
public enum FormaMascara implements StringIdentifiable {
    CUADRADO("cuadrado"),
    FRANJA("franja"),
    CIRCULO("circulo"),
    ESTRELLA("estrella"),
    TRIANGULO("triangulo");

    public final String clave;

    FormaMascara(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() {
        return clave;
    }

    /** Proporción ancho/alto de la caja de la máscara (la franja es una banda larga). */
    public float proporcion() {
        return this == FRANJA ? 4f : 1f;
    }

    /** ¿El punto (u, v) de la caja de la máscara (−0.5..0.5, v hacia abajo) cae adentro? */
    public boolean contiene(float u, float v) {
        if (u < -0.5f || u > 0.5f || v < -0.5f || v > 0.5f) return false;
        return switch (this) {
            case CUADRADO, FRANJA -> true;
            case CIRCULO -> u * u + v * v <= 0.25f;
            case TRIANGULO -> {
                // Punta arriba (v = −0.5), base abajo: el ancho crece con v.
                float t = v + 0.5f;
                yield Math.abs(u) <= t * 0.5f;
            }
            case ESTRELLA -> {
                // Cinco puntas: radio que va de 0.5 (puntas) a 0.2 (huecos) según el ángulo.
                double r = Math.sqrt(u * u + v * v);
                double a = Math.atan2(u, -v);                    // 0 = arriba
                double sector = Math.PI * 2 / 5;
                double f = Math.abs(((a % sector) + sector) % sector - sector / 2) / (sector / 2); // 1 punta .. 0 hueco
                double limite = 0.2 + (0.5 - 0.2) * f;
                yield r <= limite;
            }
        };
    }
}
