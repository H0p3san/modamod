package com.modamod.render;

import com.modamod.Modamod;
import com.modamod.item.Calce;
import com.modamod.item.TopCorte;
import com.modamod.sublimadora.RemeraItem;
import com.modamod.sublimadora.Variante;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Geometría 3D que no es una caja de skin (2026-09-30):
 * <ul>
 *   <li>el <b>cuellito</b> de la polera ("agregame un cuellito para el
 *       molde polera"): un tubo corto acanalado alrededor de la base de la
 *       cabeza, atado al TORSO (no gira con la cabeza, como un cuello de
 *       verdad);</li>
 *   <li>la <b>capucha</b> del hoodie, caída en la espalda (atada al torso)
 *       o puesta (atada a la cabeza) según {@link TopCorte#capuchaArriba};</li>
 *   <li>los <b>cordones</b> con sus puntitas, colgando del escote.</li>
 * </ul>
 * Todo en píxeles de skin (escala 1, {@code Superficie.CUERPO}) con una
 * textura lisa del color de la prenda generada en runtime
 * ({@link #texturaLisa}): exterior en u 0..32, forro más oscuro en u 32..64.
 * Los patrones y las estampas de la prenda no llegan acá (por ahora).
 */
public final class CuelloYCapucha {

    private CuelloYCapucha() {}

    /**
     * Cómo se ata el cuellito de la polera (2026-10-04, "el cuello de polera se atraviesa mucho... lo fijemos a la
     * cabeza en lugar del torso" + "dame un comando pa alternar entre los dos cuellitos"): {@code TORSO} como era
     * (quieto, la cabeza lo atraviesa al girar), {@code CABEZA} (gira con ella; al mirar abajo se hunde un poco en el
     * pecho) y {@code PARTIDO} (un aro bajo en el torso y el resto en la cabeza). Se cambia con
     * {@code /modamoddebug cuello [torso|cabeza|partido]}; solo para quien lo usa.
     */
    public enum ModoCuello { TORSO, CABEZA, PARTIDO }

    public static ModoCuello modoCuello = ModoCuello.CABEZA;   // 2026-10-08: "la polera queda el modo fijado a la cabeza"

    private static final int TEX = 64;
    /** Por fuera de la segunda capa de la skin (sombrero), que infla 0.5. */
    private static final float SOBRE_SOMBRERO = 0.75F;
    private static final int CORDON = 0xF0EEE8;
    /** Sobre una cabeza de CARAS la capucha crece un 10 % desde el cuello: ya les queda holgada salvo al robot. */
    private static final float CAPUCHA_CARAS = 1.1F;

    /** Con casco, la capucha crece desde el cuello para taparlo (infla 1 px por lado sobre los 4 de media cabeza): un 25 %. */
    private static float capuchaConCasco() {
        return 1F + GarmentFeatureRenderer.armaduraCasco / 4F;
    }

    /** Dibuja cuello de polera, capucha y cordones de lo que haya en {@code prendas}. */
    public static void dibujar(List<ItemStack> prendas, BipedEntityModel<?> biped, MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers, int luz) {
        ItemStack polera = null, chaqueta = null;
        for (ItemStack s : prendas) {
            if (!(s.getItem() instanceof RemeraItem)) continue;
            if (RemeraItem.variante(s).cuello() == Variante.Cuello.POLERA) polera = s;
            if (TopCorte.tieneCapucha(s)) chaqueta = s; // sin capucha no hay capucha ni cordones (2026-10-07)
        }
        if (polera != null && biped.body.visible) {
            float d = Math.max(0F, Calce.dilatacionEfectiva(polera));
            Identifier tela = telaDePieza(polera, RemeraItem.color(polera), true, com.modamod.region.RegionPintura.CUELLO);
            switch (modoCuello) {
                case TORSO -> GarmentFeatureRenderer.dibujarModelPart(cuello(d), CuerpoGeometria.Superficie.CUERPO,
                        tela, biped.body, matrices, vertexConsumers, luz);
                case CABEZA -> {
                    if (biped.head.visible) GarmentFeatureRenderer.dibujarModelPart(cuelloCabeza(d), CuerpoGeometria.Superficie.CUERPO,
                            tela, biped.head, matrices, vertexConsumers, luz);
                }
                case PARTIDO -> {
                    GarmentFeatureRenderer.dibujarModelPart(cuelloAro(d), CuerpoGeometria.Superficie.CUERPO,
                            tela, biped.body, matrices, vertexConsumers, luz);
                    if (biped.head.visible) GarmentFeatureRenderer.dibujarModelPart(cuelloArriba(d), CuerpoGeometria.Superficie.CUERPO,
                            tela, biped.head, matrices, vertexConsumers, luz);
                }
            }
        }
        dibujarSolapasYCamisa(prendas, biped, matrices, vertexConsumers, luz);
        // La capucha de la capa, puesta con la misma tecla (2026-09-30, "lo mismo
        // con la capucha de la capa activada por tecla"); caída la dibuja CapaMalla.
        ItemStack capa = GarmentFeatureRenderer.capaDe(prendas);
        if (chaqueta == null && capa != null && com.modamod.item.CapaItem.capucha(capa)
                && TopCorte.capuchaArriba(capa) && CabezaCaras.visible(biped)) {
            int color = com.modamod.region.RegionResolver.colorBase(capa, com.modamod.region.Lado.IZQUIERDA);
            // Zona Detalles, como la capucha caída (2026-10-23, "la capucha no esta agarrando color").
            Identifier tela = telaDePieza(capa, color, false, com.modamod.region.RegionPintura.CAPA_DETALLES);
            matrices.push();
            CabezaCaras.agrandar(matrices, biped, CAPUCHA_CARAS, capuchaConCasco());
            conApliques(capa, matrices, vertexConsumers, luz, () -> CapuchaArchivo.puestaCapa(TopCorte.estiloCapucha(capa), tela,
                    0F, biped.head, matrices, vertexConsumers, luz));
            matrices.pop();
        }
        // Caída, la de Blockbench (2026-10-24, "pasá la capucha caída de la capa a la de Blockbench así lleva apliques"):
        // en el marco del torso, por fuera del paño (que arranca a 2 + separación y tiene ~1 px) y del embozo si lo hay.
        if (chaqueta == null && capa != null && com.modamod.item.CapaItem.capucha(capa) && !TopCorte.capuchaArriba(capa)
                && biped.body.visible) {
            int color = com.modamod.region.RegionResolver.colorBase(capa, com.modamod.region.Lado.IZQUIERDA);
            Identifier tela = telaDePieza(capa, color, false, com.modamod.region.RegionPintura.CAPA_DETALLES);
            float atras = Math.max(com.modamod.item.CapaItem.embozoLargo(capa) ? 0F : 1.2F + GarmentFeatureRenderer.separacionCapa(prendas, true),
                    com.modamod.item.CapaItem.embozo(capa) >= 0 ? 2.7F + GarmentFeatureRenderer.separacionCapa(prendas, false) : 0F);
            conApliques(capa, matrices, vertexConsumers, luz, () -> CapuchaArchivo.caida(TopCorte.estiloCapucha(capa), tela,
                    0F, atras, biped.body, matrices, vertexConsumers, luz));
        }
        if (chaqueta != null) {
            float d = Math.max(0F, Calce.dilatacionEfectiva(chaqueta));
            // También la zona Pecho (2026-10-23): pintado por zonas, la capucha quedaba con el color base.
            Identifier tela = telaDePieza(chaqueta, RemeraItem.color(chaqueta), false, com.modamod.region.RegionPintura.PECHO);
            if (TopCorte.capuchaArriba(chaqueta)) {
                if (CabezaCaras.visible(biped)) {
                    ItemStack top = chaqueta;
                    matrices.push();
                    CabezaCaras.agrandar(matrices, biped, CAPUCHA_CARAS, capuchaConCasco());
                    conApliques(top, matrices, vertexConsumers, luz,
                            () -> CapuchaArchivo.puesta(TopCorte.estiloCapucha(top), tela, d, biped.head, matrices, vertexConsumers, luz));
                    matrices.pop();
                }
            } else if (biped.body.visible) {
                // Con embozo en la capa, la bolsa va por fuera de él (2026-10-23): el embozo de atrás llega a z 4,4 + su separación.
                float atras = capa != null && com.modamod.item.CapaItem.embozo(capa) >= 0
                        ? 2.7F + GarmentFeatureRenderer.separacionCapa(prendas, false) : 0F;
                ItemStack top = chaqueta;
                conApliques(top, matrices, vertexConsumers, luz,
                        () -> CapuchaArchivo.caida(TopCorte.estiloCapucha(top), tela, d, atras, biped.body, matrices, vertexConsumers, luz));
            }
            // Sin cordones (2026-10-14, "nada de capucha con cordones"): la capucha es solo capucha.
        }
    }

    /**
     * Dibuja la capucha grabando su malla y le pone encima los apliques de la superficie Capucha de {@code prenda}
     * (2026-10-24, "y la capucha?"). La malla queda también para el click de la Estilista ("capucha").
     */
    private static void conApliques(ItemStack prenda, MatrixStack matrices, VertexConsumerProvider vcp, int luz, Runnable dibujo) {
        boolean hay = !ApliqueRenderer.apliquesEn(prenda, com.modamod.aplique.Aplique.Superficie.CAPUCHA).isEmpty();
        MallaCapturada malla = GarmentFeatureRenderer.grabar("capucha", hay, dibujo);
        if (malla != null) {
            ApliqueRenderer.dibujarEnMalla(prenda, com.modamod.aplique.Aplique.Superficie.CAPUCHA, malla, vcp, luz,
                    matrices.peek().getNormalMatrix());
        }
    }

    // ── solapas y cuello de camisa (2026-10-07, "traje separado... varios tipos de solapa") ────────────

    /** Una fila (1 px de alto) de una solapa: desde {@code xin} (borde de adentro, hacia el medio) {@code w} px hacia afuera. */
    private record Fila(float xin, float w) {}

    private static final Fila[] SOLAPA_PICO = {
            new Fila(3.0F, 1.0F), new Fila(2.7F, 1.1F), new Fila(2.4F, 1.2F), new Fila(2.0F, 2.2F),
            new Fila(1.5F, 1.4F), new Fila(1.0F, 1.0F), new Fila(0.5F, 0.9F)};
    private static final Fila[] SOLAPA_REDONDA = {
            new Fila(3.0F, 1.0F), new Fila(2.7F, 1.5F), new Fila(2.4F, 1.7F), new Fila(2.0F, 1.9F),
            new Fila(1.5F, 1.8F), new Fila(1.0F, 1.5F), new Fila(0.5F, 1.1F)};
    private static final Fila[] SOLAPA_CHAL = {
            new Fila(3.0F, 1.2F), new Fila(2.6F, 1.5F), new Fila(2.2F, 1.5F), new Fila(1.8F, 1.5F), new Fila(1.5F, 1.5F),
            new Fila(1.2F, 1.5F), new Fila(0.9F, 1.4F), new Fila(0.7F, 1.2F), new Fila(0.5F, 1.0F)};
    /** Las puntas del cuello de camisa: tres filas que apenas bajan del cuello. */
    private static final Fila[] CUELLO_CAMISA = {new Fila(1.4F, 1.9F), new Fila(1.1F, 1.9F), new Fila(0.8F, 1.4F)};

    private static final float LIMITE_X = 4.1F;
    private static final float GROSOR_SOLAPA = 0.7F;
    /** Cuánto sobresale la solapa de la tela (px). */
    private static final float SALE = 0.55F;

    /** Los anchos (px) por fila de 1 px de cada tipo de solapa: el perfil que comparten la malla y la zona de Tintes. */
    static float[] anchosDe(com.modamod.item.ChaquetaSolapa tipo) {
        Fila[] filas = filasDe(tipo);
        float[] a = new float[filas.length];
        for (int i = 0; i < a.length; i++) a[i] = filas[i].w;
        return a;
    }

    /** Los anchos (px) por fila de las puntas del cuello de camisa: los comparte la malla y la zona de Tintes. */
    static float[] anchosCamisa() {
        float[] a = new float[CUELLO_CAMISA.length];
        for (int i = 0; i < a.length; i++) a[i] = CUELLO_CAMISA[i].w;
        return a;
    }

    private static Fila[] filasDe(com.modamod.item.ChaquetaSolapa tipo) {
        return switch (tipo) {
            case PICO -> SOLAPA_PICO;
            case REDONDA -> SOLAPA_REDONDA;
            case CHAL -> SOLAPA_CHAL;
            case NINGUNA -> new Fila[0];
        };
    }

    /**
     * Dibuja las solapas de la chaqueta y el cuello de camisa de la remera (en el marco del torso). Cada fila es una
     * caja de 1 px de alto; con busto cada una sube hasta apoyarse sobre la cúpula ({@code BustoRender.sobreBusto}).
     * El cuello de camisa va un poco más afuera que cualquier otra tela, para que asome por encima de un saco.
     */
    private static void dibujarSolapasYCamisa(List<ItemStack> prendas, BipedEntityModel<?> biped, MatrixStack matrices,
                                              VertexConsumerProvider vertexConsumers, int luz) {
        if (!biped.body.visible) return;
        float dMax = 0F;
        for (ItemStack s : prendas) if (s.getItem() instanceof RemeraItem) dMax = Math.max(dMax, Calce.dilatacionEfectiva(s));
        for (ItemStack s : prendas) {
            if (!(s.getItem() instanceof RemeraItem)) continue;
            if (com.modamod.item.TopCorte.frente(s).abre()) {
                // Los cantos de la apertura (2026-10-08, "vamos por parte" → parte 3): una tira de tela fina a cada
                // lado, de arriba abajo, que le da grosor a la abertura y sigue hasta el ruedo; las solapas van encima.
                float[] bordeA = bordeDelEscote(s);
                Identifier telaA = telaDelTorso(s);
                if (bordeA != null && telaA != null) {
                    if (com.modamod.item.TopCorte.frente(s) == com.modamod.item.ChaquetaFrente.ABIERTA_PECHO) {
                        bordeA = java.util.Arrays.copyOf(bordeA, com.modamod.item.ChaquetaFrente.FILAS_PECHO * SUB);
                    }
                    // Las filas que cuelgan del ruedo en el calce suelto y el oversize (2026-10-15, "el corte en el medio de la
                    // remera a veces no llega hasta el borde inferior"): la tira sigue hasta donde termina la tela.
                    if (com.modamod.item.TopCorte.frente(s) != com.modamod.item.ChaquetaFrente.ABIERTA_PECHO && bordeA.length > 0) {
                        int largo = bordeA.length;
                        bordeA = java.util.Arrays.copyOf(bordeA, largo + Calce.leer(s).colgado * SUB);
                        java.util.Arrays.fill(bordeA, largo, bordeA.length, bordeA[largo - 1]);
                    }
                    float[] anchos = new float[Math.max(1, bordeA.length / SUB)];
                    java.util.Arrays.fill(anchos, 0.9F);
                    SolapaMalla.dibujar(telaA, bordeA, SUB, anchos, Math.max(0F, Calce.dilatacionEfectiva(s)),
                            com.modamod.render.relieve.BustoRender.carpaDe(Calce.leer(s)), 0.15F, 0.4F,
                            biped, matrices, vertexConsumers, luz);
                }
            }
            if (s.getItem() instanceof RemeraItem
                    && com.modamod.item.TopCorte.solapa(s) != com.modamod.item.ChaquetaSolapa.NINGUNA) {
                float d = Math.max(0F, Calce.dilatacionEfectiva(s));
                // Pegada al borde real del escote y de la apertura (2026-10-08, "las solapas no esten pegadas a la
                // abertura del cuello ni de la chaqueta"): el borde de adentro de cada fila sale de la tela.
                float[] borde = bordeDelEscote(s);
                Fila[] perfil = filasDe(com.modamod.item.TopCorte.solapa(s));
                Identifier tela = telaDelTorso(s);
                if (borde != null && tela != null) {
                    float[] anchos = new float[perfil.length];
                    for (int i = 0; i < perfil.length; i++) anchos[i] = perfil[i].w;
                    SolapaMalla.dibujar(tela, borde, SUB, anchos, d, com.modamod.render.relieve.BustoRender.carpaDe(Calce.leer(s)),
                            biped, matrices, vertexConsumers, luz);
                } else {
                    // Sin la tela compuesta todavía: las cajitas de siempre.
                    dibujarFilas("solapa" + com.modamod.item.TopCorte.solapa(s), perfil, d + 0.1F, s,
                            oscurecer(RemeraItem.color(s), 0.88F), biped, matrices, vertexConsumers, luz);
                }
            }
            if (RemeraItem.variante(s).cuello() == Variante.Cuello.CAMISA) {
                float d = Math.max(0F, Calce.dilatacionEfectiva(s));
                float dc = Math.max(d, dMax) + 0.3F;
                int color = RemeraItem.color(s);
                Identifier lisa = texturaLisa(color, false);
                float[] bordeC = bordeDelEscote(s);
                Identifier telaC = telaDelTorso(s);
                // Las puntas, de tela continua pegada al borde del escote: con la textura de la prenda si ya está
                // compuesta (lleva sus patrones y estampas) y, si no, de color liso.
                if (bordeC != null) {
                    SolapaMalla.dibujar(telaC != null ? telaC : lisa, bordeC, SUB, anchosCamisa(), dc,
                            com.modamod.render.relieve.BustoRender.carpaDe(Calce.leer(s)), 0.2F, 0.6F,
                            biped, matrices, vertexConsumers, luz);
                } else {
                    dibujarFilas("camisa", CUELLO_CAMISA, dc, s, color, biped, matrices, vertexConsumers, luz);
                }
            }
        }
    }

    /** Perfiles del borde del escote por textura de tela (px del centro del frente al borde de la tela, fila por fila). */
    private static final Map<Identifier, float[]> BORDES = new HashMap<>();
    /** Muestras por px de alto del perfil del borde: la textura es de 8x, así se aprecia la diagonal del escote. */
    private static final int SUB = 4;

    /**
     * Distancia (px) del centro del frente al borde de la tela en cada fila del torso, leída de la textura de la
     * prenda: el escote (V, redondo, cuadrado, corazón, camisa) y, si el frente está abierto, la apertura. 0 = la tela
     * llega al centro (frente cerrado). {@code null} si la tela todavía no se compuso.
     */
    /** La textura de la tela del torso de la prenda, o null. */
    static Identifier telaDelTorso(ItemStack ropa) {
        for (com.modamod.render.Pieza p : PiezasDePrenda.de(ropa, null)) {
            if (p.parte() == com.modamod.garment.Parte.TORSO) return p.textura();
        }
        return null;
    }

    private static float[] bordeDelEscote(ItemStack ropa) {
        Identifier tela = null;
        int filasTorso = 0;
        for (com.modamod.render.Pieza p : PiezasDePrenda.de(ropa, null)) {
            if (p.parte() == com.modamod.garment.Parte.TORSO) {
                tela = p.textura();
                filasTorso = p.filaHasta() - p.filaDesde();
                break;
            }
        }
        if (tela == null) return null;
        float[] cacheado = BORDES.get(tela);
        if (cacheado != null) return cacheado;
        NativeImage img = ClothingTextureCache.imagenBase(tela);
        if (img == null) return null;
        float k = img.getWidth() / 64F;
        float[] borde = new float[Math.max(1, Math.min(filasTorso, 12)) * SUB];
        for (int r = 0; r < borde.length; r++) {
            int y = Math.min(img.getHeight() - 1, (int) ((20 + (r + 0.5F) / SUB) * k));
            float e = 4F;   // sin tela en toda la mitad: el borde queda en el costado
            for (int x = Math.round(24 * k); x < Math.round(28 * k); x++) {
                if ((img.getColor(x, y) >>> 24) >= 128) { e = (x - 24 * k) / k; break; }
            }
            borde[r] = e;
        }
        BORDES.put(tela, borde);
        return borde;
    }

    /** Las filas de una solapa con el borde de adentro sobre el borde de la tela; sin tela leída quedan como están. */
    private static Fila[] pegarAlBorde(Fila[] base, float[] borde) {
        if (borde == null) return base;
        Fila[] out = new Fila[Math.min(base.length, borde.length / SUB)];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Fila(Math.max(0F, borde[Math.min(borde.length - 1, i * SUB + SUB / 2)]), base[i].w);
        }
        return out;
    }

    private static int oscurecer(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f), g = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static void dibujarFilas(String clave, Fila[] filas, float d, ItemStack ropa, int color,
                                     BipedEntityModel<?> biped, MatrixStack matrices,
                                     VertexConsumerProvider vertexConsumers, int luz) {
        if (filas.length == 0) return;
        com.modamod.render.relieve.BustoRender.Busto busto = com.modamod.render.relieve.BustoRender.actual;
        float carpa = com.modamod.render.relieve.BustoRender.carpaDe(Calce.leer(ropa));
        float[] zD = new float[filas.length], zI = new float[filas.length];
        boolean elevado = false;
        if (busto != null) {
            for (int i = 0; i < filas.length; i++) {
                float mid = filas[i].xin + filas[i].w / 2F;
                zD[i] = elevacion(busto, mid, i + 0.5F, d, carpa);
                zI[i] = elevacion(busto, -mid, i + 0.5F, d, carpa);
                elevado |= zD[i] != 0F || zI[i] != 0F;
            }
        }
        ModelPart parte;
        String key = clave + "|" + d + "|" + java.util.Arrays.hashCode(filas);
        if (!elevado && CACHE.containsKey(key)) parte = CACHE.get(key);
        else {
            List<ModelPart.Cuboid> cs = new ArrayList<>();
            for (int i = 0; i < filas.length; i++) {
                Fila f = filas[i];
                float w = Math.min(f.w, LIMITE_X - f.xin);
                if (w < 0.05F) continue;   // el borde del escote ya llegó al costado: nada que tapar en esta fila
                float z0 = -2 - d - SALE;
                cs.add(caja(0, 0, f.xin, i, z0 + zD[i], w, 1.02F, GROSOR_SOLAPA, 0, 0, 0, TODAS));
                cs.add(caja(0, 0, -f.xin - w, i, z0 + zI[i], w, 1.02F, GROSOR_SOLAPA, 0, 0, 0, TODAS));
            }
            if (elevado) parte = new ModelPart(cs, Map.of());
            else parte = parte(key, cs);
        }
        GarmentFeatureRenderer.dibujarModelPart(parte, CuerpoGeometria.Superficie.CUERPO, texturaLisa(color, false),
                biped.body, matrices, vertexConsumers, luz);
    }

    /** Cuánto más adelante (px, ≤ 0) está la cúpula del busto en (x, y) del frente que la tela plana. */
    static float elevacion(com.modamod.render.relieve.BustoRender.Busto busto, float x, float y, float d,
                                   float carpa) {
        com.modamod.render.relieve.BustoRender.Punto p =
                com.modamod.render.relieve.BustoRender.sobreBusto(busto, x, y, d, carpa);
        return p == null ? 0F : Math.min(0F, p.pos().z + 2 + d);
    }

    // ── geometría ─────────────────────────────────────────────────────────
    private static final Map<String, ModelPart> CACHE = new HashMap<>();

    private static final Set<Direction> TODAS = EnumSet.allOf(Direction.class);

    private static ModelPart.Cuboid caja(int u, int v, float x, float y, float z, float sx, float sy, float sz,
                                         float dx, float dy, float dz, Set<Direction> caras) {
        return new ModelPart.Cuboid(u, v, x, y, z, sx, sy, sz, dx, dy, dz, false, TEX, TEX, caras);
    }

    private static ModelPart parte(String key, List<ModelPart.Cuboid> cuboides) {
        ModelPart p = new ModelPart(cuboides, Map.of());
        CACHE.put(key, p);
        return p;
    }

    /**
     * Cuello alto: la huella de la cabeza (8x8) apenas inflada, de 1.6 px de
     * alto sobre el torso (+ la mitad de la holgura del calce, para que no
     * lo tape la tapa de arriba de un torso holgado). Tapa la barbilla, no
     * la boca.
     */
    /** Todo el cuello polera 1 px más abajo (2026-10-23, "para el cuello de la polera bajaria 1 px todo"); Y hacia abajo. */
    private static final float BAJADA_CUELLO = 1F;

    private static ModelPart cuello(float d) {
        String key = "cuello|" + d;
        ModelPart c = CACHE.get(key);
        if (c != null) return c;
        float alto = 1.6F + d * 0.5F, dil = SOBRE_SOMBRERO + d * 0.5F;
        return parte(key, List.of(caja(0, 0, -4, -alto + BAJADA_CUELLO, -4, 8, alto, 8, dil, 0, dil, TODAS)));
    }

    /**
     * El cuello fijado a la cabeza (2026-10-08, "extenderia un pixel mas abajo"): como {@link #cuello} pero con un
     * píxel más hacia el pecho, para que no se abra una rendija al inclinar la cabeza.
     */
    private static ModelPart cuelloCabeza(float d) {
        String key = "cuelloCabeza|" + d;
        ModelPart c = CACHE.get(key);
        if (c != null) return c;
        float alto = 1.6F + d * 0.5F, dil = SOBRE_SOMBRERO + d * 0.5F;
        return parte(key, List.of(caja(0, 0, -4, -alto + BAJADA_CUELLO, -4, 8, alto + 1F, 8, dil, 0, dil, TODAS)));
    }

    /** El aro bajo del cuello partido: 0,8 px sobre el torso, un poco más fino que la parte de la cabeza. */
    private static ModelPart cuelloAro(float d) {
        String key = "cuelloAro|" + d;
        ModelPart c = CACHE.get(key);
        if (c != null) return c;
        float dil = SOBRE_SOMBRERO - 0.2F + d * 0.5F;
        return parte(key, List.of(caja(0, 0, -4, -0.8F + BAJADA_CUELLO, -4, 8, 0.8F, 8, dil, 0, dil, TODAS)));
    }

    /** La parte del cuello partido que gira con la cabeza: de 0,8 px arriba del cuello hasta el alto del cuellito. */
    private static ModelPart cuelloArriba(float d) {
        String key = "cuelloArriba|" + d;
        ModelPart c = CACHE.get(key);
        if (c != null) return c;
        float alto = 1.6F + d * 0.5F, dil = SOBRE_SOMBRERO + d * 0.5F;
        return parte(key, List.of(caja(0, 0, -4, -alto + BAJADA_CUELLO, -4, 8, alto - 0.8F, 8, dil, 0, dil, TODAS)));
    }

    /**
     * Capucha puesta, en el marco de la cabeza (y -8..0): la caja de la
     * cabeza inflada, sin la cara de adelante (la cara del jugador), con un
     * bulto atrás (la punta) y un marco de forro alrededor de la cara.
     */
    private static ModelPart capuchaPuesta(float d) {
        String key = "puesta|" + d;
        ModelPart c = CACHE.get(key);
        if (c != null) return c;
        float dil = SOBRE_SOMBRERO + d * 0.25F;
        // Cerrada abajo (2026-10-02, "Hoodie capucha no cierra"): antes no tenía
        // tapa de abajo ni borde en la barbilla y se veía hueca desde abajo y de
        // costado. La tapa de abajo queda escondida debajo de la cabeza y solo
        // asoma el anillo alrededor del cuello, como una capucha cosida al hoodie.
        Set<Direction> sinCara = EnumSet.complementOf(EnumSet.of(Direction.NORTH));
        List<ModelPart.Cuboid> cs = new ArrayList<>();
        cs.add(caja(0, 0, -4, -8, -4, 8, 8, 8, dil, dil, dil, sinCara));
        // punta de atrás
        cs.add(caja(0, 0, -3, -6.5F, 4 + dil, 6, 5, 1.2F, 0, 0, 0, TODAS));
        // borde de forro alrededor de la cara (arriba y los dos costados)
        float e = 4 + dil, grosor = 1.1F;
        cs.add(caja(32, 0, -e, -8 - dil, -e, 2 * e, grosor, grosor, 0, 0, 0, TODAS));
        cs.add(caja(32, 0, -e, -8 - dil, -e, grosor, 8 + dil, grosor, 0, 0, 0, TODAS));
        cs.add(caja(32, 0, e - grosor, -8 - dil, -e, grosor, 8 + dil, grosor, 0, 0, 0, TODAS));
        // y el de abajo: cierra el marco de la cara por debajo de la barbilla.
        cs.add(caja(32, 0, -e, -0.6F, -e, 2 * e, 0.6F + dil, grosor, 0, 0, 0, TODAS));
        return parte(key, cs);
    }

    /** Capucha caída, en el marco del torso: una bolsa de tela apoyada en la nuca y la espalda, con la boca de forro arriba. */
    private static ModelPart capuchaCaida(float d) {
        String key = "caida|" + d;
        ModelPart c = CACHE.get(key);
        if (c != null) return c;
        float atras = 2 + d + 0.2F;
        List<ModelPart.Cuboid> cs = new ArrayList<>();
        cs.add(caja(0, 0, -4, -1.2F, atras, 8, 4.8F, 2.4F, 0, 0, 0, TODAS));
        cs.add(caja(0, 0, -3, 3.6F, atras + 0.2F, 6, 1.2F, 1.6F, 0, 0, 0, TODAS));
        // boca de la capucha: forro, apenas por encima de la tapa
        cs.add(caja(32, 0, -3.2F, -1.25F, atras + 0.4F, 6.4F, 0.05F, 1.6F, 0, 0, 0, TODAS));
        return parte(key, cs);
    }

    /** Dos cordones de 4 px colgando del escote, con una puntita un poco más gruesa. */
    private static ModelPart cordones(float d) {
        String key = "cordones|" + d;
        ModelPart c = CACHE.get(key);
        if (c != null) return c;
        float z = -2 - d - 0.45F;
        List<ModelPart.Cuboid> cs = new ArrayList<>();
        for (float x : new float[] { -1.8F, 1.4F }) {
            cs.add(caja(0, 0, x, 0.3F, z, 0.4F, 4, 0.4F, 0, 0, 0, TODAS));
            cs.add(caja(32, 0, x - 0.1F, 4.3F, z - 0.1F, 0.6F, 0.9F, 0.6F, 0, 0, 0, TODAS));
        }
        return parte(key, cs);
    }

    // ── textura ───────────────────────────────────────────────────────────
    private static final Map<Integer, Identifier> TEXTURAS = new HashMap<>();

    /**
     * Textura lisa de 64x64 del color {@code rgb}: exterior a la izquierda
     * (u 0..32), forro/puntita más oscuro a la derecha (u 32..64). Con
     * {@code rib}, canales verticales como el tejido de un cuello alto.
     */
    /**
     * La tela de la capucha o del cuello con los patrones de Tintes (2026-10-23, "vamos con tu recomendacion"): las capas de
     * toda la prenda y las de {@code region} (el cuello para la polera, el exterior para la capa); sin capas, la lisa de siempre.
     */
    static Identifier telaDePieza(ItemStack prenda, int rgb, boolean rib, @org.jetbrains.annotations.Nullable com.modamod.region.RegionPintura region) {
        java.util.List<com.modamod.region.RegionResolver.CapaPatron> capas = new java.util.ArrayList<>();
        for (var c : prenda.getOrDefault(com.modamod.item.ModamodComponents.CAPAS_TINTE, java.util.List.<com.modamod.region.RegionResolver.CapaPatron>of())) {
            if (c.region() == com.modamod.region.RegionPintura.TODO || c.region() == region) capas.add(c);
        }
        return capas.isEmpty() ? texturaLisa(rgb, rib) : ZonaTextura.plana(rgb, capas, rib);
    }

    static Identifier texturaLisa(int rgb, boolean rib) {
        int key = (rgb & 0xFFFFFF) | (rib ? 0x1000000 : 0);
        Identifier id = TEXTURAS.get(key);
        if (id != null) return id;
        NativeImage img = new NativeImage(TEX, TEX, true);
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                float f = x >= 32 ? 0.72F : rib && (x % 2 == 0) ? 0.82F : 1F;
                int r = (int) (((rgb >> 16) & 0xFF) * f), g = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
                img.setColor(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
        }
        id = Identifier.of(Modamod.MOD_ID, "dynamic/liso_" + Integer.toHexString(key));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
        TEXTURAS.put(key, id);
        return id;
    }
}
