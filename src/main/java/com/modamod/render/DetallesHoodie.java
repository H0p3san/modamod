package com.modamod.render;

import com.modamod.Modamod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Los detalles del hoodie pintados SOBRE la tela ya compuesta (color,
 * patrones, estampas, red): bolsillo canguro al frente y el tejido
 * acanalado (rib) de los puños y del ruedo — 2026-09-30, "bolsillo
 * canguro" / "puños y ruedo elásticos". Mismo criterio que la banda de
 * cintura del pantalón ({@code PiezasDelMod#pintarCintura}): en runtime,
 * oscureciendo la tela que ya está, así sigue al color y a cualquier
 * estampa sin un PNG por combinación.
 *
 * <p>Trabaja en el layout de SKIN a la escala real de la imagen
 * ({@code ancho / 64}): torso en (16,16) de 8x12x4, brazo derecho en
 * (40,16), izquierdo en (32,48), ambos de 4x12x4. La fila {@code f} del
 * costado de una caja cae en {@code v = vCaja + prof + f}.
 */
public final class DetallesHoodie {

    private DetallesHoodie() {}

    private static final Map<String, Identifier> CACHE = new HashMap<>();

    /**
     * La tela con los detalles pintados, o {@code base} si no se pudo leer.
     * El resultado se registra con {@link ClothingTextureCache#registrarImagenCompuesta}
     * para que el recorte de mangas ({@code PiezasDelMod#recortarMangaYCachear})
     * lo pueda volver a leer.
     */
    public static Identifier pintar(Identifier base, int filasTorso, int filasIzq, int filasDer) {
        return pintar(base, filasTorso, filasIzq, filasDer, true, true, true, true, com.modamod.item.ChaquetaFrente.CERRADA, com.modamod.item.Calce.NORMAL);
    }

    /**
     * Con el corte modular de la chaqueta (2026-10-07): {@code ribTorso}/{@code ribIzq}/{@code ribDer} = ruedo y puños
     * ajustados (2026-10-07: cada borde con su propio {@code Ruedo}), {@code canguro} = el
     * bolsillo de hoodie (sin capucha no lleva: los bolsillos van por apliques) y {@code abierta} = el frente abierto,
     * una franja del medio de la cara de adelante del torso queda transparente (se ve lo de abajo) con las dos
     * orillas más oscuras.
     */
    public static Identifier pintar(Identifier base, int filasTorso, int filasIzq, int filasDer,
                                    boolean ribTorso, boolean ribIzq, boolean ribDer, boolean canguro, boolean abierta) {
        return pintar(base, filasTorso, filasIzq, filasDer, ribTorso, ribIzq, ribDer, canguro,
                abierta ? com.modamod.item.ChaquetaFrente.ABIERTA : com.modamod.item.ChaquetaFrente.CERRADA, com.modamod.item.Calce.NORMAL);
    }

    /** Ancho de la apertura en píxeles de textura, según el calce (2026-10-08): más holgado = se abre más. */
    public static int anchoApertura(com.modamod.item.Calce calce) {
        return switch (calce) {
            case PEGADO, AJUSTADO, NORMAL -> 2;
            case SUELTO -> 3;
            case OVERSIZE -> 4;
        };
    }

    public static Identifier pintar(Identifier base, int filasTorso, int filasIzq, int filasDer,
                                    boolean ribTorso, boolean ribIzq, boolean ribDer, boolean canguro,
                                    com.modamod.item.ChaquetaFrente frente, com.modamod.item.Calce calce) {
        boolean abierta = frente.abre();
        String key = base + "#hoodie" + filasTorso + "_" + filasIzq + "_" + filasDer + (ribTorso ? "t" : "") + (ribIzq ? "i" : "") + (ribDer ? "d" : "") + (canguro ? "c" : "") + (frente != com.modamod.item.ChaquetaFrente.CERRADA ? "f" + frente.ordinal() + "_" + anchoApertura(calce) : "");
        Identifier cacheada = CACHE.get(key);
        if (cacheada != null) return cacheada;
        NativeImage origen = ClothingTextureCache.imagenBase(base);
        if (origen == null) return base;

        NativeImage img = new NativeImage(origen.getWidth(), origen.getHeight(), true);
        img.copyFrom(origen);
        int s = img.getWidth() / LayoutSkin.LADO;

        if (filasTorso > 0) {
            // Ruedo: la última fila del torso, alrededor de las 4 caras.
            if (ribTorso) rib(img, s, 16, 20 + filasTorso - 1, 24);
            if (canguro) bolsillo(img, s, filasTorso);
            // El frente abierto del todo se abre en las 12 filas del torso, no solo en las del largo (2026-10-15, "se ve que se
            // cierra justo abajo el open front"): lo que está más allá del ruedo es transparente igual, y así la apertura
            // llega hasta el borde de abajo aunque la tela dibujada tenga más filas que el largo del molde.
            if (abierta) abrirFrente(img, s, frente == com.modamod.item.ChaquetaFrente.ABIERTA ? 12 : frente.filasAbiertas(filasTorso),
                    anchoApertura(calce));
            if (frente == com.modamod.item.ChaquetaFrente.CRUZADA) cruzarFrente(img, s, filasTorso);
        }
        if (ribDer && filasDer > 0) rib(img, s, 40, 20 + filasDer - 1, 16);
        if (ribIzq && filasIzq > 0) rib(img, s, 32, 52 + filasIzq - 1, 16);

        Identifier id = Identifier.of(Modamod.MOD_ID, "dynamic/hoodie_" + Integer.toHexString(key.hashCode()));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
        ClothingTextureCache.registrarImagenCompuesta(id, img);
        CACHE.put(key, id);
        return id;
    }

    /**
     * Rib en el borde de abajo y/o de arriba de una pierna o un brazo ya recortado (2026-10-08, ruedo ajustado en
     * botamangas, medias y calientabrazos): una fila de canales alrededor de las 4 caras en la última (o primera)
     * fila con tela. Trabaja sobre la imagen compuesta, a su escala.
     */
    public static void ribEnBorde(NativeImage img, com.modamod.garment.Parte parte, int desde, int hasta, boolean inf, boolean sup) {
        if (hasta <= desde) return;
        int u, v;
        switch (parte) {
            case PIERNA_DER -> { u = 0; v = 16; }
            case PIERNA_IZQ -> { u = 16; v = 48; }
            case BRAZO_DER -> { u = 40; v = 16; }
            case BRAZO_IZQ -> { u = 32; v = 48; }
            default -> { return; }
        }
        int s = img.getWidth() / LayoutSkin.LADO;
        if (inf) rib(img, s, u, v + 4 + hasta - 1, 16);
        if (sup) rib(img, s, u, v + 4 + desde, 16);
    }

    /** Una fila de skin ({@code v}) de {@code ancho} píxeles desde {@code u}, con canales verticales. */
    private static void rib(NativeImage img, int s, int u, int v, int ancho) {
        int paso = Math.max(2, s / 2);
        for (int y = v * s; y < (v + 1) * s; y++) {
            for (int x = u * s; x < (u + ancho) * s; x++) {
                oscurecer(img, x, y, (x / paso) % 2 == 0 ? 0.78F : 0.90F);
            }
        }
    }

    /**
     * Bolsillo canguro en la cara de adelante del torso (u 20..28): 3 filas
     * justo arriba del ruedo, 6 píxeles de ancho, con las bocas en diagonal
     * a los costados. Un torso de menos de 7 filas (crop) no lleva bolsillo.
     */
    private static void bolsillo(NativeImage img, int s, int filasTorso) {
        if (filasTorso < 7) return;
        int x0 = 21 * s, x1 = 27 * s;
        int y0 = (20 + filasTorso - 4) * s, y1 = (20 + filasTorso - 1) * s;
        int borde = Math.max(1, s / 4);
        int alto = y1 - y0;
        for (int y = y0; y < y1; y++) {
            // Las bocas: la tela del bolsillo se angosta hacia arriba.
            int entrada = (int) ((y1 - y) / (float) alto * s);
            int a = x0 + entrada, b = x1 - entrada;
            for (int x = a; x < b; x++) {
                boolean costura = y < y0 + borde || y >= y1 - borde || x < a + borde || x >= b - borde;
                oscurecer(img, x, y, costura ? 0.70F : 0.93F);
            }
        }
    }

    /**
     * Frente abierto: 2 píxeles del medio de la cara de adelante del torso (u 23..25) transparentes en todo el
     * alto, con una orilla oscura de un cuarto de píxel a cada lado (la solapa).
     */
    private static void abrirFrente(NativeImage img, int s, int filasTorso, int ancho) {
        // Centrada en u 24 (el medio de la cara de adelante, 20..28).
        int x0 = (24 - ancho / 2) * s, x1 = x0 + ancho * s;
        int borde = Math.max(1, s / 4);
        for (int y = 20 * s; y < (20 + filasTorso) * s; y++) {
            for (int x = x0 - borde; x < x0; x++) oscurecer(img, x, y, 0.72F);
            for (int x = x1; x < x1 + borde; x++) oscurecer(img, x, y, 0.72F);
            for (int x = x0; x < x1; x++) img.setColor(x, y, 0);
        }
    }

    /**
     * Frente cruzado (2026-10-08): la tela cerrada con una solapa que monta sobre la otra. Una sombra de un píxel a la
     * izquierda del centro (la solapa de abajo) y una costura oscura de un cuarto de píxel en el canto de la de arriba.
     */
    private static void cruzarFrente(NativeImage img, int s, int filasTorso) {
        int borde = Math.max(1, s / 4);
        for (int y = 20 * s; y < (20 + filasTorso) * s; y++) {
            for (int x = 24 * s - s; x < 24 * s; x++) oscurecer(img, x, y, 0.90F);
            for (int x = 24 * s; x < 24 * s + borde; x++) oscurecer(img, x, y, 0.66F);
        }
    }

    /** Multiplica el RGB de un texel (formato ABGR de NativeImage) sin tocar el alfa ni los huecos. */
    private static void oscurecer(NativeImage img, int x, int y, float f) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return;
        int c = img.getColor(x, y);
        int a = c >>> 24;
        if (a == 0) return;
        int b = (int) (((c >> 16) & 0xFF) * f), g = (int) (((c >> 8) & 0xFF) * f), r = (int) ((c & 0xFF) * f);
        img.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
    }
}
