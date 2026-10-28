package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * El tiro del pantalón: cuánto sube la cintura sobre el cuboide de TORSO,
 * en filas de las 12 (misma unidad que {@link Botamanga}, otra región de
 * UV — ver {@code PiezasDelMod.pantalon}).
 *
 * Es un eje aparte de {@code largo} y no un valor más de largo: largo
 * decide hasta dónde llega la pierna, tiro decide dónde arranca la cintura.
 * Los dos son independientes — un pantalón de tiro alto puede ser bermudas o
 * puede ser largo, no cambia una cosa a la otra.
 */
public enum PantalonTiro implements StringIdentifiable {

    CORTO("corto", 1),
    MEDIO("medio", 2),
    LARGO("largo", 4),
    // Los 7 puntos del molde de rango (2026-10-04, "tiro... extendamos hasta los hombros"): la cintura sube 12, 10,
    // 8, 6, 4, 2 y 0 filas; 4 y 2 son LARGO y MEDIO de siempre. Los nuevos, al FINAL (viajan por ordinal).
    HOMBROS("hombros", 12),
    PECHO("pecho", 10),
    BAJO_PECHO("bajo_pecho", 8),
    MUY_ALTO("muy_alto", 6),
    CADERA("cadera", 0);

    public final String clave;
    public final int filas;

    PantalonTiro(String clave, int filas) {
        this.clave = clave;
        this.filas = filas;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.tiro." + clave;
    }
}
