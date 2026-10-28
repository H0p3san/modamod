package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * La capellada del calzado (2026-10-08 la altura de la caña de los borcegos; ampliada 2026-10-14 con zapatos, tenis,
 * botas y pantuflas del zip de Claude Design): {@code eje} es su clave en {@code assets/modamod/calzado/ejes.json} y
 * {@code alturaRef} lo que sube sobre el piso con la suela de referencia (px). Por ordinal: nuevos al final.
 */
public enum BorcegoCana implements StringIdentifiable {
    BAJA("baja", "borcego_baja", 4f),
    MEDIA("media", "borcego_media", 6f),
    ALTA("alta", "borcego_alta", 9f),
    BOTA_ALTA("bota_alta", "bota_alta", 11f),
    ZAPATO("zapato", "zapato", 1.7f),
    MOCASIN("mocasin", "mocasin", 2f),
    BAILARINA("bailarina", "bailarina", 1.9f),
    TACO("taco", "taco", 2f),
    TENIS_BAJA("tenis_baja", "tenis_baja", 1.5f),
    TENIS_ALTA("tenis_alta", "tenis_alta", 6f),
    PANTUFLA("pantufla", "pantufla", 2f),
    /** Sin capellada: chancla y sandalias (solo suela y tiras). */
    NINGUNA("ninguna", "ninguna", 0f);

    public final String clave, eje;
    public final float alturaRef;
    /** Filas de pierna que cubre con la suela de referencia (para el pantalón de los cortes de antes). */
    public final int alto;

    BorcegoCana(String clave, String eje, float alturaRef) {
        this.clave = clave;
        this.eje = eje;
        this.alturaRef = alturaRef;
        this.alto = Math.round(alturaRef);
    }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.borcegos.cana." + clave; }
}
