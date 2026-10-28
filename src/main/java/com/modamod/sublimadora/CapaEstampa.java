package com.modamod.sublimadora;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Una capa de estampa con máscara (2026-10-02, "que se pueda guardar varias
 * layers 12 quizas entre frente y atras y que se fije con la imagen"): en qué
 * cara va, la foto con su ajuste RELATIVO a la máscara (escala 1 = la foto
 * cubre la máscara; x, y corren la foto adentro de la máscara; el ángulo se
 * suma al de la máscara) y la máscara sobre la prenda. Van en orden en el
 * componente {@code modamod:estampas_capas} de la prenda; cada una pinta
 * encima de las anteriores.
 */
public record CapaEstampa(Estampa.Cara cara, Estampa estampa, Mascara mascara) {

    public static final int MAXIMO = 12;

    private static final Codec<Estampa.Cara> CODEC_CARA = Codec.STRING.xmap(
            s -> "espalda".equals(s) ? Estampa.Cara.ESPALDA : Estampa.Cara.FRENTE, c -> c.clave);

    public static final Codec<CapaEstampa> CODEC = RecordCodecBuilder.create(i -> i.group(
            CODEC_CARA.fieldOf("cara").forGetter(CapaEstampa::cara),
            Estampa.CODEC.fieldOf("estampa").forGetter(CapaEstampa::estampa),
            Mascara.CODEC.fieldOf("mascara").forGetter(CapaEstampa::mascara)
    ).apply(i, CapaEstampa::new));
}
