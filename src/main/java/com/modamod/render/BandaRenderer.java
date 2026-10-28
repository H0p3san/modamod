package com.modamod.render;

import com.modamod.item.BandaAncho;
import com.modamod.item.BandaHerraje;
import com.modamod.item.BandaItem;
import com.modamod.item.BandaZona;
import com.modamod.item.SombreroPatron;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Banda (2026-10-05, "correas y cintos"): un aro de cajas en el marco de una parte del cuerpo — el torso para el
 * cinto, la cabeza para el choker — con 3 zonas: banda (cuatro losas), borde (dos tiras finas arriba y abajo, apenas
 * más afuera) y herraje (placa o aro al frente, -Z). Y positivo es hacia abajo. El cinto se infla según lo holgada
 * que es la ropa de abajo ({@code dil}); los colgantes son apliques de la Mesa de estilado, que dibuja
 * {@link ApliqueRenderer} en el mismo marco.
 */
public final class BandaRenderer {

    private BandaRenderer() {}

    /** Altura de la cintura en el torso (px desde arriba) y del choker bajo la cabeza. */
    private static final float Y_CINTURA = 7.6f, Y_COLLAR = -1.2f;
    private static final float GROSOR = 0.7f, BORDE_ALTO = 0.35f, BORDE_SALE = 0.12f;
    // Medidas del collar calcadas de la proporción del de Player Collars (2026-10-11, "el choker tiene que ser como el
    // collar de leashable collars... va como en el pecho"): anillo de ~8,5 × 6 px, de 0,85 px de grosor, que apenas
    // sobresale de los costados del torso y 1 px de frente y espalda (antes 4 + 0,7 por lado: pasaba por los hombros).
    private static final float COLLAR_HX = 3.4f, COLLAR_HZ = 1.44f, COLLAR_GROSOR = 0.85f;
    /** El collar más angosto y un píxel más arriba que el original (2026-10-11, "el choker esta muy ancho y no se si deberia elevarse un poquito mas"). */
    private static final float ANCHO_COLLAR = 10.6f;

    private static float hx(BandaZona z, float dil) { return (z == BandaZona.CINTURA ? 4f : COLLAR_HX) + dil; }
    private static float hz(BandaZona z, float dil) { return (z == BandaZona.CINTURA ? 2f : COLLAR_HZ) + dil; }
    private static float grosor(BandaZona z) { return z == BandaZona.CINTURA ? GROSOR : COLLAR_GROSOR; }

    public static void dibujar(ItemStack banda, BipedEntityModel<?> biped, MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers, int luz, float dil) {
        vertexConsumers = EfectoTrim.envolver(banda, vertexConsumers);

        BandaZona zona = BandaItem.zona(banda);
        // Collar y cinto van en el torso (2026-10-11, "che te dije que va anclado en el torso").
        ModelPart marco = biped.body;
        if (!marco.visible) return;
        List<Integer> colores = BandaItem.colores(banda);
        List<SombreroPatron> patrones = BandaItem.patrones(banda);
        List<SombreroRenderer.Caja> cajas = cajas(banda, dil);
        for (int z = 0; z < 3; z++) {
            ModelPart parte = parte(cajas, z, zona, banda, dil);
            if (parte == null) continue;
            GarmentFeatureRenderer.dibujarModelPart(parte, CuerpoGeometria.Superficie.CUERPO,
                    ZonaTextura.de(banda, z, false), marco, matrices, vertexConsumers, luz);
        }
        if (BandaItem.herraje(banda).esColgante()) {
            float y0 = zona == BandaZona.CINTURA ? Y_CINTURA : Y_COLLAR;
            float hz = hz(zona, dil);
            dibujarColgante(BandaItem.herraje(banda), y0 + BandaItem.ancho(banda).alto(zona), -(hz + grosor(zona) + BORDE_SALE),
                    ZonaTextura.de(banda, 2, false), marco, matrices, vertexConsumers, luz);
        }
        // Los colgantes: apliques apoyados en la banda (el punto es de una de sus cajas, en el marco de la parte).
        EfectoTrim.volcar();   // el brillo de lo último con acabado (2026-10-22)
        ApliqueRenderer.dibujar(banda, 0f, biped, matrices, vertexConsumers, luz);
    }

    /** Las cajas de cada zona con la holgura por defecto — para apuntar el mouse en la Mesa de estilado. */
    public static List<SombreroRenderer.Caja> cajas(ItemStack banda) {
        return cajas(banda, DIL_MESA);
    }

    /** Holgura del cinto sobre la piel sola (en la Mesa de estilado y el Maniquí). */
    public static final float DIL_MESA = 0.35f;

    private static final Map<String, ModelPart> CACHE = new HashMap<>();

