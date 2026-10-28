package com.modamod.pruebas;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Maquina de pasos del cliente de pruebas. Cada paso corre en un tick del cliente; si devuelve false se reintenta el
 * tick siguiente (hasta el tope), y {@code espera} son los ticks que se dejan pasar DESPUES del paso, para que la
 * pantalla se dibuje antes de la captura. Un paso que tira una excepcion corta la corrida y deja pruebas-fallo.txt.
 */
public final class Corrida {
    public interface Accion { boolean correr(MinecraftClient c) throws Exception; }

    private record Paso(String nombre, int espera, Accion accion) {}

    private final Informe informe;
    private final List<Paso> pasos = new ArrayList<>();
    private int indice, esperando, intentos;
    private boolean terminada, mundoPedido;
    private static final int TOPE_REINTENTOS = 1200;   // 1 minuto

    public Corrida(String suite) { this.informe = new Informe(suite); }

    public Informe informe() { return informe; }

    public Corrida paso(String nombre, Accion accion) { return paso(nombre, 0, accion); }
    public Corrida paso(String nombre, int espera, Accion accion) { pasos.add(new Paso(nombre, espera, accion)); return this; }

    /** Paso que no puede fallar a medias: hace algo y sigue. */
    public Corrida hacer(String nombre, int espera, ThrowingRunnable r) { return paso(nombre, espera, c -> { r.run(c); return true; }); }
    public interface ThrowingRunnable { void run(MinecraftClient c) throws Exception; }

    /** Espera una condicion (ticks de cliente) y reintenta hasta el tope. */
    public Corrida esperar(String nombre, BooleanSupplier cond) { return paso(nombre, c -> cond.getAsBoolean()); }

    /** Captura despues de dejar renderizar; el nombre sin extension va a screenshots/ de la carpeta de la corrida. */
    public Corrida captura(String nombre) {
        return paso("captura " + nombre, 0, c -> {
            File dir = c.runDirectory;
            ScreenshotRecorder.saveScreenshot(dir, nombre + ".png", c.getFramebuffer(), t -> {});
            informe.captura("screenshots/" + nombre + ".png");
            return true;
        });
    }

    /** Espera un rato (ticks) sin hacer nada; sirve para que se renderice o para que el servidor procese. */
    public Corrida dejarPasar(int ticks) { return paso("espera " + ticks, ticks, c -> true); }

    /** Crea un mundo plano creativo nuevo y espera a estar adentro. Primer paso de cualquier corrida con mundo. */
    public Corrida mundoPlano() {
        return mundo("modamod-pruebas-" + System.currentTimeMillis(), new GameRules());
    }

    /** Igual que {@link #mundoPlano()} con nombre y reglas propias (el salon usa un nombre fijo, sin mobs ni ciclo de dia). */
    public Corrida mundo(String nombre, GameRules reglas) {
        paso("abrir mundo plano", c -> {
            if (!mundoPedido) {
                if (!(c.currentScreen instanceof TitleScreen) || c.getOverlay() != null) return false;
                mundoPedido = true;
                c.createIntegratedServerLoader().createAndStart(nombre,
                        new LevelInfo(nombre, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, reglas, DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(1234L, false, false),
                        registry -> registry.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(),
                        new TitleScreen());
            }
            return c.player != null && c.world != null && c.getOverlay() == null && c.getServer() != null;
        });
        return dejarPasar(40);
    }

    // ── helpers de servidor ─────────────────────────────────────────────────────────────

    /** Corre algo en el hilo del servidor integrado y devuelve el resultado (para armar bloques, items, Trinkets). */
    public static <T> T enServidor(MinecraftClient c, Supplier<T> f) {
        MinecraftServer s = c.getServer();
        return CompletableFuture.supplyAsync(f, s).join();
    }

    public static ServerPlayerEntity jugador(MinecraftClient c) {
        return c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
    }

    // ── ejecucion ───────────────────────────────────────────────────────────────────────

    /** Engancha la maquina al tick del cliente. {@code salida} = carpeta de resultados (run/pruebas). */
    public void iniciar() {
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (terminada) return;
            if (esperando > 0) { esperando--; return; }
            if (indice >= pasos.size()) { cerrar(c); return; }
            Paso p = pasos.get(indice);
            try {
                if (!p.accion.correr(c)) {
                    if (++intentos > TOPE_REINTENTOS) throw new AssertionError("tiempo agotado esperando: " + p.nombre);
                    return;
                }
                intentos = 0;
                indice++;
                esperando = p.espera;
                if (!p.nombre.startsWith("espera") && !p.nombre.startsWith("captura")) informe.ok(p.nombre, "");
            } catch (Throwable t) {
                informe.fallo(p.nombre, t.toString());
                t.printStackTrace();
                try { ScreenshotRecorder.saveScreenshot(c.runDirectory, "fallo.png", c.getFramebuffer(), x -> {}); informe.captura("screenshots/fallo.png"); } catch (Throwable ignorado) { /* sin captura */ }
                terminada = true;
                cerrar(c);
            }
        });
    }

    private void cerrar(MinecraftClient c) {
        terminada = true;
        Path dir = c.runDirectory.toPath().resolve("pruebas");
        informe.escribir(dir);
        System.out.println("[pruebas] informe en " + dir.resolve("informe.md"));
        c.scheduleStop();
    }

    /** GUI a escala 2 como la juega el usuario (a pedido 2026-10-19, "no deberias poner la gui a 2x?"). */
    public static void guiDos(MinecraftClient c) {
        c.options.getGuiScale().setValue(2);
        c.options.pauseOnLostFocus = false;   // sin foco el juego abre el menu de pausa y el servidor deja de avanzar
        c.onResolutionChanged();
    }

    /** Borra resultados y capturas de la corrida anterior para que no se confundan con las nuevas. */
    public static void limpiar(MinecraftClient c) {
        try {
            Path shots = c.runDirectory.toPath().resolve("screenshots");
            if (Files.isDirectory(shots)) try (var s = Files.list(shots)) { s.forEach(f -> f.toFile().delete()); }
            Path res = c.runDirectory.toPath().resolve("pruebas");
            if (Files.isDirectory(res)) try (var s = Files.list(res)) { s.forEach(f -> f.toFile().delete()); }
            // Los mundos de pruebas de corridas anteriores (cada corrida crea uno nuevo, de ~10-20 MB): se borran para no llenar el disco.
            Path saves = c.runDirectory.toPath().resolve("saves");
            if (Files.isDirectory(saves)) try (var s = Files.list(saves)) {
                s.filter(p -> p.getFileName().toString().startsWith("modamod-pruebas-") && !p.getFileName().toString().equals(c.getServer() == null ? "" : c.getServer().getSaveProperties().getLevelName()))
                        .forEach(p -> { try (var w = Files.walk(p)) { w.sorted(java.util.Comparator.reverseOrder()).forEach(q -> q.toFile().delete()); } catch (java.io.IOException ignorado) { /* queda para la proxima */ } });
            }
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
