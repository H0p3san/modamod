package com.modamod.aplique;

import com.modamod.Modamod;
import com.modamod.item.ModamodComponents;
import com.modamod.item.ModamodItems;
import com.modamod.prenda.Parte;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Apliques custom cargados con el servidor andando (2026-11-12, "una forma de cargar apliques custom una vez activo
 * el server (modelo e icono)"). En {@code config/modamod/apliques/} del servidor va un par de archivos por aplique:
 * {@code <nombre>.geo.json} (modelo de GeckoLib con las mismas reglas que los del mod: mira a -Z, apoya en z = 0, UV
 * sobre el atlas de 3 zonas de 96 px de ancho) y {@code <nombre>.png} (el ícono del molde). {@code /modamod apliques
 * recargar} los lee y los manda a todos; quien entra después los recibe al entrar. {@code /modamod apliques dar
 * <nombre>} entrega su molde, que es un molde de aplique personalizado de siempre con el modelo {@code custom_<nombre>}:
 * de ahí en más el aplique es uno más.
 *
 * <p>Opcional, {@code <nombre>.textura.png}: la textura propia del modelo, en gris y con las 3 zonas una al lado de la otra
 * (cada tercio del ancho es una zona, como el atlas del mod). Se tiñe por zona con el retazo, así que lo que sale de la
 * estación de tintes le llega igual que a los apliques del mod. Sin ese archivo el modelo usa el atlas del mod.
 *
 * <p>Opcional también, {@code <nombre>.animation.json} (2026-11-12, "si quiero hacer unas orejitas de gato animadas"): el
 * archivo de animación de Blockbench. La primera animación del archivo se reproduce en bucle sobre los huesos del modelo
 * (ver {@code render.ApliqueRenderer#animar}).
 */
public final class ApliquesCustom {

    private ApliquesCustom() {}

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("modamod");

    /** Los custom nunca pisan un modelo del mod: su clave lleva este prefijo. */
    public static final String PREFIJO = "custom_";
    // Juntos entran holgados en el MiB de un paquete del servidor.
    private static final int MAX_GEO = 512 * 1024, MAX_TEXTURA = 256 * 1024, MAX_ANIMACION = 128 * 1024, MAX_ICONO = 64 * 1024;
    /** Los archivos de un aplique, en el orden en que se suben: el ícono va último y cierra la subida. */
    public static final int GEO = 0, TEXTURA = 1, ANIMACION = 2, ICONO = 3;
    private static final int[] MAXIMOS = { MAX_GEO, MAX_TEXTURA, MAX_ANIMACION, MAX_ICONO };
    /** Cómo termina el nombre de cada archivo, en ese mismo orden. La textura y la animación son opcionales. */
    public static final String[] EXTENSIONES = { ".geo.json", ".textura.png", ".animation.json", ".png" };
    private static final String EXT = EXTENSIONES[GEO];

    /** El peso máximo (bytes) de ese archivo ({@link #GEO}, {@link #TEXTURA}, {@link #ANIMACION} o {@link #ICONO}). */
    public static int maximo(int archivo) {
        return MAXIMOS[archivo];
    }

    /**
     * Un aplique custom entero, del servidor al cliente. {@code clave} ya con el prefijo; {@code textura} vacía = el atlas del
     * mod; {@code animacion} vacía = sin animación propia.
     */
    public record Payload(String clave, byte[] geo, byte[] textura, byte[] animacion, byte[] icono) implements CustomPayload {
        public static final Id<Payload> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "aplique_custom"));
        public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.tuple(
                PacketCodecs.string(48), Payload::clave,
                PacketCodecs.byteArray(MAX_GEO), Payload::geo,
                PacketCodecs.byteArray(MAX_TEXTURA), Payload::textura,
                PacketCodecs.byteArray(MAX_ANIMACION), Payload::animacion,
                PacketCodecs.byteArray(MAX_ICONO), Payload::icono,
                Payload::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Un trozo de un archivo que sube el cliente desde la Estilista creativa (botón "Subir aplique"): un paquete del
     * cliente no pasa de 32 KB, así que va de a partes, en el orden {@link #GEO}, {@link #TEXTURA} y {@link #ANIMACION} (si hay), {@link #ICONO}. {@code parte} 0 empieza el
     * archivo; {@code ultimo} lo cierra. El servidor lo guarda en su carpeta, recarga y entrega el molde. Solo operadores.
     */
    public record Subir(net.minecraft.util.math.BlockPos pos, String nombre, int archivo, int parte, boolean ultimo, byte[] trozo)
            implements CustomPayload {
        public static final int TROZO = 30000;
        public static final Id<Subir> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "subir_aplique_custom"));
        public static final PacketCodec<RegistryByteBuf, Subir> CODEC = PacketCodec.tuple(
                net.minecraft.util.math.BlockPos.PACKET_CODEC, Subir::pos,
                PacketCodecs.string(32), Subir::nombre,
                PacketCodecs.VAR_INT, Subir::archivo,
                PacketCodecs.VAR_INT, Subir::parte,
                PacketCodecs.BOOL, Subir::ultimo,
                PacketCodecs.byteArray(TROZO), Subir::trozo,
                Subir::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Lo que va subiendo cada jugador: el archivo en curso y los que ya llegaron enteros. */
    private static final class Subida {
        String nombre;
        int archivo;
        final java.io.ByteArrayOutputStream actual = new java.io.ByteArrayOutputStream();
        final byte[][] listos = new byte[EXTENSIONES.length][];
    }

    private static final Map<java.util.UUID, Subida> SUBIDAS = new java.util.HashMap<>();

    private static void recibir(Subir p, ServerPlayerEntity jugador) {
        // Frontera de confianza: operador, frente a una Estilista creativa, y un nombre que no sale de la carpeta.
        if (!jugador.hasPermissionLevel(2) || !p.nombre().matches("[a-z0-9_]{1,32}") || p.archivo() < GEO || p.archivo() > ICONO
                || !(com.modamod.estilista.EditorEstilo.en(jugador.getWorld(), p.pos()) instanceof com.modamod.estilista.EditorEstilo be)
                || !be.creativa() || !be.canPlayerUse(jugador)) {
            SUBIDAS.remove(jugador.getUuid());
            if (p.parte() == 0 && p.archivo() == GEO) jugador.sendMessage(Text.translatable("modamod.apliques.subir.sin_permiso"), false);
            return;
        }
        Subida s = SUBIDAS.computeIfAbsent(jugador.getUuid(), u -> new Subida());
        if (p.parte() == 0) {
            if (p.archivo() == GEO) java.util.Arrays.fill(s.listos, null);
            s.nombre = p.nombre();
            s.archivo = p.archivo();
            s.actual.reset();
        }
        // De otro archivo, el ícono sin su modelo o pasado de peso: se descarta todo.
        if (!p.nombre().equals(s.nombre) || p.archivo() != s.archivo || (p.archivo() != GEO && s.listos[GEO] == null)
                || s.actual.size() + p.trozo().length > MAXIMOS[p.archivo()]) {
            SUBIDAS.remove(jugador.getUuid());
            return;
        }
        s.actual.writeBytes(p.trozo());
        if (!p.ultimo()) return;
        s.listos[p.archivo()] = s.actual.toByteArray();
        if (p.archivo() != ICONO) return;
        SUBIDAS.remove(jugador.getUuid());
        try {
            Files.createDirectories(carpeta());
            for (int i = 0; i < EXTENSIONES.length; i++) {
                // Lo opcional que no vino en esta subida no queda de una versión anterior.
                if (s.listos[i] != null) Files.write(carpeta().resolve(s.nombre + EXTENSIONES[i]), s.listos[i]);
                else Files.deleteIfExists(carpeta().resolve(s.nombre + EXTENSIONES[i]));
            }
        } catch (IOException e) {
            LOG.warn("No se pudo guardar el aplique custom {}", s.nombre, e);
            return;
        }
        recargar(jugador.getServer());
        jugador.getInventory().offerOrDrop(molde(s.nombre));
        jugador.sendMessage(Text.translatable("modamod.apliques.subir.listo", s.nombre), false);
    }

    /** Lee la carpeta y manda todo a todos; devuelve cuántos hay. */
    private static int recargar(MinecraftServer servidor) {
        int n = cargar();
        for (ServerPlayerEntity j : servidor.getPlayerManager().getPlayerList()) {
            for (Payload p : CARGADOS.values()) enviar(j, p);
        }
        return n;
    }

    /** Los cargados, por nombre (sin prefijo). Solo se toca desde el hilo del servidor. */
    private static final Map<String, Payload> CARGADOS = new TreeMap<>();

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
        PayloadTypeRegistry.playC2S().register(Subir.ID, Subir.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Subir.ID,
                (payload, context) -> context.server().execute(() -> recibir(payload, context.player())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, servidor) -> SUBIDAS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STARTED.register(servidor -> cargar());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, servidor) -> {
            for (Payload p : CARGADOS.values()) enviar(handler.player, p);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("modamod").then(CommandManager.literal("apliques")
                        .requires(f -> f.hasPermissionLevel(2))
                        .then(CommandManager.literal("recargar").executes(ctx -> {
                            int n = recargar(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.translatable("modamod.apliques.recargado", n, carpeta().toString()), true);
                            return n;
                        }))
                        .then(CommandManager.literal("dar").then(CommandManager.argument("nombre", StringArgumentType.word())
                                .suggests((ctx, b) -> CommandSource.suggestMatching(CARGADOS.keySet(), b))
                                .executes(ctx -> {
                                    String nombre = StringArgumentType.getString(ctx, "nombre");
                                    if (!CARGADOS.containsKey(nombre)) {
                                        ctx.getSource().sendError(Text.translatable("modamod.apliques.no_existe", nombre));
                                        return 0;
                                    }
                                    ctx.getSource().getPlayerOrThrow().getInventory().offerOrDrop(molde(nombre));
                                    return 1;
                                }))))));
    }

    private static Path carpeta() {
        return FabricLoader.getInstance().getConfigDir().resolve("modamod").resolve("apliques");
    }

    private static void enviar(ServerPlayerEntity jugador, Payload p) {
        if (ServerPlayNetworking.canSend(jugador, Payload.ID)) ServerPlayNetworking.send(jugador, p);
    }

    /** Lee la carpeta de nuevo; devuelve cuántos quedaron. Los que no entran se avisan en el log y se saltean. */
    private static int cargar() {
        CARGADOS.clear();
        Path dir = carpeta();
        try {
            Files.createDirectories(dir);
            try (Stream<Path> archivos = Files.list(dir)) {
                for (Path geo : (Iterable<Path>) archivos::iterator) {
                    String archivo = geo.getFileName().toString();
                    if (!archivo.endsWith(EXT)) continue;
                    String nombre = archivo.substring(0, archivo.length() - EXT.length());
                    Path icono = dir.resolve(nombre + EXTENSIONES[ICONO]), textura = dir.resolve(nombre + EXTENSIONES[TEXTURA]),
                            animacion = dir.resolve(nombre + EXTENSIONES[ANIMACION]);
                    boolean conTextura = Files.isRegularFile(textura), conAnimacion = Files.isRegularFile(animacion);
                    if (!nombre.matches("[a-z0-9_]{1,32}")) {
                        LOG.warn("Aplique custom {}: el nombre solo puede llevar a-z, 0-9 y _ (hasta 32)", archivo);
                    } else if (!Files.isRegularFile(icono)) {
                        LOG.warn("Aplique custom {}: falta el icono {}.png", archivo, nombre);
                    } else if (Files.size(geo) > MAX_GEO || Files.size(icono) > MAX_ICONO || (conTextura && Files.size(textura) > MAX_TEXTURA)
                            || (conAnimacion && Files.size(animacion) > MAX_ANIMACION)) {
                        LOG.warn("Aplique custom {}: muy pesado (modelo hasta {} KB, textura hasta {} KB, animacion hasta {} KB, icono hasta {} KB)",
                                archivo, MAX_GEO / 1024, MAX_TEXTURA / 1024, MAX_ANIMACION / 1024, MAX_ICONO / 1024);
                    } else {
                        CARGADOS.put(nombre, new Payload(PREFIJO + nombre, Files.readAllBytes(geo),
                                conTextura ? Files.readAllBytes(textura) : new byte[0],
                                conAnimacion ? Files.readAllBytes(animacion) : new byte[0], Files.readAllBytes(icono)));
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("No se pudo leer {}", dir, e);
        }
        return CARGADOS.size();
    }

    /** El molde de un custom: el personalizado de siempre, con su plantilla. */
    private static ItemStack molde(String nombre) {
        ItemStack molde = new ItemStack(ModamodItems.MOLDE_APLIQUE_PERSONALIZADO);
        // La misma blandura con que salen puestos los moldes del mod (EditorEstilo.BLANDURA_INICIAL).
        molde.set(ModamodComponents.APLIQUE_PLANTILLA, new Aplique(ModeloAplique.de(PREFIJO + nombre), Parte.TORSO, 0f, 0f, 0f,
                Direction.NORTH, 0f, 1f, RetazoApliqueItem.BLANCO, Aplique.Superficie.CAJA, 0.5f));
        molde.set(DataComponentTypes.CUSTOM_NAME, Text.literal(nombre));
        return molde;
    }
}
