package com.modamod.sublimadora;

import com.modamod.Modamod;
import com.modamod.item.PatronRed;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Compone la textura del full print: la foto recortada para llenar, con el
 * alfa de la silueta de la remera.
 *
 * Existe porque dibujar la foto como un cuadrado sobre la remera no puede
 * cubrir las mangas. La remera tiene forma de T, y cualquier rectangulo que
 * llegue hasta las mangas se sale del contorno por las esquinas. La unica
 * forma de que la estampa termine exactamente donde termina la tela es
 * multiplicar la foto por el alfa de la prenda.
 *
 * Se compone a 16x16, la resolucion del sprite del item. La foto se ve
 * gruesa, pero es la resolucion a la que se dibuja todo lo demas del item, y
 * asi el borde de la silueta queda pixel a pixel igual al de la remera lisa
 * en vez de ser mas fino que ella.
 */
public final class EstampaTextures {

    private EstampaTextures() {}



    /**
     * Una cara de la prenda: donde vive en la textura, y que pedazo del
     * dibujo le toca.
     *
     * El full print no le da a cada cara su propio recorte de la foto: eso
     * dejaba las mangas con un trozo suelto que no continuaba el torso. En
     * vez de eso hay un LIENZO virtual de 16x12 que es la remera vista de
     * frente -manga, torso, manga- y cada cara toma su rectangulo de ese
     * lienzo. Asi el motivo cruza del torso a la manga sin cortarse.
     *
     * @param rect       {u, v, ancho, alto} en la textura, en unidades de skin
     * @param lienzo     {x, y, ancho, alto} dentro del lienzo
     * @param atras      de que estampa es: la de la espalda o la del frente
     * @param espejar    si el eje horizontal va invertido. Va aparte de
     *                   atras porque no son lo mismo: la mitad trasera de un
     *                   costado PERTENECE a la estampa de atras pero se mira
     *                   de perfil, no desde atras, asi que no se espeja. Solo
     *                   se espejan las caras que de verdad se ven desde
     *                   atras: la espalda del torso y las traseras de las
     *                   mangas.
     * @param compartida caras sin lado propio, que pinta el full print que
     *                   haya. Despues de partir todo lo demas queda solo el
     *                   ruedo, que es la boca de abajo de la remera.
     * @param principal  el panel grande y plano de ese lado -el pecho, la
     *                   espalda, el frente de una pierna-. Es donde van el
     *                   logo y la estampa centrada, que a diferencia del full
     *                   print no se reparten por toda la prenda.
     */
    private record Cara(int[] rect, int[] lienzo, boolean atras,
                        boolean espejar, boolean espejarV, boolean compartida,
                        boolean principal) {
        Cara(int[] rect, int[] lienzo, boolean atras) {
            this(rect, lienzo, atras, false, false, false, false);
        }
        Cara espejada() {
            return new Cara(rect, lienzo, atras, true, espejarV, compartida, principal);
        }
        Cara principal_() {
            return new Cara(rect, lienzo, atras, espejar, espejarV, compartida, true);
        }
        /**
         * Para las caras HORIZONTALES de atras: los hombros y las tapas de las
         * mangas. La mitad delantera de una tapa va de la costura hacia el
         * pecho, o sea bajando por el lienzo. La trasera tiene que ir de la
         * costura hacia la ESPALDA, que en el lienzo tambien es bajar, pero su
         * eje v corre al reves. Sin invertirlo repite la franja de arriba en
         * vez de continuar, y las dos mitades se ven iguales.
         */
        Cara volteada() {
            return new Cara(rect, lienzo, atras, espejar, true, compartida, principal);
        }
        Cara compartida_() {
            return new Cara(rect, lienzo, atras, espejar, espejarV, true, principal);
        }
    }

    /**
     * La geometria de estampado de una prenda.
     *
     * Existe porque ya no hay una sola prenda estampable. La remera desdobla
     * torso y mangas sobre un lienzo de 24 de ancho; las medias desdoblan una
     * pierna sobre uno de 12. Lo que comparten -que la foto se coloca UNA vez
     * sobre el lienzo y cada cara toma su ventana- es todo lo demas.
     *
     * @param cuerpoY    donde empieza el cuerpo dentro del lienzo. Un diseno
     *                   con transparencia se centra sobre el CUERPO y no
     *                   sobre el lienzo entero, que arriba puede tener la
     *                   banda del hombro.
     * @param cuerpoAlto cuanto mide ese cuerpo.
     */
    private record Prenda(int escala, int lienzoAncho, int lienzoAlto,
                          int cuerpoY, int cuerpoAlto, Cara[] caras) {
        /** El rect de una cara, de unidades de skin a pixeles de la textura. */
        int[] escalar(int[] r) {
            return new int[] { r[0] * escala, r[1] * escala, r[2] * escala, r[3] * escala };
        }
    }

    private static final int LIENZO_ANCHO = 24;
    /** Las filas de arriba del lienzo son el hombro, no el cuerpo. */
    private static final int BANDA_HOMBRO = 2;
    // El largo del torso y el de las mangas salen de la Variante, asi que el
    // lienzo y la tabla de caras se arman por corte. Tienen que coincidir con
    // lo que pinta el generador de texturas: si el lienzo cuenta 12 y la tela
    // llega a 9, la estampa se encuadra sobre prenda que no existe.
    //
    // Manda el mas largo de los dos, no el torso: una manga larga sobre un
    // croptop baja mas que el ruedo, y si el lienzo terminara en el ruedo la
    // manga se saldria de el.
    private static int lienzoAlto(Variante v) {
        return BANDA_HOMBRO + Math.max(v.largo().filas, v.manga().filas);
    }

