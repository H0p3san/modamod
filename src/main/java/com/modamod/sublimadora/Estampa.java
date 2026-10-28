package com.modamod.sublimadora;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

/**
 * Una foto estampada en una cara de la remera.
 *
 * Guarda el UUID y no la imagen: esta ya vive en el almacenamiento de
 * Camerapture, y copiarla al ItemStack lo haria pesar de mas por prenda.
 *
 * La escala y la posicion van NORMALIZADAS al area imprimible, no en
 * pixeles. Asi el mismo numero sirve para el icono del inventario, para la
 * remera apoyada en la plancha y para cuando se dibuje sobre el cuerpo, que
 * son tres resoluciones distintas.
 *
 * @param foto   la foto de Camerapture
 * @param escala 1.0 cubre la remera entera, 0.15 es un logo, más de 1.0
 *               sobre-escala más allá de la cobertura completa (ver
 *               {@link #ESCALA_MAXIMA})
 * @param x      corrimiento horizontal, -0.5 a 0.5, 0 es centrado
 * @param y      corrimiento vertical, -0.5 a 0.5, positivo es hacia arriba
 * @param angulo rotación en grados, 0 es como viene la foto
 * @param espejo simetría lateral (2026-09-28, "a la sublimadora hay que
 *               agregarle simetria lateral para medias y cubrebrazos"): la
 *               pierna/brazo izquierdo lleva el ESPEJO del derecho en vez
 *               de la misma copia. Solo lo usan las prendas de a pares.
 */
public record Estampa(UUID foto, float escala, float x, float y, float angulo, boolean cubrir, boolean espejo) {

    /** Sin simetría lateral — todas las estampas de antes. */
    public Estampa(UUID foto, float escala, float x, float y, float angulo, boolean cubrir) {
        this(foto, escala, x, y, angulo, cubrir, false);
    }

    /** Ni tan chica que no se lea, ni tan grande que se coma las mangas. */
    public static final float ESCALA_DEFECTO = 0.42f;
    public static final float ESCALA_MINIMA = 0.10f;
    /** Acá el diseño llega a cubrir el lienzo entero — el "100%" de siempre. */
    public static final float ESCALA_CUBRIR = 1.0f;
    /**
     * Techo real del control — a pedido (2026-09-20, "me gustaria que el
     * limite maximo... fuera mas grande"): por arriba de {@link #ESCALA_CUBRIR}
     * el diseño sigue creciendo MÁS ALLÁ de cubrir la prenda entera (ver
     * {@code EstampaTextures#pintarLienzo}), en vez de plancharse en el
     * 100% de siempre.
     */
    public static final float ESCALA_MAXIMA = 1.5f;

    /**
     * Los dos presets de estampado, que es lo que elige la palanca.
     *
     * Son presets y no un slider a proposito: cubren los dos casos reales de
     * una sublimadora -la remera entera, o un logo en el pecho- sin obligar a
     * pelear con controles finos a ciegas, gastando tinta y veinte segundos
     * por intento. El record igual guarda escala y posicion libres, asi que
     * agregar control fino despues no rompe nada de esto.
     */
    public enum Modo {
        /** Chica y arriba a la derecha: el logo bordado del pecho. */
        LOGO("logo", 0.42f, 0.18f, 0.18f, false),
        /** La estampa clasica: centrada, del tamano de un dibujo al frente. */
        CENTRADA("centrada", 0.90f, 0f, 0.02f, false),
        /** Full print: cubre el torso entero, recortando lo que sobre. */
        COMPLETO("completo", 1.0f, 0f, 0f, true);

        public final String clave;
        public final float escala, x, y;
        /** Recortar para llenar en vez de encoger para entrar. */
        public final boolean cubrir;

        Modo(String clave, float escala, float x, float y, boolean cubrir) {
            this.clave = clave;
            this.escala = escala;
            this.x = x;
            this.y = y;
            this.cubrir = cubrir;
        }

        public Estampa aplicar(UUID foto) {
            return new Estampa(foto, escala, x, y, 0f, cubrir);
        }

        public Modo siguiente() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** En que cara de la remera va. Una prenda puede tener las dos. */
    public enum Cara {
        FRENTE("frente"),
        ESPALDA("espalda");

        public final String clave;

        Cara(String clave) {
            this.clave = clave;
        }
    }

    public Estampa {
        escala = MathHelper.clamp(escala, ESCALA_MINIMA, ESCALA_MAXIMA);
        x = MathHelper.clamp(x, -0.5f, 0.5f);
        y = MathHelper.clamp(y, -0.5f, 0.5f);
        angulo = ((angulo % 360f) + 360f) % 360f;
    }

    /** Centrada y del tamano de siempre: como salian las remeras viejas. */
    public static Estampa centrada(UUID foto) {
        return new Estampa(foto, ESCALA_DEFECTO, 0f, 0f, 0f, false);
    }

    // Escala y posicion son opcionales a proposito: una estampa centrada y
    // del tamano por defecto no escribe nada en el NBT.
    public static final Codec<Estampa> CODEC = RecordCodecBuilder.create(i -> i.group(
            Uuids.CODEC.fieldOf("foto").forGetter(Estampa::foto),
            Codec.FLOAT.optionalFieldOf("escala", ESCALA_DEFECTO).forGetter(Estampa::escala),
            Codec.FLOAT.optionalFieldOf("x", 0f).forGetter(Estampa::x),
            Codec.FLOAT.optionalFieldOf("y", 0f).forGetter(Estampa::y),
            Codec.FLOAT.optionalFieldOf("angulo", 0f).forGetter(Estampa::angulo),
            Codec.BOOL.optionalFieldOf("cubrir", false).forGetter(Estampa::cubrir),
            Codec.BOOL.optionalFieldOf("espejo", false).forGetter(Estampa::espejo)
    ).apply(i, Estampa::new));

    // A mano y no con PacketCodec.tuple: con el espejo son 7 campos y tuple llega a 6.
    public static final PacketCodec<ByteBuf, Estampa> PACKET_CODEC = PacketCodec.of(
            (e, buf) -> {
                Uuids.PACKET_CODEC.encode(buf, e.foto());
                buf.writeFloat(e.escala());
                buf.writeFloat(e.x());
                buf.writeFloat(e.y());
                buf.writeFloat(e.angulo());
                buf.writeBoolean(e.cubrir());
                buf.writeBoolean(e.espejo());
            },
            buf -> new Estampa(Uuids.PACKET_CODEC.decode(buf), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean()));
}
