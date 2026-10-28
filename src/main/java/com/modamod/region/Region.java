package com.modamod.region;

/**
 * A que pedazo de una prenda apunta una operacion.
 *
 * Existe porque "que lado toco" aparecia distinto en cada lugar del mod: el
 * telar ciclaba Ambas/Izquierda/Derecha, la sublimadora tiene un selector
 * fisico frente/espalda, y los componentes se llamaban right_dyed_color en
 * ingles pero estampa_frente en castellano. Es EL MISMO concepto y ahora
 * tiene un solo nombre.
 *
 * Son dos ejes que no se mezclan —bilateral y frente/espalda— y por eso son
 * dos enums en vez de uno solo con seis valores: una prenda declara
 * {@code {IZQUIERDA, DERECHA, AMBAS}} para tenir y {@code {FRENTE, ESPALDA}}
 * para estampar, y ninguna de las dos listas tiene sentido en el otro eje.
 * Los dos implementan esta interfaz para poder viajar juntos en el
 * {@code Set<Region>} que devuelve una prenda por operacion.
 *
 * Ojo con {@link Lado#AMBAS} y {@link Cara#AMBAS}: son constantes DISTINTAS
 * de enums distintos, no hay ambiguedad al compararlas.
 */
public sealed interface Region permits Lado, Cara {

    /** Nombre estable. Va en claves de cache, nunca en pantalla. */
    String clave();

    /** Clave de traduccion del boton en la GUI de la estacion. */
    default String traduccion() {
        return "modamod.region." + clave();
    }
}