    /**
     * Las caras de un corte, con su ventana del lienzo.
     *
     * Se arma por variante porque el largo del torso cambia y la musculosa no
     * tiene mangas. La regla sigue siendo que dos caras VISIBLES no compartan
     * rectangulo: si lo hacen, se ve el mismo pedazo de foto dos veces.
     *
     * Las unicas superposiciones que quedan son a proposito, entre
     * superficies que estan en el mismo lugar del cuerpo una delante de la
     * otra: la externa de la manga contra el costado del torso, y la interna
     * contra el frente. Ahi compartir es lo que da continuidad, y la de
     * adentro no se ve nunca.
     */
    private static Cara[] caras(Variante v) {
        Cara[] hechas = CARAS.get(v);
        if (hechas != null) return hechas;

        int largo = v.largo().filas;
        int manga = v.manga().filas;
        java.util.List<Cara> lista = new java.util.ArrayList<>();

        // ── torso ────────────────────────────────────────────────────
        lista.add(new Cara(new int[] { 20, 20, 8, largo }, new int[] { 8, 2, 8, largo }, false).principal_());
        lista.add(new Cara(new int[] { 32, 20, 8, largo }, new int[] { 8, 2, 8, largo }, true)
                .espejada().principal_());
        lista.add(new Cara(new int[] { 20, 18, 8, 2 }, new int[] { 8, 0, 8, 2 }, false));            // hombro delantero
        lista.add(new Cara(new int[] { 20, 16, 8, 2 }, new int[] { 8, 0, 8, 2 }, true).volteada());  // hombro trasero
        lista.add(new Cara(new int[] { 18, 20, 2, largo }, new int[] { 2, 2, 2, largo }, false));    // costado der delantero
        lista.add(new Cara(new int[] { 16, 20, 2, largo }, new int[] { 0, 2, 2, largo }, true));     // costado der trasero
        lista.add(new Cara(new int[] { 28, 20, 2, largo }, new int[] { 20, 2, 2, largo }, false));   // costado izq delantero
        lista.add(new Cara(new int[] { 30, 20, 2, largo }, new int[] { 22, 2, 2, largo }, true));    // costado izq trasero

        if (v.tieneMangas()) {
            // manga derecha, brazo en -X
            lista.add(new Cara(new int[] { 44, 20, 4, manga }, new int[] { 4, 2, 4, manga }, false));
            lista.add(new Cara(new int[] { 52, 20, 4, manga }, new int[] { 4, 2, 4, manga }, true).espejada());
            lista.add(new Cara(new int[] { 42, 20, 2, manga }, new int[] { 2, 2, 2, manga }, false));   // externa delantera
            lista.add(new Cara(new int[] { 40, 20, 2, manga }, new int[] { 0, 2, 2, manga }, true));    // externa trasera
            lista.add(new Cara(new int[] { 48, 20, 2, manga }, new int[] { 8, 2, 2, manga }, false));   // interna delantera
            lista.add(new Cara(new int[] { 50, 20, 2, manga }, new int[] { 8, 2, 2, manga }, true));    // interna trasera
            lista.add(new Cara(new int[] { 44, 18, 4, 2 }, new int[] { 4, 0, 4, 2 }, false));            // superior delantera
            lista.add(new Cara(new int[] { 44, 16, 4, 2 }, new int[] { 4, 0, 4, 2 }, true).volteada());  // superior trasera

            // manga izquierda, brazo en +X. Ojo: el desdoblado pone interna y
            // externa en columnas distintas que en la derecha, porque el orden
            // es siempre der/frente/izq/atras en coordenadas del modelo.
            lista.add(new Cara(new int[] { 36, 52, 4, manga }, new int[] { 16, 2, 4, manga }, false));
            lista.add(new Cara(new int[] { 44, 52, 4, manga }, new int[] { 16, 2, 4, manga }, true).espejada());
            lista.add(new Cara(new int[] { 40, 52, 2, manga }, new int[] { 20, 2, 2, manga }, false));  // externa delantera
            lista.add(new Cara(new int[] { 42, 52, 2, manga }, new int[] { 22, 2, 2, manga }, true));   // externa trasera
            lista.add(new Cara(new int[] { 34, 52, 2, manga }, new int[] { 14, 2, 2, manga }, false));  // interna delantera
            lista.add(new Cara(new int[] { 32, 52, 2, manga }, new int[] { 14, 2, 2, manga }, true));   // interna trasera
            lista.add(new Cara(new int[] { 36, 50, 4, 2 }, new int[] { 16, 0, 4, 2 }, false));            // superior delantera
            lista.add(new Cara(new int[] { 36, 48, 4, 2 }, new int[] { 16, 0, 4, 2 }, true).volteada());  // superior trasera
        }

        hechas = lista.toArray(new Cara[0]);
        CARAS.put(v, hechas);
        return hechas;
    }

    private static final Map<Variante, Cara[]> CARAS = new HashMap<>();

    /** La remera del corte que sea. */
    private static Prenda deLaRemera(Variante v) {
        return new Prenda(com.modamod.render.CuerpoGeometria.ESCALA_TELA, LIENZO_ANCHO, lienzoAlto(v),
                BANDA_HOMBRO, v.largo().filas, caras(v));
    }

    // --- medias -----------------------------------------------------------
    // La media arranca en la fila 22 de las 12 del cuboide de la pierna: las
    // dos primeras filas del muslo quedan al aire. Igual que con la remera,
    // estos numeros TIENEN que ser los mismos que los de la textura, que es
    // una silueta plana en socks_solid_layer_1.png.
    private static final int MEDIA_Y = 22;
    private static final int MEDIA_ALTO = 10;
    /** 2 de un costado + 2 + el frente de 4 + 2 + 2 del otro costado. */
    private static final int LIENZO_PIERNA = 12;

    /**
     * Las caras de una pierna, dando la vuelta.
     *
     * Las dos piernas comparten lienzo: las medias son un par y un solo item,
     * y darle a cada pierna su mitad de la foto obligaria a juntar los
     * tobillos para ver el dibujo. Van iguales, como un par de verdad.
     */
    private static Cara[] deUnaPierna(int u, int v) {
        int y = v + MEDIA_Y - 16;   // el cuboide de la pierna empieza en v
        int h = MEDIA_ALTO;
        return new Cara[] {
                // El costado va partido: la mitad de adelante es del frente y
                // la de atras de la espalda, igual que en el torso.
                new Cara(new int[] { u,      y, 2, h }, new int[] { 0,  0, 2, h }, true),
                new Cara(new int[] { u + 2,  y, 2, h }, new int[] { 2,  0, 2, h }, false),
                new Cara(new int[] { u + 4,  y, 4, h }, new int[] { 4,  0, 4, h }, false).principal_(),
                new Cara(new int[] { u + 8,  y, 2, h }, new int[] { 8,  0, 2, h }, false),
                new Cara(new int[] { u + 10, y, 2, h }, new int[] { 10, 0, 2, h }, true),
                new Cara(new int[] { u + 12, y, 4, h }, new int[] { 4,  0, 4, h }, true)
                        .espejada().principal_(),
                // La planta del pie. No tiene lado propio -no se ve ni de
                // frente ni de atras- asi que la pinta el full print que haya.
                new Cara(new int[] { u + 8, v, 4, 4 }, new int[] { 4, h - 4, 4, 4 }, false)
                        .compartida_(),
        };
    }

    /** Las medias: pierna derecha en uv(0,16), izquierda en uv(16,48). */
    private static final Prenda MEDIAS;
    static {
        java.util.List<Cara> todas = new java.util.ArrayList<>();
        todas.addAll(java.util.Arrays.asList(deUnaPierna(0, 16)));
        todas.addAll(java.util.Arrays.asList(deUnaPierna(16, 48)));
        MEDIAS = new Prenda(com.modamod.render.CuerpoGeometria.ESCALA_TELA,
                LIENZO_PIERNA, MEDIA_ALTO, 0, MEDIA_ALTO, todas.toArray(new Cara[0]));
    }

    /**
     * El brazo del calientabrazos: MISMO layout que una pierna de media
     * (deUnaPierna ya toma u/v como parámetro, y el cuboide de brazo tiene
     * el mismo ancho/alto/profundidad que el de pierna) — solo cambia el
     * UV de origen: BRAZO_DER en uv(40,16), BRAZO_IZQ en uv(32,48) (mismos
     * v que pierna, coincidencia real del layout de skin, no aproximación).
     * A pedido (2026-09-19, "hace todas las prendas sublimables").
     */
    private static final Prenda BRAZO;
    static {
        java.util.List<Cara> todas = new java.util.ArrayList<>();
        todas.addAll(java.util.Arrays.asList(deUnaPierna(40, 16)));
        todas.addAll(java.util.Arrays.asList(deUnaPierna(32, 48)));
        BRAZO = new Prenda(com.modamod.render.CuerpoGeometria.ESCALA_TELA,
                LIENZO_PIERNA, MEDIA_ALTO, 0, MEDIA_ALTO, todas.toArray(new Cara[0]));
    }

