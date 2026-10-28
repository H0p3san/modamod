package com.modamod.sublimadora;

import com.modamod.Modamod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Banners y escudos con foto a 16x (2026-10-06, "solo tintes y sublimadoras a banners y 16x"): arma una textura de
 * 1024×1024 con el MISMO diseño que la de 64×64 de vanilla (cara de adelante y de atrás del paño o del escudo, 16
 * veces más grandes), así el mismo {@code ModelPart} la dibuja sin tocar su geometría. La foto sale del almacén de
 * Camerapture como en {@link EstampaTextures}; mientras no haya bajado se dibuja el banner de siempre.
 * <p>
 * Tintes (2026-10-24, "tintes banners"): el banner también lleva las capas de la Estación de Tintes. Se dibuja en orden:
 * el paño teñido ({@link #tinte}, a 4x), los diseños del telar de vanilla y la foto ({@link #foto}, transparente fuera
 * de la foto) arriba de todo — ver {@code BannerRendererMixin}.
 */
public final class BannerHD {

    private BannerHD() {}

    /** Factor de resolución sobre vanilla. */
    public static final int ESCALA = 16;
    private static final int LADO = 64 * ESCALA;

    /** Foto y capas de tinte del banner o escudo que se está dibujando. */
    public record Contexto(@Nullable Estampa foto, List<com.modamod.region.RegionResolver.CapaPatron> capas) {
        /** null si no lleva nada del mod (se dibuja el de vanilla). */
        @Nullable
        public static Contexto de(@Nullable Estampa foto, @Nullable List<com.modamod.region.RegionResolver.CapaPatron> capas) {
            if (capas == null) capas = List.of();
            return foto == null && capas.isEmpty() ? null : new Contexto(foto, capas);
        }
    }

    /** Lo que se está dibujando ahora (lo fijan los mixins del render; null = vanilla). */
    @Nullable public static Contexto contexto;

    public enum Forma {
        /** Paño del banner: caja de 20×40×1, cara de adelante en (1,1), de atrás en (22,1). */
        BANNER(20, 40, 1, 22),
        /** Placa del escudo: 12×22×1, adelante en (1,1), atrás en (14,1). */
        ESCUDO(12, 22, 1, 14),
        /** El panel del frente de máscaras y barbijo (2026-10-24): 30×30 en (1,1) de su textura; sin cara de atrás. */
        CARA(30, 30, 1, -1),
        /** El frente de la copa de las gorras (2026-10-25): 48×24 en (1,1), proyectado de frente; sin cara de atrás. */
        GORRA(48, 24, 1, -1);

        final int ancho, alto, x0, xAtras;
        Forma(int ancho, int alto, int x0, int xAtras) { this.ancho = ancho; this.alto = alto; this.x0 = x0; this.xAtras = xAtras; }
    }

    private record Clave(UUID foto, int escala, int x, int y, int angulo, boolean cubrir, Forma forma) {}

    private static final int MAXIMO_EN_CACHE = 12;
    private static final Map<Clave, Identifier> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<Clave, Long> REINTENTO = new LinkedHashMap<>();
    private static int numero = 0;

    /** El paño teñido (color del banner + capas de Tintes, a 4x), o null si no lleva capas. */
    @Nullable
    public static Identifier tinte(@Nullable DyeColor color) {
        Contexto c = contexto;
        if (c == null || c.capas().isEmpty()) return null;
        int rgb = color == null ? 0xFFFFFF : color.getEntityColor() & 0xFFFFFF;
        return com.modamod.render.ZonaTextura.banner(rgb, c.capas());
    }

    /** La foto a 16x (transparente fuera de la foto), o null si no hay o todavía no se puede armar. */
    @Nullable
    public static Identifier foto(Forma forma) {
        return foto(forma, contexto == null ? null : contexto.foto());
    }

    /** Lo mismo con la estampa {@code e} (la de una máscara o un barbijo, 2026-10-24). */
    @Nullable
    public static Identifier foto(Forma forma, @Nullable Estampa e) {
        if (e == null) return null;
        Clave k = new Clave(e.foto(), Math.round(e.escala() * 100), Math.round(e.x() * 100), Math.round(e.y() * 100),
                Math.round(e.angulo()), e.cubrir(), forma);
        Identifier ya = CACHE.get(k);
        if (ya != null) return ya;
        long ahora = System.currentTimeMillis();
        Long luego = REINTENTO.get(k);
        if (luego != null && ahora < luego) return null;   // la foto todavía no bajó: no se vuelve a intentar cada cuadro

        NativeImage img = componer(e, forma);
        if (img == null) {
            REINTENTO.put(k, ahora + 500);
            return null;
        }
        var tm = MinecraftClient.getInstance().getTextureManager();
        Identifier id = tm.registerDynamicTexture(Modamod.MOD_ID + "_banner_hd_" + (numero++), new NativeImageBackedTexture(img));
        CACHE.put(k, id);
        REINTENTO.remove(k);
        if (CACHE.size() > MAXIMO_EN_CACHE) {
            var it = CACHE.entrySet().iterator();
            var viejo = it.next();
            it.remove();
            tm.destroyTexture(viejo.getValue());
        }
        return id;
    }

    @Nullable
    private static NativeImage componer(Estampa e, Forma forma) {
        var info = EstampaTextures.fotoDe(e.foto());
        if (info == null) return null;
        NativeImage foto = EstampaTextures.leerDeLaGpu(info.textura(), info.ancho(), info.alto());
        if (foto == null) return null;
        try {
            // Transparente salvo la foto (2026-10-24): debajo va el paño (teñido o de vanilla) con los diseños del telar.
            NativeImage out = new NativeImage(LADO, LADO, true);
            int w = forma.ancho * ESCALA, h = forma.alto * ESCALA;
            int fx = forma.x0 * ESCALA, fy = ESCALA;
            pintarCara(out, foto, e, forma, fx, fy, w, h, false);
            if (forma.xAtras >= 0) pintarCara(out, foto, e, forma, forma.xAtras * ESCALA, fy, w, h, true);
            return out;
        } finally {
            foto.close();
        }
    }

    /** Pone la foto en el rectángulo ({@code fx, fy, w, h}); {@code espejo} para la cara de atrás, que se mira desde el otro lado. */
    private static void pintarCara(NativeImage out, NativeImage foto, Estampa e, Forma forma, int fx, int fy, int w, int h, boolean espejo) {
        float relCara = (float) forma.ancho / forma.alto;      // ancho/alto de la cara
        float relFoto = (float) foto.getWidth() / foto.getHeight();
        // En unidades de "alto de la cara = 1".
        float altoCubrir = Math.max(1f, relCara / relFoto);
        float alto;
        if (EstampaTextures.tieneTransparencia(foto)) {
            // Un diseño con fondo transparente se pone entero, sin recortar (igual que en las prendas).
            alto = Math.min(1f, relCara / relFoto) * 0.9f * Math.max(e.escala(), Estampa.ESCALA_MINIMA) / Estampa.ESCALA_CUBRIR;
        } else if (e.escala() <= Estampa.ESCALA_CUBRIR) {
            float t = (e.escala() - Estampa.ESCALA_MINIMA) / (Estampa.ESCALA_CUBRIR - Estampa.ESCALA_MINIMA);
            alto = 0.25f + (altoCubrir - 0.25f) * MathHelper.clamp(t, 0f, 1f);
        } else {
            alto = altoCubrir * e.escala();
        }
        if (e.cubrir()) alto = Math.max(alto, altoCubrir);
        float ancho = alto * relFoto;
        float cx = relCara / 2f + e.x() * relCara;
        float cy = 0.5f - e.y();
        double rad = Math.toRadians(-e.angulo());
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float px = (x + 0.5f) / w;
                if (espejo) px = 1f - px;
                float lx = px * relCara, ly = (y + 0.5f) / h;
                float dx = lx - cx, dy = ly - cy;
                float rx = dx * cos - dy * sin, ry = dx * sin + dy * cos;
                float u = (rx + ancho / 2f) / ancho, v = (ry + alto / 2f) / alto;
                if (u < 0f || u >= 1f || v < 0f || v >= 1f) continue;
                int pixel = muestrear(foto, u, v);
                if ((pixel >>> 24) == 0) continue;
                out.setColor(fx + x, fy + y, pixel);
            }
        }
    }

    /** Muestreo bilineal (ABGR). */
    private static int muestrear(NativeImage foto, float u, float v) {
        float sx = u * foto.getWidth() - 0.5f, sy = v * foto.getHeight() - 0.5f;
        int x0 = MathHelper.clamp((int) Math.floor(sx), 0, foto.getWidth() - 1);
        int y0 = MathHelper.clamp((int) Math.floor(sy), 0, foto.getHeight() - 1);
        int x1 = Math.min(x0 + 1, foto.getWidth() - 1), y1 = Math.min(y0 + 1, foto.getHeight() - 1);
        float fx = MathHelper.clamp(sx - x0, 0f, 1f), fy = MathHelper.clamp(sy - y0, 0f, 1f);
        int a = foto.getColor(x0, y0), b = foto.getColor(x1, y0), c = foto.getColor(x0, y1), d = foto.getColor(x1, y1);
        int salida = 0;
        for (int desp = 0; desp < 32; desp += 8) {
            float arriba = ((a >>> desp) & 0xFF) * (1 - fx) + ((b >>> desp) & 0xFF) * fx;
            float abajo = ((c >>> desp) & 0xFF) * (1 - fx) + ((d >>> desp) & 0xFF) * fx;
            salida |= (Math.round(arriba * (1 - fy) + abajo * fy) & 0xFF) << desp;
        }
        return salida;
    }
}
