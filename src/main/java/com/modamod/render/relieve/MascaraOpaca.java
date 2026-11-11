package com.modamod.render.relieve;

import com.modamod.prenda.Parte;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Qué px de skin tapa de verdad una tela (2026-11-11): un px cuenta como tapado solo si todos sus texels son opacos, así una
 * abertura, un escote, una trama de red o una tela translúcida dejan ver el relieve de lo de abajo
 * ({@link MapaRelieve#sinOpacos}). Se saca una vez por textura; los ids de las texturas dinámicas no se reusan.
 */
public final class MascaraOpaca {

    private MascaraOpaca() {}

    /** Por textura y slim: [parte][cara][fila * ancho + columna]. */
    private static final Map<String, boolean[][][]> HECHAS = new HashMap<>();

    /** La máscara de esa parte, o null si la textura no es una imagen en memoria (el llamador aplana por filas). */
    @Nullable
    public static boolean[][] de(Identifier textura, Parte parte, boolean slim) {
        int i = MapaRelieve.indice(parte);
        if (i < 0) return null;
        String clave = clave(textura, slim);
        boolean[][][] m = HECHAS.get(clave);
        if (m == null) {
            if (!(MinecraftClient.getInstance().getTextureManager().getOrDefault(textura, null)
                    instanceof NativeImageBackedTexture t) || t.getImage() == null) return null;
            if (HECHAS.size() > 512) HECHAS.clear();
            m = armar(t.getImage(), slim);
            HECHAS.put(clave, m);
        }
        return m[i];
    }

    public static String clave(Identifier textura, boolean slim) {
        return textura + (slim ? "/s" : "");
    }

    static boolean[][][] armar(NativeImage img, boolean slim) {
        int s = Math.max(1, img.getWidth() / 64);
        Parte[] partes = {Parte.TORSO, Parte.BRAZO_DER, Parte.BRAZO_IZQ, Parte.PIERNA_DER, Parte.PIERNA_IZQ};
        boolean[][][] m = new boolean[partes.length][4][];
        for (Parte parte : partes) {
            int y0 = MapaRelieve.arribaCostados(parte);
            for (int cara = 0; cara < 4; cara++) {
                int ancho = MapaRelieve.anchoPx(parte, cara, slim), x0 = MapaRelieve.inicioCara(parte, cara, slim);
                boolean[] o = new boolean[MapaRelieve.FILAS_PX * ancho];
                for (int py = 0; py < MapaRelieve.FILAS_PX; py++) {
                    for (int px = 0; px < ancho; px++) o[py * ancho + px] = opaco(img, (x0 + px) * s, (y0 + py) * s, s);
                }
                m[MapaRelieve.indice(parte)][cara] = o;
            }
        }
        return m;
    }

    private static boolean opaco(NativeImage img, int x0, int y0, int s) {
        if (x0 + s > img.getWidth() || y0 + s > img.getHeight()) return false;
        for (int y = y0; y < y0 + s; y++) {
            for (int x = x0; x < x0 + s; x++) if (((img.getColor(x, y) >>> 24) & 0xFF) < 250) return false;
        }
        return true;
    }
}