    /**
     * Simetría lateral (2026-09-28, "a la sublimadora hay que agregarle
     * simetria lateral para medias y cubrebrazos"): las caras de un lado
     * leyendo el lienzo al revés — cada cara toma la ventana espejada
     * ({@code LIENZO_PIERNA - x - ancho}) y da vuelta su eje horizontal,
     * así un logo corrido hacia afuera en una pierna queda hacia afuera en
     * la otra.
     */
    private static Cara[] espejarLado(Cara[] caras) {
        Cara[] out = new Cara[caras.length];
        for (int i = 0; i < caras.length; i++) {
            Cara c = caras[i];
            int[] l = c.lienzo();
            out[i] = new Cara(c.rect(), new int[] { LIENZO_PIERNA - l[0] - l[2], l[1], l[2], l[3] },
                    c.atras(), !c.espejar(), c.espejarV(), c.compartida(), c.principal());
        }
        return out;
    }

    /** Mismas caras y en el MISMO orden que {@code base}, con el lado izquierdo espejado. */
    private static Prenda conEspejo(Prenda base, int uDer, int vDer, int uIzq, int vIzq) {
        java.util.List<Cara> todas = new java.util.ArrayList<>();
        todas.addAll(java.util.Arrays.asList(deUnaPierna(uDer, vDer)));
        todas.addAll(java.util.Arrays.asList(espejarLado(deUnaPierna(uIzq, vIzq))));
        return new Prenda(base.escala(), base.lienzoAncho(), base.lienzoAlto(), base.cuerpoY(), base.cuerpoAlto(),
                todas.toArray(new Cara[0]));
    }

    private static final Prenda MEDIAS_ESPEJO = conEspejo(MEDIAS, 0, 16, 16, 48);
    private static final Prenda BRAZO_ESPEJO = conEspejo(BRAZO, 40, 16, 32, 48);

    /** La versión con el lado izquierdo espejado, o null si la prenda no va de a pares. */
    @Nullable
    private static Prenda espejadaDe(ItemStack stack) {
        if (stack.getItem() == com.modamod.item.ModamodItems.SOCKS_SOLID
                || stack.getItem() == com.modamod.item.ModamodItems.PANTALON) return MEDIAS_ESPEJO;
        if (stack.getItem() == com.modamod.item.ModamodItems.CALIENTABRAZOS) return BRAZO_ESPEJO;
        return null;
    }

    /**
     * La pollera (2026-09-29, "quiero poder... sublimarla"): su tela tiene
     * el layout de la caja del torso (ver {@code render.PolleraMalla}), así
     * que sus caras son las del torso de la remera a largo completo — frente
     * y espalda enteros más los costados partidos al medio, sobre un lienzo
     * de 16: [der atrás 2 | der adelante 2 | frente 8 | izq adelante 2 | izq atrás 2].
     */
    private static final Prenda POLLERA = new Prenda(com.modamod.render.CuerpoGeometria.ESCALA_TELA, 16, 12, 0, 12,
            new Cara[] {
                    new Cara(new int[] { 20, 20, 8, 12 }, new int[] { 4, 0, 8, 12 }, false).principal_(),
                    new Cara(new int[] { 32, 20, 8, 12 }, new int[] { 4, 0, 8, 12 }, true).espejada().principal_(),
                    new Cara(new int[] { 18, 20, 2, 12 }, new int[] { 2, 0, 2, 12 }, false),    // costado der delantero
                    new Cara(new int[] { 16, 20, 2, 12 }, new int[] { 0, 0, 2, 12 }, true),     // costado der trasero
                    new Cara(new int[] { 28, 20, 2, 12 }, new int[] { 12, 0, 2, 12 }, false),  // costado izq delantero
                    new Cara(new int[] { 30, 20, 2, 12 }, new int[] { 14, 0, 2, 12 }, true),   // costado izq trasero
            });

    /**
     * El delantal (2026-10-11, "dale con tirantes y sublimacion"): solo el frente, la tira del torso de u 18 a 30
     * (el ancho de la malla) y 24 filas de alto, sobre un lienzo de 12x24.
     */
    private static final Prenda DELANTAL = new Prenda(com.modamod.render.CuerpoGeometria.ESCALA_TELA, 12, 24, 0, 24,
            new Cara[] {
                    new Cara(new int[] { 18, 20, 12, 24 }, new int[] { 0, 0, 12, 24 }, false).principal_(),
            });

    /**
     * La capa (2026-09-29, "forro aparte"): Frente = el exterior (la cara
     * del frente del cuboide 10x16 de la capa vanilla, u 1..11) y Espalda =
     * el forro (u 12..22), cada uno con su foto sobre un lienzo de 10x16.
     */
    private static final Prenda CAPA = new Prenda(com.modamod.render.CuerpoGeometria.ESCALA_TELA, 10, 16, 0, 16,
            new Cara[] {
                    new Cara(new int[] { 1, 1, 10, 16 }, new int[] { 0, 0, 10, 16 }, false).principal_(),
                    new Cara(new int[] { 12, 1, 10, 16 }, new int[] { 0, 0, 10, 16 }, true).espejada().principal_(),
            });

    /** Que prenda estampable es este stack, o null si no lo es. */
    @Nullable
    private static Prenda prendaDe(ItemStack stack) {
        if (stack.getItem() instanceof RemeraItem) return deLaRemera(RemeraItem.variante(stack));
        if (stack.getItem() == com.modamod.item.ModamodItems.SOCKS_SOLID) return MEDIAS;
        // Pantalón usa el mismo UV de pierna que las medias (misma Parte.
        // PIERNA_*) — el mapeo de caras es idéntico, se reusa tal cual.
        if (stack.getItem() == com.modamod.item.ModamodItems.PANTALON) return MEDIAS;
        if (stack.getItem() == com.modamod.item.ModamodItems.CALIENTABRAZOS) return BRAZO;
        if (stack.getItem() instanceof com.modamod.item.PolleraItem) return POLLERA;
        if (stack.getItem() instanceof com.modamod.item.CapaItem) return CAPA;
        if (stack.getItem() instanceof com.modamod.item.DelantalItem) return DELANTAL;
        return null;   // ModItems.esEstampable tiene que decir lo mismo
    }



    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Map<String, Identifier> CACHE_CUERPO = new HashMap<>();
    /** UUIDs que ya fallaron, para no reintentar la lectura en cada frame. */
    private static final Map<Object, Boolean> FALLADAS = new HashMap<>();

    /** El sprite de cada corte, que es de donde sale su silueta. */
    private static final Map<Variante, NativeImage> MASCARAS = new HashMap<>();

    /**
     * Textura de la remera con la foto adentro, o null si todavia no se pudo
     * componer. Devolver null es una respuesta valida: el renderer cae al
     * cuadrado recortado al torso, que es peor pero nunca se ve roto.
     */
    @Nullable
    static Identifier fullPrint(Variante variante, UUID id, Identifier texturaFoto,
                                int ancho, int alto) {
        // La silueta depende del corte: un croptop recorta la foto mas arriba
        // que un remeron, y una musculosa no la deja llegar a las mangas.
        String clave = variante.clave() + "|" + id;
        Identifier hecha = CACHE.get(clave);
        if (hecha != null) return hecha;
        if (FALLADAS.containsKey(clave)) return null;

        NativeImage silueta = mascara(variante);
        if (silueta == null) {
            FALLADAS.put(clave, true);
            return null;
        }
        NativeImage foto = leerDeLaGpu(texturaFoto, ancho, alto);
        if (foto == null) {
            // Sin marcarla como fallada: puede ser que la textura todavia no
            // este subida y ande en el proximo frame.
            return null;
        }

        try {
            NativeImage compuesta = componer(silueta, foto);
            Identifier destino = Identifier.of(Modamod.MOD_ID,
                    "estampada/" + variante.clave() + "_" + id);
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(destino, new NativeImageBackedTexture(compuesta));
            com.modamod.render.ClothingTextureCache.registrarImagenCompuesta(destino, compuesta);
            CACHE.put(clave, destino);
            return destino;
        } catch (Throwable e) {
            FALLADAS.put(clave, true);
            return null;
        } finally {
            foto.close();
        }
    }

