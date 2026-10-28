package com.modamod.aplique;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.MathHelper;

/**
 * Cómo se balancea un aplique que cuelga (2026-10-04, "quiero que el objeto cuelgue desde el centro superior,
 * no desde atrás ni desde su centro geométrico"). La INTENSIDAD es la {@code blandura} del aplique (0 = rígido);
 * acá el resto: dónde está el pivote, cuánto se abre, cada cuánto se mece solo y en qué eje.
 *
 * @param pivote    dónde cuelga ({@link Pivote}); {@code ox, oy, oz} (px) se le suman, para correrlo
 * @param velocidad vaivén propio en Hz (0 = solo reacciona al movimiento de quien lo lleva)
 * @param amplitud  ángulo máximo en grados
 * @param eje       en qué plano se balancea ({@link Eje})
 */
public record Oscilacion(Pivote pivote, float ox, float oy, float oz, float velocidad, float amplitud, Eje eje) {

    /** Agregar valores nuevos siempre al FINAL (viajan por red por ordinal). */
    public enum Pivote implements StringIdentifiable {
        /** El centro del borde de arriba de la cara de contacto: como un pin o un cartelito colgado. */
        BORDE_SUPERIOR,
        CENTRO_CARA,
        CENTRO_OBJETO,
        /** Solo el desplazamiento {@code ox, oy, oz} sobre el centro de la cara de contacto. */
        PERSONALIZADO;

        @Override
        public String asString() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Eje implements StringIdentifiable {
        /** Gira alrededor de la normal de la tela: el objeto sigue paralelo a ella. */
        EN_PLANO,
        /** Se despega de la tela como una bisagra (nunca la atraviesa). */
        BISAGRA,
        AMBOS;

        @Override
        public String asString() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final Oscilacion DEFECTO = new Oscilacion(Pivote.BORDE_SUPERIOR, 0f, 0f, 0f, 0f, 20f, Eje.EN_PLANO);

    public static final float VELOCIDAD_MAX = 4f, AMPLITUD_MAX = 60f, PIVOTE_MAX = 16f;

    public Oscilacion {
        if (pivote == null) pivote = Pivote.BORDE_SUPERIOR;
        if (eje == null) eje = Eje.EN_PLANO;
        ox = fin(ox, -PIVOTE_MAX, PIVOTE_MAX);
        oy = fin(oy, -PIVOTE_MAX, PIVOTE_MAX);
        oz = fin(oz, -PIVOTE_MAX, PIVOTE_MAX);
        velocidad = fin(velocidad, 0f, VELOCIDAD_MAX);
        amplitud = fin(amplitud, 0f, AMPLITUD_MAX);
    }

    private static float fin(float v, float min, float max) {
        return Float.isFinite(v) ? MathHelper.clamp(v, min, max) : MathHelper.clamp(0f, min, max);
    }

    public static final Codec<Oscilacion> CODEC = RecordCodecBuilder.create(i -> i.group(
            StringIdentifiable.createCodec(Pivote::values).optionalFieldOf("pivote", Pivote.BORDE_SUPERIOR).forGetter(Oscilacion::pivote),
            Codec.FLOAT.optionalFieldOf("ox", 0f).forGetter(Oscilacion::ox),
            Codec.FLOAT.optionalFieldOf("oy", 0f).forGetter(Oscilacion::oy),
            Codec.FLOAT.optionalFieldOf("oz", 0f).forGetter(Oscilacion::oz),
            Codec.FLOAT.optionalFieldOf("velocidad", 0f).forGetter(Oscilacion::velocidad),
            Codec.FLOAT.optionalFieldOf("amplitud", 20f).forGetter(Oscilacion::amplitud),
            StringIdentifiable.createCodec(Eje::values).optionalFieldOf("eje", Eje.EN_PLANO).forGetter(Oscilacion::eje)
    ).apply(i, Oscilacion::new));

    /** Cambia un campo numérico por índice: 0-2 pivote libre, 3 velocidad, 4 amplitud (el panel lateral). */
    public Oscilacion conCampo(int i, float v) {
        float[] f = { ox, oy, oz, velocidad, amplitud };
        f[i] = v;
        return new Oscilacion(pivote, f[0], f[1], f[2], f[3], f[4], eje);
    }

    public float campo(int i) {
        return new float[] { ox, oy, oz, velocidad, amplitud }[i];
    }

    public Oscilacion conPivote(Pivote p) {
        return new Oscilacion(p, ox, oy, oz, velocidad, amplitud, eje);
    }

    public Oscilacion conEje(Eje e) {
        return new Oscilacion(pivote, ox, oy, oz, velocidad, amplitud, e);
    }

    public void escribir(PacketByteBuf buf) {
        buf.writeVarInt(pivote.ordinal());
        for (float f : new float[] { ox, oy, oz, velocidad, amplitud }) buf.writeFloat(f);
        buf.writeVarInt(eje.ordinal());
    }

    public static Oscilacion leer(PacketByteBuf buf) {
        Pivote[] pivotes = Pivote.values();
        Eje[] ejes = Eje.values();
        Pivote p = pivotes[MathHelper.clamp(buf.readVarInt(), 0, pivotes.length - 1)];
        float ox = buf.readFloat(), oy = buf.readFloat(), oz = buf.readFloat(), v = buf.readFloat(), a = buf.readFloat();
        Eje e = ejes[MathHelper.clamp(buf.readVarInt(), 0, ejes.length - 1)];
        return new Oscilacion(p, ox, oy, oz, v, a, e);
    }
}
