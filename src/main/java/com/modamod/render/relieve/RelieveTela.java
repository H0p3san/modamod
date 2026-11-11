package com.modamod.render.relieve;

import com.modamod.Modamod;
import com.modamod.prenda.Parte;
import com.modamod.item.Calce;
import com.modamod.item.TexturaTela;
import com.modamod.render.ClothingTextureCache;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * El relieve de una tela (2026-10-01): la suma de
 * <ol>
 *   <li><b>lo de abajo, según el calce</b> — la tela envuelve al cuerpo (o a la
 *       prenda de abajo): Pegado lo copia tal cual; cuanto más holgado, más
 *       "puentea" los huecos (entre los pectorales, el escote) y solo marca lo
 *       que sobresale. Es un máximo con pendiente ({@link #envolver}): nunca
 *       queda por dentro de lo de abajo, así que las capas se siguen
 *       respetando;</li>
 *   <li><b>la textura del molde</b> ({@link TexturaTela}: fruncido, acolchado);</li>
 *   <li><b>la extrusión de la prenda</b> ("archivo por prenda"):
 *       {@code textures/models/relieve/<id de la prenda>.png}, gris en el
 *       layout de la skin (cualquier escala), 255 = {@link #EXTRUSION_MAXIMA}
 *       px hacia afuera. Ver {@code tools/generar_relieve_prendas.py}.</li>
 * </ol>
 */
public final class RelieveTela {

    private RelieveTela() {}

    public static final float EXTRUSION_MAXIMA = 0.6f;
    /** Cuánto baja la tela por cada px que se aleja de un bulto (px/px). */
    private static final float PENDIENTE = 0.55f;

    private static final Map<String, MapaRelieve> CACHE = new HashMap<>();

    public static MapaRelieve de(MapaRelieve abajo, @Nullable Calce calce, TexturaTela textura,
                                 @Nullable Identifier extrusion) {
        NativeImage img = extrusion == null ? null : ClothingTextureCache.imagenBase(extrusion);
        String clave = abajo.clave + ">" + (calce == null ? "?" : calce.name()) + "/" + textura.name()
                + "/" + (img == null ? "-" : extrusion.toString());
        MapaRelieve m = CACHE.get(clave);
        if (m != null) return m;
        if (CACHE.size() > 512) CACHE.clear();   // ídem MapaRelieve.SIN_FILAS
        float radio = radio(calce);
        m = MapaRelieve.generar(clave, abajo.slim, (parte, cara, fu, fv, ancho) -> {
            float h = envolver(abajo, parte, cara, fu, fv, ancho, radio);
            float extra = texturaEn(textura, fu, fv, ancho);
            if (img != null) extra += extrusionEn(img, parte, cara, fu, fv, abajo.slim);
            return h + extra * RelieveCuerpo.ventana(fu, 0.12f);
        });
        CACHE.put(clave, m);
        return m;
    }

    /** La extrusión de una prenda por su id, si el archivo existe. */
    public static Identifier extrusionDe(Identifier idPrenda) {
        return Identifier.of(Modamod.MOD_ID, "textures/models/relieve/" + idPrenda.getPath() + ".png");
    }

    /** Radio (px) en el que la tela puentea los huecos de lo de abajo. */
    static float radio(@Nullable Calce calce) {
        if (calce == null) return 1f;
        return switch (calce) {
            case PEGADO -> 0f;
            case AJUSTADO -> 0.6f;
            case NORMAL -> 1.3f;
            case SUELTO -> 2f;
            case OVERSIZE -> 3f;
        };
    }

    /**
     * Máximo de lo de abajo con pendiente: en cada punto, el bulto más alto
     * de alrededor menos lo que baja la tela por la distancia. Con radio 0
     * es lo de abajo tal cual.
     */
    static float envolver(MapaRelieve abajo, Parte parte, int cara, float fu, float fv, int ancho, float radio) {
        float punto = abajo.altura(parte, cara, fu, fv);
        if (radio <= 0f || abajo.plana(parte, cara)) return punto;
        float h = punto;
        for (int anillo = 1; anillo <= 2; anillo++) {
            float r = radio * anillo / 2f;
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                float dx = (float) Math.cos(a) * r, dy = (float) Math.sin(a) * r;
                float otro = abajo.altura(parte, cara, fu + dx / ancho, fv + dy / MapaRelieve.FILAS_PX);
                h = Math.max(h, otro - PENDIENTE * r);
            }
        }
        // Lo que el puente sube por encima de lo de abajo se apaga en el borde de la cara (2026-11-05, "media abierta en una
        // arista"): tomaba la altura de un bulto de adentro (el gemelo), la tela quedaba levantada en la arista y la cara
        // se despegaba de la vecina. Nada en el primer medio px, entero desde 1,5 px.
        float aLado = Math.min(fu, 1f - fu) * ancho, aPunta = Math.min(fv, 1f - fv) * MapaRelieve.FILAS_PX;
        return punto + (h - punto) * RelieveCuerpo.rampa(aLado - 0.5f, 1f) * RelieveCuerpo.rampa(aPunta - 0.5f, 1f);
    }

    /** La textura del molde, en px de la cara. */
    static float texturaEn(TexturaTela textura, float fu, float fv, int ancho) {
        float x = fu * ancho, y = fv * MapaRelieve.FILAS_PX;
        // El relieve se apaga hacia las aristas de la cara (2026-11-04, "la felpa separa las caras de la remera"): cada cara
        // se empuja por su normal, y con relieve en el borde las caras vecinas quedaban despegadas con una hendija.
        float aBorde = Math.min(Math.min(x, ancho - x), Math.min(y, MapaRelieve.FILAS_PX - y));
        float borde = Math.max(0f, Math.min(1f, aBorde / 1.2f));
        return borde * texturaBruta(textura, x, y);
    }

    private static float texturaBruta(TexturaTela textura, float x, float y) {
        return switch (textura) {
            case LISA -> 0f;
            case FRUNCIDO -> {
                // Un pliegue por px, con un poco de vaivén para que no sea un peine.
                float s = (float) Math.sin((x + 0.15f * Math.sin(y * 1.3f)) * Math.PI * 2);
                yield 0.22f * (0.5f + 0.5f * s);
            }
            case FELPA -> {
                // Mechones: ruido de celdas de 0,6 px, suavizado con una segunda capa más fina.
                float a = ruido(x / 0.6f, y / 0.6f), b = ruido(x / 0.3f + 7.3f, y / 0.3f + 2.1f);
                yield 0.3f * (0.65f * a + 0.35f * b);
            }
            case ACOLCHADO -> {
                // Rombos de 3 px: costura en las diagonales, almohadón en el medio.
                float celda = 3f;
                float a = frac((x + y) / celda), b = frac((x - y) / celda);
                float pa = 1f - (2f * a - 1f) * (2f * a - 1f), pb = 1f - (2f * b - 1f) * (2f * b - 1f);
                yield 0.35f * (float) Math.sqrt(Math.max(0f, pa * pb));
            }
        };
    }

    /** El gris del archivo de la prenda en la cara pedida (layout de skin clásico). */
    static float extrusionEn(NativeImage img, Parte parte, int cara, float fu, float fv, boolean slim) {
        // Siempre en el layout clásico (brazos de 4): el archivo se dibuja una vez.
        float xPx = MapaRelieve.inicioCara(parte, cara, false) + fu * MapaRelieve.anchoPx(parte, cara, false);
        float yPx = MapaRelieve.arribaCostados(parte) + fv * MapaRelieve.FILAS_PX;
        int x = Math.min(img.getWidth() - 1, Math.max(0, (int) (xPx * img.getWidth() / 64f)));
        int y = Math.min(img.getHeight() - 1, Math.max(0, (int) (yPx * img.getHeight() / 64f)));
        int px = img.getColor(x, y);
        if (((px >>> 24) & 0xFF) == 0) return 0f;
        return (px & 0xFF) / 255f * EXTRUSION_MAXIMA;
    }

    /** Valor pseudoaleatorio fijo de 0 a 1 por celda entera (el mismo mechón siempre en el mismo lugar). */
    private static float ruido(float x, float y) {
        int h = (int) Math.floor(x) * 374761393 + (int) Math.floor(y) * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }
}
