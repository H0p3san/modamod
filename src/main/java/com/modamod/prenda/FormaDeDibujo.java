package com.modamod.prenda;

/** Cómo se dibuja una prenda puesta. */
public enum FormaDeDibujo {
    /** Piezas pegadas a la piel, en el layout de la skin (medias, pantalón, remera, calientabrazos). */
    FORRO,
    /** Geometría propia que cuelga o se abre del cuerpo (pollera, capa, delantal). */
    MALLA,
    /** Cajas sueltas sobre una parte del cuerpo (sombrero, banda, calzado, máscara, barbijo). */
    ACCESORIO,
    /** No se viste (retazo de aplique, maletín, banner). */
    NINGUNA;

    /** Lo dibuja el recorrido de ropa del cuerpo (forro o malla), no un renderer de accesorio. */
    public boolean esDeCuerpo() {
        return this == FORRO || this == MALLA;
    }
}
