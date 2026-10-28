package com.modamod.aplique;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * Cómo se apoya un aplique en la tela (2026-10-04, "separar conceptualmente orientación, pivote, cara de
 * contacto, posición y profundidad"). Todo está en el marco de la superficie ({@code render.TransformAplique}):
 * +X a la derecha, +Y hacia arriba y +Z hacia adentro de la tela; las longitudes en px de skin.
 *
 * @param caraBase      la cara del objeto que se apoya en la tela (en el espacio del objeto: arriba, abajo, este,
 *                      oeste, norte = frente, sur = atrás). Solo cuenta en los apliques de objeto.
 * @param offsetNormal  el {@code surfaceOffset}: + saca el aplique hacia afuera de la prenda, − lo mete adentro, 0 = apoyado
 * @param dx            desplazamiento fino sobre la tela (x, y) y hacia afuera (z, + = afuera)
 * @param rx            rotación (grados) alrededor del centro de la cara de contacto
 * @param sx            escala por eje, además del Tamaño general del aplique
 */
public record Colocacion(Direction caraBase, float offsetNormal, float dx, float dy, float dz,
                         float rx, float ry, float rz, float sx, float sy, float sz) {

    public static final Colocacion DEFECTO = new Colocacion(Direction.SOUTH, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);

    public static final float OFFSET_MAX = 8f, DESPLAZAMIENTO_MAX = 16f, ESCALA_MIN = 0.1f, ESCALA_MAX = 4f;

    public Colocacion {
        if (caraBase == null) caraBase = Direction.SOUTH;
        offsetNormal = fin(offsetNormal, -OFFSET_MAX, OFFSET_MAX);
        dx = fin(dx, -DESPLAZAMIENTO_MAX, DESPLAZAMIENTO_MAX);
        dy = fin(dy, -DESPLAZAMIENTO_MAX, DESPLAZAMIENTO_MAX);
        dz = fin(dz, -DESPLAZAMIENTO_MAX, DESPLAZAMIENTO_MAX);
        rx = fin(rx, -180f, 180f);
        ry = fin(ry, -180f, 180f);
        rz = fin(rz, -180f, 180f);
        sx = fin(sx, ESCALA_MIN, ESCALA_MAX);
        sy = fin(sy, ESCALA_MIN, ESCALA_MAX);
        sz = fin(sz, ESCALA_MIN, ESCALA_MAX);
    }

    /** Acota y descarta NaN/infinitos (vienen de la red). */
    private static float fin(float v, float min, float max) {
        return Float.isFinite(v) ? MathHelper.clamp(v, min, max) : MathHelper.clamp(0f, min, max);
    }

    public static final Codec<Colocacion> CODEC = RecordCodecBuilder.create(i -> i.group(
            Direction.CODEC.optionalFieldOf("cara_base", Direction.SOUTH).forGetter(Colocacion::caraBase),
            Codec.FLOAT.optionalFieldOf("offset_normal", 0f).forGetter(Colocacion::offsetNormal),
            Codec.FLOAT.optionalFieldOf("dx", 0f).forGetter(Colocacion::dx),
            Codec.FLOAT.optionalFieldOf("dy", 0f).forGetter(Colocacion::dy),
            Codec.FLOAT.optionalFieldOf("dz", 0f).forGetter(Colocacion::dz),
            Codec.FLOAT.optionalFieldOf("rx", 0f).forGetter(Colocacion::rx),
            Codec.FLOAT.optionalFieldOf("ry", 0f).forGetter(Colocacion::ry),
            Codec.FLOAT.optionalFieldOf("rz", 0f).forGetter(Colocacion::rz),
            Codec.FLOAT.optionalFieldOf("sx", 1f).forGetter(Colocacion::sx),
            Codec.FLOAT.optionalFieldOf("sy", 1f).forGetter(Colocacion::sy),
            Codec.FLOAT.optionalFieldOf("sz", 1f).forGetter(Colocacion::sz)
    ).apply(i, Colocacion::new));

    public void escribir(PacketByteBuf buf) {
        buf.writeVarInt(caraBase.getId());
        for (float f : new float[] { offsetNormal, dx, dy, dz, rx, ry, rz, sx, sy, sz }) buf.writeFloat(f);
    }

    public static Colocacion leer(PacketByteBuf buf) {
        Direction cara = Direction.byId(buf.readVarInt());
        return new Colocacion(cara, buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    /** Cambia un campo numérico por índice: 0 offset, 1-3 desplazamiento, 4-6 rotación, 7-9 escala (el panel lateral). */
    public Colocacion conCampo(int i, float v) {
        float[] f = { offsetNormal, dx, dy, dz, rx, ry, rz, sx, sy, sz };
        f[i] = v;
        return new Colocacion(caraBase, f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9]);
    }

    public float campo(int i) {
        return new float[] { offsetNormal, dx, dy, dz, rx, ry, rz, sx, sy, sz }[i];
    }

    public Colocacion conCara(Direction c) {
        return new Colocacion(c, offsetNormal, dx, dy, dz, rx, ry, rz, sx, sy, sz);
    }

    public Colocacion conRotacion(float x, float y, float z) {
        return new Colocacion(caraBase, offsetNormal, dx, dy, dz, x, y, z, sx, sy, sz);
    }
}
