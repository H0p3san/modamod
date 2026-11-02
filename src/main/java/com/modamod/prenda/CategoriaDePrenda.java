package com.modamod.prenda;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

/**
 * Qué es una prenda (o algo que las máquinas tratan como prenda). Es la única lista de categorías del mod: la declara
 * cada ítem a través de su {@link DefinicionDePrenda}, y las máquinas la consultan en lugar de preguntar por clases.
 *
 * <p>Se guarda por nombre ({@link #CODEC}), nunca por número de orden: agregar una categoría no corre nada guardado.
 * Cada máquina tiene su propia lista de las categorías que acepta, en el orden en que las muestra.
 */
public enum CategoriaDePrenda implements StringIdentifiable {
    REMERA("remera"),
    PANTALON("pantalon"),
    MEDIAS("medias"),
    CALIENTABRAZOS("calientabrazos"),
    POLLERA("pollera"),
    CAPA("capa"),
    DELANTAL("delantal"),
    SOMBRERO("sombrero"),
    BANDA("banda"),
    CALZADO("calzado"),
    MASCARA("mascara"),
    BARBIJO("barbijo"),
    /** El retazo de aplique: no se viste, pero Tintes lo pinta por zonas. */
    APLIQUE("aplique"),
    /** El maletín: no se viste, pero Tintes lo pinta por zonas. */
    MALETIN("maletin"),
    /** Cualquier banner: no se viste, pero Tintes y la Sublimadora lo trabajan. */
    BANNER("banner");

    public static final Codec<CategoriaDePrenda> CODEC = StringIdentifiable.createCodec(CategoriaDePrenda::values);

    private final String clave;

    CategoriaDePrenda(String clave) {
        this.clave = clave;
    }

    public String clave() {
        return clave;
    }

    @Override
    public String asString() {
        return clave;
    }
}
