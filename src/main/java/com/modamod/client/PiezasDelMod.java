package com.modamod.client;

import com.modamod.Modamod;
import com.modamod.garment.Capa;
import com.modamod.garment.Parte;
import com.modamod.item.Calce;
import com.modamod.item.CalientabrazosItem;
import com.modamod.item.ModamodItems;
import com.modamod.item.MediasLargo;
import com.modamod.item.PantalonItem;
import com.modamod.region.Lado;
import com.modamod.region.RegionResolver;
import com.modamod.render.CajaSkin;
import com.modamod.render.ClothingTextureCache;
import com.modamod.render.CuerpoGeometria;
import com.modamod.render.LayoutSkin;
import com.modamod.render.Pieza;
import com.modamod.render.PiezasDePrenda;
import com.modamod.sublimadora.Estampa;
import com.modamod.sublimadora.EstampaTextures;
import com.modamod.sublimadora.ModItems;
import com.modamod.sublimadora.RemeraItem;
import com.modamod.sublimadora.Variante;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Como se ve cada prenda puesta: la mitad CLIENTE de PrendasDelMod.
 *
 * Cada prenda devuelve la lista de piezas que dibuja. Lo que antes hacia un
 * TrinketRenderer por prenda —cada uno con su propio modelo, su propia
 * dilatacion y su propio orden— es ahora una lista de datos que el
 * GarmentFeatureRenderer dibuja en el orden que dice la capa.
 *
 * <h2>La regla de la textura</h2>
 * Layout de SKIN a 8x, y TRANSPARENTE donde la prenda no tiene tela. Nada de
 * rellenar con piel: de eso se encarga el cuerpo base, abajo de todo.
 */
public final class PiezasDelMod {

    private PiezasDelMod() {}

    public static void init() {
        PiezasDePrenda.registrar(ModamodItems.SOCKS_SOLID, PiezasDelMod::medias);
        PiezasDePrenda.registrar(ModamodItems.PANTALON, PiezasDelMod::pantalon);
        PiezasDePrenda.registrar(ModItems.REMERA, PiezasDelMod::remera);
        PiezasDePrenda.registrar(ModamodItems.CALIENTABRAZOS, PiezasDelMod::calientabrazos);
        PiezasDePrenda.registrar(ModamodItems.POLLERA, PiezasDelMod::pollera);
    }

    /**
     * Una pieza por pierna, cada una con su textura.
     *
     * Van separadas y no como una sola textura de las dos piernas porque el
     * color y el patron se resuelven POR LADO: un par disparejo son dos
     * composiciones distintas, y el cache las guarda por separado.
     *
     * El LARGO ({@link MediasLargo}) en cambio es ENTERO, no por lado —
     * un componente único, sin variante RIGHT_*: un par de medias de largo
     * disparejo (una hasta la rodilla, la otra zoquete) no es un caso real
     * que hiciera falta cubrir, a diferencia del color.
     */
    /**
     * Muslos/pantorrillas/pie con volumen en las medias — SUSPENDIDO hasta la v2
     * (2026-09-26): la versión por franjas era provisional; la definitiva es la
     * malla redondeada del prototipo (ver memoria muslos-malla-v2), como opción
     * "vanilla / redondeado". El código de {@code CuerpoGeometria} y el render
     * quedan armados: prender esto los reactiva.
     */
    // Reactivado el 2026-10-18 ("reactivá el volumen de muslo y pantorrilla en las medias para que el calce se note"): las medias
    // casi no cambiaban con el calce (Pegado a Normal son 0,25 px). Sigue siendo la versión por franjas, no la malla de la v2.
    private static final boolean VOLUMEN_MEDIAS = true;

