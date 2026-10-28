package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/** Ala del sombrero (2026-10-04; ampliada 2026-10-14, "sombrero vaquero, redondo, de copa, gorra, boina"). Por ordinal: nuevos al final. */
public enum SombreroAla implements StringIdentifiable {
    ANCHA("ancha", 19f),
    CORTA("corta", 13f),
    /** Vincha (2026-10-11, "la cofia o vincha se puede adaptar al sombrero"): un arco sobre la cabeza con un moño; sin cono. */
    VINCHA("vincha", 0f),
    /** Cofia de maid: una tira de tela al frente de la cabeza con volado y un moño; sin cono. */
    COFIA("cofia", 0f),
    /** Vaquera: losa central con los lados levantados. */
    VAQUERA("vaquera", 19f),
    /** Angosta, la del sombrero de copa. */
    ANGOSTA("angosta", 11f),
    /** Visera curva de gorra: la punta baja un poco. */
    VISERA_CURVA("visera_curva", 0f),
    /** Visera chata de gorra. */
    VISERA_CHATA("visera_chata", 0f),
    /** Sin ala (boina, gorro). */
    SIN_ALA("sin_ala", 0f),
    /** Caída: el borde baja alrededor (piluso). */
    CAIDA("caida", 17f),
    /** Plana y enorme (paja, charro). */
    PLANA("plana", 26f),
    /** Media (fedora, panamá). */
    MEDIA("media", 15f),
    /** Tricornio (pirata): los tres lados levantados. */
    TRICORNIO("tricornio", 18f),
    /** Visera corta (gorra de panadero). */
    VISERA_CORTA("visera_corta", 0f),
    /** Larga: más ancha que la ancha (2026-10-14). */
    LARGA("larga", 24f);

    public final String clave;
    /** Lado del ala en píxeles del modelo. */
    public final float lado;

    SombreroAla(String clave, float lado) {
        this.clave = clave;
        this.lado = lado;
    }

    /** ¿Es una vincha o una cofia (sin cono ni ala)? */
    public boolean esDiadema() { return this == VINCHA || this == COFIA; }

    /** Las gorras (2026-10-25): las que llevan foto de la Sublimadora en el frente de la copa. */
    public boolean esVisera() { return this == VISERA_CURVA || this == VISERA_CHATA || this == VISERA_CORTA; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.sombrero.ala." + clave; }
}