    private static ModelPart parte(List<SombreroRenderer.Caja> cajas, int zona, BandaZona z, ItemStack banda, float dil) {
        String key = z + "|" + BandaItem.ancho(banda) + "|" + BandaItem.herraje(banda) + "|" + Math.round(dil * 20) + "|" + zona;
        ModelPart c = CACHE.get(key);
        if (c != null) return c;
        List<ModelPart.Cuboid> cubos = new ArrayList<>();
        for (SombreroRenderer.Caja caja : cajas) {
            if (caja.zona() != zona) continue;
            float[] a = caja.min(), b = caja.max();
            cubos.add(SombreroRenderer.caja(a[0], a[1], a[2], b[0] - a[0], b[1] - a[1], b[2] - a[2]));
        }
        if (cubos.isEmpty()) return null;
        c = new ModelPart(cubos, Map.of());
        CACHE.put(key, c);
        return c;
    }

    private static List<SombreroRenderer.Caja> cajas(ItemStack banda, float dil) {
        BandaZona zona = BandaItem.zona(banda);
        BandaAncho ancho = BandaItem.ancho(banda);
        BandaHerraje herraje = BandaItem.herraje(banda);
        boolean cintura = zona == BandaZona.CINTURA;
        float y0 = cintura ? Y_CINTURA : Y_COLLAR;
        float h = ancho.alto(zona);
        float hx = hx(zona, dil), hz = hz(zona, dil), t = grosor(zona);
        List<SombreroRenderer.Caja> out = new ArrayList<>();
        if (!cintura) {
            collarOriginal(out, h / 1.6f, dil);
        } else {
        // Banda: frente, espalda y los dos costados.
        losas(out, 0, hx, hz, t, y0, y0 + h, 0f);
        // Borde: tiras de arriba y abajo, un poquito más afuera y más altas que la banda para no pelear el plano.
        losas(out, 1, hx, hz, t, y0 - 0.1f, y0 + BORDE_ALTO, BORDE_SALE);
        losas(out, 1, hx, hz, t, y0 + h - BORDE_ALTO, y0 + h + 0.1f, BORDE_SALE);
        }
        // Herraje al frente, apoyado en la cara de afuera.
        float escala = cintura ? 1f : 0.6f;
        float zf0 = -hz - t - BORDE_SALE - 0.5f * escala, zf1 = -hz - t - BORDE_SALE;
        float ya = y0 - 0.25f, yb = y0 + h + 0.25f;
        float mx = 1.9f * escala;
        switch (herraje) {
            case PLACA -> out.add(caja(2, -mx, ya, zf0, mx, yb, zf1));
            case ARO -> {
                float ancha = 2.3f * escala, barra = 0.6f * escala;
                out.add(caja(2, -ancha, ya, zf0, ancha, ya + barra, zf1));
                out.add(caja(2, -ancha, yb - barra, zf0, ancha, yb, zf1));
                out.add(caja(2, -ancha, ya, zf0, -ancha + barra, yb, zf1));
                out.add(caja(2, ancha - barra, ya, zf0, ancha, yb, zf1));
            }
            default -> { }
        }
        return out;
    }

    // ── colgantes del collar (2026-10-08) ──

    private static final float GANANCIA_COLGANTE = 0.5f;
    private static final Map<BandaHerraje, ModelPart> COLGANTES = new HashMap<>();

    /** Un cubito (x0, y0, z0, x1, y1, z1) relativo al punto del que cuelga; -Z es hacia afuera del pecho. */
    private static ModelPart.Cuboid cubito(float x0, float y0, float z0, float x1, float y1, float z1) {
        return SombreroRenderer.caja(x0, y0, z0, x1 - x0, y1 - y0, z1 - z0);
    }

    private static ModelPart colgante(BandaHerraje h) {
        ModelPart c = COLGANTES.get(h);
        if (c != null) return c;
        List<ModelPart.Cuboid> k = new ArrayList<>();
        k.add(cubito(-0.4f, -0.3f, -0.5f, 0.4f, 0.4f, -0.1f));                          // la argolla de la que cuelga
        switch (h) {
            case CAMPANA -> {
                k.add(cubito(-0.8f, 0.4f, -1.1f, 0.8f, 1.2f, -0.1f));                    // cúpula
                k.add(cubito(-1.2f, 1.2f, -1.5f, 1.2f, 1.9f, 0.1f));                     // boca
                k.add(cubito(-0.35f, 1.9f, -0.9f, 0.35f, 2.4f, -0.4f));                  // badajo
            }
            case HUESO -> {
                k.add(cubito(-1.1f, 0.3f, -0.9f, 1.1f, 0.9f, -0.1f));                    // cabeza de arriba
                k.add(cubito(-0.4f, 0.9f, -0.8f, 0.4f, 2.1f, -0.2f));                    // caña
                k.add(cubito(-1.1f, 2.1f, -0.9f, 1.1f, 2.7f, -0.1f));                    // cabeza de abajo
            }
            case CORAZON -> {
                k.add(cubito(-1.2f, 0.4f, -0.9f, -0.1f, 1.0f, -0.1f));                   // lóbulo izquierdo
                k.add(cubito(0.1f, 0.4f, -0.9f, 1.2f, 1.0f, -0.1f));                     // lóbulo derecho
                k.add(cubito(-1.2f, 1.0f, -0.9f, 1.2f, 1.7f, -0.1f));
                k.add(cubito(-0.7f, 1.7f, -0.9f, 0.7f, 2.3f, -0.1f));
                k.add(cubito(-0.3f, 2.3f, -0.9f, 0.3f, 2.7f, -0.1f));                    // punta
            }
            default -> {                                                                // MEDALLA
                k.add(cubito(-0.7f, 0.5f, -0.7f, 0.7f, 0.8f, -0.1f));
                k.add(cubito(-1.1f, 0.8f, -0.7f, 1.1f, 2.4f, -0.1f));
                k.add(cubito(-0.7f, 2.4f, -0.7f, 0.7f, 2.7f, -0.1f));
            }
        }
        c = new ModelPart(k, Map.of());
        COLGANTES.put(h, c);
        return c;
    }

