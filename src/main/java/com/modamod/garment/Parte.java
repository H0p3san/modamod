package com.modamod.garment;

import com.modamod.region.Lado;
import org.jetbrains.annotations.Nullable;

/**
 * Un pedazo del cuerpo sobre el que se puede dibujar.
 *
 * Es mas fino que el slot a proposito. Una prenda deja de estar atada a UNA
 * parte del cuerpo: el slot sigue existiendo para el inventario y la
 * exclusividad, pero lo que se renderiza es un conjunto de piezas, y cada
 * pieza vive en una de estas. Eso es lo que deja que un remeron dibuje torso
 * MAS la parte de arriba del muslo, o que una polera sea torso mas una banda
 * en la cabeza, sin inventar un slot por caso.
 *
 * Las partes bilaterales van separadas y no como "PIERNAS": el mod ya deja
 * teñir una pierna distinto de la otra, asi que el par nunca es una unidad.
 */
public enum Parte {

    CABEZA("cabeza", null),
    TORSO("torso", null),
    BRAZO_DER("brazo_der", Lado.DERECHA),
    BRAZO_IZQ("brazo_izq", Lado.IZQUIERDA),
    PIERNA_DER("pierna_der", Lado.DERECHA),
    PIERNA_IZQ("pierna_izq", Lado.IZQUIERDA);

    private final String clave;
    private final Lado lado;

    Parte(String clave, @Nullable Lado lado) {
        this.clave = clave;
        this.lado = lado;
    }

    public String clave() {
        return clave;
    }

    /** De que lado del cuerpo esta, o null si es unica (cabeza, torso). */
    @Nullable
    public Lado lado() {
        return lado;
    }

    /**
     * El lado con el que hay que consultar al RegionResolver por esta parte.
     *
     * Las partes unicas usan IZQUIERDA porque es el lado PRIMARIO: es donde
     * viven los componentes base. Un torso no tiene lado, pero su color
     * igual esta guardado ahi.
     */
    public Lado ladoOPrimario() {
        return lado == null ? Lado.IZQUIERDA : lado;
    }
}
