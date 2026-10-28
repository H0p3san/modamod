package com.modamod.region;

/**
 * El eje frente/espalda: hoy solo el torso de la remera, para las estampas.
 *
 * A diferencia de {@link Lado}, aca NO hay lado primario ni herencia: cada
 * cara guarda su propio componente (estampa_frente / estampa_espalda). Es
 * que no existe el caso "las dos iguales sin guardar nada": dos caras con la
 * misma foto igual son dos prensados y dos juegos de tinta.
 */
public enum Cara implements Region {

    FRENTE("frente"),
    ESPALDA("espalda"),
    AMBAS("ambas_caras");

    private final String clave;

    Cara(String clave) {
        this.clave = clave;
    }

    @Override
    public String clave() {
        return clave;
    }

    public Cara[] concretas() {
        return this == AMBAS ? new Cara[]{ FRENTE, ESPALDA } : new Cara[]{ this };
    }

    public Cara opuesta() {
        return switch (this) {
            case FRENTE -> ESPALDA;
            case ESPALDA -> FRENTE;
            case AMBAS -> AMBAS;
        };
    }
}
