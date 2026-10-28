package com.modamod.client;

import com.modamod.render.ClothingTextureCache;
import com.modamod.render.EfectoTrim;
import com.modamod.render.Perf;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * HUD de rendimiento y memoria (a pedido, 2026-10-08, "hud en pantalla, para ver lag y consumo de recursos"):
 * {@code /modamoddebug perf} lo prende y lo apaga. Muestra FPS y ms por cuadro, la memoria de la JVM, el costo por
 * cuadro de cada parte del mod (promedio móvil y pico de los últimos ~3 s) y el tamaño de sus cachés.
 * Las secciones se anidan: "ropa" ya incluye a las demás.
 */
public final class PerfHud {

    private PerfHud() {}

    private static final int VENTANA = 180;                       // cuadros del pico
    private static final double SUAVIZADO = 0.08;                  // promedio móvil exponencial

    private static final int N = Perf.Seccion.values().length;
    private static final double[] MS = new double[N], PROMEDIO = new double[N], PICO = new double[N];
    private static final int[] LLAMADAS = new int[N];
    private static int cuadros = 0;
    private static long ultimoCuadro = 0;
    private static double msCuadro = 0, picoCuadro = 0, promedioCuadro = 0;

    public static void init() {
        HudRenderCallback.EVENT.register(PerfHud::dibujar);
        ClientCommandRegistrationCallback.EVENT.register((d, r) -> d.register(
                ClientCommandManager.literal("modamoddebug").then(ClientCommandManager.literal("perf").executes(ctx -> {
                    Perf.activo = !Perf.activo;
                    ctx.getSource().sendFeedback(Text.literal("HUD de rendimiento: " + (Perf.activo ? "prendido" : "apagado")));
                    reiniciar();
                    return 1;
                }))));
    }

    private static void reiniciar() {
        java.util.Arrays.fill(PROMEDIO, 0);
        java.util.Arrays.fill(PICO, 0);
        cuadros = 0;
        picoCuadro = 0;
        promedioCuadro = 0;
        ultimoCuadro = 0;
    }

    private static void dibujar(DrawContext g, net.minecraft.client.render.RenderTickCounter tick) {
        if (!Perf.activo) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        // Lo acumulado en el cuadro anterior (el HUD se dibuja al final del cuadro, después del mundo).
        Perf.cerrarCuadro(MS, LLAMADAS);
        long ahora = System.nanoTime();
        if (ultimoCuadro != 0) msCuadro = (ahora - ultimoCuadro) / 1_000_000.0;
        ultimoCuadro = ahora;
        promedioCuadro += (msCuadro - promedioCuadro) * SUAVIZADO;
        if (cuadros % VENTANA == 0) {
            java.util.Arrays.fill(PICO, 0);
            picoCuadro = 0;
        }
        picoCuadro = Math.max(picoCuadro, msCuadro);
        for (int i = 0; i < N; i++) {
            PROMEDIO[i] += (MS[i] - PROMEDIO[i]) * SUAVIZADO;
            PICO[i] = Math.max(PICO[i], MS[i]);
        }
        cuadros++;

        Runtime rt = Runtime.getRuntime();
        double usada = (rt.totalMemory() - rt.freeMemory()) / 1048576.0, maxima = rt.maxMemory() / 1048576.0;

        java.util.List<String> lineas = new java.util.ArrayList<>();
        lineas.add(String.format("ModaMod perf  %d FPS  cuadro %.1f ms (pico %.0f)", mc.getCurrentFps(), promedioCuadro, picoCuadro));
        lineas.add(String.format("memoria JVM %.0f / %.0f MB (%.0f%%)", usada, maxima, 100 * usada / maxima));
        lineas.add("-- ms por cuadro: prom / pico  (llamadas)");
        for (Perf.Seccion s : Perf.Seccion.values()) {
            int i = s.ordinal();
            lineas.add(String.format("%-14s %5.2f / %5.1f  (%d)", s.rotulo, PROMEDIO[i], PICO[i], LLAMADAS[i]));
        }
        double[] tela = ClothingTextureCache.estadoCache();
        int[] trim = EfectoTrim.estadoCache();
        lineas.add("-- cachés");
        lineas.add(String.format("telas compuestas %d  imágenes base %d (%.1f MB)", (int) tela[0], (int) tela[1], tela[2]));
        lineas.add(String.format("acabados %d telas, %d cuadros (~%.0f MB GPU)", trim[0], trim[1], trim[1] * 1.0));

        int ancho = 0;
        for (String l : lineas) ancho = Math.max(ancho, mc.textRenderer.getWidth(l));
        int x = 4, y = 4, alto = lineas.size() * 10 + 4;
        g.fill(x - 2, y - 2, x + ancho + 4, y + alto, 0xA0000000);
        for (String l : lineas) {
            int color = l.startsWith("--") ? 0xFF9FD0FF : 0xFFFFFFFF;
            g.drawText(mc.textRenderer, l, x, y, color, false);
            y += 10;
        }
    }
}
