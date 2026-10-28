package com.modamod.render;

import com.modamod.item.PatronRed;
import net.minecraft.client.texture.NativeImage;

/**
 * Materiales de tela (2026-10-08, "trama de denim o jeans... un solo molde así como hay tramas que haya materiales,
 * cuero también con un mapa especular"): en vez de agujerear, la trama {@link PatronRed#DENIM} y {@link PatronRed#CUERO}
 * modulan la tela ya compuesta texel por texel (el color y los patrones quedan; se les suma la trama del material).
 *
 * <ul>
 *   <li><b>Denim:</b> sarga diagonal 2/1 (el hilo de trama asoma más claro cada tres) con variación por hilo y un
 *       desgaste de baja frecuencia que aclara el color a manchones.</li>
 *   <li><b>Cuero:</b> grano de poros con grietas finas y un "especular" pintado en la textura: una banda de brillo
 *       en el centro de cada cara y destellos en las crestas del grano. (Un mapa especular de verdad necesitaría un
 *       shader pack con PBR; esto se ve igual con y sin shaders.)</li>
 *   <li><b>Pana</b> (2026-10-22): canaletas verticales de medio pixel de skin, claras en la cresta y oscuras en el
 *       surco, con un poco de variación por canaleta y a lo largo del hilo.</li>
 * </ul>
 */
public final class MaterialTela {
    private MaterialTela() {}

    /** Aplica el material a un rectángulo (una cara de la caja de la prenda) de la imagen compuesta. */
    public static void aplicar(NativeImage img, PatronRed tipo, int x0, int y0, int x1, int y1) {
        int w = img.getWidth(), h = img.getHeight();
        x0 = Math.max(0, x0); y0 = Math.max(0, y0);
        x1 = Math.min(w, x1); y1 = Math.min(h, y1);
        int escala = Math.max(1, w / 64);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int c = img.getColor(x, y);
                int a = (c >>> 24) & 0xFF;
                if (a == 0) continue;
                float factor, blanco = 0f;
                if (tipo == PatronRed.DENIM) {
                    int hilo = Math.max(2, escala * 3 / 8);
                    int cx = x / hilo, cy = y / hilo;
                    boolean trama = Math.floorMod(cx - cy, 3) == 0;
                    factor = trama ? 1.14f : 0.94f;
                    factor += (azar(cx, cy) - 0.5f) * 0.07f;
                    float gasto = ruido(x / (escala * 2.5f), y / (escala * 2.5f));
                    blanco = Math.max(0f, gasto - 0.58f) * 0.45f;
                    factor += (gasto - 0.5f) * 0.10f;
                } else if (tipo == PatronRed.PANA) {
                    int canal = Math.max(3, escala / 2);
                    int cx = Math.floorMod(x - x0, canal * 1000) / canal;
                    float t = (Math.floorMod(x - x0, canal) + 0.5f) / canal;
                    factor = 0.80f + 0.30f * (float) Math.sin(Math.PI * t);
                    factor += (azar(cx, 7) - 0.5f) * 0.05f + (ruido(cx * 0.5f, y / (escala * 1.5f)) - 0.5f) * 0.06f;
                    if (t > 0.35f && t < 0.65f) blanco = 0.04f;               // brillo de la cresta
                } else {
                    float celda = Math.max(2f, escala * 0.3f);
                    float n = ruido(x / celda, y / celda);
                    factor = 0.86f + 0.24f * n;
                    if (Math.abs(n - 0.5f) < 0.035f) factor *= 0.78f;       // grietas entre poros
                    float t = (x - x0) / (float) Math.max(1, x1 - x0 - 1);
                    factor *= 1f + 0.17f * (float) Math.sin(Math.PI * t);     // banda de brillo
                    if (n > 0.88f) blanco = (n - 0.88f) * 3.5f;               // destellos en las crestas
                }
                int r = c & 0xFF, g = (c >> 8) & 0xFF, b = (c >> 16) & 0xFF;
                r = canal(r, factor, blanco); g = canal(g, factor, blanco); b = canal(b, factor, blanco);
                img.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
    }

    private static int canal(int v, float factor, float blanco) {
        float f = v * factor;
        f += (255f - f) * Math.min(1f, blanco);
        return Math.max(0, Math.min(255, Math.round(f)));
    }

    /** Valor al azar 0..1 estable de una celda entera. */
    private static float azar(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }

    /** Ruido de valor bilineal 0..1. */
    private static float ruido(float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = azar(xi, yi), b = azar(xi + 1, yi), c = azar(xi, yi + 1), d = azar(xi + 1, yi + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }
}