    private static List<Pieza> medias(ItemStack stack, net.minecraft.entity.LivingEntity entidad) {
        // Siempre el archivo COMPLETO (CANCAN): cobertura de extremidad son
        // dos anclajes que se intersecan, recortados en runtime — ver
        // MediasLargo#filasVisibles.
        Identifier base = MediasLargo.TEXTURA_BASE;
        float dilatacion = Calce.dilatacionEfectiva(stack);
        int[] filasIzq = MediasLargo.filasVisibles(stack, Lado.IZQUIERDA);
        int[] filasDer = MediasLargo.filasVisibles(stack, Lado.DERECHA);
        return List.of(
                new Pieza(Parte.PIERNA_IZQ, Capa.MEDIA, texturaMedia(base, stack, Lado.IZQUIERDA), dilatacion, filasIzq[0], filasIzq[1], VOLUMEN_MEDIAS,
                        com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.INF_IZQ), com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.SUP_IZQ)),
                new Pieza(Parte.PIERNA_DER, Capa.MEDIA, texturaMedia(base, stack, Lado.DERECHA), dilatacion, filasDer[0], filasDer[1], VOLUMEN_MEDIAS,
                        com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.INF_DER), com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.SUP_DER)));
    }

    /**
     * Resuelve las hasta 3 capas de patrón puestas en {@code lado} a su
     * lista de {@link ClothingTextureCache.CapaMascara} (máscara + SU
     * PROPIO color, en orden de pintado) para {@code prenda} — vacía si
     * no hay ningún patrón, o si esa prenda no tiene caja mapeada
     * todavía (cae a lisa, no rompe nada).
     */
    private static List<ClothingTextureCache.CapaMascara> capasDePatron(
            String prenda, com.modamod.tinturas.TinturasBlockEntity.Categoria categoria, ItemStack stack, Lado lado) {
        List<RegionResolver.CapaPatron> capas = RegionResolver.capasTinte(stack, lado);
        if (capas.isEmpty()) return List.of();
        List<ClothingTextureCache.CapaMascara> resultado = new ArrayList<>(capas.size());
        for (RegionResolver.CapaPatron capa : capas) {
            // Rayas o motivo con tamaño/orientación/repetición PROPIOS de
            // esta capa; lisa = null (cubre toda su región) o el degradé.
            NativeImage mascara = com.modamod.render.PatronGenerador.mascaraDeCapa(prenda, capa);
            // Molde sin caja mapeada para esta prenda: el patrón no se ve
            // (no lo convierte en liso — null significa "liso").
            if (!capa.lisa() && mascara == null) continue;
            // Región (2026-09-27, "pintar por región"): null (TODO) no recorta nada.
            java.util.List<CajaSkin.Rect> region = capa.region() == com.modamod.region.RegionPintura.TODO
                    ? null : capa.region().rects(categoria, CuerpoGeometria.ESCALA_TELA);
            resultado.add(ClothingTextureCache.CapaMascara.de(capa, mascara, region));
        }
        return resultado;
    }

    private static Identifier texturaMedia(Identifier base, ItemStack stack, Lado lado) {
        int colorBase = RegionResolver.colorBase(stack, lado);
        List<ClothingTextureCache.CapaMascara> capas = capasDePatron("socks", com.modamod.tinturas.TinturasBlockEntity.Categoria.MEDIAS, stack, lado);
        int[] filas = MediasLargo.filasVisibles(stack, lado);
        Parte piernaParte = lado == Lado.DERECHA ? Parte.PIERNA_DER : Parte.PIERNA_IZQ;

        com.modamod.item.PatronRed red = com.modamod.item.PatronRed.leer(stack);
        boolean ribInf = com.modamod.item.Ruedos.get(stack, lado == Lado.DERECHA ? com.modamod.item.ZonaRuedo.INF_DER : com.modamod.item.ZonaRuedo.INF_IZQ) == com.modamod.item.Ruedo.AJUSTADO;
        boolean ribSup = com.modamod.item.Ruedos.get(stack, lado == Lado.DERECHA ? com.modamod.item.ZonaRuedo.SUP_DER : com.modamod.item.ZonaRuedo.SUP_IZQ) == com.modamod.item.Ruedo.AJUSTADO;

        // Recorte de cobertura SIEMPRE; la estampa (sublimado sobre la
        // media) es opcional, encadenada en el mismo Encima.
        ClothingTextureCache.Encima ajustes = new ClothingTextureCache.Encima() {
            @Override
            public String clave() {
                // piernaParte va SÍ o SÍ en la clave: sin esto, cuando las
                // dos piernas dan el mismo rango de filas (el caso más
                // común, "Ambas" simétrico) la clave quedaba idéntica y la
                // segunda pierna reutilizaba la textura ya recortada de la
                // primera SIN aplicarle su propio recorte — bug real
                // reportado jugando ("una queda hasta la rodilla, la otra
                // 3/4" con Ambas puesto).
                String base = "media" + piernaParte + filas[0] + "_" + filas[1] + "_" + red + (ribInf ? "i" : "") + (ribSup ? "s" : "");
                return EstampaTextures.tieneEstampa(stack) ? base + "_" + EstampaTextures.claveEstampas(stack) : base;
            }
            @Override
            public boolean aplicar(NativeImage destino) {
                if (EstampaTextures.tieneEstampa(stack)) EstampaTextures.estampar(destino, stack);
                recortarFilas(destino, piernaParte, filas[0], filas[1]);
                if (red != null) ClothingTextureCache.perforarRed(destino, red, piernaParte, filas[0], filas[1]);
                com.modamod.render.DetallesHoodie.ribEnBorde(destino, piernaParte, filas[0], filas[1], ribInf, ribSup);
                return true;
            }
        };

        return ClothingTextureCache.composeGarmentCapas(base, colorBase, capas,
                ClothingTextureCache.Shading.LEGS, ajustes);
    }

    /**
     * Torso, mas un brazo por manga.
     *
     * La textura de TORSO sale de un archivo horneado por combinación
     * (largo+manga+cuello), como siempre — pero SIEMPRE con
     * {@code manga=LARGA}, sin importar la manga real elegida: manga de
     * remera tiene un solo anclaje (Superior, hombro hacia abajo, sin
     * intersección de dos anclajes como pantalón/medias/calientabrazos),
     * así que en vez de hornear un archivo por cada uno de los 7 valores
     * de manga (36→63 combos) se recorta el BRAZO en runtime desde el
     * archivo de manga completa — mismo mecanismo que ya usan las otras 3
     * prendas, aplicado nada más que al brazo (ver
     * {@link #recortarMangaYCachear}). El TORSO usa esa misma textura SIN
     * recortar (el largo de torso es un eje aparte, no se toca acá).
     *
     * La estampa no va como pieza aparte: se pinta ADENTRO de la textura, asi
     * sigue al cuerpo sin geometria extra y queda recortada a la tela sola.
     */
    private static List<Pieza> remera(ItemStack stack, net.minecraft.entity.LivingEntity entidad) {
        // El slot decide la capa (2026-10-07, "un solo top"): en el de chaqueta la copia viene marcada como exterior.
        boolean abierta = com.modamod.item.TopCorte.frente(stack) != com.modamod.item.ChaquetaFrente.CERRADA;
        int capa = com.modamod.item.TopCorte.exterior(stack) ? Capa.CHAQUETA : Capa.TORSO_EXTERIOR;
        return piezasDeRemera(stack, capa, false, abierta);   // el canguro ya no viene con la capucha: va por apliques (2026-10-14)
    }

    private static List<Pieza> piezasDeRemera(ItemStack stack, int capa, boolean canguro, boolean abierta) {
        Variante variante = RemeraItem.variante(stack);
        Variante varianteBase = new Variante(variante.largo(), Variante.Manga.LARGA, variante.cuello());
        // La remera es Lado.AMBAS siempre para PATRON (un solo color, un
        // solo patron para toda la prenda): ver Garment.regionesDe en
        // PrendasDelMod.
        Identifier textura = EstampaTextures.cuerpoEstampado(varianteBase,
                RemeraItem.estampaDe(stack, Estampa.Cara.FRENTE),
                RemeraItem.estampaDe(stack, Estampa.Cara.ESPALDA),
                RemeraItem.color(stack),
                RegionResolver.capasTinte(stack, Lado.AMBAS),
                com.modamod.item.PatronRed.leer(stack),
                RemeraItem.capasDe(stack));
        // Si todavia no se pudo componer -la foto no bajo- se usa la lisa,
        // que es lo correcto mientras tanto.
        if (textura == null) textura = varianteBase.texturaCuerpo();
        // Ruedo de cada borde libre (2026-10-07): el torso y cada puño llevan el suyo.
        com.modamod.item.Ruedo ruedoTorso = com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.TORSO);
        com.modamod.item.Ruedo ruedoIzq = com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.PUNO_IZQ);
        com.modamod.item.Ruedo ruedoDer = com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.PUNO_DER);
        boolean ribTorso = ruedoTorso == com.modamod.item.Ruedo.AJUSTADO;
        boolean ribIzq = ruedoIzq == com.modamod.item.Ruedo.AJUSTADO;
        boolean ribDer = ruedoDer == com.modamod.item.Ruedo.AJUSTADO;
        if (ribTorso || ribIzq || ribDer || canguro || abierta) {
            textura = com.modamod.render.DetallesHoodie.pintar(textura, variante.largo().filas,
                    RemeraItem.manga(stack, Lado.IZQUIERDA).filas, RemeraItem.manga(stack, Lado.DERECHA).filas,
                    ribTorso, ribIzq, ribDer, canguro, com.modamod.item.TopCorte.frente(stack), Calce.leer(stack));
        }

        float dilatacion = Calce.dilatacionEfectiva(stack);
        List<Pieza> piezas = new ArrayList<>(3);
        piezas.add(new Pieza(Parte.TORSO, capa, textura, dilatacion, 0, variante.largo().filas, false, ruedoTorso));
        // Mangas POR LADO (2026-09-24, "vamos con mangas distintas") — cada
        // brazo lee su propio valor (RemeraItem#manga, izquierda vive en
        // Variante, derecha es un override aparte) y se recorta
        // independiente. La musculosa de ESE lado no dibuja el brazo: no
        // alcanza con que la textura tenga esa zona vacía, dibujar una caja
        // transparente por frame y por jugador es trabajo tirado.
        com.modamod.item.PatronRed red = com.modamod.item.PatronRed.leer(stack);
        int filasIzq = RemeraItem.manga(stack, Lado.IZQUIERDA).filas;
        if (filasIzq > 0) {
            piezas.add(new Pieza(Parte.BRAZO_IZQ, capa, recortarMangaYCachear(textura, Parte.BRAZO_IZQ, filasIzq, red), dilatacion, 0, filasIzq, false, ruedoIzq));
        }
        int filasDer = RemeraItem.manga(stack, Lado.DERECHA).filas;
        if (filasDer > 0) {
            piezas.add(new Pieza(Parte.BRAZO_DER, capa, recortarMangaYCachear(textura, Parte.BRAZO_DER, filasDer, red), dilatacion, 0, filasDer, false, ruedoDer));
        }
        return piezas;
    }

    private static final java.util.Map<String, Identifier> CACHE_MANGA = new java.util.HashMap<>();

    /**
     * Copia {@code base} y le recorta el brazo a {@code [0,hasta)} —
     * anclaje único (Superior), no hace falta intersección. No pasa por
     * {@link ClothingTextureCache#composeGarment} porque esa función
     * SIEMPRE tiñe (asume un origen en escala de grises); la textura de
     * remera ya viene a full color (con estampa/patrón si corresponde), un
     * segundo tinte la arruinaría.
     *
     * <p>La red (§{@link com.modamod.item.PatronRed}) se perfora ACÁ,
     * DESPUÉS de {@link #recortarFilas} — no en {@code cuerpoEstampado},
     * que solo conoce la manga LARGA de base — para que el refuerzo de
     * borde del puño se calcule contra el largo real de manga elegido
     * (bug real, 2026-09-20: "ni bordes de mangas").
     */
    private static Identifier recortarMangaYCachear(Identifier base, Parte brazoParte, int hasta,
                                                      com.modamod.item.PatronRed red) {
        String key = base + "#manga" + brazoParte + hasta;
        Identifier cacheada = CACHE_MANGA.get(key);
        if (cacheada != null) return cacheada;
        NativeImage origen = ClothingTextureCache.imagenBase(base);
        if (origen == null) return base;
        NativeImage copia = new NativeImage(origen.getWidth(), origen.getHeight(), true);
        copia.copyFrom(origen);
        recortarFilas(copia, brazoParte, 0, hasta);
        if (red != null) ClothingTextureCache.perforarRed(copia, red, brazoParte, 0, hasta);
        if (ClothingTextureCache.DEBUG_DUMP) {
            try {
                java.nio.file.Path dir = java.nio.file.Paths.get("modamod_debug");
                java.nio.file.Files.createDirectories(dir);
                copia.writeTo(dir.resolve("manga_" + brazoParte + "_" + hasta + ".png"));
            } catch (java.io.IOException ignored) {}
        }
        Identifier id = Identifier.of(Modamod.MOD_ID, "dynamic/manga_" + Integer.toHexString(key.hashCode()));
        net.minecraft.client.MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new net.minecraft.client.texture.NativeImageBackedTexture(copia));
        CACHE_MANGA.put(key, id);
        return id;
    }

    /**
     * Pantalón: la prueba del layering, con siete largos posibles. Se dibuja
     * en {@code Capa.PIERNA_EXTERIOR} (20), arriba de la media
     * ({@code Capa.MEDIA}, 10) — donde el pantalón no tiene tela se sigue
     * viendo la media, y donde ninguna de las dos tiene, el cuerpo base.
     *
     * La textura de PIERNA base cambia con {@link PantalonItem#largo} (7
     * PNGs generados por script, mismo criterio que los 36 cortes de
     * remera). Sin patrón ni estampa todavía.
     *
     * <h2>La cintura sube al torso, en runtime</h2>
     * Las 12 filas del cuboide de pierna terminan justo en el pivote de la
     * cadera, que corta seco contra el torso — un pantalón "completo" que
     * llegara solo hasta ahí se veía cortado, no puesto hasta la cintura de
     * verdad. En vez de hornear la banda en cada uno de los 7 PNGs de largo
     * (que multiplicaría por el eje de {@link PantalonItem#tiro}, 7×3=21
     * archivos para algo que es un simple rectángulo), se pinta la banda EN
     * RUNTIME sobre la textura ya compuesta, reusando el gancho
     * {@code Encima} que ya existía para las estampas — {@link #pintarCintura}.
     * El alto de la banda depende del tiro; el largo de pierna, no.
     *
     * Hay una tercera pieza en {@code Parte.TORSO}, a la misma capa que las
     * piernas: por debajo de una remera puesta (`Capa.TORSO_EXTERIOR`, 25 >
     * 20), pero visible si el torso no tiene nada encima. Usa el color de la
     * IZQUIERDA — es una sola pieza central, no tiene sentido partirla por
     * lado como las piernas.
     */
    private static List<Pieza> pantalon(ItemStack stack, net.minecraft.entity.LivingEntity entidad) {
        // Siempre el archivo COMPLETO ahora: la cobertura de pierna es dos
        // anclajes que se intersecan (ver PantalonItem#filasVisibles), no
        // un valor con un archivo pre-generado por combinación — el recorte
        // se hace en runtime, mismo lugar que ya pintaba la cintura.
        Identifier base = PantalonItem.TEXTURA_BASE;
        float dilatacion = Calce.dilatacionEfectiva(stack);
        int[] filasIzq = PantalonItem.filasVisibles(stack, Lado.IZQUIERDA);
        int[] filasDer = PantalonItem.filasVisibles(stack, Lado.DERECHA);
        int filasTiro = PantalonItem.tiro(stack).filas;
        List<Pieza> piezas = new ArrayList<>(3);
        // Ruedo de cada botamanga (2026-10-08, tanda 2): solo la geometría (ajustada aprieta, campana abre).
        piezas.add(new Pieza(Parte.PIERNA_IZQ, Capa.PIERNA_EXTERIOR, texturaPantalon(base, stack, Lado.IZQUIERDA), dilatacion, filasIzq[0], filasIzq[1],
                false, com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.BOTA_IZQ)));
        piezas.add(new Pieza(Parte.PIERNA_DER, Capa.PIERNA_EXTERIOR, texturaPantalon(base, stack, Lado.DERECHA), dilatacion, filasDer[0], filasDer[1],
                false, com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.BOTA_DER)));
        // La banda de cintura pinta las últimas filasTiro filas del torso (ver pintarCintura); con tiro 0 (a la
        // cadera, 2026-10-04) no hay banda.
        if (filasTiro > 0) {
            piezas.add(new Pieza(Parte.TORSO, Capa.PIERNA_EXTERIOR, texturaPantalon(base, stack, Lado.IZQUIERDA), dilatacion, 12 - filasTiro, 12));
        }
        return piezas;
    }

    private static Identifier texturaPantalon(Identifier base, ItemStack stack, Lado lado) {
        int colorBase = RegionResolver.colorBase(stack, lado);
        // Mismo UV de pierna que las medias (Parte.PIERNA_*), así que
        // PatronGenerador usa la misma caja para las dos — a pedido
        // (2026-09-18, "todas las prendas compatibles con los patrones").
        List<ClothingTextureCache.CapaMascara> capas = capasDePatron("pantalon", com.modamod.tinturas.TinturasBlockEntity.Categoria.PANTALON, stack, lado);
        int filasTiro = PantalonItem.tiro(stack).filas;
        int[] filasPierna = PantalonItem.filasVisibles(stack, lado);
        Parte piernaParte = lado == Lado.DERECHA ? Parte.PIERNA_DER : Parte.PIERNA_IZQ;
        com.modamod.item.PatronRed red = com.modamod.item.PatronRed.leer(stack);
        boolean ribBota = com.modamod.item.Ruedos.get(stack, lado == Lado.DERECHA ? com.modamod.item.ZonaRuedo.BOTA_DER : com.modamod.item.ZonaRuedo.BOTA_IZQ) == com.modamod.item.Ruedo.AJUSTADO;
        // Nota: usar Encima acá hace que reducirSiHaceFalta trate al
        // pantalón como si tuviera estampa (8x, sin achicar). Es una
        // sobra chica -12 filas de torso- frente a complicar esa deteccion
        // para distinguir "encima real" de "banda de cintura".
        ClothingTextureCache.Encima ajustes = new ClothingTextureCache.Encima() {
            @Override public String clave() {
                // piernaParte en la clave (mismo bug/fix que en medias): si
                // no, las dos piernas con el mismo largo comparten cache y
                // solo una se recorta de verdad. La pieza de TORSO reusa a
                // propósito la clave de IZQUIERDA (mismo Lado.IZQUIERDA en
                // su llamada), eso sigue igual.
                String base = "pantalon" + piernaParte + filasTiro + "_" + filasPierna[0] + "_" + filasPierna[1] + "_" + red + (ribBota ? "b" : "");
                return EstampaTextures.tieneEstampa(stack) ? base + "_" + EstampaTextures.claveEstampas(stack) : base;
            }
            @Override public boolean aplicar(NativeImage destino) {
                if (EstampaTextures.tieneEstampa(stack)) EstampaTextures.estampar(destino, stack);
                pintarCintura(destino, colorBase, filasTiro, capas);
                recortarFilas(destino, piernaParte, filasPierna[0], filasPierna[1]);
                if (red != null) ClothingTextureCache.perforarRed(destino, red, piernaParte, filasPierna[0], filasPierna[1]);
                com.modamod.render.DetallesHoodie.ribEnBorde(destino, piernaParte, filasPierna[0], filasPierna[1], ribBota, false);
                return true;
            }
        };
        return ClothingTextureCache.composeGarmentCapas(base, colorBase, capas,
                ClothingTextureCache.Shading.LEGS, ajustes);
    }

    /**
     * Deja transparentes (alfa 0) las filas de {@code parte} que quedan
     * FUERA de {@code [desde,hasta)} (de las 12 del cuboide) — el recorte
     * en runtime que reemplaza a "elegir un archivo por valor de largo"
     * ahora que la cobertura de extremidad son dos anclajes que se
     * intersecan. Mismo mecanismo fila-por-fila que {@link #pintarCintura},
     * pero borrando en vez de pintando.
     */
    private static void recortarFilas(NativeImage img, Parte parte, int desde, int hasta) {
        int escala = CuerpoGeometria.ESCALA_TELA;
        CajaSkin caja = LayoutSkin.base(parte, false).escalada(escala);
        for (CajaSkin.Rect cara : new CajaSkin.Rect[]{
                caja.derecha(), caja.frente(), caja.izquierda(), caja.atras()}) {
            int yDesde = cara.y0() + desde * escala;
            int yHasta = cara.y0() + hasta * escala;
            for (int y = cara.y0(); y < cara.y1(); y++) {
                if (y >= yDesde && y < yHasta) continue;
                for (int x = cara.x0(); x < cara.x1(); x++) {
                    img.setColor(x, y, 0);
                }
            }
        }
        quitarTapas(img, parte, desde, hasta);
    }

    /**
     * Un tramo recortado es un tubo abierto (2026-10-21, "los calientabrazos que no llegan hasta el hombro pintan la tapa del hombro";
     * después también medias, pantalón y mangas, que usaban el mismo recorte): si no llega hasta arriba, la tapa de arriba no se pinta;
     * si no llega hasta abajo, tampoco la de la mano o el pie.
     */
    private static void quitarTapas(NativeImage img, Parte parte, int desde, int hasta) {
        CajaSkin caja = LayoutSkin.base(parte, false).escalada(CuerpoGeometria.ESCALA_TELA);
        int alto = LayoutSkin.base(parte, false).alto();
        java.util.List<CajaSkin.Rect> tapas = new java.util.ArrayList<>();
        if (desde > 0) tapas.add(caja.arriba());
        if (hasta < alto) tapas.add(caja.abajo());
        for (CajaSkin.Rect t : tapas) {
            for (int y = t.y0(); y < t.y1(); y++) for (int x = t.x0(); x < t.x1(); x++) img.setColor(x, y, 0);
        }
    }

    /**
     * Pinta una banda en las últimas {@code filas} del cuboide de TORSO —
     * lisa si no hay patrón, o con el patrón encima (donde la máscara sea
     * opaca) igual que el resto de la prenda, a pedido (2026-09-18,
     * "sigo sin entender porque la parte del torso del pantalon no se
     * pinta"): antes esta banda era SIEMPRE lisa, sin importar el patrón
     * elegido, porque {@code PatronGenerador} no tenía una caja para el
     * TORSO de pantalón — ya se sumó (ver {@code PatronGenerador
     * #CAJAS_POR_PRENDA}).
     */
    private static void pintarCintura(NativeImage img, int colorRgb, int filas,
                                       List<ClothingTextureCache.CapaMascara> capas) {
        int escala = CuerpoGeometria.ESCALA_TELA;
        CajaSkin torso = LayoutSkin.base(Parte.TORSO, false).escalada(escala);
        int alto = filas * escala;
        int blanco = 0xFFFFFFFF; // ABGR: blanco es el mismo valor en cualquier orden de canales
        int colorBase = ClothingTextureCache.tintPixel(blanco, colorRgb);
        for (CajaSkin.Rect cara : new CajaSkin.Rect[]{
                torso.derecha(), torso.frente(), torso.izquierda(), torso.atras()}) {
            int y0 = Math.max(cara.y0(), cara.y1() - alto);
            for (int y = y0; y < cara.y1(); y++) {
                for (int x = cara.x0(); x < cara.x1(); x++) img.setColor(x, y, pixelCintura(x, y, colorBase, blanco, capas));
            }
        }
        // La tapa de abajo de la cintura (2026-10-18, "se ve la pierna blanca y las tapas interiores azules"): como en vanilla, la
        // capa de más afuera pinta sus propias tapas; si esa región queda transparente se ve la tapa de la ropa interior.
        if (filas > 0) {
            CajaSkin.Rect tapa = torso.abajo();
            for (int y = tapa.y0(); y < tapa.y1(); y++) {
                for (int x = tapa.x0(); x < tapa.x1(); x++) img.setColor(x, y, pixelCintura(x, y, colorBase, blanco, capas));
            }
        }
    }

    /**
     * El color de un píxel de la banda de cintura: el base con las capas en orden, fundidas con su modo/opacidad y recortadas a
     * su región — mismo criterio que composeGarmentCapas (ver CapaMascara#cubre).
     */
    private static int pixelCintura(int x, int y, int colorBase, int blanco, List<ClothingTextureCache.CapaMascara> capas) {
        int acumulado = colorBase;
        for (ClothingTextureCache.CapaMascara capa : capas) {
            int op = capa.opacidadEn(x, y);
            if (op <= 0) continue;
            acumulado = ClothingTextureCache.mezclar(acumulado, blanco, capa.colorEn(x, y), capa.modo(), op);
        }
        return ClothingTextureCache.tramar(acumulado, x, y);
    }

    /**
     * La pollera no tiene piezas sobre la caja del torso: es toda malla
     * ({@code render.PolleraMalla}, dibujada aparte). El cinto cuadrado que
     * tenía (2026-09-17, banda lisa en las filas 9..12 del torso) se sacó el
     * 2026-10-01 ("la base cuadrada de la pollera en el torso me queda con
     * otro color y fea"): salía de la textura del pantalón con otro sombreado
     * y sin los patrones; ahora la cintura de la malla es casi recta y tapa
     * sola la unión con el torso.
     */
    private static List<Pieza> pollera(ItemStack stack, net.minecraft.entity.LivingEntity entidad) {
        return List.of();
    }

    /**
     * Calientabrazos: una pieza por brazo, cobertura sola — mismo patrón
     * que {@link #medias}. Sin tiro: se probó (dos rediseños, ver
     * docs/archivo/CANAL.md v14-v15) y se retiró — tiro es exclusivo de prendas
     * inferiores según la arquitectura definitiva de
     * docs/archivo/MAQUINAS.md §"Categorías de patrones de modelado".
     *
     * Cobertura llena desde la MUÑECA hacia arriba (archivo base elegido
     * por {@link Variante.Manga}). Con SIN no hay archivo ni Pieza — no
     * hay nada que dibujar.
     */
    private static List<Pieza> calientabrazos(ItemStack stack, net.minecraft.entity.LivingEntity entidad) {
        // Siempre el archivo COMPLETO (LARGA): cobertura de extremidad son
        // dos anclajes que se intersecan, recortados en runtime. Si los dos
        // anclajes no llegan a tocarse el recorte deja el brazo entero
        // transparente — no hace falta un caso especial "SIN" aparte.
        Identifier base = texturaBaseCalientabrazos(Variante.Manga.LARGA);
        float dilatacion = Calce.dilatacionEfectiva(stack);
        int[] filasIzq = CalientabrazosItem.filasVisibles(stack, Lado.IZQUIERDA);
        int[] filasDer = CalientabrazosItem.filasVisibles(stack, Lado.DERECHA);
        return List.of(
                new Pieza(Parte.BRAZO_IZQ, Capa.MANGA_INTERIOR,
                        texturaCalientabrazos(base, stack, Lado.IZQUIERDA), dilatacion, filasIzq[0], filasIzq[1], false,
                        com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.INF_IZQ), com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.SUP_IZQ)),
                new Pieza(Parte.BRAZO_DER, Capa.MANGA_INTERIOR,
                        texturaCalientabrazos(base, stack, Lado.DERECHA), dilatacion, filasDer[0], filasDer[1], false,
                        com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.INF_DER), com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.SUP_DER)));
    }

    private static Identifier texturaBaseCalientabrazos(Variante.Manga cobertura) {
        return Identifier.of(Modamod.MOD_ID,
                "textures/models/armor/calientabrazos_" + cobertura.clave + "_layer_1.png");
    }

    private static Identifier texturaCalientabrazos(Identifier base, ItemStack stack, Lado lado) {
        int colorBase = RegionResolver.colorBase(stack, lado);
        // Caja de brazo propia en PatronGenerador (BRAZO_DER/IZQ) — a
        // pedido (2026-09-18, "todas las prendas compatibles con los
        // patrones").
        List<ClothingTextureCache.CapaMascara> capas = capasDePatron("calientabrazos", com.modamod.tinturas.TinturasBlockEntity.Categoria.CALIENTABRAZOS, stack, lado);
        int[] filas = CalientabrazosItem.filasVisibles(stack, lado);
        Parte brazoParte = lado == Lado.DERECHA ? Parte.BRAZO_DER : Parte.BRAZO_IZQ;
        com.modamod.item.PatronRed red = com.modamod.item.PatronRed.leer(stack);
        boolean ribInf = com.modamod.item.Ruedos.get(stack, lado == Lado.DERECHA ? com.modamod.item.ZonaRuedo.INF_DER : com.modamod.item.ZonaRuedo.INF_IZQ) == com.modamod.item.Ruedo.AJUSTADO;
        boolean ribSup = com.modamod.item.Ruedos.get(stack, lado == Lado.DERECHA ? com.modamod.item.ZonaRuedo.SUP_DER : com.modamod.item.ZonaRuedo.SUP_IZQ) == com.modamod.item.Ruedo.AJUSTADO;
        ClothingTextureCache.Encima recorte = new ClothingTextureCache.Encima() {
            // brazoParte en la clave — mismo bug/fix que medias/pantalón.
            @Override public String clave() {
                String base = "brazo" + brazoParte + filas[0] + "_" + filas[1] + "_" + red + (ribInf ? "i" : "") + (ribSup ? "s" : "");
                return EstampaTextures.tieneEstampa(stack) ? base + "_" + EstampaTextures.claveEstampas(stack) : base;
            }
            @Override public boolean aplicar(NativeImage destino) {
                if (EstampaTextures.tieneEstampa(stack)) EstampaTextures.estampar(destino, stack);
                recortarFilas(destino, brazoParte, filas[0], filas[1]);
                if (red != null) ClothingTextureCache.perforarRed(destino, red, brazoParte, filas[0], filas[1]);
                com.modamod.render.DetallesHoodie.ribEnBorde(destino, brazoParte, filas[0], filas[1], ribInf, ribSup);
                return true;
            }
        };
        return ClothingTextureCache.composeGarmentCapas(base, colorBase, capas,
                ClothingTextureCache.Shading.NONE, recorte);
    }
}
