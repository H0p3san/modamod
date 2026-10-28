package com.modamod.render;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.SkinTextures;

import java.util.HashMap;
import java.util.Map;

/**
 * Saca el tono de piel de un jugador desde su textura de skin.
 *
 * Ya no se usa para repintar nada en runtime: eso lo hace el cuerpo base.
 * Queda para UNA cosa, que es la que justifica su existencia — el tono por
 * defecto del cuerpo base se DERIVA de la skin del jugador, una sola vez, en
 * vez de arrancar con un beige elegido por nosotros. Es lo que hace que
 * ponerse una prenda no le cambie el color de piel a alguien que no pidio
 * nada.
 *
 * Método: MODA (color más frecuente) sobre varias regiones que en la enorme
 * mayoría de las skins son piel, no un pixel fijo. La versión vieja
 * sampleaba solo (44,20) y su comentario decía que era "la parte interna del
 * antebrazo" — en el layout 64x64 ese pixel es en realidad la esquina
 * superior de la cara FRONTAL del brazo derecho, o sea el hombro, que en la
 * mayoría de las skins está tapado por la manga de la remera.
 *
 * Dentro de la cara los ojos, la boca y el pelo son minoría, así que la moda
 * cae en el tono de piel. Sumar los antebrazos da margen para las skins con
 * casco o máscara.
 */
public final class SkinToneSampler {

    /**
     * El beige de respaldo, en ABGR.
     *
     * NativeImage empaqueta los pixeles como 0xAABBGGRR y no ARGB: este valor
     * estuvo escrito como ARGB un tiempo y el respaldo salia azulado cuando
     * no se podia leer la skin.
     */
    private static final int FALLBACK_ABGR = 0xFF789AC4;

    /** Rectángulo de muestreo en la textura: [x0, y0, x1, y1] inclusive. */
    private record Region(int x0, int y0, int x1, int y1) {}

    // Cara frontal de la cabeza: capa base, x 8..15 / y 8..15.
    private static final Region FACE = new Region(8, 8, 15, 15);

    // Antebrazos: mitad BAJA de la cara frontal de cada brazo. La mitad alta
    // es el hombro, que suele ser manga. Los UV del brazo cambian entre skins
    // wide (brazo de 4px) y slim/Alex (3px), por eso hay dos juegos.
    private static final Region RIGHT_FOREARM_WIDE = new Region(44, 26, 47, 31);
    private static final Region LEFT_FOREARM_WIDE = new Region(36, 58, 39, 63);
    private static final Region RIGHT_FOREARM_SLIM = new Region(43, 26, 45, 31);
    private static final Region LEFT_FOREARM_SLIM = new Region(35, 58, 37, 63);

    private SkinToneSampler() {}

    /** Tono neutro para cuando no se puede leer la skin. */
    public static int fallbackTone() {
        return FALLBACK_ABGR;
    }

    public static int sampleSkinTone(NativeImage skinImage) {
        return sampleSkinTone(skinImage, SkinTextures.Model.WIDE);
    }

    public static int sampleSkinTone(NativeImage skinImage, SkinTextures.Model model) {
        if (skinImage == null) return FALLBACK_ABGR;
        boolean slim = model == SkinTextures.Model.SLIM;

        Map<Integer, Integer> counts = new HashMap<>();
        tally(counts, skinImage, FACE);
        tally(counts, skinImage, slim ? RIGHT_FOREARM_SLIM : RIGHT_FOREARM_WIDE);
        tally(counts, skinImage, slim ? LEFT_FOREARM_SLIM : LEFT_FOREARM_WIDE);

        int best = FALLBACK_ABGR;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return bestCount > 0 ? best : FALLBACK_ABGR;
    }

    private static void tally(Map<Integer, Integer> counts, NativeImage img, Region r) {
        for (int y = r.y0(); y <= r.y1(); y++) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) continue;
                int px;
                try {
                    px = img.getColor(x, y);
                } catch (Exception e) {
                    continue;
                }
                // Pixeles transparentes no son piel (skins con zonas vacías).
                if (((px >> 24) & 0xFF) < 250) continue;
                counts.merge(px, 1, Integer::sum);
            }
        }
    }
}
