package com.modamod.body;

import com.modamod.Modamod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Paquetes de la GUI de elegir cuerpo (2026-09-29, "la primera vez que uno
 * se pone una prenda del mod te lance una gui con el color de skin calculado
 * y te de la alternativa de elegir cualquiera de esas texturas base"):
 * <ul>
 *   <li>{@link Elegir} (cliente -> servidor): lo que se confirmó en la GUI.</li>
 *   <li>{@link Abrir} (servidor -> cliente): {@code /modamod elegir}
 *   vuelve a abrir la GUI — el comando corre en el servidor y la pantalla
 *   vive en el cliente.</li>
 * </ul>
 */
public final class RedCuerpo {

    /**
     * Cuerpo por clave y los colores de sus 3 zonas en RGB — Base
     * ({@link PerfilCuerpo#TONO_DE_LA_SKIN} = sacarlo de la skin), Clara y
     * Oscura ({@link PerfilCuerpo#TONO_AUTOMATICO} = salen del Base).
     */
    public record Elegir(String cuerpo, int tono, int claro, int oscuro, int rubor, int fuerza,
                         String arriba, String abajo, int colorInterior, boolean siempre,
                         boolean bustoCuadrado, int volumen) implements CustomPayload {
        public static final Id<Elegir> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "elegir_cuerpo"));
        /** A mano: son 12 campos y {@code PacketCodec.tuple} llega hasta 6. */
        public static final PacketCodec<RegistryByteBuf, Elegir> CODEC = PacketCodec.of(
                (e, buf) -> {
                    buf.writeString(e.cuerpo(), 32);
                    buf.writeInt(e.tono());
                    buf.writeInt(e.claro());
                    buf.writeInt(e.oscuro());
                    buf.writeInt(e.rubor());
                    buf.writeVarInt(e.fuerza());
                    buf.writeString(e.arriba(), 32);
                    buf.writeString(e.abajo(), 32);
                    buf.writeInt(e.colorInterior());
                    buf.writeBoolean(e.siempre());
                    buf.writeBoolean(e.bustoCuadrado());
                    buf.writeVarInt(e.volumen());
                },
                buf -> new Elegir(buf.readString(32), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readVarInt(), buf.readString(32), buf.readString(32), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Abrir() implements CustomPayload {
        public static final Id<Abrir> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "abrir_elegir_cuerpo"));
        public static final PacketCodec<RegistryByteBuf, Abrir> CODEC = PacketCodec.unit(new Abrir());

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    private RedCuerpo() {}

    private static RopaInterior interiorDe(Elegir e) {
        InteriorArriba arriba = RopaInterior.DEFECTO.arriba();
        for (InteriorArriba a : InteriorArriba.values()) if (a.clave.equals(e.arriba())) arriba = a;
        InteriorAbajo abajo = RopaInterior.DEFECTO.abajo();
        for (InteriorAbajo b : InteriorAbajo.values()) if (b.clave.equals(e.abajo())) abajo = b;
        return new RopaInterior(arriba, abajo, e.colorInterior() & 0xFFFFFF);
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(Elegir.ID, Elegir.CODEC);
        PayloadTypeRegistry.playS2C().register(Abrir.ID, Abrir.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Elegir.ID, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayerEntity jugador = context.player();
                    CuerpoBase cuerpo = CuerpoBase.deClave(payload.cuerpo());
                    // conCuerpo/conTonos ya marcan el perfil como elegido: la
                    // GUI no vuelve a saltar sola.
                    PerfilesDeCuerpo.poner(jugador, PerfilesDeCuerpo.de(jugador).conCuerpo(cuerpo)
                            .conTonos(payload.tono() & 0xFFFFFF, payload.claro() & 0xFFFFFF, payload.oscuro() & 0xFFFFFF, payload.rubor() & 0xFFFFFF,
                                    net.minecraft.util.math.MathHelper.clamp(payload.fuerza(), 0, 100))
                            .conInterior(interiorDe(payload))
                            .conSiempre(payload.siempre())
                            .conBustoCuadrado(payload.bustoCuadrado())
                            .conVolumen(payload.volumen()));
                }));
    }

    public static void abrirEn(ServerPlayerEntity jugador) {
        ServerPlayNetworking.send(jugador, new Abrir());
    }
}
