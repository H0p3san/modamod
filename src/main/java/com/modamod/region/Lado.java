package com.modamod.region;

/**
 * El eje bilateral: piernas, brazos, y cualquier prenda que venga de a pares.
 *
 * IZQUIERDA es la del JUGADOR (anatomica), no la del que mira. Coincide con
 * la resolucion textura/modelo que ya estaba verificada contra
 * PlayerEntityModel.getTexturedModelData: el brazo derecho vive en uv(40,16)
 * y el izquierdo en uv(32,48), y el jugador tiene su derecha en -X.
 *
 * La IZQUIERDA es ademas el lado PRIMARIO: es la que guarda el valor. La
 * derecha guarda overrides opcionales y, cuando faltan, hereda. Asi un par
 * parejo no ocupa data extra y las 16 recetas de crafteo —que solo setean
 * DYED_COLOR— siguen dando pares iguales sin tocarlas.
 */
public enum Lado implements Region {

    IZQUIERDA("izquierda"),
    DERECHA("derecha"),
    AMBAS("ambas");

    private final String clave;

    Lado(String clave) {
        this.clave = clave;
    }

    @Override
    public String clave() {
        return clave;
    }

    /**
     * Los lados concretos que abarca. AMBAS abarca los dos; los otros, solo
     * a si mismos.
     *
     * Sirve para recorrer sin ramificar en cada lugar que aplica algo.
     */
    public Lado[] concretos() {
        return this == AMBAS ? new Lado[]{ IZQUIERDA, DERECHA } : new Lado[]{ this };
    }

    /** AMBAS no tiene opuesto: se devuelve a si misma. */
    public Lado opuesto() {
        return switch (this) {
            case IZQUIERDA -> DERECHA;
            case DERECHA -> IZQUIERDA;
            case AMBAS -> AMBAS;
        };
    }
}
