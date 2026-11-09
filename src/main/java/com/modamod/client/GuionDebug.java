package com.modamod.client;

import com.mojang.brigadier.StringReader;
import com.modamod.body.PerfilesDeCuerpo;
import com.modamod.ropa.ListaDeRopa;
import dev.emi.trinkets.api.TrinketInventory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Guion de prueba sin manos (2026-11-08, "agrega herramientas de debug asi probas mas vos q yo"): si al entrar a un mundo
 * de un jugador existe {@code run/modamod_debug/guion.txt}, lo ejecuta línea por línea y lo renombra a
 * {@code guion.hecho.txt} (así no vuelve a correr). Solo en desarrollo (lo registra {@link ModamodClient}).
 *
 * <pre>
 * busto 4 cuadrado|redondo   talle del busto (dosis, -1..7) y forma
 * volumen 0|1|2              redondeado, voxel fino o voxel grueso
 * vaciar                     saca toda la ropa puesta
 * poner 0 modamod:remera[...] pone el ítem (sintaxis de /give) en el lugar de ropa (0 = el de más afuera)
 * cmd time set noon          un comando, sin la barra
 * esperar 20                 ticks
 * zoom 20                    campo de visión (30..110; menos = más cerca), 45 de entrada
 * listar                     escribe en el log la ropa puesta y el perfil
 * foto nombre frente|atras|costado|abajo|arriba   una captura en run/screenshots/guion_nombre_angulo.png
 * fotos nombre               las cinco
 * salir                      cierra el juego
 * </pre>
 * Sin {@code salir} el juego queda abierto y cada {@code guion.txt} nuevo se ejecuta apenas aparece.
 * Las líneas vacías y las que empiezan con # se saltean. Cada paso deja una línea "[guion]" en el log.
 */
public final class GuionDebug {

    private GuionDebug() {}

    private static final Path ARCHIVO = Path.of("modamod_debug", "guion.txt");
    private static final String[] ANGULOS = {"frente", "atras", "costado", "abajo", "arriba"};

