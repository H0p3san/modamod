package com.modamod.client;

import com.modamod.aplique.ObjetoAplique;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El ícono automático del molde de aplique (2026-10-04, "usa un fondo de papel craft con un filtro encima que marque
 * cuatro áreas con distinta iluminación simulando un papel doblado y en ese lugar pongas el dibujo de la cara del
 * bloque del aplique en dibujo lineal de ser posible sino en escala de grises... un recuadro por fuera del color del
 * destaque de la mesa de estilo"): una imagen de 64x64 armada en el juego, una vez por objeto, con
 * <ol>
 *   <li>el papel kraft con cuatro zonas de luz y los pliegues en cruz;</li>
 *   <li>la cara de adelante del bloque (o el sprite del ítem) en dibujo lineal: tinta marrón donde dos texeles
 *       vecinos cambian mucho de tono o de transparencia; si la textura es demasiado ruidosa para eso, escala de
 *       grises de tinta;</li>
 *   <li>el recuadro lila de la Mesa de estilado.</li>
 * </ol>
 */
public final class IconoMoldeAplique {

    private IconoMoldeAplique() {}

    private static final int T = 64;
    /** Lado de la zona del dibujo y su esquina (el recuadro ocupa los 4 px de afuera). */
    private static final int LADO = 48, ORIGEN = 8;
    private static final int[] TINTA = { 59, 36, 16 };
    private static final int[] PAPEL = { 233, 204, 165 };

    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Map<Identifier, int[]> SPRITES = new HashMap<>();

    /** La textura del ícono para ese objeto (la crea la primera vez). */
    public static Identifier de(ObjetoAplique o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String clave = Integer.toHexString((net.minecraft.registry.Registries.ITEM.getId(o.item().getItem()) + "|"
                + o.bloque() + "|" + o.variante() + "|" + net.minecraft.item.ItemStack.hashCode(o.item())).hashCode());
        Identifier id = CACHE.get(clave);
        if (id != null) return id;
        NativeImage img = new NativeImage(T, T, true);
        papel(img);
        int[] sprite = spriteDe(o);          // {ancho, alto, argb...} o null
        if (sprite != null) dibujo(img, sprite);
        marco(img);
        id = Identifier.of("modamod", "dynamic/molde_aplique_" + clave);
        mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
        CACHE.put(clave, id);
        return id;
    }

    // ── el papel ─────────────────────────────────────────────────────────────

    private static int abgr(int r, int g, int b, int a) {
        return (a << 24) | (Math.min(255, Math.max(0, b)) << 16) | (Math.min(255, Math.max(0, g)) << 8)
                | Math.min(255, Math.max(0, r));
    }

    /** Papel kraft con cuatro zonas de luz (arriba a la izquierda la más clara, abajo a la izquierda la más oscura) y pliegues. */
    private static void papel(NativeImage img) {
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                float luz = y < T / 2 ? (x < T / 2 ? 1.07f : 1.01f) : (x < T / 2 ? 0.86f : 0.94f);
                // un degradé suave dentro de cada zona y algo de grano
                luz *= 1f + 0.03f * (float) Math.sin((x + y) * 0.35);
                int grano = ((x * 73856093) ^ (y * 19349663)) & 7;
                luz += (grano - 3.5f) / 255f * 3f;
                // los pliegues en cruz: una línea más oscura y otra más clara al lado
                if (x == T / 2 - 1 || y == T / 2 - 1) luz *= 0.9f;
                if (x == T / 2 || y == T / 2) luz *= 1.04f;
                img.setColor(x, y, abgr((int) (PAPEL[0] * luz), (int) (PAPEL[1] * luz), (int) (PAPEL[2] * luz), 255));
            }
        }
    }

    /** El recuadro del color de destaque de la Mesa (lila): borde oscuro, dos px claros y un filete marrón por dentro. */
    private static void marco(NativeImage img) {
        int claro = EstiloPergamino.Tema.LILA.claro, oscuro = EstiloPergamino.Tema.LILA.oscuro;
        int cl = abgr((claro >> 16) & 255, (claro >> 8) & 255, claro & 255, 255);
        int os = abgr((oscuro >> 16) & 255, (oscuro >> 8) & 255, oscuro & 255, 255);
        int fi = abgr(TINTA[0], TINTA[1], TINTA[2], 255);
        for (int y = 0; y < T; y++) {
            for (int x = 0; x < T; x++) {
                int d = Math.min(Math.min(x, y), Math.min(T - 1 - x, T - 1 - y));
                if (d == 0) img.setColor(x, y, os);
                else if (d <= 2) img.setColor(x, y, cl);
                else if (d == 3) img.setColor(x, y, fi);
            }
        }
    }

    // ── el dibujo ────────────────────────────────────────────────────────────

    /** Pinta {@code color} (tinta) con ese alfa sobre lo que ya hay. */
    private static void tinta(NativeImage img, int x, int y, int alfa) {
        if (x < 0 || y < 0 || x >= T || y >= T) return;
        int c = img.getColor(x, y);
        float a = alfa / 255f;
        int r = (int) ((c & 255) * (1 - a) + TINTA[0] * a);
        int g = (int) (((c >> 8) & 255) * (1 - a) + TINTA[1] * a);
        int b = (int) (((c >> 16) & 255) * (1 - a) + TINTA[2] * a);
        img.setColor(x, y, abgr(r, g, b, 255));
    }

    private static int luminancia(int argb) {
        return (int) (0.299 * ((argb >> 16) & 255) + 0.587 * ((argb >> 8) & 255) + 0.114 * (argb & 255));
    }

    /**
     * Dibujo lineal sobre la grilla de texeles: una línea de tinta entre dos texeles vecinos cuando cambia mucho el
     * tono o uno es transparente y el otro no (la silueta va más gruesa). Si salen demasiadas líneas (una textura
     * ruidosa, como una piedra), se dibuja en escala de grises de tinta.
     */
    private static void dibujo(NativeImage img, int[] s) {
        int w = s[0], h = s[1];
        int[] px = new int[w * h];
        System.arraycopy(s, 2, px, 0, w * h);
        float paso = LADO / (float) Math.max(w, h);          // px del ícono por texel
        int ox = ORIGEN + (int) ((LADO - w * paso) / 2), oy = ORIGEN + (int) ((LADO - h * paso) / 2);

        boolean[] vertical = new boolean[(w + 1) * h], horizontal = new boolean[w * (h + 1)];
        boolean[] silV = new boolean[(w + 1) * h], silH = new boolean[w * (h + 1)];
        int lineas = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i <= w; i++) {
                int a = i > 0 ? px[j * w + i - 1] : 0, b = i < w ? px[j * w + i] : 0;
                boolean sil = ((a >>> 24) > 40) != ((b >>> 24) > 40);
                boolean lin = sil || ((a >>> 24) > 40 && (b >>> 24) > 40 && Math.abs(luminancia(a) - luminancia(b)) > 38);
                vertical[j * (w + 1) + i] = lin;
                silV[j * (w + 1) + i] = sil;
                if (lin) lineas++;
            }
        }
        for (int j = 0; j <= h; j++) {
            for (int i = 0; i < w; i++) {
                int a = j > 0 ? px[(j - 1) * w + i] : 0, b = j < h ? px[j * w + i] : 0;
                boolean sil = ((a >>> 24) > 40) != ((b >>> 24) > 40);
                boolean lin = sil || ((a >>> 24) > 40 && (b >>> 24) > 40 && Math.abs(luminancia(a) - luminancia(b)) > 38);
                horizontal[j * w + i] = lin;
                silH[j * w + i] = sil;
                if (lin) lineas++;
            }
        }
        boolean ruidoso = lineas > 0.55 * w * h;          // más de media línea por texel: no se lee
        if (ruidoso) {
            // Escala de grises: cada texel, más tinta cuanto más oscuro.
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int c = px[j * w + i];
                    if ((c >>> 24) < 40) continue;
                    int alfa = (int) (200 * (1 - luminancia(c) / 255f)) + 25;
                    for (int y = (int) (j * paso); y < (int) ((j + 1) * paso); y++) {
                        for (int x = (int) (i * paso); x < (int) ((i + 1) * paso); x++) tinta(img, ox + x, oy + y, alfa);
                    }
                }
            }
        }
        for (int j = 0; j < h; j++) {
            for (int i = 0; i <= w; i++) {
                if (!vertical[j * (w + 1) + i] || (ruidoso && !silV[j * (w + 1) + i])) continue;
                boolean sil = silV[j * (w + 1) + i];
                int x = ox + Math.min((int) (i * paso), LADO - 1);
                for (int y = oy + (int) (j * paso); y < oy + (int) ((j + 1) * paso) + 1; y++) {
                    tinta(img, x, y, sil ? 255 : 170);
                    if (sil) tinta(img, i == 0 || !silSolo(px, w, i - 1, j) ? x + 1 : x - 1, y, 255);
                }
            }
        }
        for (int j = 0; j <= h; j++) {
            for (int i = 0; i < w; i++) {
                if (!horizontal[j * w + i] || (ruidoso && !silH[j * w + i])) continue;
                boolean sil = silH[j * w + i];
                int y = oy + Math.min((int) (j * paso), LADO - 1);
                for (int x = ox + (int) (i * paso); x < ox + (int) ((i + 1) * paso) + 1; x++) {
                    tinta(img, x, y, sil ? 255 : 170);
                    if (sil) tinta(img, x, j == 0 || !silSolo(px, w, i, j - 1) ? y + 1 : y - 1, 255);
                }
            }
        }
    }

    /** Si el texel (i, j) es opaco (para engrosar la silueta hacia adentro). */
    private static boolean silSolo(int[] px, int w, int i, int j) {
        return (px[j * w + i] >>> 24) > 40;
    }

    // ── de dónde sale el dibujo ──────────────────────────────────────────────

    /** {ancho, alto, argb...} de la cara de adelante del bloque o del sprite del ítem; null si no se puede leer. */
    @Nullable
    private static int[] spriteDe(ObjetoAplique o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Random azar = Random.create(42);
        Sprite sprite = null;
        try {
            net.minecraft.block.Block bloque = ObjetoAplique.bloqueDe(o.item());
            if (bloque != null) {
                // La cara de adelante del bloque (norte), con el estado elegido en modo bloque.
                BlockState estado = o.bloque() ? o.estado() : bloque.getDefaultState();
                List<net.minecraft.client.render.model.BakedQuad> quads =
                        mc.getBlockRenderManager().getModel(estado).getQuads(estado, Direction.NORTH, azar);
                if (!quads.isEmpty()) sprite = quads.get(0).getSprite();
                if (sprite == null) sprite = mc.getBlockRenderManager().getModel(estado).getParticleSprite();
            }
            if (sprite == null) {
                var modelo = mc.getItemRenderer().getModel(o.item(), mc.world, null, 0);
                List<net.minecraft.client.render.model.BakedQuad> quads = modelo.getQuads(null, null, azar);
                if (quads.isEmpty()) quads = modelo.getQuads(null, Direction.NORTH, azar);
                sprite = !quads.isEmpty() ? quads.get(0).getSprite() : modelo.getParticleSprite();
            }
        } catch (RuntimeException e) {
            return null;
        }
        if (sprite == null) return null;
        Identifier id = sprite.getContents().getId();
        int[] datos = SPRITES.get(id);
        if (datos != null) return datos;
        Identifier archivo = Identifier.of(id.getNamespace(), "textures/" + id.getPath() + ".png");
        try (InputStream in = mc.getResourceManager().getResource(archivo).orElseThrow().getInputStream();
             NativeImage png = NativeImage.read(in)) {
            // Las texturas animadas traen los cuadros uno abajo del otro: el primero es de alto = ancho del sprite.
            int w = Math.min(png.getWidth(), sprite.getContents().getWidth());
            int h = Math.min(png.getHeight(), sprite.getContents().getHeight());
            datos = new int[2 + w * h];
            datos[0] = w;
            datos[1] = h;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = png.getColor(x, y);          // ABGR
                    int a = (c >>> 24) & 255, b = (c >> 16) & 255, g = (c >> 8) & 255, r = c & 255;
                    datos[2 + y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
            SPRITES.put(id, datos);
            return datos;
        } catch (Exception e) {
            return null;
        }
    }
}
