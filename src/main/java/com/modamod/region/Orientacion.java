package com.modamod.region;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Como esta puesta la prenda. Dos bits, no destructivos.
 *
 * - girado: frente y espalda cambian de lugar. La remera al reves: la estampa
 *   y el escote pasan atras.
 * - espejado: izquierda y derecha cambian de lugar, para TODA la prenda de
 *   una. Distinto de espejar una capa, que permuta los datos guardados.
 *
 * La clave es que esto NO permuta nada: lo aplica {@link RegionResolver} al
 * resolver, asi que sacar el flag devuelve la prenda exactamente a como
 * estaba. Vale igual para el render, el icono y el tooltip.
 *
 * La geometria no rota. Una remera es casi simetrica frente/espalda, asi que
 * "al reves" es remapear la superficie y no la malla; las prendas asimetricas
 * de forma (un buzo con capucha, un vestido con cola) declaran que no se
 * pueden girar.
 */
public record Orientacion(boolean girado, boolean espejado) {

    public static final Orientacion NORMAL = new Orientacion(false, false);

    public static final Codec<Orientacion> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("girado", false).forGetter(Orientacion::girado),
            Codec.BOOL.optionalFieldOf("espejado", false).forGetter(Orientacion::espejado)
    ).apply(i, Orientacion::new));

    public static final PacketCodec<ByteBuf, Orientacion> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, Orientacion::girado,
            PacketCodecs.BOOL, Orientacion::espejado,
            Orientacion::new);

    public boolean esNormal() {
        return !girado && !espejado;
    }

    public Orientacion girar() {
        return new Orientacion(!girado, espejado);
    }

    public Orientacion espejar() {
        return new Orientacion(girado, !espejado);
    }
}
