package com.modamod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * El ícono de 64×64 de los accesorios (sombrero, calzado, banda, maletín; 2026-11-03, "pantallitas en negro"): no tienen
 * plantilla como la ropa del cuerpo, así que se dibuja su ítem de verdad ({@code IconoAccesorioItemRenderer}, con sus moldes,
 * colores y patrones) en un framebuffer aparte y se lee la imagen. Se dibuja fuera del render del mundo, al final del tick
 * del cliente: quien lo pide recibe null la primera vez y vuelve a preguntar ({@link #pendiente}).
 */
public final class IconoAccesorioImagen {

    private IconoAccesorioImagen() {}

    private static final int LADO = 64;
    private static final int MAXIMO = 32;

    /** Las imágenes ya dibujadas, por el hash del stack (ítem y componentes). */
    private static final Map<Integer, NativeImage> HECHAS = new LinkedHashMap<>(16, 0.75f, true);
    /** Lo pedido que falta dibujar. */
    private static final Map<Integer, ItemStack> PEDIDAS = new LinkedHashMap<>();
    private static final Set<Integer> FALLIDAS = new LinkedHashSet<>();

    /** Una copia del ícono de {@code stack} (la cierra quien la pide), o null si no es un accesorio o todavía no está. */
    @Nullable
    public static NativeImage copia(ItemStack stack) {
        if (!AccesorioRenderer.es(stack)) return null;
        int clave = ItemStack.hashCode(stack);
        NativeImage hecha = HECHAS.get(clave);
        if (hecha == null) {
            if (!FALLIDAS.contains(clave)) PEDIDAS.putIfAbsent(clave, stack.copy());
            return null;
        }
        NativeImage salida = new NativeImage(LADO, LADO, true);
        salida.copyFrom(hecha);
        return salida;
    }

    /** ¿Se pidió y todavía no se dibujó? */
    public static boolean pendiente(ItemStack stack) {
        return AccesorioRenderer.es(stack) && PEDIDAS.containsKey(ItemStack.hashCode(stack));
    }

    /** Dibuja lo pedido; se llama al final del tick del cliente, en el hilo de render y fuera del render del mundo. */
    public static void procesar() {
        if (PEDIDAS.isEmpty()) return;
        var it = PEDIDAS.entrySet().iterator();
        var e = it.next();
        it.remove();
        NativeImage img = dibujar(e.getValue());
        if (img == null) {
            FALLIDAS.add(e.getKey());
            return;
        }
        NativeImage vieja = HECHAS.put(e.getKey(), img);
        if (vieja != null) vieja.close();
        while (HECHAS.size() > MAXIMO) {
            var primera = HECHAS.entrySet().iterator();
            primera.next().getValue().close();
            primera.remove();
        }
    }

    /** Recarga de recursos: se tiran las imágenes. */
    public static void limpiar() {
        for (NativeImage i : HECHAS.values()) i.close();
        HECHAS.clear();
        PEDIDAS.clear();
        FALLIDAS.clear();
    }

    @Nullable
    private static NativeImage dibujar(ItemStack stack) {
        MinecraftClient cliente = MinecraftClient.getInstance();
        SimpleFramebuffer fb = new SimpleFramebuffer(LADO, LADO, true, MinecraftClient.IS_SYSTEM_MAC);
        BufferAllocator memoria = new BufferAllocator(256 * 1024);
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack vista = RenderSystem.getModelViewStack();
        vista.pushMatrix();
        try {
            fb.setClearColor(0f, 0f, 0f, 0f);
            fb.clear(MinecraftClient.IS_SYSTEM_MAC);
            fb.beginWrite(true);
            // La misma cámara que la interfaz, pero de 64×64 y con el ítem a 4x (16 → 64).
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, LADO, LADO, 0f, 1000f, 21000f), VertexSorter.BY_Z);
            vista.translation(0f, 0f, -11000f);
            RenderSystem.applyModelViewMatrix();
            VertexConsumerProvider.Immediate buffers = VertexConsumerProvider.immediate(memoria);
            DrawContext contexto = new DrawContext(cliente, buffers);
            contexto.getMatrices().scale(LADO / 16f, LADO / 16f, 1f);
            contexto.drawItem(stack, 0, 0);
            contexto.draw();
            NativeImage img = new NativeImage(LADO, LADO, false);
            fb.beginRead();
            img.loadFromTextureImage(0, false);
            img.mirrorVertically();
            return img;
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger("modamod").warn("No se pudo dibujar el ícono de {}", stack.getItem(), ex);
            return null;
        } finally {
            vista.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            fb.endWrite();
            fb.delete();
            memoria.close();
            cliente.getFramebuffer().beginWrite(true);
        }
    }
}
