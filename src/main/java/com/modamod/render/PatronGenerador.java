package com.modamod.render;

import com.modamod.item.TamanoPatron;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.StringIdentifiable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera las máscaras de patrón (rayas, etc) EN CÓDIGO en vez de un PNG
 * por cada combinación prenda×patrón×tamaño — a pedido (2026-09-18):
 * "que tal si lo hacemos generado proceduralmente, [...] tengo otro
 * [patrón] que es las rayas superiores y como van a saber donde
 * ubicarse". La respuesta es {@link Forma}: cada patrón declara CÓMO se
 * ubica (alternado por toda la caja, o pegado a un extremo/al medio), y
 * esta clase sabe DÓNDE está esa caja para cada prenda (mismas
 * coordenadas que antes vivían a mano en los scripts de generación de
 * PNGs).
 *
 * <p>Segunda vuelta (2026-09-18, "pensaba en agregar rotación... si
 * además se agrega ubicación"; ampliado 2026-09-19 a ángulo libre, "girarlo
 * en angulo"): el ángulo (0°=horizontal de siempre, 90°=vertical, o
 * cualquier diagonal entre medio) rota la rayita — es un eje aparte de
 * {@link Forma} porque cualquier combinación tiene sentido (una raya "al
 * medio" puede ser horizontal, vertical o diagonal).
 *
 * <h2>Convención de coordenadas</h2>
 * Todo en el atlas "SKIN a 8x" (512×512) que ya usa {@link
 * ClothingTextureCache}/{@code PiezasDelMod} — una {@link Caja} es la
 * fila de las 4 caras laterales (derecha+frente+izquierda+atrás) de un
 * cuboide del layout de skin, calculadas a mano una sola vez acá (antes
 * repetidas en cada script de Python que generaba un PNG).
 */
public final class PatronGenerador {

    private PatronGenerador() {}

    private static final int LADO_ATLAS = 512;

    /**
     * Una caja de caras dentro del atlas: x0..x1, y arrancando en yTop, alto
     * filas; {@code prof} = ancho en px de las caras der/izq (y alto de las
     * tapas). Las tapas de arriba/abajo quedan justo encima de la fila de
     * caras (ver {@link #recorrer}).
     */
    public record Caja(int x0, int yTop, int x1, int alto, int prof) {
        int ancho() { return (x1 - x0 - 2 * prof) / 2; }
    }

    // PIERNA_DER (u=0,v=16,ancho=4,prof=4) / PIERNA_IZQ (u=16,v=48) a escala 8:
    // caras en x0..x0+128 (4 caras de 32px), y desde (v+prof)*8.
    private static final Caja PIERNA_DER = new Caja(0, 160, 128, 96, 32);
    private static final Caja PIERNA_IZQ = new Caja(128, 416, 256, 96, 32);
    // BRAZO_DER (u=40,v=16) / BRAZO_IZQ (u=32,v=48), mismo cálculo.
    private static final Caja BRAZO_DER = new Caja(320, 160, 448, 96, 32);
    private static final Caja BRAZO_IZQ = new Caja(256, 416, 384, 96, 32);
    // TORSO (u=16,v=16,ancho=8,prof=4): caras 2*prof+2*ancho=192 de ancho.
    private static final Caja TORSO = new Caja(128, 160, 320, 96, 32);
    // La capa (2026-09-29, "capas... como las capas vanilla"): el paño es la
    // caja 10x16x1 de la capa vanilla en uv 0,0 (exterior = frente, forro =
    // atrás) y capucha/cuello salen de una caja de detalles 12x8x2 en uv 24,0
    // (ver render.CapaMalla).
    private static final Caja CAPA = new Caja(0, 8, 176, 128, 8);
    private static final Caja CAPA_DETALLES = new Caja(192, 16, 416, 64, 16);

    // El delantal (2026-10-11): la misma tira del torso (u 16..40) pero de 24 px de alto (v 20..44), para que el
    // paño de pecho a pies quede a 1 px de modelo = 1 px de textura (ver PolleraMalla#dibujarDelantal).
    private static final Caja DELANTAL = new Caja(128, 160, 320, 192, 32);

    /** El valor (pixel de máscara) de un punto de la tira de 4 caras, en coordenadas de la tira. */
    @FunctionalInterface
    private interface ValorTira {
        int valor(int lx, int ly);
    }

    /**
     * Pinta en {@code img} las 4 caras de la caja Y sus 2 tapas (2026-09-28,
     * "la parte de arriba de la remera no se esta pintando"): antes solo se
     * pintaba la fila de caras, así que los hombros (tapa de arriba del
     * torso) y la planta/el ruedo (tapa de abajo) quedaban siempre con el
     * color base. Cada pixel de tapa se traduce a la CONTINUACIÓN de la tira:
     * la mitad de la tapa pegada al frente sigue al frente (filas negativas,
     * "por encima" de la fila 0), la mitad pegada a la espalda sigue a la
     * espalda (espejada en x, como está la espalda en el atlas) — así una
     * raya o un motivo cruza el hombro sin cortarse, igual que el arnés.
     * Abajo es lo mismo, con la tapa dada vuelta (en la tapa de abajo el
     * frente queda del lado de arriba del atlas).
     */
    private static void recorrer(Caja caja, NativeImage img, ValorTira f) {
        int w = caja.x1() - caja.x0(), h = caja.alto(), prof = caja.prof(), ancho = caja.ancho();
        for (int ly = 0; ly < h; ly++) {
            for (int lx = 0; lx < w; lx++) {
                img.setColor(caja.x0() + lx, caja.yTop() + ly, f.valor(lx, ly));
            }
        }
        int frente = prof, espalda = 2 * prof + ancho;
        int yTapa = caja.yTop() - prof;
        for (int cy = 0; cy < prof; cy++) {
            for (int cx = 0; cx < ancho; cx++) {
                // Tapa de arriba: su borde de abajo toca el frente.
                boolean ladoFrente = cy >= prof / 2;
                int lxA = ladoFrente ? frente + cx : espalda + (ancho - 1 - cx);
                int lyA = ladoFrente ? -(prof - cy) : -(cy + 1);
                img.setColor(caja.x0() + prof + cx, yTapa + cy, f.valor(lxA, lyA));
                // Tapa de abajo: su borde de arriba toca el frente.
                boolean abajoFrente = cy < prof / 2;
                int lxB = abajoFrente ? frente + cx : espalda + (ancho - 1 - cx);
                int lyB = abajoFrente ? h + cy : h + (prof - 1 - cy);
                img.setColor(caja.x0() + prof + ancho + cx, yTapa + cy, f.valor(lxB, lyB));
            }
        }
    }

    /**
     * Qué cajas pinta un patrón para cada prenda — la pierna/brazo que
     * gobierna esa prenda (medias y pantalón comparten pierna, calienta-
     * brazos usa brazo, remera usa torso+los dos brazos). Prenda sin
     * entrada acá → sin patrón procedural, cae a la prenda lisa.
     */
    private static final Map<String, List<Caja>> CAJAS_POR_PRENDA = Map.of(
            "socks", List.of(PIERNA_DER, PIERNA_IZQ),
            // TORSO acá también (a pedido, 2026-09-18: "sigo sin entender
            // porque la parte del torso del pantalon no se pinta") — la
            // banda de cintura (PiezasDelMod#pintarCintura) pinta las
            // últimas filas de este mismo cuadro, y ahora puede leer
            // patrón de acá en vez de quedar SIEMPRE lisa.
            "pantalon", List.of(PIERNA_DER, PIERNA_IZQ, TORSO),
            "calientabrazos", List.of(BRAZO_DER, BRAZO_IZQ),
            "remera", List.of(TORSO, BRAZO_DER, BRAZO_IZQ),
            // La pollera nueva (2026-09-29, "quiero poder... teñirla"): su tela
            // usa el layout de la caja del TORSO, envuelto en la campana (ver
            // render.PolleraMalla), así que los patrones van en esa caja.
            "pollera", List.of(TORSO),
            "capa", List.of(CAPA, CAPA_DETALLES),
            "delantal", List.of(DELANTAL),
            // La textura plana de una zona de sombrero, banda o calzado (2026-10-15, ver ZonaTextura): una sola "caja" sin tapas
            // que cubre el atlas entero; 1 texel de la zona = 8 px de la máscara (1 px de skin).
            "zona", List.of(new Caja(0, 0, LADO_ATLAS, LADO_ATLAS, 0)));

    /**
     * Dónde cae la rayita a lo largo del eje que le toca según
     * {@link Orientacion} (alto de la caja si es HORIZONTAL, ancho si es
     * VERTICAL):
     * <ul>
     *   <li>{@code ALTERNADO}: banda y hueco del mismo grosor, repetida
     *   de punta a punta — no importa cuánta tela tenga la prenda, el
     *   recorte final (fuera de esta clase) se encarga de mostrar solo lo
     *   que corresponde.</li>
     *   <li>{@code ARRIBA}/{@code ABAJO}: una sola banda pegada a ese
     *   extremo, sin repetirse (ej. "rayas superiores").</li>
     *   <li>{@code MEDIO}: una sola banda centrada.</li>
     * </ul>
     */
    /**
     * A pedido (2026-09-19, "desarrollame mas opcion 2"): la forma dejó de
     * venir pegada al ítem del patrón — {@code StringIdentifiable} para
     * poder guardarla como componente de dato por capa, mismo mecanismo
     * que {@link com.modamod.item.TamanoPatron}.
     */
    public enum Forma implements StringIdentifiable {
        ALTERNADO("alternado"), ARRIBA("arriba"), ABAJO("abajo"), MEDIO("medio"), TRES_RAYAS("tres_rayas");

        public final String clave;

        Forma(String clave) {
            this.clave = clave;
        }

        @Override
        public String asString() {
            return clave;
        }

        public String traduccion() {
            return "modamod.patronforma." + clave;
        }
    }

    private static final Map<String, NativeImage> CACHE = new HashMap<>();

    /** Overload de compatibilidad: horizontal (0°), como todo el patrón antes de la rotación. */
    public static NativeImage mascaraPara(String prenda, Forma forma, int grosorBase, TamanoPatron tamano) {
        return mascaraPara(prenda, forma, 0f, grosorBase, tamano, 0.5f);
    }

    /** Overload de compatibilidad: sin posición (formas fijas, que la ignoran). */
    public static NativeImage mascaraPara(String prenda, Forma forma, float anguloGrados,
                                          int grosorBase, TamanoPatron tamano) {
        return mascaraPara(prenda, forma, anguloGrados, grosorBase, tamano, 0.5f);
    }

    /**
     * La máscara para {@code prenda} con esta forma/ángulo/grosor/tamaño/
     * posición, o null si la prenda no tiene caja mapeada (cae a la
     * prenda lisa). Cacheada por combinación exacta (ángulo redondeado a
     * un paso de 15° — mismo criterio que {@code posicion}, no explotar
     * el cache con floats infinitos) — el grosor final ya incluye la
     * escala del tamaño, así que Grande/Mediano/Chico son entradas de
     * cache distintas, generadas una sola vez cada una.
     *
     * <h2>Ángulo (reemplaza a la vieja Horizontal/Vertical, 2026-09-19)</h2>
     * 0°=horizontal (de siempre, banda cruza TODO el ancho de la caja),
     * 90°=vertical (banda sube TODA la altura) — cualquier valor entre
     * medio es una diagonal. En vez de sweepear una sola fila/columna por
     * "d" como antes, cada pixel (x,y) de la caja proyecta sobre el eje
     * rotado: {@code d = x*sin(ang) + y*cos(ang)}, y {@code largo} es el
     * rango real de esa proyección sobre las 4 esquinas de la caja (en
     * 0°/90° da EXACTO lo mismo que antes: alto o ancho de la caja).
     *
     * <p>{@code posicion} (0.0..1.0, a lo largo de "d") solo mueve algo en
     * {@link Forma#TRES_RAYAS}: las formas fijas (ARRIBA/ABAJO/MEDIO/
     * ALTERNADO) la ignoran.
     */
    public static NativeImage mascaraPara(String prenda, Forma forma, float anguloGrados,
                                          int grosorBase, TamanoPatron tamano, float posicion) {
        return mascaraPara(prenda, forma, anguloGrados, grosorBase, tamano, posicion, 0);
    }

    /**
     * Como el de arriba, con la {@code semilla} que alimenta el valor al
     * azar de cada raya (Variación: Aleatorio, 2026-09-28).
     */
    public static NativeImage mascaraPara(String prenda, Forma forma, float anguloGrados,
                                          int grosorBase, TamanoPatron tamano, float posicion, int semilla) {
        return mascaraPara(prenda, forma, anguloGrados, grosorBase, tamano, posicion, semilla, 1f);
    }

    /**
     * Con {@code distancia} (2026-09-30, sliders de distancia): factor del
     * período de las rayas — 1 = raya y hueco del mismo ancho, como siempre;
     * 0.5 = sin hueco (pegadas); 3 = el triple de período. Solo cambia
     * ALTERNADO y TRES_RAYAS (las de una sola raya no tienen distancia).
     */
    public static NativeImage mascaraPara(String prenda, Forma forma, float anguloGrados,
                                          int grosorBase, TamanoPatron tamano, float posicion, int semilla,
                                          float distancia) {
        return mascaraPara(prenda, forma, anguloGrados, grosorBase, tamano, posicion, semilla, distancia, false);
    }

    /** Con {@code simetria} del torso (2026-09-30): las rayas en diagonal quedan en V, ver {@link #reflejoTorso}. */
    public static NativeImage mascaraPara(String prenda, Forma forma, float anguloGrados,
                                          int grosorBase, TamanoPatron tamano, float posicion, int semilla,
                                          float distancia, boolean simetria) {
        List<Caja> cajas = cajasDe(prenda);
        if (cajas == null) return null;

        int grosor = Math.max(1, Math.round(grosorBase * tamano.escala));
        int posPaso = forma == Forma.TRES_RAYAS ? Math.round(Math.max(0f, Math.min(1f, posicion)) * 100) : 0;
        int anguloPaso = Math.round(anguloGrados / 15f) * 15;
        int periodo = Math.max(grosor, Math.round(grosor * 2 * distancia));
        String key = prenda + "|" + forma + "|" + anguloPaso + "|" + grosor + "|" + posPaso + "|" + semilla + "|" + periodo + "|" + simetria;
        NativeImage cacheada = CACHE.get(key);
        if (cacheada != null) return cacheada;

        NativeImage img = mascaraVacia();

        // Espejo brazo/pierna IZQ (2026-09-20/21): un brazo está en -X y el
        // otro en +X, así que la columna der()/izq() de este atlas es la
        // cara de AFUERA en uno y la de ADENTRO en el otro. El espejo de
        // verdad del perímetro cíclico der-frente-izq-atras alrededor del
        // eje que pasa por el centro de frente y de atrás es
        // "x' = 3·cuarto − x (mod ancho)": intercambia der↔izq (cada uno
        // invertido) e invierte frente/atrás DENTRO de su columna — así una
        // diagonal sale con la inclinación opuesta en el otro brazo.
        // TRES_RAYAS: bloque fijo hueco,raya,hueco,raya,hueco,raya que se
        // desliza con posPaso a lo largo del eje (3 períodos).
        int bloque = periodo * 3;
        // Grosor del contorno de una raya: un cuarto de su ancho, mínimo 1px.
        int contorno = Math.max(1, grosor / 4);
        double rad = Math.toRadians(anguloPaso);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        for (Caja caja : cajas) {
            int w = caja.x1() - caja.x0(), h = caja.alto();
            boolean espejar = caja == PIERNA_IZQ || caja == BRAZO_IZQ;
            int cuarto = w / 4;
            double p00 = 0, p10 = w * sin, p01 = h * cos, p11 = w * sin + h * cos;
            double min = Math.min(Math.min(p00, p10), Math.min(p01, p11));
            double max = Math.max(Math.max(p00, p10), Math.max(p01, p11));
            int largo = (int) Math.round(max - min);
            int inicioBloque = Math.round(posPaso / 100f * Math.max(0, largo - bloque));
            double minFinal = min;
            boolean simetrica = simetria && caja == TORSO;
            recorrer(caja, img, desplegar(prenda, caja, simetrica, (lx, ly) -> {
                int lxEspejado = espejar ? Math.floorMod(3 * cuarto - lx, w) : lx;
                if (simetrica) lxEspejado = reflejoTorso(lxEspejado, w);
                int d = (int) Math.round(lxEspejado * sin + ly * cos - minFinal);
                boolean opaco = rayaOpaca(forma, d, grosor, periodo, largo, inicioBloque, bloque);
                boolean esContorno = !opaco && (rayaOpaca(forma, d - contorno, grosor, periodo, largo, inicioBloque, bloque)
                        || rayaOpaca(forma, d + contorno, grosor, periodo, largo, inicioBloque, bloque));
                // Número de raya: el de la banda de "d" (el contorno cae
                // al lado de una raya, toma el número de esa).
                int dRaya = esContorno && rayaOpaca(forma, d - contorno, grosor, periodo, largo, inicioBloque, bloque)
                        ? d - contorno : d + (esContorno ? contorno : 0);
                int indice = forma == Forma.TRES_RAYAS
                        ? Math.floorDiv(dRaya - inicioBloque, periodo) : Math.floorDiv(dRaya, periodo);
                int cob = opaco || esContorno ? 255 : 0;
                return pixelMascara(cob, esContorno, indice, 0, semilla, ly, h);
            }));
        }

        CACHE.put(key, img);
        return img;
    }

    /** ¿La raya de esta forma pinta en la distancia {@code d} del eje? */
    private static boolean rayaOpaca(Forma forma, int d, int grosor, int periodo, int largo, int inicioBloque, int bloque) {
        // ARRIBA/ABAJO dejan un margen del mismo grosor que la rayita antes
        // de empezar (2026-09-18, "un poquito mas abajo del borde").
        return switch (forma) {
            case ALTERNADO -> Math.floorMod(d, periodo) < grosor;
            case ARRIBA -> d >= grosor && d < grosor * 2;
            case ABAJO -> d >= largo - grosor * 2 && d < largo - grosor;
            case MEDIO -> d >= (largo - grosor) / 2 && d < (largo - grosor) / 2 + grosor;
            case TRES_RAYAS -> {
                // La raya ocupa el final de cada período (con período = 2
                // grosores es lo mismo de siempre: hueco, raya).
                int rel = d - inicioBloque;
                yield rel >= 0 && rel < bloque && rel % periodo >= periodo - grosor;
            }
        };
    }

    // ── Canales de la máscara (2026-09-28, Fase 2: varios colores) ──────
    // La máscara ya no dice solo "pinta / no pinta": cada pixel lleva en
    // sus canales lo necesario para elegir el color AL COMPONER, sin tener
    // que generar una máscara por combinación de colores:
    //   alfa  = cobertura 0..255 (el vichy usa 128)
    //   rojo  = bit 7: es CONTORNO; bits 0..6: valor al azar de esta
    //           repetición (Variación: Aleatorio)
    //   verde = número de repetición (Variación: Alternar)
    //   azul  = altura dentro de la pieza, 0 arriba .. 255 abajo (Degradé)
    // Se escribe en TODOS los pixeles de la caja, también los de cobertura
    // 0: una capa invertida pinta justo ahí y necesita los mismos datos.

    private static int pixelMascara(int cobertura, boolean contorno, int indiceA, int indiceB, int semilla, int ly, int h) {
        int azar = (int) (mezclarHash(indiceA, indiceB, semilla) & 0x7F);
        int rojo = (contorno ? 0x80 : 0) | azar;
        int verde = Math.floorMod(indiceA + indiceB, 256);
        int azul = h <= 1 ? 0 : Math.max(0, Math.min(255, ly * 255 / (h - 1)));
        // NativeImage es ABGR.
        return (cobertura << 24) | (azul << 16) | (verde << 8) | rojo;
    }

    /** Pixel de contorno según los canales de arriba. */
    public static boolean esContorno(int pixel) { return (pixel & 0x80) != 0; }
    /** Valor al azar (0..127) de la repetición de este pixel. */
    public static int azar(int pixel) { return pixel & 0x7F; }
    /** Número de repetición (0..255) de este pixel. */
    public static int repeticion(int pixel) { return (pixel >> 8) & 0xFF; }
    /** Altura (0 arriba .. 255 abajo) de este pixel dentro de su pieza. */
    public static int altura(int pixel) { return (pixel >> 16) & 0xFF; }

    /**
     * Clave de prenda de la pollera para {@link #mascaraDeCapa}: los
     * patrones dependen de la forma y del largo (ver {@link #desplegar}).
     */
    public static String clavePollera(com.modamod.item.PolleraForma forma, com.modamod.item.PolleraLargo largo) {
        return clavePollera(forma, largo, 0);
    }

    /** Con la cola del que la lleva en pasos de media unidad ({@link PolleraMalla#pasoDeCola}). */
    public static String clavePollera(com.modamod.item.PolleraForma forma, com.modamod.item.PolleraLargo largo,
                                      int pasoCola) {
        return "pollera:" + forma.name() + ":" + largo.name() + (pasoCola > 0 ? ":" + pasoCola : "");
    }

    private static List<Caja> cajasDe(String prenda) {
        return CAJAS_POR_PRENDA.get(prenda.startsWith("pollera:") ? "pollera" : prenda);
    }

    /**
     * Patrones de la pollera sin deformar (2026-10-01, "alguna forma de
     * acomodar las UVs de las polleras para que los patrones no se
     * deformen?"): la tela da la vuelta entera en los 24 px de textura en
     * todas las filas, pero el ruedo es mucho más ancho que la cintura (y el
     * largo de tela no son 12 px). Acá cada pixel de la textura se lleva a
     * dónde cae en la tela desplegada de esa pollera ({@link PolleraMalla.Perfil}):
     * en horizontal, la distancia al centro del frente por la vuelta de esa
     * fila / 24; en vertical, la fracción del largo de tela. El patrón se
     * evalúa ahí, así que en la textura queda "abierto en abanico" y sobre
     * la pollera los lunares salen redondos y las rayas parejas. La costura
     * queda en el centro de la espalda, como en una pollera de verdad.
     *
     * <p>Con simetría, la mitad izquierda lee la derecha espejada antes de
     * desplegar (el reflejo del torso no sirve: la media vuelta ya no mide 96).
     */
    private static ValorTira desplegar(String prenda, Caja caja, boolean simetria, ValorTira f) {
        if (!prenda.startsWith("pollera:") || caja != TORSO) return f;
        String[] partes = prenda.split(":");
        PolleraMalla.Perfil perfil = PolleraMalla.perfil(com.modamod.item.PolleraForma.valueOf(partes[1]),
                com.modamod.item.PolleraLargo.valueOf(partes[2]), partes.length > 3 ? Integer.parseInt(partes[3]) / 2f : 0f);
        int w = caja.x1() - caja.x0(), h = caja.alto();
        int centro = w / 3, mitad = w / 2;
        float alto = perfil.largoTela / perfil.vAlto;   // px de tela por fila de textura (2026-10-08: solo usa vAlto filas)
        return (lx, ly) -> {
            int d = Math.floorMod(lx - centro + mitad, w) - mitad;
            if (simetria && d >= 0) d = -1 - d;
            float q = Math.max(0f, Math.min(1f, (ly + 0.5f) / perfil.vAlto));
            float ancho = perfil.circunferenciaEn(q) / 24f;
            return f.valor(centro + Math.round(d * ancho), Math.round(ly * alto));
        };
    }

    private static NativeImage mascaraVacia() {
        NativeImage img = new NativeImage(LADO_ATLAS, LADO_ATLAS, true);
        for (int y = 0; y < LADO_ATLAS; y++) for (int x = 0; x < LADO_ATLAS; x++) img.setColor(x, y, 0);
        return img;
    }

    /**
     * Punto de entrada único para una capa (2026-09-28): rayas si el molde
     * es de rayas, motivo repetido si es de motivo. Para una capa LISA
     * devuelve la máscara de degradé si la necesita (Variación: Degradé
     * con más de un color), o null (= liso de un color, cubre todo).
     * También null si la prenda no tiene caja mapeada o el molde no
     * existe — para una capa con molde, quien llama la saltea.
     */
    @org.jetbrains.annotations.Nullable
    public static NativeImage mascaraDeCapa(String prenda, com.modamod.region.RegionResolver.CapaPatron capa) {
        if (capa.lisa()) {
            return capa.variacion() == Variacion.DEGRADE && !capa.extras().isEmpty() ? mascaraGradiente(prenda) : null;
        }
        com.modamod.item.ClothingPatternItem item = com.modamod.item.ClothingPatternItem.porId(capa.patronId());
        if (item == null) return null;
        DistribucionPatron dist = capa.distribucion() == null ? DistribucionPatron.DEFECTO : capa.distribucion();
        if (item.motivo != null) {
            return mascaraMotivo(prenda, item.motivo, item.grosorBase * capa.tamano().escala,
                    capa.repeticion(), capa.semilla(), capa.posicion(), capa.angulo(), dist);
        }
        // Rayas: la distancia que cuenta es la que cruza las rayas — 0° son
        // horizontales (se separan en vertical), 90° verticales (en horizontal).
        double rad = Math.toRadians(capa.angulo());
        double s2 = Math.sin(rad) * Math.sin(rad);
        float distancia = (float) (dist.distanciaV() * (1 - s2) + dist.distanciaH() * s2);
        return mascaraPara(prenda, capa.forma(), capa.angulo(), item.grosorBase, capa.tamano(), capa.posicion(),
                capa.semilla(), distancia, dist.simetria());
    }

    /** Capa lisa con degradé: cubre todas las cajas de la prenda, con la altura en el canal azul. */
    @org.jetbrains.annotations.Nullable
    public static NativeImage mascaraGradiente(String prenda) {
        List<Caja> cajas = cajasDe(prenda);
        if (cajas == null) return null;
        String key = prenda + "|gradiente";
        NativeImage cacheada = CACHE.get(key);
        if (cacheada != null) return cacheada;
        NativeImage img = mascaraVacia();
        for (Caja caja : cajas) {
            recorrer(caja, img, (lx, ly) -> pixelMascara(255, false, 0, 0, 0, ly, caja.alto()));
        }
        CACHE.put(key, img);
        return img;
    }

    /** Separación entre motivos: la celda mide esto por el tamaño del motivo. */
    private static final double MOTIVO_CELDA = 1.8;
    /** Cuánto más grande es el motivo en {@link Repeticion#UNICO} (logo) que en la grilla. */
    private static final double MOTIVO_UNICO = 2.5;
    /** En {@link Repeticion#DISPERSO}, fracción de celdas que quedan vacías — si no, se lee como grilla movida. */
    private static final double MOTIVO_VACIAS = 0.3;
    /** Período del vichy (franja + hueco) por unidad de escala. */
    private static final double VICHY_PERIODO = 8;
    /** Períodos de los otros cuadros (2026-10-22), misma unidad que el vichy. */
    private static final double TARTAN_PERIODO = 24, LENADOR_PERIODO = 20, PATA_DE_GALLO_PERIODO = 16, ARGYLE_PERIODO = 20,
            DIPLOMATICAS_PERIODO = 12, VENTANA_PERIODO = 24, ZIGZAG_PERIODO = 12;

    /**
     * Máscara de un {@link Motivo} repetido (Fase 1 de la propuesta de
     * patrones, 2026-09-28). {@code escala} = pixeles del atlas por pixel
     * del sprite (grosorBase del molde × tamaño). Canales: ver "Canales de
     * la máscara" — el contorno de un sprite es un pixel de sprite
     * alrededor del dibujo (escala con el motivo).
     *
     * <p>Horizontalmente la celda se ajusta para que entre una cantidad
     * ENTERA de celdas en el perímetro de las 4 caras: así el motivo que
     * cae en la costura de atrás sigue del otro lado sin cortarse.
     */
    public static NativeImage mascaraMotivo(String prenda, Motivo motivo, double escala,
                                            Repeticion repeticion, int semilla, float posicion) {
        return mascaraMotivo(prenda, motivo, escala, repeticion, semilla, posicion, 0f, DistribucionPatron.DEFECTO);
    }

    /**
     * Con giro de grilla ({@code anguloGrilla}, el ángulo de la capa) y
     * {@link DistribucionPatron} (giro de cada motivo y distancias) —
     * 2026-09-30, "que los patrones si se puedan girar y que haya un slider
     * vertical y horizontal para ponerlos mas juntos". Con la grilla girada
     * ya no entra una cantidad entera de columnas en el perímetro: en la
     * costura de atrás el dibujo puede no empalmar.
     */
    public static NativeImage mascaraMotivo(String prenda, Motivo motivo, double escala,
                                            Repeticion repeticion, int semilla, float posicion,
                                            float anguloGrilla, DistribucionPatron dist) {
        List<Caja> cajas = cajasDe(prenda);
        if (cajas == null) return null;
        double k = Math.max(0.75, Math.round(escala * 4) / 4.0);
        int posPaso = Math.round(Math.max(0f, Math.min(1f, posicion)) * 20);
        int grillaPaso = Math.floorMod(Math.round(anguloGrilla / 15f) * 15, 360);
        String key = prenda + "|motivo|" + motivo + "|" + k + "|" + repeticion + "|" + semilla + "|" + posPaso
                + "|" + grillaPaso + "|" + dist.clave();
        NativeImage cacheada = CACHE.get(key);
        if (cacheada != null) return cacheada;

        NativeImage img = mascaraVacia();
        int[] celda = new int[2];
        for (Caja caja : cajas) {
            int w = caja.x1() - caja.x0(), h = caja.alto();
            boolean espejar = caja == PIERNA_IZQ || caja == BRAZO_IZQ;
            int cuarto = w / 4;
            // Centro del FRENTE de esta caja: en brazo/pierna las 4 caras
            // miden lo mismo (frente = 2ª columna); el torso es der 32 |
            // frente 64 | izq 32 | atrás 64.
            double centroFrente = caja == TORSO ? 64 : cuarto * 1.5;
            boolean simetrica = dist.simetria() && caja == TORSO;
            recorrer(caja, img, desplegar(prenda, caja, simetrica, (lx, ly) -> {
                int u = espejar ? Math.floorMod(3 * cuarto - lx, w) : lx;
                if (simetrica) u = reflejoTorso(u, w);
                int rol = motivo.esAnimal()
                        ? coberturaAnimal(motivo, u, ly, w, h, k, semilla, posPaso / 20.0, centroFrente, grillaPaso, dist, celda)
                        : motivo.esProcedural()
                        ? coberturaCuadros(motivo, u, ly, w, h, k, posPaso / 20.0, centroFrente, grillaPaso, dist, celda)
                        : rolSprite(motivo, repeticion, u + 0.5, ly + 0.5, w, h, k, semilla,
                                posPaso / 20.0, centroFrente, grillaPaso, dist, celda);
                int cob = rol == ROL_NADA ? 0 : rol == ROL_MEDIO ? 128 : 255;
                return pixelMascara(cob, rol == ROL_CONTORNO, celda[0], celda[1], semilla, ly, h);
            }));
        }
        CACHE.put(key, img);
        return img;
    }

    private static final int ROL_NADA = 0, ROL_RELLENO = 1, ROL_CONTORNO = 2, ROL_MEDIO = 3;

    /**
     * Qué es este pixel dentro del motivo, y de qué celda (en {@code celda}:
     * columna, fila). Mira la celda del pixel y sus 8 vecinas: con los
     * motivos girados o con poca distancia (2026-09-30) un motivo se sale de
     * su celda, y sin mirar las vecinas quedaría cortado en el borde.
     */
    private static int rolSprite(Motivo m, Repeticion rep, double u, double v, int w, int h,
                                 double k, int semilla, double posicion, double centroFrente,
                                 int anguloGrilla, DistribucionPatron dist, int[] celda) {
        double giro = Math.toRadians(dist.giroMotivo());
        double anchoM = m.ancho * k, altoM = m.alto * k;
        if (rep == Repeticion.UNICO) {
            celda[0] = 0;
            celda[1] = 0;
            double kk = k * MOTIVO_UNICO;
            // El logo gira con los dos ángulos (no hay grilla que girar aparte).
            return rolEn(m, u - centroFrente, v - posicion * h, giro + Math.toRadians(anguloGrilla), kk,
                    false, dist.espejo());
        }
        double tam = Math.max(anchoM, altoM) * MOTIVO_CELDA;
        double celdaY = tam * dist.distanciaV();
        double celdaX;
        int columnas;
        double gu, gv;
        if (anguloGrilla == 0) {
            // Grilla derecha: entra una cantidad entera de columnas en el
            // perímetro (el motivo de la costura de atrás sigue del otro lado).
            columnas = Math.max(1, (int) Math.round(w / (tam * dist.distanciaH())));
            celdaX = (double) w / columnas;
            gu = u;
            gv = v;
        } else {
            columnas = 0;
            celdaX = tam * dist.distanciaH();
            double a = -Math.toRadians(anguloGrilla), cos = Math.cos(a), sin = Math.sin(a);
            double du = u - centroFrente, dv = v - h / 2.0;
            gu = du * cos - dv * sin + centroFrente;
            gv = du * sin + dv * cos + h / 2.0;
        }
        // Posición corre la grilla en vertical (0.5 = sin corrimiento); X, en horizontal
        // (2026-10-12): la grilla es periódica en el perímetro, así que no deja costura.
        gv += (posicion - 0.5) * celdaY;
        gu += (dist.posX() - 0.5) * celdaX;

        int fila0 = (int) Math.floor(gv / celdaY);
        int resultado = ROL_NADA;
        for (int df = -1; df <= 1; df++) {
            int fila = fila0 + df;
            double corrimiento = rep == Repeticion.LADRILLO && Math.floorMod(fila, 2) == 1 ? celdaX / 2 : 0;
            int col0 = (int) Math.floor((gu - corrimiento) / celdaX);
            for (int dc = -1; dc <= 1; dc++) {
                int col = col0 + dc;
                int colId = columnas > 0 ? Math.floorMod(col, columnas) : col;
                double lu = gu - ((col + 0.5) * celdaX + corrimiento);
                double lv = gv - (fila + 0.5) * celdaY;
                if (rep == Repeticion.DISPERSO) {
                    long hash = mezclarHash(colId, fila, semilla);
                    if ((hash & 0xFF) / 255.0 < MOTIVO_VACIAS) continue;
                    double libreX = Math.max(0, (celdaX - anchoM) / 2 - k), libreY = Math.max(0, (celdaY - altoM) / 2 - k);
                    lu -= (((hash >> 8) & 0xFF) / 255.0 * 2 - 1) * libreX;
                    lv -= (((hash >> 16) & 0xFF) / 255.0 * 2 - 1) * libreY;
                }
                double giroCelda = giro;
                if (dist.giroAzar() != DistribucionPatron.GiroAzar.NINGUNO) {
                    long hAzar = mezclarHash(colId, fila, semilla + 7919);
                    giroCelda += dist.giroAzar().angulo(((hAzar >>> 4) & 0xFFFF) / 65536.0);
                }
                int rol = rolEn(m, lu, lv, giroCelda, k, dist.alternancia().espeja(colId, fila), dist.espejo());
                if (rol == ROL_RELLENO) {
                    celda[0] = colId;
                    celda[1] = fila;
                    return ROL_RELLENO;
                }
                if (rol == ROL_CONTORNO && resultado == ROL_NADA) {
                    resultado = ROL_CONTORNO;
                    celda[0] = colId;
                    celda[1] = fila;
                }
            }
        }
        if (resultado == ROL_NADA) {
            celda[0] = columnas > 0 ? Math.floorMod((int) Math.floor(gu / celdaX), columnas) : (int) Math.floor(gu / celdaX);
            celda[1] = fila0;
        }
        return resultado;
    }

    /**
     * Simetría del torso (2026-09-30): la mitad que va del centro del frente
     * (u 64, a escala 8) hasta el centro de la espalda (64 + w/2) toma el
     * pixel reflejado de la otra mitad — así el lado izquierdo es el espejo
     * del derecho, en el frente y en la espalda.
     */
    private static int reflejoTorso(int u, int w) {
        int centro = w / 3;          // der w/6 + medio frente w/6 (192: 32 + 32 = 64)
        int mitad = w / 2;
        if (u >= centro && u < centro + mitad) return Math.floorMod(2 * centro - 1 - u, w);
        return u;
    }

    /** Relleno / contorno / nada de un motivo centrado en (0,0), girado {@code giro} radianes, a escala {@code kk}. */
    private static int rolEn(Motivo m, double lu, double lv, double giro, double kk) {
        return rolEn(m, lu, lv, giro, kk, false, DistribucionPatron.Espejo.NINGUNO);
    }

    /**
     * Con espejos (2026-09-30): el motivo final es
     * {@code alternado( girado( espejado(motivo) ) )}, así que al pixel se le
     * aplica la inversa en el orden contrario — primero la alternancia (dar
     * vuelta el motivo YA girado: con Giro sale un zigzag), después el giro,
     * y al final el espejo propio del motivo.
     */
    private static int rolEn(Motivo m, double lu, double lv, double giro, double kk,
                             boolean alternado, DistribucionPatron.Espejo espejo) {
        if (alternado) lu = -lu;
        if (giro != 0) {
            double cos = Math.cos(-giro), sin = Math.sin(-giro);
            double ru = lu * cos - lv * sin, rv = lu * sin + lv * cos;
            lu = ru;
            lv = rv;
        }
        if (espejo.horizontal) lu = -lu;
        if (espejo.vertical) lv = -lv;
        int sx = (int) Math.floor(lu / kk + m.ancho / 2.0), sy = (int) Math.floor(lv / kk + m.alto / 2.0);
        if (m.pinta(sx, sy)) return ROL_RELLENO;
        // Contorno: un pixel de sprite alrededor del dibujo (8 vecinos).
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && m.pinta(sx + dx, sy + dy)) return ROL_CONTORNO;
            }
        }
        return ROL_NADA;
    }

    /**
     * Cuadros: la grilla (columnas justas en el perímetro, giro, posición, distancias) es la misma para todos; cada
     * motivo decide qué pinta en (fx, fy), la fracción 0..1 dentro de su cuadro. Cada cuadro es una "repetición".
     * <ul>
     * <li>Vichy: franjas horizontales y verticales del mismo color que se cruzan — 50% donde pasa una sola, 100% en
     * el cruce (así sale el cuadro de 3 tonos de verdad con un solo color de capa). Sin contorno.</li>
     * <li>Tartán (kilt): franja ancha, franja angosta y una línea fina; las franjas igual que el vichy (50% / 100% en
     * el cruce) y la línea es CONTORNO, o sea el último color con Contorno prendido.</li>
     * <li>Leñador (cuadro búfalo): vichy de cuadro grande con una línea fina de contorno en medio del hueco.</li>
     * <li>Pata de gallo: el tejido real, 4 hilos oscuros y 4 claros en las dos direcciones con sarga 2/2.</li>
     * <li>Argyle: un rombo por cuadro (más alto que ancho) y líneas finas de contorno paralelas a sus lados que lo
     * cruzan por el medio; con Alternar los rombos vecinos cambian de color.</li>
     * <li>Rayas diplomáticas: una línea fina vertical por cuadro (la de los trajes). Ventana: líneas finas en las dos
     * direcciones. Zigzag: franjas horizontales que suben y bajan en V, media altura de cuadro cada una.</li>
     * </ul>
     */
    private static int coberturaCuadros(Motivo m, int u, int v, int w, int h, double k, double posicion, double centroFrente,
                                        int anguloGrilla, DistribucionPatron dist, int[] celda) {
        double periodoBase = m == Motivo.TARTAN ? TARTAN_PERIODO : m == Motivo.LENADOR ? LENADOR_PERIODO
                : m == Motivo.PATA_DE_GALLO ? PATA_DE_GALLO_PERIODO : m == Motivo.ARGYLE ? ARGYLE_PERIODO : m == Motivo.DIPLOMATICAS ? DIPLOMATICAS_PERIODO
                : m == Motivo.VENTANA ? VENTANA_PERIODO : m == Motivo.ZIGZAG ? ZIGZAG_PERIODO : VICHY_PERIODO;
        double deseado = periodoBase * k * 2;
        double periodoY = deseado * dist.distanciaV() * (m == Motivo.ARGYLE ? 1.4 : 1);
        double periodoX, uu, vv;
        if (anguloGrilla == 0) {
            int columnas = Math.max(1, (int) Math.round(w / (deseado * dist.distanciaH())));
            periodoX = (double) w / columnas;
            uu = u;
            vv = v;
        } else {
            // Vichy en diagonal (2026-09-30): el cuadriculado gira alrededor del centro del frente.
            periodoX = deseado * dist.distanciaH();
            double a = -Math.toRadians(anguloGrilla), cos = Math.cos(a), sin = Math.sin(a);
            double du = u - centroFrente, dv = v - h / 2.0;
            uu = du * cos - dv * sin + centroFrente;
            vv = du * sin + dv * cos + h / 2.0;
        }
        vv += (posicion - 0.5) * periodoY;
        celda[0] = (int) Math.floor(uu / periodoX);
        celda[1] = (int) Math.floor(vv / periodoY);
        double fx = (((uu % periodoX) + periodoX) % periodoX) / periodoX;
        double fy = (((vv % periodoY) + periodoY) % periodoY) / periodoY;
        // Línea fina: 2 px del atlas por escala, nunca menos de 2 (un cuarto de pixel de skin).
        double lineaX = Math.max(2, 2 * k) / periodoX, lineaY = Math.max(2, 2 * k) / periodoY;
        switch (m) {
            case TARTAN -> {
                if (Math.abs(fx - 0.46) < lineaX / 2 || Math.abs(fy - 0.46) < lineaY / 2) return ROL_CONTORNO;
                int n = franjaTartan(fx) + franjaTartan(fy);
                return n == 2 ? ROL_RELLENO : n == 1 ? ROL_MEDIO : ROL_NADA;
            }
            case LENADOR -> {
                if (Math.abs(fx - 0.75) < lineaX / 2 || Math.abs(fy - 0.75) < lineaY / 2) return ROL_CONTORNO;
            }
            case PATA_DE_GALLO -> {
                int hx = Math.min(7, (int) (fx * 8)), hy = Math.min(7, (int) (fy * 8));
                boolean urdimbre = ((hx + hy) & 3) < 2;
                return (urdimbre ? hx < 4 : hy < 4) ? ROL_RELLENO : ROL_NADA;
            }
            case DIPLOMATICAS -> {
                return fx < lineaX ? ROL_RELLENO : ROL_NADA;
            }
            case VENTANA -> {
                return fx < lineaX || fy < lineaY ? ROL_RELLENO : ROL_NADA;
            }
            case ZIGZAG -> {
                double s = fy - Math.abs(2 * fx - 1) * 0.5;
                return s - Math.floor(s) < 0.5 ? ROL_RELLENO : ROL_NADA;
            }
            case ARGYLE -> {
                if (Math.abs(fx - fy) < lineaX / 2 || Math.abs(fx + fy - 1) < lineaX / 2) return ROL_CONTORNO;
                return Math.abs(fx - 0.5) + Math.abs(fy - 0.5) < 0.5 ? ROL_RELLENO : ROL_NADA;
            }
            default -> { }
        }
        boolean vertical = fx < 0.5;
        boolean horizontal = fy < 0.5;
        if (vertical && horizontal) return ROL_RELLENO;
        return vertical || horizontal ? ROL_MEDIO : ROL_NADA;
    }

    /** 1 si la fracción cae en una franja del tartán (ancha 0..0,30, angosta 0,62..0,74), 0 si no. */
    private static int franjaTartan(double f) {
        return f < 0.30 || (f >= 0.62 && f < 0.74) ? 1 : 0;
    }

    // ── Animal print (2026-10-19) ────────────────────────────────────────────────────────
    // Todo se calcula en "unidades de celda" (cx, cy): una celda cuadrada de ANIMAL_CELDA px por k. Con la grilla derecha
    // entran N celdas justas en el perímetro y el ruido es periodico en N, asi que el dibujo cierra en la costura de atras.

    private static final double ANIMAL_CELDA = 18;

    private static double hash01(int a, int b, int semilla) {
        return (mezclarHash(a, b, semilla) >>> 11 & 0xFFFFF) / 1048576.0;
    }

    /** Ruido de valor suave en [0,1], periodico en x cada {@code periodo} (0 = sin periodo). */
    private static double ruido(double x, double y, int periodo, int semilla) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        int xa = periodo > 0 ? Math.floorMod(x0, periodo) : x0, xb = periodo > 0 ? Math.floorMod(x0 + 1, periodo) : x0 + 1;
        double a = hash01(xa, y0, semilla), b = hash01(xb, y0, semilla), c = hash01(xa, y0 + 1, semilla), d = hash01(xb, y0 + 1, semilla);
        double arriba = a + (b - a) * fx, abajo = c + (d - c) * fx;
        return arriba + (abajo - arriba) * fy;
    }

    /**
     * Rol del pixel en un animal print: RELLENO = color 1 de la capa, CONTORNO = color 2, NADA = tela. {@code celda}
     * lleva la celda del pixel (para Variación Alternar/Aleatorio).
     */
    private static int coberturaAnimal(Motivo m, int u, int v, int w, int h, double k, int semilla, double posicion,
                                       double centroFrente, int anguloGrilla, DistribucionPatron dist, int[] celda) {
        double base = ANIMAL_CELDA * k * (m == Motivo.VACA ? 1.7 : m == Motivo.JIRAFA ? 1.5 : m == Motivo.SERPIENTE ? 0.9 : m == Motivo.DALMATA ? 0.8
                : m == Motivo.CAMUFLAJE || m == Motivo.CAMUFLAJE_DIGITAL ? 1.4 : 1.0);
        double up = u, vp = v;
        if (m == Motivo.CAMUFLAJE_DIGITAL) {
            // Cuadraditos de 1 pixel de skin por escala (8 px del atlas con Tamaño normal), múltiplos de 4.
            double q = Math.max(4, 4 * Math.round(2 * k));
            up = (Math.floor(u / q) + 0.5) * q;
            vp = (Math.floor(v / q) + 0.5) * q;
        }
        double celdaY = base * dist.distanciaV(), celdaX, uu, vv;
        int columnas;
        if (anguloGrilla == 0) {
            columnas = Math.max(1, (int) Math.round(w / (base * dist.distanciaH())));
            celdaX = (double) w / columnas;
            uu = up;
            vv = vp;
        } else {
            columnas = 0;
            celdaX = base * dist.distanciaH();
            double a = -Math.toRadians(anguloGrilla), cos = Math.cos(a), sin = Math.sin(a);
            double du = up - centroFrente, dv = vp - h / 2.0;
            uu = du * cos - dv * sin + centroFrente;
            vv = du * sin + dv * cos + h / 2.0;
        }
        vv += (posicion - 0.5) * celdaY;
        double cx = uu / celdaX, cy = vv / celdaY;
        int fila0 = (int) Math.floor(cy), col0 = (int) Math.floor(cx);
        celda[0] = columnas > 0 ? Math.floorMod(col0, columnas) : col0;
        celda[1] = fila0;
        return switch (m) {
            case LEOPARDO -> rosetas(cx, cy, columnas, semilla, celda);
            case CEBRA -> rayasAnimal(cx, cy, columnas, semilla, false);
            case TIGRE -> rayasAnimal(cx, cy, columnas, semilla, true);
            case JIRAFA -> parchesJirafa(cx, cy, columnas, semilla, celda);
            case SERPIENTE -> escamasSerpiente(cx, cy, columnas, semilla, celda);
            case DALMATA -> lunaresDalmata(cx, cy, columnas, semilla, celda);
            case CAMUFLAJE, CAMUFLAJE_DIGITAL -> camuflaje(cx, cy, columnas, semilla);
            default -> parchesVaca(cx, cy, columnas, semilla);
        };
    }

    /**
     * Camuflaje (2026-10-22): dos ruidos suaves con algo de detalle; donde sube el primero, manchón de color 1; donde
     * sube el segundo (y no el primero), manchón de color 2 (CONTORNO, con Contorno prendido). El resto es la tela.
     */
    private static int camuflaje(double cx, double cy, int columnas, int semilla) {
        double a = ruido(cx, cy, columnas, semilla) + (ruido(cx * 2, cy * 2, columnas * 2, semilla + 7) - 0.5) * 0.45;
        if (a > 0.58) return ROL_RELLENO;
        double b = ruido(cx + 0.5, cy + 0.5, columnas, semilla + 3) + (ruido(cx * 2, cy * 2, columnas * 2, semilla + 13) - 0.5) * 0.45;
        return b > 0.6 ? ROL_CONTORNO : ROL_NADA;
    }

    /** Leopardo: una roseta (anillo abierto de color 1 con halo de color 2 adentro) por celda, con jitter y borde irregular. */
    private static int rosetas(double cx, double cy, int columnas, int semilla, int[] celda) {
        double px = cx + (ruido(cx * 3, cy * 3, columnas * 3, semilla + 11) - 0.5) * 0.35;
        double py = cy + (ruido(cx * 3 + 40, cy * 3, columnas * 3, semilla + 29) - 0.5) * 0.35;
        int fila0 = (int) Math.floor(cy), col0 = (int) Math.floor(cx);
        for (int df = -1; df <= 1; df++) {
            for (int dc = -1; dc <= 1; dc++) {
                int fila = fila0 + df, col = col0 + dc, colId = columnas > 0 ? Math.floorMod(col, columnas) : col;
                double jx = (hash01(colId, fila, semilla) - 0.5) * 0.35, jy = (hash01(colId, fila, semilla + 1) - 0.5) * 0.35;
                double dx = px - (col + 0.5 + jx), dy = py - (fila + 0.5 + jy);
                double r = 0.30 + 0.10 * hash01(colId, fila, semilla + 2);
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d >= r) continue;
                double hueco = hash01(colId, fila, semilla + 3) * 2 * Math.PI;
                double ang = Math.abs(Math.atan2(dy, dx) - hueco);
                ang = Math.min(ang, 2 * Math.PI - ang);
                celda[0] = colId;
                celda[1] = fila;
                if (d > 0.55 * r) return ang < 0.55 ? ROL_NADA : ROL_RELLENO;
                if (d > 0.30 * r) return ROL_CONTORNO;
                return ROL_NADA;
            }
        }
        return ROL_NADA;
    }

    /** Cebra (rayas anchas que se bifurcan) y tigre (rayas en pares, ondulantes y afiladas en las puntas, con halo de color 2). */
    private static int rayasAnimal(double cx, double cy, int columnas, int semilla, boolean tigre) {
        if (tigre) return rayasTigre(cx, cy, columnas, semilla);
        double s = cx + (ruido(cx, cy * 0.5, columnas, semilla) - 0.5) * 1.6 + (ruido(cx * 2, cy, columnas * 2, semilla + 5) - 0.5) * 0.5;
        double dist = Math.abs(s - Math.floor(s) - 0.5);          // 0 = eje de la raya, 0.5 = entre rayas
        double ancho = 0.20 + 0.14 * ruido(cx, cy, columnas, semilla + 9);
        return dist < ancho ? ROL_RELLENO : ROL_NADA;
    }

    /**
     * Tigre (2026-10-19, "tigre no la vi muy reconocible"): rayas CORTAS y ONDULADAS, en pares apenas desparejos, que se afinan
     * hasta terminar en punta (el ancho sale de una envolvente vertical) y con un halo ancho de color 2, como el pelaje.
     */
    private static int rayasTigre(double cx, double cy, int columnas, int semilla) {
        double s = cx + (ruido(cx, cy * 0.8, columnas, semilla) - 0.5) * 1.3 + (ruido(cx * 2, cy * 1.6, columnas * 2, semilla + 5) - 0.5) * 0.8;
        int banda = (int) Math.floor(s);
        double f = s - banda;
        int id = columnas > 0 ? Math.floorMod(banda, columnas) : banda;
        // Dos rayas por banda, cada una con su propio largo y grosor.
        double mejor = 0;
        int rol = ROL_NADA;
        for (int par = 0; par < 2; par++) {
            double centro = par == 0 ? 0.24 + 0.08 * hash01(id, 0, semilla + 21) : 0.72 + 0.08 * hash01(id, 1, semilla + 22);
            double envolvente = ruido(cx * 1.0 + par * 17, cy * 1.5, columnas, semilla + 31 + par);
            double largo = (envolvente - 0.30) / 0.70;                // <= 0 fuera de la raya, 1 en el medio: afila las puntas
            if (largo <= 0) continue;
            double ancho = (0.05 + 0.13 * hash01(id, 2 + par, semilla + 41)) * Math.min(1, largo * 1.6);
            double d = Math.abs(f - centro);
            if (d < ancho) return ROL_RELLENO;
            if (d < ancho + 0.07 && rol == ROL_NADA) rol = ROL_CONTORNO;
        }
        return rol;
    }

    /**
     * Jirafa (2026-10-19, "vamos con serpiente y jirafa"): parches poligonales (Voronoi con puntos corridos al azar) separados
     * por una veta fina de tela; el color 2 es una franja angosta pegada a la veta, por dentro del parche.
     */
    private static int parchesJirafa(double cx, double cy, int columnas, int semilla, int[] celda) {
        double px = cx + (ruido(cx * 2, cy * 2, columnas * 2, semilla + 51) - 0.5) * 0.30;
        double py = cy + (ruido(cx * 2 + 33, cy * 2, columnas * 2, semilla + 52) - 0.5) * 0.30;
        int fila0 = (int) Math.floor(py), col0 = (int) Math.floor(px);
        double d1 = 9, d2 = 9;
        int mejorCol = 0, mejorFila = 0;
        for (int df = -1; df <= 1; df++) {
            for (int dc = -1; dc <= 1; dc++) {
                int fila = fila0 + df, col = col0 + dc, colId = columnas > 0 ? Math.floorMod(col, columnas) : col;
                double dx = px - (col + 0.15 + 0.7 * hash01(colId, fila, semilla + 53));
                double dy = py - (fila + 0.15 + 0.7 * hash01(colId, fila, semilla + 54));
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < d1) { d2 = d1; d1 = d; mejorCol = colId; mejorFila = fila; } else if (d < d2) d2 = d;
            }
        }
        celda[0] = mejorCol;
        celda[1] = mejorFila;
        double borde = d2 - d1;                       // 0 sobre la veta entre dos parches
        if (borde < 0.09) return ROL_NADA;
        return borde < 0.17 ? ROL_CONTORNO : ROL_RELLENO;
    }

    /**
     * Serpiente: escamas redondas que se solapan, en filas a media altura y corridas media celda (el patron de "escamas de
     * pez"); gana la escama de la fila mas baja que cubre el pixel, asi se ve el borde de la de arriba. Borde = color 2;
     * relleno = color 1 en manchones suaves (no escama por escama), el resto queda de tela.
     */
    private static int escamasSerpiente(double cx, double cy, int columnas, int semilla, int[] celda) {
        int fila0 = (int) Math.floor(cy * 2);
        for (int j = fila0 + 1; j >= fila0 - 2; j--) {
            double corrimiento = Math.floorMod(j, 2) == 1 ? 0.5 : 0;
            int col = (int) Math.floor(cx - corrimiento);
            for (int dc = 0; dc <= 1; dc++) {
                int c = col + dc;
                double dx = cx - (c + 0.5 + corrimiento), dy = cy - (j * 0.5 + 0.5);
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d >= 0.5) continue;
                int colId = columnas > 0 ? Math.floorMod(c, columnas) : c;
                celda[0] = colId;
                celda[1] = j;
                if (d > 0.38) return ROL_CONTORNO;
                double tono = ruido(cx, cy, columnas, semilla + 60) + 0.35 * (hash01(colId, j, semilla + 61) - 0.5);
                return tono > 0.42 && dy < 0.22 ? ROL_RELLENO : ROL_NADA;
            }
        }
        return ROL_NADA;
    }

    /**
     * Dalmata (2026-10-19, "vamos con el dalmata"): lunares chicos de tamanos distintos, uno por celda con jitter, algunos
     * celdas vacias y borde irregular; sin halo (el color 2 solo marca un anillito finito en los mas grandes).
     */
    private static int lunaresDalmata(double cx, double cy, int columnas, int semilla, int[] celda) {
        double px = cx + (ruido(cx * 4, cy * 4, columnas * 4, semilla + 71) - 0.5) * 0.18;
        double py = cy + (ruido(cx * 4 + 29, cy * 4, columnas * 4, semilla + 72) - 0.5) * 0.18;
        int fila0 = (int) Math.floor(cy), col0 = (int) Math.floor(cx);
        for (int df = -1; df <= 1; df++) {
            for (int dc = -1; dc <= 1; dc++) {
                int fila = fila0 + df, col = col0 + dc, colId = columnas > 0 ? Math.floorMod(col, columnas) : col;
                if (hash01(colId, fila, semilla + 73) < 0.22) continue;                     // celda sin lunar
                double r = 0.10 + 0.22 * hash01(colId, fila, semilla + 74) * hash01(colId, fila, semilla + 75);
                r = Math.max(0.11, r * 2.1);
                double dx = px - (col + 0.2 + 0.6 * hash01(colId, fila, semilla + 76));
                double dy = py - (fila + 0.2 + 0.6 * hash01(colId, fila, semilla + 77));
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d >= r) continue;
                celda[0] = colId;
                celda[1] = fila;
                return r > 0.22 && d > r * 0.72 ? ROL_CONTORNO : ROL_RELLENO;
            }
        }
        return ROL_NADA;
    }

    /** Vaca: parches grandes (ruido umbralizado) con un borde fino de color 2. */
    private static int parchesVaca(double cx, double cy, int columnas, int semilla) {
        double n = ruido(cx, cy, columnas, semilla) * 0.7 + ruido(cx * 2, cy * 2, columnas * 2, semilla + 3) * 0.3;
        if (n > 0.52) return n < 0.545 ? ROL_CONTORNO : ROL_RELLENO;
        return ROL_NADA;
    }

    /** Hash entero chico y estable (celda + semilla) — Disperso/Aleatorio tienen que dar SIEMPRE lo mismo con la misma semilla. */
    private static long mezclarHash(int a, int b, int semilla) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + semilla * 0x165667B19E3779F9L;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        return h;
    }

}
