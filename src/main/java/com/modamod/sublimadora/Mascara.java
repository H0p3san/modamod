package com.modamod.sublimadora;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.MathHelper;

/**
 * Una máscara de sublimación puesta sobre la prenda (2026-10-02, "controles de
 * posicion angulo tamaño tanto para la imagen dentro de la mascara como para
 * la mascara dentro de la prenda"): la forma y dónde va, en las mismas
 * unidades que {@link Estampa} — {@code x}, {@code y} de −0.5 a 0.5 sobre el
 * lienzo de la prenda, {@code escala} = alto de la máscara relativo al cuerpo.
 * La foto de la capa se acomoda ADENTRO de la máscara (ver {@link CapaEstampa}).
 */
public record Mascara(FormaMascara forma, float escala, float x, float y, float angulo) {

    public static final float ESCALA_DEFECTO = 0.5f, ESCALA_MINIMA = 0.1f, ESCALA_MAXIMA = 1.5f;

    public Mascara {
        escala = MathHelper.clamp(escala, ESCALA_MINIMA, ESCALA_MAXIMA);
        x = MathHelper.clamp(x, -0.5f, 0.5f);
        y = MathHelper.clamp(y, -0.5f, 0.5f);
        angulo = ((angulo % 360f) + 360f) % 360f;
    }

    public static final Codec<Mascara> CODEC = RecordCodecBuilder.create(i -> i.group(
            StringIdentifiable.createCodec(FormaMascara::values).fieldOf("forma").forGetter(Mascara::forma),
            Codec.FLOAT.optionalFieldOf("escala", ESCALA_DEFECTO).forGetter(Mascara::escala),
            Codec.FLOAT.optionalFieldOf("x", 0f).forGetter(Mascara::x),
            Codec.FLOAT.optionalFieldOf("y", 0f).forGetter(Mascara::y),
            Codec.FLOAT.optionalFieldOf("angulo", 0f).forGetter(Mascara::angulo)
    ).apply(i, Mascara::new));
}