    private static final Deque<String> pasos = new ArrayDeque<>();
    private static boolean cargado;
    private static int espera;
    /** Foto pedida: se saca unos cuadros después de girar, cuando la pose ya se asentó. */
    private static String fotoPendiente;
    private static int cuadrosFoto;
    private static int fovAntes = 70;
    private static int ticksSinGuion;
    /** Hay un guion corriendo (se restaura todo al vaciarse la cola), hay fotos (HUD y FOV cambiados), se cierra al final. */
    private static boolean enCurso, modoFoto, cerrarAlTerminar;
    private static int cerrarEn;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(GuionDebug::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || !(mc.getServer() instanceof IntegratedServer)) {
            cargado = false;
            return;
        }
        if (!cargado) {
            cargado = true;
            cargar(mc);
            espera = 40; // que termine de cargar la ropa y las texturas
        }
        if (cerrarEn > 0 && --cerrarEn == 0) { mc.scheduleStop(); return; }
        // Sin foco la ventana abre el menú de pausa y el mundo se congela: mientras corre el guion, no se pausa.
        if (!pasos.isEmpty() || fotoPendiente != null) {
            mc.options.pauseOnLostFocus = false;
            if (mc.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen) mc.setScreen(null);
        }
        if (fotoPendiente != null) {
            sostenerGiro(mc);
            if (--cuadrosFoto > 0) return;
            String nombre = fotoPendiente;
            fotoPendiente = null;
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, nombre + ".png", mc.getFramebuffer(), m -> {});
            log("foto " + nombre);
            return;
        }
        if (espera > 0) { espera--; return; }
        String linea = pasos.poll();
        if (linea == null && enCurso) {
            // Terminó: todo como estaba (la ropa, el perfil, la cámara), con o sin "salir".
            enCurso = false;
            restaurar((IntegratedServer) mc.getServer(), mc.player.getUuid());
            if (modoFoto) {
                modoFoto = false;
                mc.options.getFov().setValue(fovAntes);
                mc.options.hudHidden = false;
                mc.options.setPerspective(Perspective.FIRST_PERSON);
            }
            mc.options.pauseOnLostFocus = true;
            log("fin");
            if (cerrarAlTerminar) { cerrarAlTerminar = false; cerrarEn = 10; }   // que llegue la ropa devuelta antes de guardar
            return;
        }
        if (linea == null) {
            // Sin "salir" el juego queda abierto mirando el archivo: un guion nuevo corre sin relanzar (2026-11-08, "demoro
            // mucho eso": cada vuelta de gradle + Minecraft son ~3 minutos).
            // Contador propio: el tiempo del mundo se frena con el juego en pausa.
            if (++ticksSinGuion % 20 == 0 && Files.exists(mc.runDirectory.toPath().resolve(ARCHIVO))) cargar(mc);
            return;
        }
        try {
            ejecutar(mc, linea);
        } catch (Exception e) {
            log("ERROR en '" + linea + "': " + e);
        }
    }

    private static void cargar(MinecraftClient mc) {
        Path p = mc.runDirectory.toPath().resolve(ARCHIVO);
        if (!Files.exists(p)) return;
        try {
            List<String> lineas = Files.readAllLines(p, StandardCharsets.UTF_8);
            Files.move(p, p.resolveSibling("guion.hecho.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            for (String l : lineas) {
                String t = l.strip();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (t.startsWith("fotos ")) {
                    for (String a : ANGULOS) pasos.add("foto " + t.substring(6).strip() + " " + a);
                } else {
                    pasos.add(t);
                }
            }
            log("cargado, " + pasos.size() + " pasos");
            enCurso = !pasos.isEmpty();
        } catch (IOException e) {
            log("no se pudo leer " + p + ": " + e);
        }
    }

    private static void ejecutar(MinecraftClient mc, String linea) throws Exception {
        String[] t = linea.split(" ", 3);
        IntegratedServer server = (IntegratedServer) mc.getServer();
        java.util.UUID uuid = mc.player.getUuid();
        switch (t[0]) {
            case "busto" -> {
                int dosis = Integer.parseInt(t[1]);
                boolean cuadrado = t.length > 2 && t[2].strip().equals("cuadrado");
                enServidor(server, uuid, p -> PerfilesDeCuerpo.poner(p,
                        PerfilesDeCuerpo.de(p).conBusto(dosis, Long.MAX_VALUE).conBustoCuadrado(cuadrado)));
                espera = 10;
            }
            case "volumen" -> {
                int v = Integer.parseInt(t[1]);
                enServidor(server, uuid, p -> PerfilesDeCuerpo.poner(p, PerfilesDeCuerpo.de(p).conVolumen(v)));
                espera = 10;
            }
            case "vaciar" -> {
                enServidor(server, uuid, p -> {
                    TrinketInventory inv = ListaDeRopa.de(p);
                    if (inv != null) for (int i = 0; i < inv.size(); i++) inv.setStack(i, ItemStack.EMPTY);
                });
                espera = 5;
            }
            case "poner" -> {
                int lugar = Integer.parseInt(t[1]);
                String item = t[2];
                enServidor(server, uuid, p -> {
                    try {
                        ItemStringReader.ItemResult r = new ItemStringReader(server.getRegistryManager()).consume(new StringReader(item));
                        ItemStack s = new ItemStackArgument(r.item(), r.components()).createStack(1, false);
                        TrinketInventory inv = ListaDeRopa.de(p);
                        if (inv != null) inv.setStack(lugar, s);
                    } catch (Exception e) {
                        log("ERROR poner '" + item + "': " + e);
                    }
                });
                espera = 10;
            }
            case "listar" -> enServidor(server, uuid, p -> {
                TrinketInventory inv = ListaDeRopa.de(p);
                if (inv != null) for (int i = 0; i < inv.size(); i++) {
                    ItemStack s = inv.getStack(i);
                    if (!s.isEmpty()) log("lugar " + i + ": " + net.minecraft.registry.Registries.ITEM.getId(s.getItem())
                            + s.getComponentChanges());
                }
                log("perfil: " + PerfilesDeCuerpo.de(p));
            });
            case "cmd" -> mc.player.networkHandler.sendChatCommand(linea.substring(4).strip());
            case "esperar" -> espera = Integer.parseInt(t[1]);
            case "zoom" -> {
                entrarModoFoto(mc);
                mc.options.getFov().setValue(Integer.parseInt(t[1]));
            }
            case "chato" -> com.modamod.render.relieve.BustoRender.debugChatoAbierto = Float.parseFloat(t[1]);
            case "medir" -> {
                com.modamod.render.relieve.BustoRender.debugMedir = true;
                pasos.addFirst("medirfin");
                espera = 1;
            }
            case "medirfin" -> com.modamod.render.relieve.BustoRender.debugMedir = false;
            case "ocultar" -> {
                com.modamod.render.relieve.BustoRender.debugSinBustoCuerpo = t[1].equals("cuerpo");
                com.modamod.render.relieve.BustoRender.debugSinBustoTela = t[1].equals("tela");
            }
            case "foto" -> girar(mc, t[1], t.length > 2 ? t[2].strip() : "frente");
            case "salir" -> cerrarAlTerminar = true;
            default -> log("no entiendo '" + linea + "'");
        }
        log(linea);
    }

    /** Pone la cámara en tercera persona mirando al jugador desde {@code angulo} y deja la foto pendiente. */
    private static void girar(MinecraftClient mc, String nombre, String angulo) {
        cuadrosFoto = 10;
        entrarModoFoto(mc);
        // La cámara de tercera persona de frente sigue al yaw del jugador: queda fija (yaw 0) y lo que gira es el cuerpo.
        float giro = 0f, pitch = 12f;      // pitch > 0: la cámara baja un poco y centra el torso
        switch (angulo) {
            case "atras" -> giro = 180f;
            case "costado" -> giro = 90f;
            case "abajo" -> pitch = 55f;   // la cámara queda abajo mirando para arriba
            case "arriba" -> pitch = -55f;
            default -> {}
        }
        mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        var p = mc.player;
        p.setYaw(0f); p.prevYaw = 0f;
        p.setPitch(pitch); p.prevPitch = pitch;
        giroCuerpo = giro;
        fotoPendiente = "guion_" + nombre + "_" + angulo;
    }

    /** Sin HUD y con el FOV de las fotos; se guarda el FOV de antes para devolverlo al terminar. */
    private static void entrarModoFoto(MinecraftClient mc) {
        if (modoFoto) return;
        modoFoto = true;
        fovAntes = mc.options.getFov().getValue();
        mc.options.hudHidden = true;
        mc.options.getFov().setValue(45);
    }

    /** El yaw del cuerpo y la cabeza mientras se espera la foto (se repite cada tick: el jugador lo corrige solo). */
    private static float giroCuerpo;

    private static void sostenerGiro(MinecraftClient mc) {
        var p = mc.player;
        p.setHeadYaw(giroCuerpo); p.prevHeadYaw = giroCuerpo;
        p.setBodyYaw(giroCuerpo); p.prevBodyYaw = giroCuerpo;
    }

    /** La ropa y el perfil de antes del guion: se devuelven al salir, así el guion no pisa la partida. */
    private static List<ItemStack> ropaAntes;
    private static com.modamod.body.PerfilCuerpo perfilAntes;

    private static void enServidor(IntegratedServer server, java.util.UUID uuid, java.util.function.Consumer<ServerPlayerEntity> accion) {
        server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p == null) return;
            if (ropaAntes == null) {
                TrinketInventory inv = ListaDeRopa.de(p);
                ropaAntes = new java.util.ArrayList<>();
                if (inv != null) for (int i = 0; i < inv.size(); i++) ropaAntes.add(inv.getStack(i).copy());
                perfilAntes = PerfilesDeCuerpo.de(p);
            }
            accion.accept(p);
        });
    }

    private static void restaurar(IntegratedServer server, java.util.UUID uuid) {
        server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p == null || ropaAntes == null) return;
            TrinketInventory inv = ListaDeRopa.de(p);
            if (inv != null) for (int i = 0; i < inv.size() && i < ropaAntes.size(); i++) inv.setStack(i, ropaAntes.get(i));
            PerfilesDeCuerpo.poner(p, perfilAntes);
            ropaAntes = null;
            log("ropa y perfil restaurados");
        });
    }

    private static void log(String s) {
        org.slf4j.LoggerFactory.getLogger("modamod").info("[guion] " + s);
    }
}
