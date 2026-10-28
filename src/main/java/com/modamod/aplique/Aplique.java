package com.modamod.aplique;

import com.modamod.garment.Parte;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * Un aplique puesto en una prenda (2026-10-01, "poder poner cualquier prenda y
 * agregarles modelos 3d anclados en la geometria de la prenda").
 *
 * <p>La posición es la del click en la Mesa de estilado: un punto sobre la
 * SUPERFICIE de la caja de esa {@link Parte} (sin inflar por el calce), en
 * píxeles de skin del espacio local de la parte (el de {@code ModelPart}: y
 * crece hacia los pies, -z es el frente), y la cara que se tocó ({@code cara},
 * la normal hacia afuera en ese mismo espacio). Al dibujar, el aplique sigue
 * la pose de la parte y se corre hacia afuera lo que infle el calce.
 *
 * @param giro    grados alrededor de la normal
 * @param escala  1 = tamaño del modelo
 * @param colores las 3 zonas (RGB), del retazo con que se puso
 * @param blandura 0..1: cuánto se mueve como tela blanda con el movimiento
 *                 (2026-10-04, "tela blanda afectada por el movimiento... configurable por
 *                 aplique"); 0 = rígido. Ver {@code render.FisicaApliques}.
 * @param fila    copias hacia abajo (botonera, 2026-10-22); {@link Fila#DEFECTO} = una sola
 */
public record Aplique(ModeloAplique modelo, Parte parte, float x, float y, float z, Direction cara,
                      float giro, float escala, List<Integer> colores, Superficie superficie, float blandura,
                      @org.jetbrains.annotations.Nullable ObjetoAplique objeto,
                      Colocacion colocacion, Oscilacion oscilacion, int padre, Fila fila) {

    /** 12 (2026-10-04, apliques sobre apliques: los que van sobre otro también cuentan). */
    public static final int MAXIMO_POR_PRENDA = 12;

    /**
     * Sobre qué está puesto (2026-10-02, "Pollera y Capa no registran click on
     * garment"): en una caja de parte del cuerpo ({@code CAJA}: x, y, z en px
     * de la parte, como siempre) o en la malla de la pollera o de la capa
     * ({@code x}, {@code y} = u, v de la textura de la tela en px de 64; se
     * ubica sobre la malla del cuadro con {@code render.MallaCapturada}).
     * Va al final del enum si se agregan más.
     */
    public enum Superficie implements net.minecraft.util.StringIdentifiable {
        CAJA, POLLERA, CAPA,
        /**
         * Sobre OTRO aplique de la misma prenda (2026-10-04, "podemos poner apliques sobre apliques?"): {@code padre}
         * es el índice de ese aplique en la lista (siempre menor que el propio); {@code x}, {@code y}, {@code z} son
         * el punto en el espacio del objeto del padre (px, centrado en su cubo) y {@code cara} la cara de su caja
         * donde se apoya.
         */
        APLIQUE,
        /** Sobre la malla del delantal (2026-10-11): x, y = u, v de su tela en px de 64, como la pollera. */
        DELANTAL,
        /**
         * Sobre la capucha de Blockbench del Top o de la capa (2026-10-24, "y la capucha?"): x, y = u, v de su tela en
         * px de 64; sigue a la capucha puesta y caída.
         */
        CAPUCHA;

        @Override
        public String asString() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public Aplique {
        blandura = Math.max(0f, Math.min(1f, blandura));
        if (colocacion == null) colocacion = Colocacion.DEFECTO;
        if (oscilacion == null) oscilacion = Oscilacion.DEFECTO;
        if (fila == null) fila = Fila.DEFECTO;
    }

    /** Sin fila (un solo aplique, lo de antes de la botonera). */
    public Aplique(ModeloAplique modelo, Parte parte, float x, float y, float z, Direction cara,
                   float giro, float escala, List<Integer> colores, Superficie superficie, float blandura,
                   @org.jetbrains.annotations.Nullable ObjetoAplique objeto, Colocacion colocacion, Oscilacion oscilacion, int padre) {
        this(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, objeto, colocacion, oscilacion, padre, Fila.DEFECTO);
    }

    /** Sin padre (todos los apliques salvo los que van sobre otro aplique). */
    public Aplique(ModeloAplique modelo, Parte parte, float x, float y, float z, Direction cara,
                   float giro, float escala, List<Integer> colores, Superficie superficie, float blandura,
                   @org.jetbrains.annotations.Nullable ObjetoAplique objeto, Colocacion colocacion, Oscilacion oscilacion) {
        this(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, objeto, colocacion, oscilacion, -1);
    }

    /** Con objeto y colocación por defecto. */
    public Aplique(ModeloAplique modelo, Parte parte, float x, float y, float z, Direction cara,
                   float giro, float escala, List<Integer> colores, Superficie superficie, float blandura,
                   @org.jetbrains.annotations.Nullable ObjetoAplique objeto) {
        this(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, objeto,
                Colocacion.DEFECTO, Oscilacion.DEFECTO);
    }

    /** Sin objeto (un modelo GeckoLib de siempre). */
    public Aplique(ModeloAplique modelo, Parte parte, float x, float y, float z, Direction cara,
                   float giro, float escala, List<Integer> colores, Superficie superficie, float blandura) {
        this(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, null,
                Colocacion.DEFECTO, Oscilacion.DEFECTO);
    }

    /** Rígido (lo de antes de la tela blanda). */
    public Aplique(ModeloAplique modelo, Parte parte, float x, float y, float z, Direction cara,
                   float giro, float escala, List<Integer> colores, Superficie superficie) {
        this(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, 0f);
    }

    /** En una caja de parte del cuerpo (lo de siempre). */
    public Aplique(ModeloAplique modelo, Parte parte, float x, float y, float z, Direction cara,
                   float giro, float escala, List<Integer> colores) {
        this(modelo, parte, x, y, z, cara, giro, escala, colores, Superficie.CAJA);
    }

    public static final Codec<Parte> CODEC_PARTE = Codec.STRING.xmap(Parte::valueOf, Parte::name);

    public static final Codec<Aplique> CODEC = RecordCodecBuilder.create(i -> i.group(
            net.minecraft.util.StringIdentifiable.createCodec(ModeloAplique::values).fieldOf("modelo").forGetter(Aplique::modelo),
            CODEC_PARTE.fieldOf("parte").forGetter(Aplique::parte),
            Codec.FLOAT.fieldOf("x").forGetter(Aplique::x),
            Codec.FLOAT.fieldOf("y").forGetter(Aplique::y),
            Codec.FLOAT.fieldOf("z").forGetter(Aplique::z),
            Direction.CODEC.fieldOf("cara").forGetter(Aplique::cara),
            Codec.FLOAT.optionalFieldOf("giro", 0f).forGetter(Aplique::giro),
            Codec.FLOAT.optionalFieldOf("escala", 1f).forGetter(Aplique::escala),
            Codec.INT.listOf().optionalFieldOf("colores", List.of(0xFFFFFF, 0xFFFFFF, 0xFFFFFF)).forGetter(Aplique::colores),
            net.minecraft.util.StringIdentifiable.createCodec(Superficie::values)
                    .optionalFieldOf("superficie", Superficie.CAJA).forGetter(Aplique::superficie),
            Codec.FLOAT.optionalFieldOf("blandura", 0f).forGetter(Aplique::blandura),
            // Optional y no null: DataResult no acepta null (un aplique viejo sin objeto rompía la carga del jugador).
            ObjetoAplique.CODEC.optionalFieldOf("objeto").forGetter(ap -> java.util.Optional.ofNullable(ap.objeto())),
            Colocacion.CODEC.optionalFieldOf("colocacion", Colocacion.DEFECTO).forGetter(Aplique::colocacion),
            Oscilacion.CODEC.optionalFieldOf("oscilacion", Oscilacion.DEFECTO).forGetter(Aplique::oscilacion),
            Codec.INT.optionalFieldOf("padre", -1).forGetter(Aplique::padre),
            Fila.CODEC.optionalFieldOf("fila", Fila.DEFECTO).forGetter(Aplique::fila)
    ).apply(i, (modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, objeto, colocacion, oscilacion, padre, fila) -> {
        ObjetoAplique obj = objeto.orElse(null);
        // Migración (2026-10-04): las inclinaciones de antes del objeto pasan a ser rotación de la colocación.
        if (obj != null && (obj.inclinarX() != 0f || obj.inclinarY() != 0f)) {
            colocacion = colocacion.conRotacion(colocacion.rx() + obj.inclinarX(), colocacion.ry() + obj.inclinarY(), colocacion.rz());
            obj = obj.conInclinacion(0f, 0f);
        }
        return new Aplique(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, obj, colocacion, oscilacion, padre, fila);
    }));

    /** Color de la zona {@code zona} (0..2), blanco si falta. */
    public int color(int zona) {
        return zona < colores.size() ? colores.get(zona) : 0xFFFFFF;
    }

    public Aplique conGiro(float g) {
        return new Aplique(modelo, parte, x, y, z, cara, g, escala, colores, superficie, blandura, objeto, colocacion, oscilacion, padre, fila);
    }

    public Aplique conEscala(float e) {
        return new Aplique(modelo, parte, x, y, z, cara, giro, e, colores, superficie, blandura, objeto, colocacion, oscilacion, padre, fila);
    }

    public Aplique conObjeto(ObjetoAplique o) {
        return new Aplique(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, o, colocacion, oscilacion, padre, fila);
    }

    public Aplique conPadre(int p) {
        return new Aplique(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, objeto, colocacion, oscilacion, p, fila);
    }

    public Aplique conColocacion(Colocacion c) {
        return new Aplique(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, objeto, c, oscilacion, padre, fila);
    }

    public Aplique conOscilacion(Oscilacion o) {
        return new Aplique(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, objeto, colocacion, o, padre, fila);
    }

    public Aplique conFila(Fila f) {
        return new Aplique(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, blandura, objeto, colocacion, oscilacion, padre, f);
    }

    public Aplique conBlandura(float b) {
        return new Aplique(modelo, parte, x, y, z, cara, giro, escala, colores, superficie, b, objeto, colocacion, oscilacion, padre, fila);
    }
}
