package com.modamod.sublimadora;

import com.modamod.Modamod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Escanear estampa" de la Sublimadora (2026-10-02, "agregar un boton de
 * escanear estampa y conectar como si se usara la camara de camerapture con
 * shift click y se clickeara en cargar imagen, abre explorador para
 * seleccionar imagen y produce una imagen de camerapture que queda en el slot
 * de frente si esta vacio primero y si se repite en el slot de atras").
 *
 * <p>Lo mismo que hace la cámara de Camerapture al cargar una imagen, pero
 * sin cámara y con la foto directo en la máquina:
 * <ol>
 *   <li>el cliente elige el archivo, lo achica y lo comprime a WebP con las
 *       mismas funciones y límites de Camerapture ({@code SublimadoraScreen});</li>
 *   <li>lo manda en partes ({@link Parte}): un paquete del cliente no puede
 *       pasar de ~32 KB;</li>
 *   <li>el servidor arma la imagen, gasta 1 papel del tanque de la máquina
 *       (como la cámara gasta 1 papel; nada en creativo o en la Sublimadora
 *       creativa), la guarda en el almacenamiento de fotos de Camerapture y
 *       pone el ítem de foto en Frente; si Frente ya tiene foto, en Espalda;
 *       si las dos están ocupadas, en el almacén o en el inventario.</li>
 * </ol>
 */
public final class EscanearEstampa {

    private EscanearEstampa() {}

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("modamod-escanear");

    /** Tamaño de cada parte (bytes): por debajo del tope de un paquete C2S. */
    public static final int TAMANO_PARTE = 30000;

    /** Una parte de la imagen: {@code subida} la identifica, {@code total} = cantidad de partes. */
    public record Parte(BlockPos pos, int subida, int indice, int total, byte[] datos) implements CustomPayload {
        public static final Id<Parte> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "sublimadora_escanear"));
        public static final PacketCodec<RegistryByteBuf, Parte> CODEC = PacketCodec.of(
                (p, buf) -> {
                    buf.writeBlockPos(p.pos());
                    buf.writeVarInt(p.subida());
                    buf.writeVarInt(p.indice());
                    buf.writeVarInt(p.total());
                    buf.writeByteArray(p.datos());
                },
                buf -> new Parte(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readByteArray(TAMANO_PARTE)));

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Lo que va llegando de cada jugador (una subida a la vez). */
    private static final class Subida {
        final int id, total;
        final BlockPos pos;
        int siguiente = 0;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Subida(int id, int total, BlockPos pos) {
            this.id = id;
            this.total = total;
            this.pos = pos;
        }
    }

    private static final Map<UUID, Subida> SUBIDAS = new HashMap<>();

    public static void init() {
        PayloadTypeRegistry.playC2S().register(Parte.ID, Parte.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Parte.ID, (payload, context) ->
                context.server().execute(() -> recibir(context.player(), payload)));
    }

    private static void recibir(ServerPlayerEntity jugador, Parte p) {
        // Sin Camerapture no hay dónde guardar la foto, y tocar CameraptureCompat tiraría NoClassDefFoundError en el
        // hilo del servidor (hallazgo H05, 2026-10-08).
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("camerapture")) {
            SUBIDAS.remove(jugador.getUuid());
            error(jugador, "modamod.sublimadora.escanear.sin_camerapture");
            return;
        }
        if (!(jugador.getWorld().getBlockEntity(p.pos()) instanceof SublimadoraBlockEntity be)
                || !be.canPlayerUse(jugador) || p.total() <= 0) {
            SUBIDAS.remove(jugador.getUuid());
            return;
        }
        int maximo = CameraptureCompat.maximoBytes();
        if (p.total() > maximo / TAMANO_PARTE + 2) {
            error(jugador, "modamod.sublimadora.escanear.grande");
            return;
        }
        Subida s = SUBIDAS.get(jugador.getUuid());
        if (p.indice() == 0 || s == null || s.id != p.subida()) {
            if (p.indice() != 0) return;                         // restos de una subida vieja
            s = new Subida(p.subida(), p.total(), p.pos());
            SUBIDAS.put(jugador.getUuid(), s);
        }
        if (p.indice() != s.siguiente || !s.pos.equals(p.pos())) {
            SUBIDAS.remove(jugador.getUuid());
            error(jugador, "modamod.sublimadora.escanear.fallo");
            return;
        }
        s.bytes.writeBytes(p.datos());
        s.siguiente++;
        if (s.bytes.size() > maximo) {
            SUBIDAS.remove(jugador.getUuid());
            error(jugador, "modamod.sublimadora.escanear.grande");
            return;
        }
        if (s.siguiente < s.total) return;
        SUBIDAS.remove(jugador.getUuid());
        terminar(jugador, be, s.bytes.toByteArray());
    }

    private static void terminar(ServerPlayerEntity jugador, SublimadoraBlockEntity be, byte[] imagen) {
        // Como la cámara: 1 papel por foto (del tanque de la máquina).
        boolean gratis = jugador.isCreative() || com.modamod.util.MaquinaCreativa.es(be);
        if (!gratis && be.getPapel() <= 0) {
            error(jugador, "modamod.sublimadora.escanear.sin_papel");
            return;
        }
        ItemStack foto;
        try {
            foto = CameraptureCompat.crearFoto(jugador, imagen);
        } catch (Exception e) {
            LOG.error("No se pudo guardar la estampa escaneada", e);
            error(jugador, "modamod.sublimadora.escanear.fallo");
            return;
        }
        if (!gratis) be.gastarPapel(1);
        // La foto queda guardada en la bolsa de fotos (2026-10-13); además se pone en Frente si está vacío, si no en Espalda.
        be.absorber(foto.copy());
        for (int slot = 0; slot < 2 && !foto.isEmpty(); slot++) {
            if (!be.getStack(slot).isEmpty()) continue;
            be.setStack(slot, foto);
            if (!be.getStack(slot).isEmpty()) foto = ItemStack.EMPTY;
        }
        be.markDirty();
        jugador.sendMessage(Text.translatable("modamod.sublimadora.escanear.listo").formatted(Formatting.GREEN), true);
    }

    private static void error(ServerPlayerEntity jugador, String clave) {
        jugador.sendMessage(Text.translatable(clave).formatted(Formatting.RED), true);
    }
}
