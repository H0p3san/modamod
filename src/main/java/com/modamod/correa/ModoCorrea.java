package com.modamod.correa;

import net.minecraft.util.StringIdentifiable;

/**
 * PEGADA: da la vuelta por la superficie de la caja entre sus dos puntos. COLGANTE: solo el 1.º punto la sujeta y cuelga
 * con tela blanda hacia el 2.º. ANCLADA (2026-10-06, "un tipo de anclaje de un solo punto y que cuelgue con la gravedad"):
 * un solo punto y cuelga hacia abajo el largo elegido. COLGADA ("la cadena cuelgue"): dos puntos y un largo mayor que la
 * distancia: cuelga en curva entre los dos con la gravedad. Los nuevos van al final (viajan por ordinal).
 */
public enum ModoCorrea implements StringIdentifiable {
    PEGADA("pegada"),
    COLGANTE("colgante"),
    ANCLADA("anclada"),
    COLGADA("colgada");

    public final String clave;

    ModoCorrea(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.correa.modo." + clave; }

    /** ¿Se arma con dos clicks (inicio y fin)? ANCLADA solo con uno. */
    public boolean dosPuntos() { return this != ANCLADA; }

    /** ¿Usa el largo? (la cadena que cuelga) */
    public boolean usaLargo() { return this == ANCLADA || this == COLGADA; }

    public ModoCorrea siguiente() { return values()[(ordinal() + 1) % values().length]; }
}
