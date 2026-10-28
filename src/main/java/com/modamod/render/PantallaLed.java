package com.modamod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Textura "glowmask" DINÁMICA para los 3 LEDs (verde/rojo/amarillo) de
 * cada máquina — a pedido (2026-09-21, "que brillen de verdad... tipo
 * lucecitas geckolib"). Del mismo tamaño que el atlas real de la
 * máquina, transparente salvo en los pixeles UV de cada LED — ver
 * {@link com.modamod.render.LedGlowLayer}, que la renderiza en una
 * segunda pasada con {@code RenderLayer.getEyes(...)} (luz forzada al
 * máximo, brilla aunque esté oscuro).
 *
 * <h2>Por qué NO se usa {@code AutoGlowingGeoLayer}/{@code _glowmask.png}
 * de GeckoLib</h2>
 * Ese mecanismo "oficial" espera un ARCHIVO de recurso real
 * ({@code &lt;textura&gt;_glowmask.png}, cargado vía
 * {@code ResourceManager}) — sirve para un brillo FIJO, pero acá el
 * color de cada LED es dinámico (parpadea, cambia con el estado de la
 * máquina). Peor: revisando el bytecode de
 * {@code AutoGlowingTexture.getEmissiveResource}, si algo YA está
 * registrado bajo ese id pero NO es instancia de
 * {@code GeoAbstractTexture}, GeckoLib lo PISA con su propia textura de
 * recurso — registrar acá una {@link NativeImageBackedTexture} común no
 * sobreviviría. Por eso se usa un {@link net.minecraft.client.render.RenderLayer#getEyes}
 * directo (mismo mecanismo que los ojos de Enderman/araña) en vez de
 * pelear con esa cadena de carga — funciona con cualquier textura
 * registrada normal, exactamente como el resto de las texturas
 * dinámicas de este mod.
 *
 * <h2>Por qué NO se repinta cada frame</h2>
 * Mismo bug real que ya causó un crash de memoria nativa esta sesión
 * (ver {@link PantallaMaquina}): acá se cachea UNA sola textura por
 * máquina colocada ({@link BlockPos}) y solo se repinta in-place cuando
 * el color de algún LED cambió de verdad desde la última vez (el
 * parpadeo sí cambia — cada ~10 ticks — pero eso sigue siendo mucho más
 * barato que una asignación nueva por frame).
 */
public final class PantallaLed {

    private PantallaLed() {}

    public record Rect(int u, int v, int ancho, int alto) {}

    private static final class Entrada {
        final Identifier id;
        final NativeImageBackedTexture textura;
        int[] ultimosColores;

        Entrada(Identifier id, NativeImageBackedTexture textura, int cantidadLeds) {
            this.id = id;
            this.textura = textura;
            this.ultimosColores = new int[cantidadLeds];
        }
    }

    private static final Map<String, Entrada> CACHE = new HashMap<>();

    /**
     * @param anchoAtlas/altoAtlas tamaño real del atlas de esa máquina (NO el compuesto de PantallaMaquina).
     * @param leds                 rectángulo UV de cada LED, mismo orden que {@code colores}.
     * @param colores               color ABGR opaco de cada LED (0 = apagado/transparente).
     * @param pos                   posición del bloque dueño — una textura por máquina colocada.
     */
    public static Identifier con(int anchoAtlas, int altoAtlas, Rect[] leds, int[] colores, BlockPos pos) {
        String key = anchoAtlas + "x" + altoAtlas + "|" + pos.asLong();
        Entrada entrada = CACHE.get(key);
        if (entrada == null) {
            NativeImage img = new NativeImage(anchoAtlas, altoAtlas, true);
            for (int y = 0; y < altoAtlas; y++) {
                for (int x = 0; x < anchoAtlas; x++) {
                    img.setColor(x, y, 0);
                }
            }
            Identifier id = Identifier.of("modamod", "dynamic/led_" + Integer.toHexString(key.hashCode()));
            NativeImageBackedTexture textura = new NativeImageBackedTexture(img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, textura);
            entrada = new Entrada(id, textura, leds.length);
            CACHE.put(key, entrada);
        }

        if (!Arrays.equals(entrada.ultimosColores, colores)) {
            NativeImage img = entrada.textura.getImage();
            for (int i = 0; i < leds.length; i++) {
                Rect r = leds[i];
                int color = colores[i];
                for (int y = r.v(); y < r.v() + r.alto(); y++) {
                    for (int x = r.u(); x < r.u() + r.ancho(); x++) {
                        img.setColor(x, y, color);
                    }
                }
            }
            entrada.textura.upload();
            entrada.ultimosColores = colores.clone();
        }
        return entrada.id;
    }
}
