package com.modamod.sublimadora;

import com.modamod.Modamod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * El corte de una prenda: tres ejes independientes.
 *
 * Antes era un enum de cuatro valores fijos, y no escalaba. Sumar mangas
 * largas y escotes multiplicaba las combinaciones: tres largos por cuatro
 * mangas por tres cuellos son 36, y con una receta por combinacion y color
 * serian 576 recetas — ademas de imposibles de expresar en una grilla de 3x3.
 *
 * Por eso los ejes NO se craftean: se craftea una prenda y se la reforma en
 * el telar. Ademas de ahorrar recetas, deja rehacer una prenda que ya tenes
 * sin volver a gastar lana, y sin perder la estampa.
 */
public record Variante(Largo largo, Manga manga, Cuello cuello) {

    /** Cuanto baja la tela sobre las 12 filas del torso. */
    public enum Largo implements StringIdentifiable {
        CROP("crop", 5),
        NORMAL("normal", 9),
        LARGO("largo", 12),
        // Los 7 puntos del molde de rango (2026-10-04, "agreguemos un molde y medida mas" + "el molde de rango o
        // cobertura cubra tambien a los de torso"): 3, 5, 7, 9, 10, 11, 12. Los nuevos van al FINAL (viajan por ordinal).
        TOP("top", 3),
        CORTO("corto", 7),
        CADERA_ALTA("cadera_alta", 10),
        CADERA("cadera", 11);

        public final String clave;
        public final int filas;

        Largo(String clave, int filas) {
            this.clave = clave;
            this.filas = filas;
        }

        /**
         * El largo de los 3 de siempre cuya textura y modelo de ítem sirven para este: el más corto que lo
         * contiene (la tela se recorta en runtime a {@link #filas}; no hay una textura por cada largo nuevo).
         */
        public Largo base() {
            if (filas <= CROP.filas) return CROP;
            if (filas <= NORMAL.filas) return NORMAL;
            return LARGO;
        }

        @Override public String asString() { return clave; }
    }

    /**
     * Cuanto baja la manga sobre las 12 filas del brazo.
     *
     * <p><b>2026-09-15</b>: {@link #MINIMA} y {@link #MEDIA} se agregaron
     * para cerrar el escalón parejo 0/2/4/6/8/10/12 que usa el Molde de
     * Rango (calientabrazos usa las 6 de punta a punta; manga de remera
     * usa 0..10 desde su único anclaje, Superior — ver
     * {@code ModeladoBlockEntity#mangaRemeraDeRango}).
     */
    public enum Manga implements StringIdentifiable {
        SIN("sin", 0),
        MINIMA("minima", 2),
        CORTA("corta", 4),
        MEDIA("media", 6),
        TRES_CUARTOS("tres_cuartos", 8),
        SIETE_OCTAVOS("siete_octavos", 10),
        LARGA("larga", 12);

        public final String clave;
        public final int filas;

        Manga(String clave, int filas) {
            this.clave = clave;
            this.filas = filas;
        }

        @Override public String asString() { return clave; }
    }

    /**
     * El escote.
     *
     * A esta resolucion el pecho tiene 8 de ancho por 12 de alto, asi que el
     * escote se juega en dos o tres filas. La V es la que mejor se lee: dos
     * diagonales que se cortan al medio se distinguen de lejos. Un escote
     * redondo profundo se confundiria con el normal.
     */
    public enum Cuello implements StringIdentifiable {
        REDONDO("redondo"),
        V("v"),
        POLERA("polera"),
        /** Cuadrado y corazón (2026-10-04): al FINAL, el cuello viaja por red y se guarda por ordinal. */
        CUADRADO("cuadrado"),
        CORAZON("corazon"),
        /** Cuello de camisa (2026-10-07, "traje separado"): escote en V chico + solapitas 3D (CuelloYCapucha). */
        CAMISA("camisa"),
        /**
         * El cuello de fábrica (2026-10-12, "tienen el cuello cerrado, me parece que ese puede ser el de fabrica"):
         * el frente entero, sin escote (solo la tapa de arriba abierta). El REDONDO pasó a ser el escote en U con su
         * molde. Al FINAL: el cuello viaja por red y se guarda por ordinal. Usa los archivos del redondo.
         */
        CERRADO("cerrado");

