package com.modamod.guardarropas;

import java.util.UUID;

/** Una pantalla de outfits abierta sobre una colección (el Guardarropas o el Maletín): recibe la lista actualizada del servidor. */
public interface VisorColeccion {
    /** Servidor: de qué colección es esta pantalla. */
    UUID coleccion();

    /** Cliente: la lista que llegó del servidor. */
    void setDatos(OutfitsDatos datos);
}
