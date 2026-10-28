package com.modamod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * La pantallita de la tijera de la Modeladora (2026-10-05, "una pantallita donde va a aparecer una tijera (cuando esté
 * colocada) que va a mostrar en una barra verde su durabilidad"). Se pinta por código sobre la misma textura dinámica de
 * la máquina ({@link PantallaMaquina#con}), en un rectángulo libre del atlas que el hueso `tijera` lee; solo se repinta
 * cuando cambia lo que muestra.
 */
public final class PantallaTijera {

    /** Rectángulo del atlas (u, v, ancho, alto): el mismo que lee el hueso `tijera` (ver tools/ajustar_panel_modeladora.py). */
    public static final PantallaMaquina.Rect RECT = new PantallaMaquina.Rect(90, 50, 38, 30);

    private record Estado(Identifier textura, boolean hay, int nivel) {}

    private static final Map<Long, Estado> ULTIMO = new HashMap<>();

    // ABGR (NativeImage): fondo, hoja y mango de la tijera, marco y barra.
    private static final int FONDO = 0xFF140F0D, HOJA = 0xFFDAD4D0, MANGO = 0xFF3A44C8, HOJA_APAGADA = 0xFF3A3532, MANGO_APAGADO = 0xFF2A2640;
    private static final int MARCO = 0xFF8A8480, PISTA = 0xFF1C1A18, VERDE = 0xFF3CD23C;

    private PantallaTijera() {}

    /** Repinta la pantallita en {@code textura} si cambió algo: {@code hay} = hay tijera, {@code fraccion} = lo que le queda (0..1). */
    public static void pintar(Identifier textura, BlockPos pos, boolean hay, float fraccion) {
        int nivel = hay ? Math.round(Math.max(0f, Math.min(1f, fraccion)) * 30f) : 0;
        Estado nuevo = new Estado(textura, hay, nivel);
        if (nuevo.equals(ULTIMO.get(pos.asLong()))) return;
        AbstractTexture t = MinecraftClient.getInstance().getTextureManager().getTexture(textura);
        if (!(t instanceof NativeImageBackedTexture tex) || tex.getImage() == null) return;
        NativeImage img = tex.getImage();
        int u0 = RECT.u(), v0 = RECT.v(), w = RECT.ancho(), h = RECT.alto();
        if (u0 + w > img.getWidth() || v0 + h > img.getHeight()) return;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setColor(u0 + x, v0 + y, FONDO);
        dibujarTijera(img, u0 + (w - 15) / 2, v0 + 2, hay);
        // Barra de durabilidad: marco de 1 px, pista oscura y relleno verde que se vacía con el uso.
        int bx = u0 + 4, by = v0 + 20, bw = w - 8, bh = 6;
        for (int y = 0; y < bh; y++) for (int x = 0; x < bw; x++) {
            boolean borde = x == 0 || y == 0 || x == bw - 1 || y == bh - 1;
            img.setColor(bx + x, by + y, borde ? MARCO : PISTA);
        }
        int lleno = Math.round((bw - 2) * nivel / 30f);
        for (int y = 1; y < bh - 1; y++) for (int x = 0; x < lleno; x++) img.setColor(bx + 1 + x, by + y, VERDE);
        tex.upload();
        ULTIMO.put(pos.asLong(), nuevo);
    }

    /** Una tijera de 15 x 13 px: dos hojas que se cruzan y dos anillos de mango. */
    private static void dibujarTijera(NativeImage img, int ox, int oy, boolean viva) {
        int hoja = viva ? HOJA : HOJA_APAGADA, mango = viva ? MANGO : MANGO_APAGADO;
        linea(img, ox + 2, oy, ox + 9, oy + 7, hoja);
        linea(img, ox + 12, oy, ox + 5, oy + 7, hoja);
        linea(img, ox + 9, oy + 7, ox + 11, oy + 10, hoja);
        linea(img, ox + 5, oy + 7, ox + 3, oy + 10, hoja);
        anillo(img, ox + 10, oy + 9, mango);
        anillo(img, ox + 1, oy + 9, mango);
    }

    private static void anillo(NativeImage img, int x, int y, int color) {
        for (int i = 0; i < 4; i++) { img.setColor(x + i, y, color); img.setColor(x + i, y + 3, color); }
        for (int j = 1; j < 3; j++) { img.setColor(x, y + j, color); img.setColor(x + 3, y + j, color); }
    }

    private static void linea(NativeImage img, int x0, int y0, int x1, int y1, int color) {
        int pasos = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= pasos; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) Math.max(1, pasos));
            int y = y0 + Math.round((y1 - y0) * i / (float) Math.max(1, pasos));
            img.setColor(x, y, color);
        }
    }
}