    /**
     * Recorta la foto para llenar el cuadrado de la remera y le aplica el
     * alfa de la silueta.
     *
     * NativeImage empaqueta ABGR, no ARGB: el rojo esta en los bits bajos.
     * Por eso el color se copia entero y solo se rearma el byte de alfa, en
     * vez de descomponer y volver a componer los canales.
     */
    private static NativeImage componer(NativeImage silueta, NativeImage foto) {
        int lado = silueta.getWidth();
        NativeImage salida = new NativeImage(NativeImage.Format.RGBA, lado, silueta.getHeight(), false);

        // Cuadrado centrado de la foto: se descarta el eje que sobra.
        int corte = Math.min(foto.getWidth(), foto.getHeight());
        int desdeX = (foto.getWidth() - corte) / 2;
        int desdeY = (foto.getHeight() - corte) / 2;

        for (int y = 0; y < salida.getHeight(); y++) {
            for (int x = 0; x < lado; x++) {
                int alfaPrenda = (silueta.getColor(x, y) >>> 24) & 0xFF;
                if (alfaPrenda == 0) {
                    salida.setColor(x, y, 0);
                    continue;
                }
                int fx = desdeX + x * corte / lado;
                int fy = desdeY + y * corte / salida.getHeight();
                int pixel = foto.getColor(fx, fy);
                int alfaFoto = (pixel >>> 24) & 0xFF;
                int alfa = alfaFoto * alfaPrenda / 255;
                salida.setColor(x, y, (alfa << 24) | (pixel & 0x00FFFFFF));
            }
        }
        return salida;
    }

    /**
     * La remera del cuerpo con patron y estampas ya pintados encima, o null
     * si no hay nada de eso o no se pudo componer.
     *
     * Se compone una textura entera en vez de dibujar cuadrados sobre el
     * cuerpo porque la prenda YA es una textura en layout de skin: meter la
     * foto adentro de ella la hace seguir al cuerpo sin geometria extra, y de
     * paso queda recortada a la tela sola.
     *
     * El orden es tenido → patron → estampa, igual que en el resto del mod:
     * la foto se imprime sobre la prenda terminada y no se tine con ella.
     */
    @Nullable
    public static Identifier cuerpoEstampado(Variante variante, @Nullable Estampa frente,
                                      @Nullable Estampa espalda, int color,
                                      @Nullable Identifier patronId, int patronColor) {
        com.modamod.item.ClothingPatternItem item = patronId == null ? null : com.modamod.item.ClothingPatternItem.porId(patronId);
        com.modamod.render.PatronGenerador.Forma forma = item != null ? item.forma : com.modamod.render.PatronGenerador.Forma.ALTERNADO;
        return cuerpoEstampado(variante, frente, espalda, color,
                patronId == null ? java.util.List.of() : java.util.List.of(new com.modamod.region.RegionResolver.CapaPatron(
                        patronId, patronColor, com.modamod.item.TamanoPatron.GRANDE, 0f, 0.5f, forma, false)),
                null);
    }

    /**
     * Hasta {@code CAPAS_MAXIMO} capas apiladas, CADA UNA con su propio
     * color, tamaño Y orientación (ya vienen en cada {@code CapaPatron})
     * — a pedido (2026-09-18 "dale mandale 3" y "orden y cambio de
     * color", 2026-09-19 "variar orientacion y tamaño entre cada capa").
     */
    @Nullable
    public static Identifier cuerpoEstampado(Variante variante, @Nullable Estampa frente,
                                      @Nullable Estampa espalda, int color,
                                      java.util.List<com.modamod.region.RegionResolver.CapaPatron> capas) {
        return cuerpoEstampado(variante, frente, espalda, color, capas, null);
    }

    /**
     * Como el de arriba, con el molde de red (§{@link PatronRed}) opcional
     * — a pedido (2026-09-20). Perfora DESPUÉS de teñir/patronar/estampar,
     * mismo orden que {@link com.modamod.render.ClothingTextureCache#perforarRed}
     * documenta para el resto de las prendas.
     */
    @Nullable
    public static Identifier cuerpoEstampado(Variante variante, @Nullable Estampa frente,
                                      @Nullable Estampa espalda, int color,
                                      java.util.List<com.modamod.region.RegionResolver.CapaPatron> capas,
                                      @Nullable PatronRed red) {
        return cuerpoEstampado(variante, frente, espalda, color, capas, red, java.util.List.of());
    }

    /** Con las capas con máscara de la Sublimadora (2026-10-02). */
    @Nullable
    public static Identifier cuerpoEstampado(Variante variante, @Nullable Estampa frente,
                                      @Nullable Estampa espalda, int color,
                                      java.util.List<com.modamod.region.RegionResolver.CapaPatron> capas,
                                      @Nullable PatronRed red, java.util.List<CapaEstampa> mascaras) {
        // Sin estampas, sin patron, sin red y sin tenir no hay nada que
        // componer: se usa la textura del pack tal cual.
        if (frente == null && espalda == null && capas.isEmpty() && red == null && color == RemeraItem.BLANCO
                && mascaras.isEmpty()) {
            return null;
        }
        String clave = variante.clave() + "|" + frente + "|" + espalda + "|" + color + "|" + capas + "|" + red
                + "|" + mascaras;
        Identifier hecha = CACHE_CUERPO.get(clave);
        if (hecha != null) return hecha;
        if (FALLADAS.containsKey(clave)) return null;

        NativeImage base = telaCuerpo(variante);
        if (base == null) {
            FALLADAS.put(clave, true);
            return null;
        }
        NativeImage salida = new NativeImage(NativeImage.Format.RGBA,
                base.getWidth(), base.getHeight(), false);
        salida.copyFrom(base);
        if (color != RemeraItem.BLANCO) tenir(salida, color);
        if (!capas.isEmpty()) aplicarPatron(salida, base, capas);

        if (!estampar(salida, deLaRemera(variante), null, frente, espalda, mascaras)) {
            salida.close();
            return null;
        }

        if (red != null) {
            // Solo TORSO acá: el largo de torso se recorta por geometría
            // (rango de filas de la Pieza, 0..variante.largo().filas),
            // nunca borra alpha de la textura — por eso hace falta pasarle
            // ese rango a mano (el "corte virtual" que documenta
            // ClothingTextureCache#perforarRed) para que el dobladillo de
            // verdad quede reforzado ("inferior de remera"). Los BRAZOS en
            // cambio se recortan a su largo real DESPUÉS de esto, en una
            // copia aparte (ver PiezasDelMod#recortarMangaYCachear) — así
            // que se perforan ahí, no acá, para que el refuerzo de borde
            // se calcule contra el puño de verdad y no contra la manga
            // LARGA que se usa como base (bug real: "ni bordes de mangas").
            com.modamod.render.ClothingTextureCache.perforarRed(salida, red,
                    com.modamod.garment.Parte.TORSO, 0, variante.largo().filas);
        }

        Identifier destino = Identifier.of(Modamod.MOD_ID,
                "cuerpo/" + Long.toHexString(clave.hashCode() & 0xFFFFFFFFL));
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(destino, new NativeImageBackedTexture(salida));
        // Sin esto, ClothingTextureCache#imagenBase (que lee del resource
        // pack) nunca encuentra esta textura -- y el recorte de manga en
        // runtime cae silenciosamente a "sin recortar" para cualquier
        // remera teñida. Ver el javadoc de registrarImagenCompuesta.
        com.modamod.render.ClothingTextureCache.registrarImagenCompuesta(destino, salida);
        CACHE_CUERPO.put(clave, destino);
        return destino;
    }

