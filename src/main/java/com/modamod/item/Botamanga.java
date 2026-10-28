package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * El eje de largo de pierna, compartido entre pantalón y medias
 * (2026-09-23, "botamanga sera aparte y solo funcionara para pantalones
 * y medias") — reemplaza los antes-separados {@code PantalonLargo} y
 * {@code MediasLargo}, unificados en un solo enum ya que ninguno de los
 * dos usaba assets horneados por valor en el render real (cada garment
 * recorta en runtime desde UNA sola textura base con {@link
 * com.modamod.region.RegionResolver}/{@code PiezasDelMod#recortarFilas},
 * ver {@code PantalonItem#filasVisibles}/{@code MediasLargo#filasVisibles}
 * — hoy {@code MediasLargo#filasVisibles}, después de este cambio migrado
 * a este enum).
 *
 * <p>Pantalón usa SOLO el anclaje inferior de acá en más (pierde el
 * superior); medias sigue con los dos, igual que antes.
 *
 * <p>Nombres por posición anatómica de la pierna, no por largo —mismo
 * criterio que {@link com.modamod.sublimadora.Variante.Manga} para el
 * brazo—, filas 0..12 igual escala que antes.
 */
public enum Botamanga implements StringIdentifiable {

    MUSLO_ALTO("muslo_alto", 0),
    MUSLO("muslo", 2),
    RODILLA("rodilla", 4),
    PANTORRILLA("pantorrilla", 6),
    TOBILLO_ALTO("tobillo_alto", 8),
    TOBILLO("tobillo", 10),
    PIE("pie", 12);

    public final String clave;
    public final int filas;

    Botamanga(String clave, int filas) {
        this.clave = clave;
        this.filas = filas;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.item.botamanga." + clave;
    }
}
