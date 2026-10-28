package com.modamod.client;

import com.modamod.sublimadora.EscanearEstampa;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Lado cliente de "Escanear estampa" (2026-10-02, "abre explorador para
 * seleccionar imagen y produce una imagen de camerapture"): el mismo camino
 * que "Cargar imagen" de la cámara de Camerapture — explorador de archivos
 * de TinyFileDialogs en otro hilo, la imagen achicada a la resolución máxima
 * del servidor y comprimida a WebP bajando la calidad hasta que entre en el
 * tope de bytes — y después se manda en partes a la Sublimadora
 * ({@link EscanearEstampa.Parte}), que la convierte en la foto.
 */
public final class EscanerEstampaCliente {

    private EscanerEstampaCliente() {}

    private static final Logger LOG = LoggerFactory.getLogger("modamod-escanear");
    private static final String[] EXTENSIONES = {"*.png", "*.jpg", "*.jpeg", "*.webp", "*.bmp", "*.gif"};
    /** Si ya hay un explorador abierto (no se abren dos). */
    private static volatile boolean ocupado = false;
    private static int subidas = 0;

    /** ¿Está Camerapture? Sin él no hay dónde guardar la foto (hallazgo H05, 2026-10-08). */
    public static boolean disponible() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("camerapture");
    }

    /** Abre el explorador y, si se elige una imagen, la manda a la Sublimadora de {@code pos}. */
    public static void escanear(BlockPos pos) {
        if (ocupado) return;
        if (!disponible()) {
            avisar("modamod.sublimadora.escanear.sin_camerapture", Formatting.RED);
            return;
        }
        ocupado = true;
        Thread hilo = new Thread(() -> {
            try {
                String ruta = elegirArchivo();
                if (ruta != null) preparar(pos, Path.of(ruta));
            } catch (Exception | LinkageError e) {   // LinkageError: Camerapture de otra versión (no es una Exception)
                LOG.error("No se pudo escanear la estampa", e);
                avisar("modamod.sublimadora.escanear.fallo", Formatting.RED);
            } finally {
                ocupado = false;
            }
        }, "modamod-escaner");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static String elegirArchivo() {
        try (MemoryStack pila = MemoryStack.stackPush()) {
            PointerBuffer filtros = pila.mallocPointer(EXTENSIONES.length);
            for (String e : EXTENSIONES) filtros.put(pila.UTF8(e));
            filtros.flip();
            return TinyFileDialogs.tinyfd_openFileDialog(
                    Text.translatable("modamod.sublimadora.escanear.titulo").getString(), "", filtros,
                    Text.translatable("modamod.sublimadora.escanear.imagenes").getString(), false);
        }
    }

    private static void preparar(BlockPos pos, Path archivo) throws Exception {
        byte[] listos = CameraptureEscanerCompat.comprimir(archivo);
        if (listos != null) MinecraftClient.getInstance().execute(() -> enviar(pos, listos));
    }

    private static void enviar(BlockPos pos, byte[] bytes) {
        int id = ++subidas;
        int tam = EscanearEstampa.TAMANO_PARTE;
        int total = Math.max(1, (bytes.length + tam - 1) / tam);
        for (int i = 0; i < total; i++) {
            byte[] parte = Arrays.copyOfRange(bytes, i * tam, Math.min(bytes.length, (i + 1) * tam));
            ClientPlayNetworking.send(new EscanearEstampa.Parte(pos, id, i, total, parte));
        }
        avisar("modamod.sublimadora.escanear.enviando", Formatting.GRAY);
    }

    static void avisar(String clave, Formatting color) {
        MinecraftClient cliente = MinecraftClient.getInstance();
        cliente.execute(() -> {
            if (cliente.player != null) cliente.player.sendMessage(Text.translatable(clave).formatted(color), true);
        });
    }
}
