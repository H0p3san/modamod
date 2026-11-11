package com.modamod.render.relieve;

import com.modamod.body.PerfilCuerpo;
import com.modamod.prenda.Parte;
import com.modamod.render.ClothingTextureCache;
import com.modamod.render.GarmentFeatureRenderer;
import com.modamod.render.Pieza;
import com.modamod.render.PiezasDePrenda;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chequeo de capas (2026-11-11, "la geometria es el punto mas complicado"): dibuja la tela de un conjunto con el código de
 * siempre ({@link GarmentFeatureRenderer#dibujarTela}) pero en un grabador, arma la superficie de cada tela en cada cara de
 * cada parte y avisa dónde una tela de más adentro queda por fuera de una de más afuera que ahí es opaca. Es lo que se ve
 * como "asoma" o "atraviesa". Mide lo que de verdad se dibuja, sin pantalla; lo llama el guion de prueba ({@code capas}).
 *
 * <p>Límites: compara telas de piezas (las del layout de la skin), no el cuerpo ni las mallas sueltas (pollera, capa,
 * delantal); una cara inclinada o redondeada entra por su caja, con su profundidad media.
 */
public final class ChequeoCapas {

    private ChequeoCapas() {}

    /** Muestras por px de skin en la grilla de cada cara. */
    private static final int POR_PX = 8;
    /** Lo que una capa de adentro puede pasar a la de afuera sin contar (px). */
    private static final float TOLERANCIA = 0.02f;
    /** Margen de la grilla alrededor de la caja de la parte (px). */
    private static final float MARGEN = 3f;

    private static final Parte[] PARTES = {Parte.TORSO, Parte.BRAZO_DER, Parte.BRAZO_IZQ, Parte.PIERNA_DER, Parte.PIERNA_IZQ};
    private static final String[] LADO = {"frente", "espalda", "costado -x", "costado +x"};

    /** Graba los vértices que le llegan: 8 floats cada uno (x, y, z en px; u, v en px de skin; normal). */
    private static final class Grabador implements VertexConsumer {
        float[] v = new float[4096];
        int n;

        @Override public VertexConsumer vertex(float x, float y, float z) {
            if (n + 8 > v.length) v = Arrays.copyOf(v, v.length * 2);
            v[n] = x * 16f; v[n + 1] = y * 16f; v[n + 2] = z * 16f;
            n += 8;
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer texture(float u, float w) { v[n - 5] = u * 64f; v[n - 4] = w * 64f; return this; }
        @Override public VertexConsumer overlay(int u, int w) { return this; }
        @Override public VertexConsumer light(int u, int w) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { v[n - 3] = x; v[n - 2] = y; v[n - 1] = z; return this; }
    }

    /** La caja de la parte en el modelo sin pose (px): {x0, x1, y0, z0, z1}. */
    private static float[] caja(Parte parte, boolean slim) {
        return switch (parte) {
            case TORSO -> new float[]{-4f, 4f, 0f, -2f, 2f};
            case BRAZO_DER -> new float[]{slim ? -7f : -8f, -4f, 0f, -2f, 2f};
            case BRAZO_IZQ -> new float[]{4f, slim ? 7f : 8f, 0f, -2f, 2f};
            case PIERNA_DER -> new float[]{-3.9f, 0.1f, 12f, -2f, 2f};
            default -> new float[]{-0.1f, 3.9f, 12f, -2f, 2f};
        };
    }

    private static Parte parteDe(float u, float w, boolean slim) {
        for (Parte p : PARTES) {
            int arriba = MapaRelieve.arribaCostados(p);
            if (w >= arriba && w <= arriba + MapaRelieve.FILAS_PX && MapaRelieve.caraEn(p, u, slim) >= 0) return p;
        }
        return null;
    }

    /** La superficie de una tela en una cara: lo más afuera en cada muestra (−∞ sin tela) y si ahí es opaca. */
    private static final class Cara {
        final float[] fuera;
        final boolean[] opaca;
        Cara(int n) { fuera = new float[n]; opaca = new boolean[n]; Arrays.fill(fuera, Float.NEGATIVE_INFINITY); }
    }

    /**
     * @return una línea por cada par de telas que se atraviesan en alguna cara (vacío = todo en orden)
     */
    public static List<String> revisar(BipedEntityModel<?> modelo, boolean slim, List<ItemStack> prendas, PerfilCuerpo perfil) {
        Map<Identifier, Grabador> grabado = new LinkedHashMap<>();
        VertexConsumerProvider vcp = capa -> grabado.computeIfAbsent(ClothingTextureCache.ultimaTextura, t -> new Grabador());
        RelieveRender.fijarVolumen(perfil);
        GarmentFeatureRenderer.mapaFigura = RelieveCuerpo.de(perfil, slim);
        try {
            GarmentFeatureRenderer.dibujarTela(modelo, slim, prendas, new MatrixStack(), vcp, 0xF000F0, null,
                    GarmentFeatureRenderer.ArmaduraVisible.NINGUNA);
        } finally {
            GarmentFeatureRenderer.mapaFigura = null;
        }

        // El orden de las telas de cada parte, de adentro hacia afuera (como GarmentFeatureRenderer#agrupar).
        Map<Parte, List<Identifier>> orden = new EnumMap<>(Parte.class);
        for (ItemStack stack : prendas) {
            List<Pieza> propias = new ArrayList<>(PiezasDePrenda.de(stack, null));
            propias.sort(Comparator.comparingInt(Pieza::capa));
            for (Pieza p : propias) orden.computeIfAbsent(p.parte(), x -> new ArrayList<>()).add(p.textura());
        }

        List<String> avisos = new ArrayList<>();
        for (Parte parte : PARTES) {
            List<Identifier> telas = orden.get(parte);
            if (telas == null || telas.size() < 2) continue;
            float[] c = caja(parte, slim);
            for (int lado = 0; lado < 4; lado++) {
                boolean porZ = lado < 2;
                float l0 = (porZ ? c[0] : c[3]) - MARGEN, l1 = (porZ ? c[1] : c[4]) + MARGEN;
                int ancho = Math.round((l1 - l0) * POR_PX), alto = Math.round((MapaRelieve.FILAS_PX + 2 * MARGEN) * POR_PX);
                List<Cara> caras = new ArrayList<>();
                for (Identifier tela : telas) {
                    Cara cara = new Cara(ancho * alto);
                    Grabador g = grabado.get(tela);
                    if (g != null) pintar(cara, g, parte, slim, lado, c, l0, ancho, alto, MascaraOpaca.de(tela, parte, slim));
                    caras.add(cara);
                }
                for (int a = 0; a < caras.size(); a++) {
                    for (int b = a + 1; b < caras.size(); b++) {
                        int malas = 0;
                        float peor = 0f;
                        Cara ad = caras.get(a), af = caras.get(b);
                        for (int i = 0; i < ad.fuera.length; i++) {
                            if (!ad.opaca[i] || !af.opaca[i]) continue;
                            float pasa = ad.fuera[i] - af.fuera[i];
                            if (pasa > TOLERANCIA) { malas++; peor = Math.max(peor, pasa); }
                        }
                        if (malas > 0) {
                            avisos.add(String.format("%s %s: la tela %d asoma sobre la %d en %.1f px² (hasta %.2f px)", parte, LADO[lado],
                                    a, b, malas / (float) (POR_PX * POR_PX), peor));
                        }
                    }
                }
            }
        }
        return avisos;
    }

    /** Vuelca en {@code cara} los cuadros de {@code g} que son de {@code parte} y miran hacia {@code lado}. */
    private static void pintar(Cara cara, Grabador g, Parte parte, boolean slim, int lado, float[] c, float l0, int ancho, int alto,
                               boolean[][] opaco) {
        for (int q = 0; q + 32 <= g.n; q += 32) {
            float[] v = g.v;
            float nx = 0, nz = 0, u = 0, w = 0;
            for (int k = 0; k < 4; k++) { nx += v[q + k * 8 + 5]; nz += v[q + k * 8 + 7]; u += v[q + k * 8 + 3]; w += v[q + k * 8 + 4]; }
            nx /= 4f; nz /= 4f; u /= 4f; w /= 4f;
            float haciaAfuera = switch (lado) { case 0 -> -nz; case 1 -> nz; case 2 -> -nx; default -> nx; };
            if (haciaAfuera < 0.7f || parteDe(u, w, slim) != parte) continue;
            float la = Float.MAX_VALUE, lb = -Float.MAX_VALUE, ya = Float.MAX_VALUE, yb = -Float.MAX_VALUE, fuera = 0f;
            float uEnLa = u, uEnLb = u, wEnYa = w, wEnYb = w;
            for (int k = 0; k < 4; k++) {
                int o = q + k * 8;
                float lat = lado < 2 ? v[o] : v[o + 2], y = v[o + 1] - c[2];
                if (lat < la) { la = lat; uEnLa = v[o + 3]; }
                if (lat > lb) { lb = lat; uEnLb = v[o + 3]; }
                if (y < ya) { ya = y; wEnYa = v[o + 4]; }
                if (y > yb) { yb = y; wEnYb = v[o + 4]; }
                fuera += switch (lado) { case 0 -> c[3] - v[o + 2]; case 1 -> v[o + 2] - c[4]; case 2 -> c[0] - v[o]; default -> v[o] - c[1]; };
            }
            fuera /= 4f;
            int cIdx = MapaRelieve.caraEn(parte, u, slim), anchoCara = MapaRelieve.anchoPx(parte, cIdx, slim);
            int u0 = MapaRelieve.inicioCara(parte, cIdx, slim), w0 = MapaRelieve.arribaCostados(parte);
            int xa = Math.max(0, (int) Math.ceil((la - l0) * POR_PX - 0.5f)), xb = Math.min(ancho - 1, (int) Math.floor((lb - l0) * POR_PX - 0.5f));
            int za = Math.max(0, (int) Math.ceil((ya + MARGEN) * POR_PX - 0.5f)), zb = Math.min(alto - 1, (int) Math.floor((yb + MARGEN) * POR_PX - 0.5f));
            for (int iy = za; iy <= zb; iy++) {
                float ty = yb - ya < 1e-4f ? 0.5f : ((iy + 0.5f) / POR_PX - MARGEN - ya) / (yb - ya);
                int py = Math.max(0, Math.min(MapaRelieve.FILAS_PX - 1, (int) Math.floor(wEnYa + (wEnYb - wEnYa) * ty) - w0));
                for (int ix = xa; ix <= xb; ix++) {
                    float tx = lb - la < 1e-4f ? 0.5f : ((ix + 0.5f) / POR_PX + l0 - la) / (lb - la);
                    int px = Math.max(0, Math.min(anchoCara - 1, (int) Math.floor(uEnLa + (uEnLb - uEnLa) * tx) - u0));
                    if (opaco != null && opaco[cIdx] != null && !opaco[cIdx][py * anchoCara + px]) continue;
                    int i = iy * ancho + ix;
                    cara.opaca[i] = true;
                    cara.fuera[i] = Math.max(cara.fuera[i], fuera);
                }
            }
        }
    }
}