    /**
     * Pinta las estampas de un stack sobre una textura ya armada.
     *
     * Es el punto de entrada de las prendas que NO se leen del pack tal cual:
     * las medias llegan aca ya tenidas, con su patron y con la piel de la
     * pierna reconstruida, y lo unico que falta es la foto.
     *
     * Devuelve false si alguna foto todavia no se pudo leer, para que quien
     * llama no cachee: en el proximo frame puede estar.
     */
    public static boolean estampar(NativeImage destino, ItemStack stack) {
        Prenda prenda = prendaDe(stack);
        if (prenda == null) return true;
        return estampar(destino, prenda, espejadaDe(stack),
                RemeraItem.estampaDe(stack, Estampa.Cara.FRENTE),
                RemeraItem.estampaDe(stack, Estampa.Cara.ESPALDA), RemeraItem.capasDe(stack));
    }

    /** Si el stack tiene alguna cara impresa. */
    public static boolean tieneEstampa(ItemStack stack) {
        return RemeraItem.estaEstampada(stack);
    }

    /** Con que identificar las estampas de un stack en una clave de cache. */
    public static String claveEstampas(ItemStack stack) {
        return RemeraItem.estampaDe(stack, Estampa.Cara.FRENTE)
                + "|" + RemeraItem.estampaDe(stack, Estampa.Cara.ESPALDA) + "|" + RemeraItem.capasDe(stack);
    }

    /**
     * A pedido (2026-09-19, "tamaño 95 a 100 pega un salto"): antes esto
     * ramificaba entre {@code pintar} (logo/centrada, achica dentro del
     * panel de una sola cara) y {@code pintarDelLienzo} (full print, cubre
     * TODO el lienzo desenrollado) — dos algoritmos distintos, con un
     * salto real entre los dos en vez de una curva. Ahora es UNA sola
     * función ({@link #pintarLienzo}) para las dos, y lo que cambia
     * continuo con la escala es el TAMAÑO del diseño sobre el mismo
     * lienzo: chico y centrado en el cuerpo a escala mínima, tapando todo
     * el lienzo a escala máxima — sin ninguna rama.
     */
    private static boolean estampar(NativeImage salida, Prenda prenda, @Nullable Prenda espejada,
                                    @Nullable Estampa frente, @Nullable Estampa espalda) {
        return estampar(salida, prenda, espejada, frente, espalda, java.util.List.of());
    }

    /** Con las capas con máscara, que van después (encima) de las estampas de frente y espalda. */
    private static boolean estampar(NativeImage salida, Prenda prenda, @Nullable Prenda espejada,
                                    @Nullable Estampa frente, @Nullable Estampa espalda,
                                    java.util.List<CapaEstampa> capas) {
        boolean listo = estamparCaras(salida, prenda, espejada, frente, espalda);
        for (CapaEstampa capa : capas) {
            for (int k = 0; k < prenda.caras().length; k++) {
                Cara cara = prenda.caras()[k];
                // Una máscara es de un solo lado: no pinta el ruedo/la planta compartidos.
                if (cara.compartida() || cara.atras() != (capa.cara() == Estampa.Cara.ESPALDA)) continue;
                if (capa.estampa().espejo() && espejada != null) cara = espejada.caras()[k];
                listo &= pintarConMascara(salida, capa, cara, prenda);
            }
        }
        return listo;
    }

    private static boolean estamparCaras(NativeImage salida, Prenda prenda, @Nullable Prenda espejada,
                                         @Nullable Estampa frente, @Nullable Estampa espalda) {
        // El ruedo/la planta (caras sin lado propio) los pinta el que
        // tenga la escala MAS GRANDE de los dos lados — el chico no llega
        // ahí de todos modos, así que en la práctica se resuelve solo por
        // los límites de volcar(); esto es solo el desempate cuando los
        // dos llegan.
        Estampa compartida;
        if (frente == null) compartida = espalda;
        else if (espalda == null) compartida = frente;
        else compartida = frente.escala() >= espalda.escala() ? frente : espalda;

        // Si alguna foto todavia no esta lista se abandona SIN cachear: en el
        // proximo frame puede estar, y cachear a medias dejaria una prenda con
        // una sola de sus dos estampas para siempre.
        boolean listo = true;
        for (int k = 0; k < prenda.caras().length; k++) {
            Cara cara = prenda.caras()[k];
            // Cada cara la pinta la estampa de SU lado. Antes un full print
            // en el frente pintaba tambien la espalda, porque el bucle usaba
            // la misma estampa para todas.
            Estampa suya = cara.compartida() ? compartida
                    : (cara.atras() ? espalda : frente);
            if (suya == null) continue;
            // Simetría lateral: la misma cara, leída del lienzo espejado
            // (mismo orden de caras, ver conEspejo).
            if (suya.espejo() && espejada != null) cara = espejada.caras()[k];
            listo &= pintarLienzo(salida, suya, cara, prenda);
        }
        return listo;
    }

    /**
     * Cuanto se achica un diseno con transparencia dentro del lienzo, para
     * que no quede pegado al borde de la prenda.
     */
    private static final float MARGEN_DISENO = 0.88f;

    /**
     * Tine la tela multiplicando por el color, que es como tine vanilla.
     *
     * Va ANTES de las estampas a proposito: la foto se imprime sobre la
     * prenda ya tenida y no se tine con ella, igual que en una sublimadora de
     * verdad.
     */
    private static void tenir(NativeImage tela, int color) {
        // El color viene en RGB pero NativeImage empaqueta ABGR: el rojo del
        // color es el byte bajo de la imagen y el azul el alto.
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        for (int y = 0; y < tela.getHeight(); y++) {
            for (int x = 0; x < tela.getWidth(); x++) {
                int px = tela.getColor(x, y);
                int alfa = (px >>> 24) & 0xFF;
                if (alfa == 0) continue;
                int pr = px & 0xFF, pg = (px >>> 8) & 0xFF, pb = (px >>> 16) & 0xFF;
                tela.setColor(x, y, (alfa << 24)
                        | ((pb * b / 255) << 16)
                        | ((pg * g / 255) << 8)
                        | (pr * r / 255));
            }
        }
    }