        public final String clave;

        Cuello(String clave) {
            this.clave = clave;
        }

        @Override public String asString() { return clave; }
    }

    /**
     * Lo que sale de la mesa de crafteo antes de pasar por el telar —
     * sin mangas por defecto (2026-09-23, "de hecho la default tiene
     * que venir sin mangas").
     */
    public static final Variante BASE = new Variante(Largo.NORMAL, Manga.SIN, Cuello.CERRADO);

    public static final Codec<Variante> CODEC = RecordCodecBuilder.create(i -> i.group(
            StringIdentifiable.createCodec(Largo::values).optionalFieldOf("largo", Largo.NORMAL)
                    .forGetter(Variante::largo),
            StringIdentifiable.createCodec(Manga::values).optionalFieldOf("manga", Manga.CORTA)
                    .forGetter(Variante::manga),
            StringIdentifiable.createCodec(Cuello::values).optionalFieldOf("cuello", Cuello.CERRADO)
                    .forGetter(Variante::cuello)
    ).apply(i, Variante::new));

    public static final PacketCodec<ByteBuf, Variante> PACKET_CODEC = PacketCodec.tuple(
            net.minecraft.network.codec.PacketCodecs.indexed(i -> Largo.values()[i], Enum::ordinal),
            Variante::largo,
            net.minecraft.network.codec.PacketCodecs.indexed(i -> Manga.values()[i], Enum::ordinal),
            Variante::manga,
            net.minecraft.network.codec.PacketCodecs.indexed(i -> Cuello.values()[i], Enum::ordinal),
            Variante::cuello,
            Variante::new);

    /** Las 36 combinaciones, para generar recursos y para cachear. */
    public static List<Variante> todas() {
        List<Variante> out = new ArrayList<>();
        for (Largo l : Largo.values()) {
            for (Manga m : Manga.values()) {
                for (Cuello c : Cuello.values()) out.add(new Variante(l, m, c));
            }
        }
        return out;
    }

    /** Nombre de archivo de este corte: largo_manga_cuello. */
    public String clave() {
        return largo.clave + "_" + manga.clave + "_" + cuello.clave;
    }

    /** La clave de archivo: los largos nuevos usan la textura y el modelo del largo de siempre que los contiene. */
    public String claveBase() {
        // La camisa no tiene sprites ni modelos propios (2026-10-07): usa los del cuello en V.
        return largo.base().clave + "_" + manga.clave + "_" + (cuello == Cuello.CAMISA ? Cuello.V
                : cuello == Cuello.CERRADO ? Cuello.REDONDO : cuello).clave;
    }

    public boolean tieneMangas() {
        return manga != Manga.SIN;
    }

    public Identifier texturaCuerpo() {
        // Cuadrado y corazón no tienen archivo: se recortan en runtime del redondo (ver CuelloRecorte).
        Cuello archivo = CuelloRecorte.recortaEnRuntime(cuello) || cuello == Cuello.CERRADO ? Cuello.REDONDO : cuello;
        return Identifier.of(Modamod.MOD_ID, "textures/entity/cuerpo_" + largo.base().clave + "_" + manga.clave
                + "_" + archivo.clave + ".png");
    }

    public Identifier modeloItem() {
        // La camisa no tiene modelos propios (2026-10-07): usa los del cuello en V.
        if (cuello == Cuello.CAMISA) return new Variante(largo, manga, Cuello.V).modeloItem();
        if (cuello == Cuello.CERRADO) return new Variante(largo, manga, Cuello.REDONDO).modeloItem();
        return Identifier.of(Modamod.MOD_ID, "item/corte_" + claveBase());
    }

    /**
     * Como se llama la prenda.
     *
     * Un solo nombre para 36 cortes las volveria indistinguibles en el
     * inventario, y 36 nombres serian ilegibles. Se nombra por el rasgo que
     * mas la define y el resto va al tooltip.
     */
    public String nombre() {
        if (largo.filas <= Largo.CORTO.filas) return "croptop";
        if (manga == Manga.SIN) return "musculosa";
        if (cuello == Cuello.POLERA) return "polera";
        if (cuello == Cuello.CAMISA) return "camisa";
        if (largo == Largo.LARGO) return "remeron";
        return "remera";
    }
}
