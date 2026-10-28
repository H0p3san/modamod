package com.modamod.correa;

import com.modamod.garment.Parte;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * Una correa libre puesta en una prenda o wearable (2026-10-05, "correas libres... rectas u oblicuas, pegadas o
 * colgantes"). Va en el componente {@code modamod:correas} (aparte de los apliques: {@code Aplique} ya no admite
 * campos). Los dos puntos son sobre la superficie de la caja de la {@link Parte} sin inflar, en px de skin del espacio
 * local de la parte, como el punto de un aplique.
 *
 * @param ancho   ancho de la tira en px (1..4)
 * @param colores banda, borde y herraje (RGB)
 * @param blandura 0..1: cuánto se mueve la correa colgante con el movimiento
 * @param largo   px de cadena de las correas que cuelgan (ANCLADA: lo que cuelga; COLGADA: la longitud entre los dos puntos)
 */
public record Correa(Parte parte, Punto desde, Punto hasta, EstiloCorrea estilo, ModoCorrea modo, float ancho,
                     List<Integer> colores, float blandura, Superficie superficie, float largo) {

    /**
     * Sobre qué va (2026-10-05, "superame esos limites"): la caja de la parte del cuerpo o las cajas del sombrero y la
     * banda ({@code CAJA}; x, y, z en px del marco de su parte), o la malla de la pollera o la capa (x, y = u, v de la
     * tela en px de 64, como un aplique).
     */
    public enum Superficie implements StringIdentifiable {
        CAJA, POLLERA, CAPA, DELANTAL;

        @Override
        public String asString() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static final int MAXIMO_POR_PRENDA = 8;
    public static final float ANCHO_MIN = 1f, ANCHO_MAX = 4f;
    /** Largo de la cadena que cuelga (2026-10-06, "ademas de ancho deberia poder agregarsele largo"): de 2 a 32 px de a 2. */
    public static final float LARGO_MIN = 2f, LARGO_MAX = 32f, LARGO_PASO = 2f, LARGO_INICIAL = 8f;
    public static final List<Integer> DE_FABRICA = List.of(0x5A3A24, 0x8A6240, 0xC9A24A);

    /** Un punto de la superficie de la caja y la cara (normal) donde está. */
    /** La correa en la parte de enfrente, espejada en x (2026-10-08): el borcego izquierdo copia al derecho. */
    public Correa espejoX() {
        Parte p = parte == Parte.PIERNA_DER ? Parte.PIERNA_IZQ : parte == Parte.PIERNA_IZQ ? Parte.PIERNA_DER
                : parte == Parte.BRAZO_DER ? Parte.BRAZO_IZQ : parte == Parte.BRAZO_IZQ ? Parte.BRAZO_DER : parte;
        return new Correa(p, desde.espejoX(), hasta.espejoX(), estilo, modo, ancho, colores, blandura, superficie, largo);
    }

    public record Punto(float x, float y, float z, Direction cara) {
        /** El mismo punto en la parte de enfrente (2026-10-08, borcego izquierdo): x al revés y las caras este/oeste cambiadas. */
        public Punto espejoX() {
            Direction c = cara == Direction.EAST ? Direction.WEST : cara == Direction.WEST ? Direction.EAST : cara;
            return new Punto(-x, y, z, c);
        }

        public static final Codec<Punto> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("x").forGetter(Punto::x),
                Codec.FLOAT.fieldOf("y").forGetter(Punto::y),
                Codec.FLOAT.fieldOf("z").forGetter(Punto::z),
                Direction.CODEC.fieldOf("cara").forGetter(Punto::cara)
        ).apply(i, Punto::new));
    }

    public static final Codec<Correa> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.xmap(n -> Parte.values()[Math.floorMod(n, Parte.values().length)], Parte::ordinal).fieldOf("parte").forGetter(Correa::parte),
            Punto.CODEC.fieldOf("desde").forGetter(Correa::desde),
            Punto.CODEC.fieldOf("hasta").forGetter(Correa::hasta),
            StringIdentifiable.createCodec(EstiloCorrea::values).fieldOf("estilo").forGetter(Correa::estilo),
            StringIdentifiable.createCodec(ModoCorrea::values).fieldOf("modo").forGetter(Correa::modo),
            Codec.FLOAT.fieldOf("ancho").forGetter(Correa::ancho),
            Codec.INT.listOf().fieldOf("colores").forGetter(Correa::colores),
            Codec.FLOAT.fieldOf("blandura").forGetter(Correa::blandura),
            StringIdentifiable.createCodec(Superficie::values).optionalFieldOf("superficie", Superficie.CAJA)
                    .forGetter(Correa::superficie),
            Codec.FLOAT.optionalFieldOf("largo", LARGO_INICIAL).forGetter(Correa::largo)
    ).apply(i, Correa::new));

    public Correa(Parte parte, Punto desde, Punto hasta, EstiloCorrea estilo, ModoCorrea modo, float ancho,
                  List<Integer> colores, float blandura) {
        this(parte, desde, hasta, estilo, modo, ancho, colores, blandura, Superficie.CAJA, LARGO_INICIAL);
    }

    public Correa(Parte parte, Punto desde, Punto hasta, EstiloCorrea estilo, ModoCorrea modo, float ancho,
                  List<Integer> colores, float blandura, Superficie superficie) {
        this(parte, desde, hasta, estilo, modo, ancho, colores, blandura, superficie, LARGO_INICIAL);
    }

    public Correa conColores(List<Integer> c) { return new Correa(parte, desde, hasta, estilo, modo, ancho, c, blandura, superficie, largo); }

    public Correa conModo(ModoCorrea m) { return new Correa(parte, desde, hasta, estilo, m, ancho, colores, blandura, superficie, largo); }

    public Correa conAncho(float a) { return new Correa(parte, desde, hasta, estilo, modo, a, colores, blandura, superficie, largo); }

    public Correa conLargo(float l) { return new Correa(parte, desde, hasta, estilo, modo, ancho, colores, blandura, superficie, l); }

    public Correa {
        largo = Math.max(LARGO_MIN, Math.min(LARGO_MAX, Float.isFinite(largo) ? largo : LARGO_INICIAL));
        if (colores == null || colores.size() < 3) colores = DE_FABRICA;
        ancho = Math.max(ANCHO_MIN, Math.min(ANCHO_MAX, ancho));
        blandura = Math.max(0f, Math.min(1f, blandura));
    }
}