    /**
     * Pinta un patron (rayas, etc) sobre la tela ya tenida. Va DESPUES de
     * {@link #tenir} y ANTES de la estampa, mismo orden que
     * {@code ClothingTextureCache.composeGarment}.
     *
     * Reusa la MISMA mascara para los 36 cortes en vez de generar una por
     * corte — el problema de "N prendas x M patrones" que PRENDAS.md §6
     * marca. La mascara se autora del tamano del corte MAS GRANDE
     * (remeron + manga larga) y cada corte mas chico la recorta solo,
     * gratis: se pinta patron unicamente donde la tela de ESE corte ya tiene
     * alfa — un croptop nunca ve el patron mas alla de su propio ruedo,
     * porque ahi la base ya es transparente.
     *
     * Si la prenda todavía no tiene caja mapeada en {@code PatronGenerador}
     * cae a la prenda tenida lisa, igual que cualquier patrón del mod.
     */
    private static void aplicarPatron(NativeImage tela, NativeImage cruda,
                                      java.util.List<com.modamod.region.RegionResolver.CapaPatron> capas) {
        for (com.modamod.region.RegionResolver.CapaPatron capa : capas) {
            // Sin molde = capa lisa (2026-09-27, cuadradito de Tinturas):
            // máscara null, cubre toda su región.
            NativeImage mascara = com.modamod.render.PatronGenerador.mascaraDeCapa("remera", capa);
            if (!capa.lisa() && mascara == null) continue;
            // Región (2026-09-27, "pintar por región"): esta es la ÚNICA
            // prenda cuya composición NO pasa por
            // ClothingTextureCache#composeGarmentCapas (torso+mangas viven
            // en el MISMO atlas acá), así que el recorte se repite a mano —
            // null (TODO) no recorta nada.
            java.util.List<com.modamod.render.CajaSkin.Rect> region = capa.region() == com.modamod.region.RegionPintura.TODO
                    ? null : capa.region().rects(com.modamod.tinturas.TinturasBlockEntity.Categoria.REMERA,
                            com.modamod.render.CuerpoGeometria.ESCALA_TELA);
            // Capas en orden: cada una pinta ENCIMA de la anterior donde su
            // máscara sea opaca — a pedido, "orden y cambio de color".
            // Mismo criterio de cobertura (región + máscara + invertido) y de
            // fundido (modo + opacidad) que ClothingTextureCache#composeGarmentCapas.
            // Cuello: además de su zona, solo el borde real del escote de ESTE corte.
            NativeImage bordeCuello = capa.region().requiereBordeCuello()
                    ? com.modamod.render.ClothingTextureCache.mascaraBordeCuello(cruda)
                    : capa.region().esSolapaConTipo()
                    ? com.modamod.render.ClothingTextureCache.mascaraSolapa(cruda, capa.region()) : null;
            com.modamod.render.ClothingTextureCache.CapaMascara cm =
                    com.modamod.render.ClothingTextureCache.CapaMascara.de(capa, mascara, region, bordeCuello);
            int alto = mascara == null ? tela.getHeight() : Math.min(tela.getHeight(), mascara.getHeight());
            int ancho = mascara == null ? tela.getWidth() : Math.min(tela.getWidth(), mascara.getWidth());
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    // Sin tela de ESTE corte en este pixel, no hay donde pintar
                    // patron: es el recorte gratis contra el ruedo del corte.
                    int px = tela.getColor(x, y);
                    if (((px >>> 24) & 0xFF) == 0) continue;
                    int op = cm.opacidadEn(x, y);
                    if (op <= 0) continue;
                    int crudo = x < cruda.getWidth() && y < cruda.getHeight() ? cruda.getColor(x, y) : 0xFFFFFFFF;
                    tela.setColor(x, y, com.modamod.render.ClothingTextureCache.tramar(
                            com.modamod.render.ClothingTextureCache.mezclar(px, crudo, cm.colorEn(x, y), capa.modo(), op), x, y));
                }
            }
        }
    }

    /**
     * Cuanto mide (relativo al cuerpo) el diseño a la escala MINIMA — un
     * sello/logo chico centrado. A la escala MAXIMA el diseño llega a
     * cubrir el lienzo entero (ver más abajo) — todo lo de en medio es
     * una interpolación lineal entre las dos puntas, a pedido (2026-09-19,
     * "tamaño 95 a 100 pega un salto"): antes eran dos algoritmos
     * distintos con un salto real en vez de una curva.
     */
    private static final float ALTO_CHICO_REL = 0.35f;

    /**
     * Pinta el pedazo de foto que le toca a una cara, con el diseño
     * colocado sobre el lienzo entero al tamaño que le toca según la
     * escala — chico y centrado sobre el CUERPO a escala mínima, cubriendo
     * el lienzo entero (hombros incluidos) a escala máxima. Reemplaza a
     * los viejos {@code pintar}/{@code pintarDelLienzo}: eran dos
     * algoritmos separados (logo-en-un-panel vs full-print-en-todo-el-
     * lienzo) con un salto real entre los dos; ahora es uno solo, y lo
     * único que cambia con la escala es el tamaño.
     *
     * <p>La foto se coloca UNA sola vez -no cara por cara- y de esa
     * colocación cada cara toma su ventana (ver {@link #volcar}). Es lo
     * que hace que el dibujo continúe de una cara a la otra en vez de
     * repetirse, y lo que hace que un diseño chico simplemente no
     * alcance el ruedo/la manga sin necesidad de una rama aparte.
     */
    private static boolean pintarLienzo(NativeImage salida, Estampa estampa, Cara cara,
                                        Prenda prenda) {
        CameraptureClientCompat.Foto info = fotoDe(estampa.foto());
        if (info == null) return false;
        NativeImage foto = leerDeLaGpu(info.textura(), info.ancho(), info.alto());
        if (foto == null) return false;

        try {
            float relFoto = (float) foto.getWidth() / foto.getHeight();
            float altoChico = prenda.cuerpoAlto() * ALTO_CHICO_REL;
            float altoGrande;
            if (tieneTransparencia(foto)) {
                // Un diseno con fondo transparente se pone ENTERO, sin
                // recortar: recortarlo como a una foto le comeria justo
                // los bordes, que en un logo es donde vive la forma.
                altoGrande = Math.min(prenda.lienzoAncho() / relFoto, (float) prenda.cuerpoAlto()) * MARGEN_DISENO;
            } else {
                // Una foto opaca se recorta para llenar, y a escala 100%
                // llena el lienzo ENTERO: si solo cubriera el cuerpo, los
                // hombros quedarian sin estampar.
                altoGrande = Math.max(prenda.lienzoAncho() / relFoto, (float) prenda.lienzoAlto());
            }

            float alto;
            if (estampa.escala() <= Estampa.ESCALA_CUBRIR) {
                // Tramo de siempre: de un logo chico (ESCALA_MINIMA) a
                // cubrir el lienzo entero (ESCALA_CUBRIR = 100%), curva
                // continua sin salto (2026-09-19).
                float t = (estampa.escala() - Estampa.ESCALA_MINIMA)
                        / (Estampa.ESCALA_CUBRIR - Estampa.ESCALA_MINIMA);
                alto = altoChico + (altoGrande - altoChico) * t;
            } else {
                // Tramo nuevo (2026-09-20, "el limite maximo... mas
                // grande"): mas alla de cubrir, la escala es directamente
                // un multiplicador de sobre-tamaño — 150% es 1.5x el
                // diseño a "cubrir todo".
                alto = altoGrande * estampa.escala();
            }
            float ancho = alto * relFoto;

            // Centro: sobre el CUERPO y no el lienzo entero -las filas de
            // arriba son la banda del hombro, no el cuerpo-, corrido por
            // x()/y() de la Estampa (antes solo importaban en el logo).
            float cx = prenda.lienzoAncho() / 2f + estampa.x() * prenda.lienzoAncho();
            float cy = prenda.cuerpoY() + prenda.cuerpoAlto() / 2f - estampa.y() * prenda.cuerpoAlto();
            float x0 = cx - ancho / 2f;
            float y0 = cy - alto / 2f;

            volcar(salida, foto, prenda.escalar(cara.rect()), cara, x0, y0, ancho, alto, estampa.angulo());
            return true;
        } finally {
            foto.close();
        }
    }

    /**
     * Si la imagen trae algun pixel no opaco.
     *
     * Es lo que distingue un diseno de una foto sin tener que preguntarselo
     * al jugador: una foto de Camerapture es opaca de punta a punta, y un PNG
     * que alguien subio para estampar casi siempre tiene fondo transparente.
     */
    static boolean tieneTransparencia(NativeImage foto) {
        // De a saltos: con mirar una grilla alcanza para distinguir un fondo
        // transparente, y recorrer millones de pixeles por frame no.
        int paso = Math.max(1, Math.min(foto.getWidth(), foto.getHeight()) / 64);
        for (int y = 0; y < foto.getHeight(); y += paso) {
            for (int x = 0; x < foto.getWidth(); x += paso) {
                if (((foto.getColor(x, y) >>> 24) & 0xFF) < 250) return true;
            }
        }
        return false;
    }

    /**
     * Vuelca sobre una cara el pedazo de foto que le toca.
     *
     * Trabaja en coordenadas de LIENZO y no de foto: para cada pixel de la
     * cara calcula donde cae en el lienzo, y de ahi donde cae en la foto. Los
     * que caen fuera de la foto se dejan como estan, que es lo que permite
     * que un diseno mas chico que la prenda deje tela lisa alrededor.
     *
     * <p>{@code anguloGrados} (2026-09-20, "posibilidad de rotarla") gira el
     * diseño alrededor del centro de su propia caja: en vez de rotar la
     * foto en sí, cada pixel de SALIDA se rota en sentido inverso antes de
     * buscar su color en la foto — el resultado es el mismo (la foto se ve
     * rotada) sin tener que generar una copia rotada de la imagen fuente.
     */
    /**
     * Dónde deja pasar la foto una máscara, en coordenadas de lienzo: su
     * forma, el centro, el tamaño de su caja y el ángulo (grados).
     */
    private record Recorte(FormaMascara forma, float cx, float cy, float ancho, float alto, float angulo) {
        boolean deja(float lx, float ly) {
            double rad = Math.toRadians(-angulo);
            float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
            float dx = lx - cx, dy = ly - cy;
            float u = (dx * c - dy * s) / ancho, v = (dx * s + dy * c) / alto;
            return forma.contiene(u, v);
        }
    }

    /**
     * Una capa con máscara (2026-10-02, "mascaras de sublimacion... controles de
     * posicion angulo tamaño tanto para la imagen dentro de la mascara como para
     * la mascara dentro de la prenda"): la máscara se ubica sobre el lienzo como
     * una estampa (centro por x/y, alto = cuerpo × escala, girada); la foto se
     * ubica RELATIVA a la máscara — a escala 1 la cubre, x/y la corren dentro de
     * la máscara (girados con ella) y su ángulo se suma — y solo se pinta lo que
     * cae adentro de la forma.
     */
    private static boolean pintarConMascara(NativeImage salida, CapaEstampa capa, Cara cara, Prenda prenda) {
        Estampa estampa = capa.estampa();
        Mascara m = capa.mascara();
        CameraptureClientCompat.Foto info = fotoDe(estampa.foto());
        if (info == null) return false;
        NativeImage foto = leerDeLaGpu(info.textura(), info.ancho(), info.alto());
        if (foto == null) return false;
        try {
            float mAlto = prenda.cuerpoAlto() * m.escala() / m.forma().proporcion() * (m.forma() == FormaMascara.FRANJA ? 1.6f : 1f);
            float mAncho = mAlto * m.forma().proporcion();
            float mcx = prenda.lienzoAncho() / 2f + m.x() * prenda.lienzoAncho();
            float mcy = prenda.cuerpoY() + prenda.cuerpoAlto() / 2f - m.y() * prenda.cuerpoAlto();
            Recorte recorte = new Recorte(m.forma(), mcx, mcy, mAncho, mAlto, m.angulo());

            float relFoto = (float) foto.getWidth() / foto.getHeight();
            // Escala 1 = la foto cubre la caja de la máscara (recortando lo que sobra).
            float alto = Math.max(mAlto, mAncho / relFoto) * estampa.escala();
            float ancho = alto * relFoto;
            double rad = Math.toRadians(m.angulo());
            float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
            float ox = estampa.x() * mAncho, oy = -estampa.y() * mAlto;
            float fcx = mcx + ox * c - oy * s, fcy = mcy + ox * s + oy * c;
            volcar(salida, foto, prenda.escalar(cara.rect()), cara, fcx - ancho / 2f, fcy - alto / 2f, ancho, alto,
                    m.angulo() + estampa.angulo(), recorte);
            return true;
        } finally {
            foto.close();
        }
    }

    private static void volcar(NativeImage salida, NativeImage foto, int[] r, Cara cara,
                               float fx0, float fy0, float fAncho, float fAlto, float anguloGrados) {
        volcar(salida, foto, r, cara, fx0, fy0, fAncho, fAlto, anguloGrados, null);
    }

    private static void volcar(NativeImage salida, NativeImage foto, int[] r, Cara cara,
                               float fx0, float fy0, float fAncho, float fAlto, float anguloGrados,
                               @Nullable Recorte recorte) {
        int rx = r[0], ry = r[1], rw = r[2], rh = r[3];
        int[] l = cara.lienzo();
        float fcx = fx0 + fAncho / 2f, fcy = fy0 + fAlto / 2f;
        double rad = Math.toRadians(-anguloGrados);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);

        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int fondo = salida.getColor(rx + x, ry + y);
                int alfaPrenda = (fondo >>> 24) & 0xFF;
                if (alfaPrenda == 0) continue;   // fuera de la tela

                float px = (x + 0.5f) / rw;
                float py = (y + 0.5f) / rh;
                if (cara.espejar()) px = 1f - px;
                if (cara.espejarV()) py = 1f - py;

                // Donde cae este pixel en el lienzo...
                float lx = l[0] + l[2] * px;
                float ly = l[1] + l[3] * py;
                if (recorte != null && !recorte.deja(lx, ly)) continue;
                // ...rotado alrededor del centro de la caja, en sentido
                // inverso al ángulo pedido, y de ahi a la foto.
                float dx = lx - fcx, dy = ly - fcy;
                float rx2 = dx * cos - dy * sin;
                float ry2 = dx * sin + dy * cos;
                float u = (rx2 + fAncho / 2f) / fAncho;
                float v = (ry2 + fAlto / 2f) / fAlto;
                if (u < 0f || u >= 1f || v < 0f || v >= 1f) continue;

                int sx = Math.min((int) (u * foto.getWidth()), foto.getWidth() - 1);
                int sy = Math.min((int) (v * foto.getHeight()), foto.getHeight() - 1);
                int pixel = foto.getColor(sx, sy);
                int alfaFoto = (pixel >>> 24) & 0xFF;
                if (alfaFoto == 0) continue;
                salida.setColor(rx + x, ry + y, (alfaPrenda << 24) | sobre(fondo, pixel, alfaFoto));
            }
        }
    }

    /** Mezcla los tres canales bajos de ABGR, dejando el alfa afuera. */
    private static int sobre(int fondo, int encima, int alfa) {
        int salida = 0;
        for (int desp = 0; desp < 24; desp += 8) {
            int f = (fondo >>> desp) & 0xFF;
            int e = (encima >>> desp) & 0xFF;
            salida |= (((e * alfa + f * (255 - alfa)) / 255) & 0xFF) << desp;
        }
        return salida;
    }

    @Nullable
    static CameraptureClientCompat.Foto fotoDe(UUID id) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("camerapture")) return null;
        try {
            return CameraptureClientCompat.foto(id);
        } catch (Throwable ignorado) {
            return null;
        }
    }

    private static final Map<Variante, NativeImage> TELAS = new HashMap<>();

    @Nullable
    private static NativeImage telaCuerpo(Variante v) {
        if (TELAS.containsKey(v)) return TELAS.get(v);
        NativeImage img = leerRecurso(v.texturaCuerpo());
        if (img != null) CuelloRecorte.aplicar(img, v.cuello());
        TELAS.put(v, img);
        return img;
    }

    /** El sprite del corte liso, de donde sale su silueta. */
    @Nullable
    private static NativeImage mascara(Variante v) {
        if (MASCARAS.containsKey(v)) return MASCARAS.get(v);
        NativeImage img = leerRecurso(Identifier.of(Modamod.MOD_ID,
                "textures/item/corte_" + v.claveBase() + ".png"));
        MASCARAS.put(v, img);
        return img;
    }

    @Nullable
    private static NativeImage leerRecurso(Identifier id) {
        try {
            Resource recurso = MinecraftClient.getInstance().getResourceManager()
                    .getResourceOrThrow(id);
            try (InputStream in = recurso.getInputStream()) {
                return NativeImage.read(in);
            }
        } catch (Throwable e) {
            return null;
        }
    }

    /** Lo mismo para quien no conoce el tamaño (EfectoTrim, 2026-10-06): lee la textura tal cual está en la GPU. */
    @Nullable
    public static NativeImage leerTextura(Identifier textura) {
        return leerDeLaGpu(textura, 0, 0);
    }

    /**
     * Los pixeles de una textura ya subida a la placa.
     *
     * La foto de Camerapture no es un recurso del resource pack: se registra
     * en runtime, asi que la unica forma de leerla es pedirsela de vuelta a
     * la GPU. El tamano se consulta a OpenGL y no al mod, porque la textura
     * puede estar paddeada y leer con el tamano equivocado corrompe la imagen.
     */
    @Nullable
    static NativeImage leerDeLaGpu(Identifier textura, int anchoSugerido, int altoSugerido) {
        if (!RenderSystem.isOnRenderThread()) return null;
        try {
            AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(textura);
            if (tex == null) return null;
            int glId = tex.getGlId();
            if (glId <= 0) return null;

            RenderSystem.bindTexture(glId);
            int ancho = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int alto = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (ancho <= 0 || alto <= 0) {
                ancho = anchoSugerido;
                alto = altoSugerido;
            }
            if (ancho <= 0 || alto <= 0) return null;

            NativeImage imagen = new NativeImage(ancho, alto, false);
            imagen.loadFromTextureImage(0, false);
            return imagen;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Ícono 2D de la remera para {@code PantallaMaquina} (2026-09-21,
     * "quiero un preview real de la prenda... solo muestra colores"): la
     * SILUETA REAL del corte puesto (el mismo sprite {@code item/corte_*}
     * que ya se ve en el inventario, con cuello/mangas/dobladillo — no un
     * recorte del wrap de piel), teñida al color real, con la estampa
     * full-print pegada encima si corresponde. El recorte de "solo un
     * cuadrado de color" que hacía antes {@code PantallaMaquina} salía de
     * la textura de piel (layout de skin, sin forma reconocible a la
     * distancia); este sprite en cambio SÍ tiene la silueta recortada de
     * verdad, como cualquier ícono de prenda de Minecraft.
     *
     * <p>Devuelve una copia SIEMPRE nueva y de propiedad del que llama
     * (nunca la instancia compartida de {@link com.modamod.render.ClothingTextureCache#imagenBase}):
     * {@code PantallaMaquina} cierra todo lo que le llega de acá con
     * {@code close()}, así que devolver la compartida la corrompería para
     * el resto del mod la próxima vez que se usara (mismo tipo de bug que
     * causó el crash real de memoria nativa de este mismo día).
     *
     * <p>Devuelve {@code null} si no hay nada dibujable todavía (ej. la
     * foto del full print no terminó de bajar) — el llamador cae al
     * estado "apagada".
     */
    @Nullable
    public static NativeImage iconoParaPantalla(ItemStack stack) {
        Variante variante = RemeraItem.variante(stack);
        Estampa estampaFrente = RemeraItem.estampaDe(stack, Estampa.Cara.FRENTE);
        if (estampaFrente != null && estampaFrente.cubrir()) {
            CameraptureClientCompat.Foto foto = fotoSegura(estampaFrente.foto());
            if (foto != null) {
                Identifier compuesta = fullPrint(variante, estampaFrente.foto(),
                        foto.textura(), foto.ancho(), foto.alto());
                if (compuesta != null) {
                    NativeImage compartida = com.modamod.render.ClothingTextureCache.imagenBase(compuesta);
                    if (compartida != null) return copiar(compartida);
                }
            }
        }
        Identifier iconoBase = Identifier.of(Modamod.MOD_ID, "textures/item/corte_" + variante.clave() + ".png");
        NativeImage base = com.modamod.render.ClothingTextureCache.imagenBase(iconoBase);
        if (base == null) return null;
        return teñida(base, RemeraItem.color(stack));
    }

    /** Mismo guardia que usa {@code RemeraItemRenderer.fotoDe}: sin Camerapture, o si tira, ninguna foto. */
    @Nullable
    private static CameraptureClientCompat.Foto fotoSegura(UUID id) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("camerapture")) return null;
        try {
            return CameraptureClientCompat.foto(id);
        } catch (Throwable ignorado) {
            return null;
        }
    }

    private static NativeImage copiar(NativeImage origen) {
        NativeImage copia = new NativeImage(origen.getWidth(), origen.getHeight(), true);
        copia.copyFrom(origen);
        return copia;
    }

    /** Multiplica {@code base} (gris, pensada para teñir) por {@code rgb} — mismo cálculo que {@code ClothingTextureCache.tinted}. */
    private static NativeImage teñida(NativeImage base, int rgb) {
        NativeImage salida = new NativeImage(base.getWidth(), base.getHeight(), true);
        int dr = (rgb >> 16) & 0xFF, dg = (rgb >> 8) & 0xFF, db = rgb & 0xFF;
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int px = base.getColor(x, y);
                int a = (px >>> 24) & 0xFF;
                if (a == 0) { salida.setColor(x, y, 0); continue; }
                int b = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, r = px & 0xFF;
                int tr = (r * dr) / 255, tg = (g * dg) / 255, tb = (b * db) / 255;
                salida.setColor(x, y, (a << 24) | (tb << 16) | (tg << 8) | tr);
            }
        }
        return salida;
    }
}