    /**
     * El colgante del collar: cuelga del borde de abajo de la banda, al frente, y se balancea con la inercia
     * ({@link FisicaApliques#actual}, los signos como los de la punta del sombrero: falta probarlos en el juego).
     */
    private static void dibujarColgante(BandaHerraje h, float yAnclaje, float zAnclaje, net.minecraft.util.Identifier tela,
                                        ModelPart marco, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz) {
        ModelPart parte = colgante(h);
        parte.setPivot(0f, yAnclaje, zAnclaje);
        float pitch = 0f, roll = 0f;
        FisicaApliques.Desplazamiento d = FisicaApliques.actual;
        if (d != null) {
            roll = Math.max(-0.9f, Math.min(0.9f, d.blando().x * GANANCIA_COLGANTE));
            pitch = Math.max(-0.9f, Math.min(0.9f, -d.blando().z * GANANCIA_COLGANTE));
        }
        parte.pitch = pitch;
        parte.roll = roll;
        GarmentFeatureRenderer.dibujarModelPart(parte, CuerpoGeometria.Superficie.CUERPO, tela, marco, matrices, vertexConsumers, luz);
    }

    /**
     * El anillo del collar de Player Collars (jlortiz, licencia MIT; 2026-10-11, "copia el modelo de leashable collars y el
     * anclaje"): los 6 cubos de su {@code item/collar.json} llevados al marco de la cabeza con la cadena de su
     * {@code CollarRenderer} — display "head" [90,0,180] / [0,-12,25,-10,75], roll + π, escala 0,85 y desplazamiento
     * (0; 0,4125; −0,005) bloques —; como las rotaciones son de 90°, cada cubo sigue siendo una caja. {@code f} escala el
     * alto del anillo (1 = el original) y {@code dil} lo infla.
     */
    private static void collarOriginal(List<SombreroRenderer.Caja> out, float f, float dil) {
        float[][] el = {
            {3, -6, 2, 4, 0, 3}, {12, -6, 2, 13, 0, 3}, {4, -6, 2, 12, -5, 3},   // costados y barra de atrás
            {4, -1, 2, 5, 0, 4}, {11, -1, 2, 12, 0, 4}, {5, -1, 3, 11, 0, 4},    // esquinas y barra del frente
        };
        for (float[] e : el) {
            float xa = -ANCHO_COLLAR * (0.5f - e[0] / 16f), xb = -ANCHO_COLLAR * (0.5f - e[3] / 16f);
            float ya = 13.6f * (e[2] / 16f - 0.1469f), yb = 13.6f * (e[5] / 16f - 0.1469f);
            float za = 13.6f * (-e[1] / 16f - 0.1769f), zb = 13.6f * (-e[4] / 16f - 0.1769f);
            out.add(caja(0, Math.min(xa, xb) - dil, Y_COLLAR + (Math.min(ya, yb) + 0.3f) * f, Math.min(za, zb) - dil,
                    Math.max(xa, xb) + dil, Y_COLLAR + (Math.max(ya, yb) + 0.3f) * f, Math.max(za, zb) + dil));
        }
    }

    private static SombreroRenderer.Caja caja(int zona, float x0, float y0, float z0, float x1, float y1, float z1) {
        return new SombreroRenderer.Caja(zona, new float[] {x0, y0, z0}, new float[] {x1, y1, z1});
    }

    /** Las cuatro losas del aro, {@code sale} px más afuera. */
    private static void losas(List<SombreroRenderer.Caja> out, int zona, float hx, float hz, float t,
                              float ya, float yb, float sale) {
        float ox = hx + t + sale, oz = hz + t + sale;
        out.add(caja(zona, -ox, ya, -oz, ox, yb, -hz));                    // frente
        out.add(caja(zona, -ox, ya, hz, ox, yb, oz));                      // espalda
        out.add(caja(zona, -ox, ya, -hz, -hx, yb, hz));                    // costado izquierdo
        out.add(caja(zona, hx, ya, -hz, ox, yb, hz));                      // costado derecho
    }
}
