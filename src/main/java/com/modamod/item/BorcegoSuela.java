package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * La suela del calzado (2026-10-08 chata o de plataforma; ampliada 2026-10-14 con las suelas del zip de Claude Design):
 * {@code eje} es su clave en {@code ejes.json}, {@code alzado} cuánto sube la capellada sobre la suela de referencia a la
 * altura de la pierna (px) y {@code filas} las filas de pierna que ocupa. Por ordinal: nuevos al final.
 */
public enum BorcegoSuela implements StringIdentifiable {
    CHATA("chata", "chata", 1, 0f),
    PLATAFORMA("plataforma", "plataforma", 2, 1f),
    FINA("fina", "fina", 1, -0.4f),
    TACO_CHICO("taco_chico", "taco_chico", 1, 0.333f),
    CUNA("cuna", "cuna", 2, 0.884f),
    GOMA_TENIS("goma_tenis", "goma_tenis", 2, 1f),
    PLATAFORMA_TENIS("plataforma_tenis", "plataforma_tenis", 4, 2.5f),
    CHANCLA("chancla", "chancla", 1, 0f),
    SANDALIA("sandalia", "sandalia", 1, -0.2f),
    TELA("tela", "tela", 1, -0.2f);

    public final String clave, eje;
    /** Filas de pierna (px) que ocupa la suela. */
    public final int filas;
    public final float alzado;

    BorcegoSuela(String clave, String eje, int filas, float alzado) {
        this.clave = clave;
        this.eje = eje;
        this.filas = filas;
        this.alzado = alzado;
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.borcegos.suela." + clave; }
}
