package com.modamod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Compositor genérico de texturas de prendas: lee un .png base (escala de
 * grises) y devuelve una versión teñida al color pedido, cacheada. Ya no
 * dependemos del tinte automático de "dyeable" de armadura (eso solo
 * existía en el pipeline de ArmorItem) — este es el mismo mecanismo a
 * mano para cualquier prenda que se dibuje vía BodyPartTrinketRenderer.
 */
public final class ClothingTextureCache {

    private static final Map<Identifier, NativeImage> BASE_IMAGE_CACHE = new HashMap<>();
    private static final Map<String, Identifier> TINTED_CACHE = new HashMap<>();

    /** Para el HUD de rendimiento: telas compuestas en caché, imágenes base y los MB de estas últimas. */
    public static double[] estadoCache() {
        long bytes = 0;
        for (NativeImage i : BASE_IMAGE_CACHE.values()) {
            if (i != null) bytes += (long) i.getWidth() * i.getHeight() * 4;
        }
        return new double[]{TINTED_CACHE.size(), BASE_IMAGE_CACHE.size(), bytes / 1048576.0};
    }

    /**
     * Volcado a disco de cada textura compuesta, para diagnosticar sin
     * depender de lo que se alcanza a ver en una captura de pantalla.
     * Prender a mano, probar en el juego, apagar — no queda prendido.
     */
    public static boolean DEBUG_DUMP = false;

    private ClothingTextureCache() {}

    /**
     * Vacía los dos caches (imagen cruda + composición final).
     *
     * Hace falta llamarlo en cada recarga de recursos: F3+T vuelve a leer
     * los .png del disco, pero estos mapas son estáticos y sobreviven esa
     * recarga solos — sin esto, cambiar un asset y F3+T seguía mostrando
     * la composición vieja (encontrado jugando, iterando la textura de
     * calientabrazos: "sigue igual" después de F3+T, hacía falta relanzar
     * el cliente entero para ver el cambio).
     */
    public static void limpiarCache() {
        // Hallazgo H07 (2026-10-08): antes solo se vaciaban los mapas y las texturas registradas quedaban vivas (y las
        // imágenes leídas de recursos sin cerrar). Cada textura compuesta se destruye en el TextureManager (cierra su
        // imagen y la memoria de GPU); las imágenes del resource pack, que solo esta caché posee, se cierran acá. Las
        // que llegan por registrarImagenCompuesta las posee su textura: no se tocan.
        var texturas = MinecraftClient.getInstance().getTextureManager();
        for (Identifier id : new java.util.HashSet<>(TINTED_CACHE.values())) texturas.destroyTexture(id);
        for (Identifier id : IMAGENES_PROPIAS) {
            NativeImage img = BASE_IMAGE_CACHE.get(id);
            if (img != null) img.close();
        }
        IMAGENES_PROPIAS.clear();
        BASE_IMAGE_CACHE.clear();
        TINTED_CACHE.clear();
    }

    /** Ids de {@link #BASE_IMAGE_CACHE} cuya imagen leyó {@link #imagenBase} del resource pack (las posee esta caché). */
    private static final java.util.Set<Identifier> IMAGENES_PROPIAS = new java.util.HashSet<>();

    private static void volcarADisco(String key, NativeImage img) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("modamod_debug");
            java.nio.file.Files.createDirectories(dir);
            String nombre = Integer.toHexString(key.hashCode()) + ".png";
            img.writeTo(dir.resolve(nombre));
            System.out.println("[modamod-debug] volcado " + dir.resolve(nombre) + " <- " + key);
        } catch (IOException e) {
            System.out.println("[modamod-debug] no se pudo volcar: " + e);
        }
    }

    /** Devuelve la textura base sin modificar (para prendas sin tinte, ej. maid_outfit). */
    public static Identifier plain(Identifier baseTexture) {
        return baseTexture;
    }

    /** Tiñe toda la textura base con un solo color (medias, shorts dyeable). */
    public static Identifier tinted(Identifier baseTexture, int rgb) {
        String key = baseTexture + "#" + Integer.toHexString(rgb);
        Identifier cached = TINTED_CACHE.get(key);
        if (cached != null) return cached;

        NativeImage base = imagenBase(baseTexture);
        if (base == null) return baseTexture;

        NativeImage composite = new NativeImage(base.getWidth(), base.getHeight(), true);
        int dr = (rgb >> 16) & 0xFF;
        int dg = (rgb >> 8) & 0xFF;
        int db = rgb & 0xFF;

        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int px = base.getColor(x, y);
                int a = (px >> 24) & 0xFF;
                if (a == 0) {
                    composite.setColor(x, y, 0);
                    continue;
                }
                int bChan = (px >> 16) & 0xFF;
                int gChan = (px >> 8) & 0xFF;
                int rChan = px & 0xFF;
                int tr = (rChan * dr) / 255;
                int tg = (gChan * dg) / 255;
                int tb = (bChan * db) / 255;
                composite.setColor(x, y, (a << 24) | (tb << 16) | (tg << 8) | tr);
            }
        }

        Identifier id = Identifier.of("modamod", "dynamic/tint_" + Integer.toHexString(key.hashCode()));
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new NativeImageBackedTexture(composite));
        TINTED_CACHE.put(key, id);
        return id;
    }

    /**
     * Tiñe una textura con UN color principal + rellena con OTRO color
     * (piel, o un segundo tinte) las zonas con alpha 0 de la base — usado
     * por el croptop (relleno de piel) y por prendas de dos tonos.
     */
    public static Identifier tintedWithFill(Identifier baseTexture, int mainRgb, int fillRgb) {
        String key = baseTexture + "#" + Integer.toHexString(mainRgb) + "#" + Integer.toHexString(fillRgb);
        Identifier cached = TINTED_CACHE.get(key);
        if (cached != null) return cached;

        NativeImage base = imagenBase(baseTexture);
        if (base == null) return baseTexture;

        NativeImage composite = new NativeImage(base.getWidth(), base.getHeight(), true);
        int dr = (mainRgb >> 16) & 0xFF, dg = (mainRgb >> 8) & 0xFF, db = mainRgb & 0xFF;

        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int px = base.getColor(x, y);
                int a = (px >> 24) & 0xFF;
                if (a == 0) {
                    composite.setColor(x, y, fillRgb);
                    continue;
                }
                int bChan = (px >> 16) & 0xFF, gChan = (px >> 8) & 0xFF, rChan = px & 0xFF;
                int tr = (rChan * dr) / 255, tg = (gChan * dg) / 255, tb = (bChan * db) / 255;
                composite.setColor(x, y, (a << 24) | (tb << 16) | (tg << 8) | tr);
            }
        }

        Identifier id = Identifier.of("modamod", "dynamic/tintfill_" + Integer.toHexString(key.hashCode()));
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new NativeImageBackedTexture(composite));
        TINTED_CACHE.put(key, id);
        return id;
    }

    private static final float FACE_LIGHT = 1.10F;
    private static final float FACE_DARK = 0.84F;
    /** Ancho en pixeles de las caras frontal y trasera de una pierna. */
    private static final int FACE_COLUMNS = 4 * CuerpoGeometria.ESCALA_TELA;

    /**
     * Factor de luz del pixel segun donde cae en el cuboide de la pierna.
     *
     * Las caras INTERIORES de las piernas no se ven con el jugador parado —
     * quedan una contra la otra — asi que sombrearlas no separaba nada. Lo
     * que si se ve es el BORDE interno de las caras frontal y trasera: una
     * columna de 1px oscura ahi hace de sombra entre las dos piernas.
     *
     * Que columna es el borde interno sale del constructor de ModelPart.Cuboid:
     * en cada Quad, vertices[0] toma u2 (columna derecha) y vertices[1] toma u1
     * (izquierda). En la cara NORTH vertices[0] es el vertice de maxX, y en la
     * SOUTH es el de minX — o sea la trasera esta espejada respecto de la
     * frontal, como corresponde a mirarla desde atras.
     *
     * El jugador tiene su derecha en -X (right_arm pivota en x=-5), asi que
     * para la pierna derecha el lado interno es +X y para la izquierda es -X.
     */
    private static float faceFactor(int x, int y, Shading shading) {
        final int S = CuerpoGeometria.ESCALA_TELA;
        final int face = 4 * S;   // ancho de una cara del cuboide, en pixeles

        if (shading == Shading.LEGS) {
            // Pierna derecha, uv(0, 16S): caras laterales en y 20S..32S-1, x 0..16S-1
            if (y >= 20 * S && y < 32 * S && x >= 0 && x < 16 * S) {
                int col = x / face;
                return switch (col) {
                    case 0 -> FACE_LIGHT;                       // WEST, exterior
                    case 1 -> ramp((2 * face - 1) - x);         // NORTH: interno en el borde derecho
                    case 2 -> FACE_DARK;                        // EAST, interior
                    default -> ramp(x - 3 * face);              // SOUTH: interno en el borde izquierdo
                };
            }
            // Pierna izquierda, uv(16S, 48S): caras laterales en y 52S..64S-1, x 16S..32S-1
            if (y >= 52 * S && y < 64 * S && x >= 16 * S && x < 32 * S) {
                int lx = x - 16 * S;
                int col = lx / face;
                return switch (col) {
                    case 0 -> FACE_DARK;                        // WEST, interior
                    case 1 -> ramp(lx - face);                  // NORTH: interno en el borde izquierdo
                    case 2 -> FACE_LIGHT;                       // EAST, exterior
                    default -> ramp((4 * face - 1) - lx);       // SOUTH: interno en el borde derecho
                };
            }
            return 1.0F;
        }

        if (shading == Shading.ARMS) {
            // Mismo mecanismo que LEGS -mismas proporciones de cuboide
            // (ancho=prof=4, alto=12)-, trasladado al UV de los brazos:
            // BRAZO_DER uv(40S,16S), BRAZO_IZQ uv(32S,48S) (ver CuerpoGeometria).
            // Brazo derecho: caras laterales en y 20S..32S-1, x 40S..56S-1
            if (y >= 20 * S && y < 32 * S && x >= 40 * S && x < 56 * S) {
                int lx = x - 40 * S;
                int col = lx / face;
                return switch (col) {
                    case 0 -> FACE_LIGHT;                       // WEST, exterior
                    case 1 -> ramp((2 * face - 1) - lx);        // NORTH: interno en el borde derecho
                    case 2 -> FACE_DARK;                        // EAST, interior
                    default -> ramp(lx - 3 * face);             // SOUTH: interno en el borde izquierdo
                };
            }
            // Brazo izquierdo: caras laterales en y 52S..64S-1, x 32S..48S-1
            if (y >= 52 * S && y < 64 * S && x >= 32 * S && x < 48 * S) {
                int lx = x - 32 * S;
                int col = lx / face;
                return switch (col) {
                    case 0 -> FACE_DARK;                        // WEST, interior
                    case 1 -> ramp(lx - face);                  // NORTH: interno en el borde izquierdo
                    case 2 -> FACE_LIGHT;                       // EAST, exterior
                    default -> ramp((4 * face - 1) - lx);       // SOUTH: interno en el borde derecho
                };
            }
            return 1.0F;
        }

        return 1.0F;
    }

    /**
     * Degrade a lo ancho de una cara frontal o trasera, de oscuro en el borde
     * interno a claro en el externo.
     *
     * Una sola columna oscura cortaba demasiado. Repartirlo en las 4 columnas
     * ademas se parece a como cae la luz en una pierna redonda: sombra del
     * lado que da a la otra pierna, luz del lado de afuera.
     *
     * @param fromInner 0 en el borde interno, 3 en el externo.
     */
    private static float ramp(int fromInner) {
        int span = FACE_COLUMNS - 1;
        float t = Math.min(Math.max(fromInner, 0), span) / (float) span;
        return FACE_DARK + (FACE_LIGHT - FACE_DARK) * t;
    }

    /** Aclara u oscurece un pixel ABGR sin tocarle el alpha. */
    private static int shade(int abgr, float f) {
        if (f == 1.0F) return abgr;
        int a = abgr & 0xFF000000;
        int r = Math.min(255, Math.round((abgr & 0xFF) * f));
        int g = Math.min(255, Math.round(((abgr >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round(((abgr >> 16) & 0xFF) * f));
        return a | (b << 16) | (g << 8) | r;
    }

    /**
     * Multiplica un pixel ABGR por un color RGB (el tinte estilo cuero).
     *
     * Publico porque EstampaTextures pinta patrones sobre remera con esta
     * misma cuenta: es matematica pura de pixeles, no hay motivo para
     * duplicarla en otro paquete.
     */
    public static int tintPixel(int px, int rgb) {
        int a = (px >> 24) & 0xFF;
        int bChan = (px >> 16) & 0xFF, gChan = (px >> 8) & 0xFF, rChan = px & 0xFF;
        int dr = (rgb >> 16) & 0xFF, dg = (rgb >> 8) & 0xFF, db = rgb & 0xFF;
        return (a << 24) | (((bChan * db) / 255) << 16) | (((gChan * dg) / 255) << 8) | ((rChan * dr) / 255);
    }

    /**
     * Algo que se pinta ENCIMA de la prenda ya compuesta.
     *
     * Existe para que la sublimadora pueda meter la foto sin que este cache
     * sepa nada de estampas: recibe la imagen terminada y la firma con la que
     * distinguirla de otra. Asi la dependencia va en un solo sentido.
     */
    public interface Encima {
        /** Parte de la clave: dos estampas distintas no comparten textura. */
        String clave();
        /** false si todavia no se pudo -foto sin bajar-, y entonces no se cachea. */
        boolean aplicar(NativeImage destino);
    }

    /**
     * Todo junto: base tenida + patron encima. Lo que la prenda no cubre
     * queda TRANSPARENTE.
     *
     * Esa transparencia es el contrato del sistema de capas. Hasta la fase 1
     * habia un parametro mas —un tono de piel con el que rellenar el hueco—
     * porque cada prenda tenia que reconstruir sola la pierna desnuda de
     * arriba. Rellenar era justo lo que hacia que dos prendas en la misma
     * pierna se taparan por completo: cada una pintaba la pierna entera. Ese
     * hueco ahora lo llena el cuerpo base, dibujado una sola vez abajo de
     * todo.
     *
     * mask puede ser null (prenda lisa) — a diferencia de {@code
     * baseTexture} no es un {@link Identifier} de recurso: viene YA
     * generada en memoria (§{@link PatronGenerador}), no hay un PNG por
     * patrón que cargar del disco.
     */
    public static Identifier composeGarment(Identifier baseTexture, int baseRgb,
                                            @Nullable NativeImage mask, int patternRgb,
                                            Shading shading) {
        return composeGarment(baseTexture, baseRgb, mask, patternRgb, shading, null);
    }

    public static Identifier composeGarment(Identifier baseTexture, int baseRgb,
                                            @Nullable NativeImage mask, int patternRgb,
                                            Shading shading,
                                            @Nullable Encima encima) {
        // La identidad de la mascara para la clave de cache es el propio
        // objeto (PatronGenerador cachea UNA instancia por combinación
        // prenda/forma/grosor, así que System.identityHashCode alcanza —
        // no hace falta volcar los 512x512 píxeles a una clave).
        String key = baseTexture + "#" + Integer.toHexString(baseRgb)
                + "@" + (mask == null ? "null" : System.identityHashCode(mask))
                + "#" + Integer.toHexString(patternRgb) + ":" + shading
                + (encima == null ? "" : "+" + encima.clave());
        Identifier cached = TINTED_CACHE.get(key);
        if (cached != null) return cached;

        NativeImage base = imagenBase(baseTexture);
        if (base == null) return baseTexture;

        NativeImage maskImg = mask;

        NativeImage composite = new NativeImage(base.getWidth(), base.getHeight(), true);
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                // El factor por cara se aplica a la TELA: si la prenda es un
                // color plano, las dos piernas se leen pegadas, sin volumen
                // ni separacion entre una y otra.
                float f = faceFactor(x, y, shading);
                if (maskImg != null && x < maskImg.getWidth() && y < maskImg.getHeight()) {
                    int maskPx = maskImg.getColor(x, y);
                    if (((maskPx >> 24) & 0xFF) != 0) {
                        composite.setColor(x, y, shade(tintPixel(maskPx, patternRgb), f));
                        continue;
                    }
                }
                int basePx = base.getColor(x, y);
                if (((basePx >> 24) & 0xFF) != 0) {
                    composite.setColor(x, y, shade(tintPixel(basePx, baseRgb), f));
                } else {
                    composite.setColor(x, y, 0);
                }
            }
        }

        // La estampa va DESPUES del tenido y del patron, igual que en una
        // sublimadora de verdad: la foto se imprime sobre la prenda terminada
        // y no se tine con ella.
        if (encima != null && !encima.aplicar(composite)) {
            composite.close();
            return baseTexture;
        }

        if (DEBUG_DUMP) volcarADisco(key, composite);

        composite = reducirSiHaceFalta(composite, encima != null, maskImg != null);

        Identifier id = Identifier.of("modamod", "dynamic/garment_" + Integer.toHexString(key.hashCode()));
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new NativeImageBackedTexture(composite));
        TINTED_CACHE.put(key, id);
        return id;
    }

    /**
     * Una capa a componer: su máscara (opaco = pinta) y CON QUÉ COLOR —
     * cada capa el suyo. {@code invertido} (2026-09-20, "invertir los
     * colores del patron") da vuelta esa regla: donde la máscara es
     * opaca queda el color BASE (como si no hubiera nada ahí) y donde es
     * transparente pinta {@code color} — un negativo, no un color nuevo.
     */
    /** True si (x,y) cae dentro de alguno de los rects — región de una capa restringida, o el perforado de red. */
    public static boolean dentroDeAlguno(java.util.List<CajaSkin.Rect> rects, int x, int y) {
        for (CajaSkin.Rect r : rects) if (r.contiene(x, y)) return true;
        return false;
    }

    /**
     * {@code mascara} null = capa LISA (2026-09-27, cuadradito de Tinturas
     * sin molde): cubre toda su región. {@code modo}/{@code opacidad} = cómo
     * se funde con lo de abajo, ver {@link #mezclar}.
     */
    public record CapaMascara(@Nullable NativeImage mascara, int color, boolean invertido,
                               @Nullable java.util.List<CajaSkin.Rect> region,
                               com.modamod.region.ModoMezcla modo, int opacidad,
                               int[] paleta, boolean contorno, com.modamod.render.Variacion variacion,
                               @Nullable NativeImage regionExtra, boolean regionInvertida) {
        public CapaMascara(@Nullable NativeImage mascara, int color, boolean invertido,
                           @Nullable java.util.List<CajaSkin.Rect> region,
                           com.modamod.region.ModoMezcla modo, int opacidad,
                           int[] paleta, boolean contorno, com.modamod.render.Variacion variacion) {
            this(mascara, color, invertido, region, modo, opacidad, paleta, contorno, variacion, null, false);
        }
        public CapaMascara(@Nullable NativeImage mascara, int color) { this(mascara, color, false, null); }
        public CapaMascara(@Nullable NativeImage mascara, int color, boolean invertido) { this(mascara, color, invertido, null); }
        public CapaMascara(@Nullable NativeImage mascara, int color, boolean invertido,
                           @Nullable java.util.List<CajaSkin.Rect> region) {
            this(mascara, color, invertido, region, com.modamod.region.ModoMezcla.NORMAL, 100);
        }
        /** Una sola tinta, sin contorno ni variación (todo lo de antes de la Fase 2). */
        public CapaMascara(@Nullable NativeImage mascara, int color, boolean invertido,
                           @Nullable java.util.List<CajaSkin.Rect> region,
                           com.modamod.region.ModoMezcla modo, int opacidad) {
            this(mascara, color, invertido, region, modo, opacidad, new int[]{color}, false, com.modamod.render.Variacion.FIJO);
        }

        /** Desde una capa ya resuelta, con su máscara y región. */
        public static CapaMascara de(com.modamod.region.RegionResolver.CapaPatron capa, @Nullable NativeImage mascara,
                                     @Nullable java.util.List<CajaSkin.Rect> region) {
            return de(capa, mascara, region, null);
        }

        /** Con una máscara de región extra (alfa > 0 = adentro), como el borde del escote del Cuello. */
        public static CapaMascara de(com.modamod.region.RegionResolver.CapaPatron capa, @Nullable NativeImage mascara,
                                     @Nullable java.util.List<CajaSkin.Rect> region, @Nullable NativeImage regionExtra) {
            return new CapaMascara(mascara, capa.color(), capa.invertido(), region, capa.modo(), capa.opacidad(),
                    capa.paleta(), capa.contorno(), capa.variacion(), regionExtra, capa.fueraDeRegion());
        }

        /** Para la clave de cache: todo lo que cambia el resultado además de máscara/región. */
        String claveColores() {
            return java.util.Arrays.toString(paleta) + (contorno ? "c" : "") + variacion.ordinal()
                    + (regionExtra == null ? "" : "r" + System.identityHashCode(regionExtra))
                    + (regionInvertida ? "x" : "");
        }

        /**
         * El color de esta capa en el pixel (x,y) — Fase 2 (2026-09-28): con
         * contorno prendido, el último color activo es el del contorno y el
         * relleno usa los anteriores; el relleno varía entre ellos según la
         * {@link com.modamod.render.Variacion}, leyendo el número de
         * repetición / valor al azar / altura que trae la máscara.
         */
        /**
         * ¿Pinta con transparencia a medias (canal T entre 1 y 254)? Una T
         * llena es un recorte y no necesita alfa real.
         */
        public boolean translucida() {
            if (parcial(color)) return true;
            if (paleta != null) for (int c : paleta) if (parcial(c)) return true;
            return false;
        }

        private static boolean parcial(int rgb) {
            int t = (rgb >>> 24) & 0xFF;
            return t > 0 && t < 255;
        }

        public int colorEn(int x, int y) {
            if (paleta.length <= 1) return color;
            int px = mascara == null || x >= mascara.getWidth() || y >= mascara.getHeight() ? 0 : mascara.getColor(x, y);
            if (contorno && mascara != null && PatronGenerador.esContorno(px)) return paleta[paleta.length - 1];
            int relleno = contorno ? paleta.length - 1 : paleta.length;
            if (relleno <= 1) return paleta[0];
            return switch (variacion) {
                case FIJO -> paleta[0];
                case ALTERNAR -> paleta[PatronGenerador.repeticion(px) % relleno];
                case ALEATORIO -> paleta[PatronGenerador.azar(px) % relleno];
                case DEGRADE -> degradar(paleta, relleno, PatronGenerador.altura(px) / 255f);
            };
        }

        /**
         * Cuánto pinta esta capa el pixel (x,y), 0..255: fuera de su
         * región 0; lisa 255; con patrón, el alfa de la máscara (o su
         * negativo). Las rayas solo dan 0 o 255; el vichy da 128 donde pasa
         * una sola franja (2026-09-28, motivos) — por eso es un número y no
         * un sí/no.
         */
        public int cobertura(int x, int y) {
            boolean adentro = (region == null || dentroDeAlguno(region, x, y))
                    && (regionExtra == null || (x < regionExtra.getWidth() && y < regionExtra.getHeight()
                        && ((regionExtra.getColor(x, y) >>> 24) & 0xFF) != 0));
            // Velo de "resto apagado" (Fase B, 2026-09-28): liso, todo lo de
            // AFUERA de la región — la zona resaltada queda como está.
            if (regionInvertida) return adentro ? 0 : 255;
            if (!adentro) return 0;
            if (mascara == null) return 255;
            if (x >= mascara.getWidth() || y >= mascara.getHeight()) return invertido ? 255 : 0;
            int px = mascara.getColor(x, y);
            int a = (px >>> 24) & 0xFF;
            // El contorno viene siempre en la máscara; si la capa no lo
            // tiene prendido, esos pixeles son fondo.
            if (!contorno && PatronGenerador.esContorno(px)) a = 0;
            return invertido ? 255 - a : a;
        }

        /** Opacidad EFECTIVA de la capa en ese pixel: su opacidad × la cobertura. */
        public int opacidadEn(int x, int y) {
            return opacidad * cobertura(x, y) / 255;
        }
    }

    /**
     * Funde una capa de color sobre lo que ya hay (2026-09-27, "controlar
     * como se mezclan las capas"). {@code abajo} es el pixel ABGR ya
     * compuesto; {@code tela} el pixel crudo de la textura base (gris con
     * su sombreado de tela) — NORMAL tiñe la tela con el color de la capa
     * en vez de pintarlo plano, así una capa lisa no borra el relieve.
     * Multiplicar/Superponer operan contra {@code abajo} con el color puro.
     * Después, {@code opacidad} (0..100) interpola entre abajo y el resultado.
     */
    public static int mezclar(int abajo, int tela, int rgb, com.modamod.region.ModoMezcla modo, int opacidad) {
        int a = abajo & 0xFF000000;
        int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
        int ar = abajo & 0xFF, ag = (abajo >> 8) & 0xFF, ab = (abajo >> 16) & 0xFF;
        int rr, rg, rb;
        switch (modo) {
            case MULTIPLICAR -> {
                rr = ar * cr / 255; rg = ag * cg / 255; rb = ab * cb / 255;
            }
            case SUPERPONER -> {
                rr = superponer(ar, cr); rg = superponer(ag, cg); rb = superponer(ab, cb);
            }
            default -> {
                int t = tintPixel(tela | 0xFF000000, rgb);
                rr = t & 0xFF; rg = (t >> 8) & 0xFF; rb = (t >> 16) & 0xFF;
            }
        }
        int o = Math.max(0, Math.min(100, opacidad));
        if (o < 100) {
            rr = ar + (rr - ar) * o / 100;
            rg = ag + (rg - ag) * o / 100;
            rb = ab + (rb - ab) * o / 100;
        }
        // Transparencia de la tinta (2026-09-28), en el byte ALTO de rgb (0 =
        // opaco): baja el alfa de la tela donde la capa pinta — al máximo es
        // un recorte. Un alfa intermedio lo resuelve después tramar().
        int t = (rgb >>> 24) & 0xFF;
        if (t > 0) {
            int alfa = (a >>> 24) & 0xFF;
            alfa = Math.round(alfa * (1f - (o / 100f) * (t / 255f)));
            a = alfa << 24;
        }
        return a | (rb << 16) | (rg << 8) | rr;
    }

    /**
     * Transparencia real (2026-10-01, "porque la transparencia no se ve una
     * transparencia real sino pixeles q van pasando de visible a
     * invisible"): una prenda con alguna capa a medias transparente se
     * compone con su alfa verdadero (sin {@link #tramar}, que mira esta
     * bandera mientras se compone: también lo usan las estampas y la banda
     * de cintura desde su {@link Encima}) y su textura queda anotada acá
     * para que el render la dibuje en modo translúcido
     * ({@link #capaDeRender}). Las demás siguen en recorte, como siempre.
     */
    private static boolean componiendoTranslucida = false;
    private static final java.util.Set<Identifier> TRANSLUCIDAS = new java.util.HashSet<>();

    /** La última tela pedida a {@link #capaDeRender}: {@code EfectoTrim} la lee en el {@code getBuffer} que viene justo después. */
    @org.jetbrains.annotations.Nullable
    public static Identifier ultimaTextura;

    public static boolean esTranslucida(Identifier textura) {
        return TRANSLUCIDAS.contains(textura);
    }

    /**
     * El RenderLayer de una tela: translúcido (alfa de verdad, ordenado) si
     * su textura tiene transparencia a medias, si no el recorte de siempre.
     */
    public static net.minecraft.client.render.RenderLayer capaDeRender(Identifier textura) {
        ultimaTextura = textura;   // para EfectoTrim: sabe qué tela se está por dibujar (2026-10-06)
        return esTranslucida(textura)
                ? net.minecraft.client.render.RenderLayer.getEntityTranslucent(textura)
                : net.minecraft.client.render.RenderLayer.getArmorCutoutNoCull(textura);
    }

    /** Matriz de Bayer 4x4 (umbrales 0..255) para el tramado de {@link #tramar}. */
    private static final int[] BAYER = {
            8, 136, 40, 168,
            200, 72, 232, 104,
            56, 184, 24, 152,
            248, 120, 216, 88};

    /**
     * Alfa intermedio → tela calada con un tramado fino (2026-09-28): la
     * ropa se dibuja en modo RECORTE (cada pixel es tela o nada), así que la
     * transparencia a medias se hace dejando una proporción de pixeles
     * abiertos según el alfa, repartidos con una matriz de Bayer. A la
     * escala de la tela (8 px por pixel de skin) se lee como tul o voile.
     * Alfa 0 o 255 pasan tal cual.
     */
    public static int tramar(int abgr, int x, int y) {
        int alfa = (abgr >>> 24) & 0xFF;
        if (alfa == 0 || alfa == 255 || componiendoTranslucida) return abgr;
        return alfa > BAYER[(y & 3) * 4 + (x & 3)] ? abgr | 0xFF000000 : 0;
    }

    /** Ancho del borde del escote que pinta el Cuello, en px del atlas (1 unidad de skin = 8 px). */
    private static final int CUELLO_BORDE_PX = 7;
    private static final java.util.Map<NativeImage, NativeImage> CACHE_BORDE_CUELLO = new java.util.WeakHashMap<>();

    /**
     * Máscara del borde del escote de una remera (2026-09-28, "cuello es
     * solo el borde del cuello segun el patron"): los pixeles de tela a
     * menos de {@link #CUELLO_BORDE_PX} de un pixel TRANSPARENTE del
     * recorte del cuello — en la tapa de arriba del torso y en las primeras
     * filas del frente y la espalda, cada cara por separado (mismo criterio
     * que {@link #perforarRed}: el vecino de otra cara no cuenta). Como sale
     * del alfa real de {@code base}, sigue la forma del molde de cuello
     * (redondo, en V...). Cacheada por imagen base.
     */
    public static NativeImage mascaraBordeCuello(NativeImage base) {
        NativeImage hecha = CACHE_BORDE_CUELLO.get(base);
        if (hecha != null) return hecha;
        int escala = CuerpoGeometria.ESCALA_TELA;
        CajaSkin torso = LayoutSkin.base(com.modamod.garment.Parte.TORSO, false).escalada(escala);
        NativeImage m = new NativeImage(base.getWidth(), base.getHeight(), true);
        for (int y = 0; y < m.getHeight(); y++) for (int x = 0; x < m.getWidth(); x++) m.setColor(x, y, 0);
        CajaSkin.Rect frente = torso.frente(), atras = torso.atras();
        int filas = 6 * escala;
        CajaSkin.Rect[] caras = {
                torso.arriba(),
                new CajaSkin.Rect(frente.x0(), frente.y0(), frente.x1(), frente.y0() + filas),
                new CajaSkin.Rect(atras.x0(), atras.y0(), atras.x1(), atras.y0() + filas)};
        for (CajaSkin.Rect c : caras) {
            int x0 = Math.max(0, c.x0()), y0 = Math.max(0, c.y0());
            int x1 = Math.min(base.getWidth(), c.x1()), y1 = Math.min(base.getHeight(), c.y1());
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    if (((base.getColor(x, y) >>> 24) & 0xFF) == 0) continue;
                    boolean cerca = false;
                    for (int dy = -CUELLO_BORDE_PX; dy <= CUELLO_BORDE_PX && !cerca; dy++) {
                        int ny = y + dy;
                        if (ny < y0 || ny >= y1) continue;
                        for (int dx = -CUELLO_BORDE_PX; dx <= CUELLO_BORDE_PX; dx++) {
                            int nx = x + dx;
                            if (nx < x0 || nx >= x1) continue;
                            if (((base.getColor(nx, ny) >>> 24) & 0xFF) == 0) { cerca = true; break; }
                        }
                    }
                    if (cerca) m.setColor(x, y, 0xFFFFFFFF);
                }
            }
        }
        CACHE_BORDE_CUELLO.put(base, m);
        return m;
    }

    private static final java.util.Map<NativeImage, java.util.Map<String, NativeImage>> CACHE_SOLAPA = new java.util.WeakHashMap<>();

    /**
     * Máscara de la franja de tela que cubre una solapa (2026-10-08, "tintar las solapas por su propio lado"): en la
     * cara del frente del torso, de cada lado, los pixeles a entre {@code e} y {@code e + w} del centro, con {@code e}
     * el borde real de la tela (el escote; con frente abierto, al menos 1 px) y {@code w} el ancho de ese tipo de
     * solapa a esa altura, el mismo perfil con el que {@code SolapaMalla} dibuja la malla. Cacheada por imagen y región.
     */
    public static NativeImage mascaraSolapa(NativeImage base, com.modamod.region.RegionPintura region) {
        java.util.Map<String, NativeImage> porRegion = CACHE_SOLAPA.computeIfAbsent(base, b -> new java.util.HashMap<>());
        NativeImage hecha = porRegion.get(region.clave);
        if (hecha != null) return hecha;
        int escala = CuerpoGeometria.ESCALA_TELA;
        CajaSkin.Rect frente = LayoutSkin.base(com.modamod.garment.Parte.TORSO, false).escalada(escala).frente();
        NativeImage m = new NativeImage(base.getWidth(), base.getHeight(), true);
        for (int y = 0; y < m.getHeight(); y++) for (int x = 0; x < m.getWidth(); x++) m.setColor(x, y, 0);
        float k = escala;
        float[] anchos = region.solapa() == null ? CuelloYCapucha.anchosCamisa() : CuelloYCapucha.anchosDe(region.solapa());
        int centro = frente.x0() + frente.x1() >> 1;
        for (int y = frente.y0(); y < Math.min(base.getHeight(), frente.y0() + Math.round(anchos.length * k)); y++) {
            float py = (y - frente.y0() + 0.5F) / k;
            float w = SolapaMalla.anchoEn(anchos, py);
            for (int lado = -1; lado <= 1; lado += 2) {
                // El borde de la tela de ESTE lado en esta fila: el primer pixel opaco yendo desde el centro.
                int borde = -1;
                for (int d = 0; d < (frente.x1() - frente.x0()) / 2; d++) {
                    int x = lado < 0 ? centro - 1 - d : centro + d;
                    if (((base.getColor(x, y) >>> 24) & 0xFF) >= 128) { borde = d; break; }
                }
                if (borde < 0) continue;
                float e = Math.max(borde / k, region.solapaAbierta() ? 1F : 0F);
                for (int d = Math.round(e * k); d < Math.round((e + w) * k); d++) {
                    int x = lado < 0 ? centro - 1 - d : centro + d;
                    if (x < frente.x0() || x >= frente.x1()) continue;
                    if (((base.getColor(x, y) >>> 24) & 0xFF) >= 128) m.setColor(x, y, 0xFFFFFFFF);
                }
            }
        }
        porRegion.put(region.clave, m);
        return m;
    }

    /** Interpola a lo largo de los primeros {@code n} colores de la paleta, t en 0..1. */
    private static int degradar(int[] paleta, int n, float t) {
        float pos = Math.max(0f, Math.min(1f, t)) * (n - 1);
        int i = Math.min(n - 2, (int) pos);
        float f = pos - i;
        int a = paleta[i], b = paleta[i + 1];
        int tr = Math.round(((a >>> 24) & 0xFF) * (1 - f) + ((b >>> 24) & 0xFF) * f);
        int r = Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return (tr << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int superponer(int abajo, int arriba) {
        return abajo < 128 ? 2 * abajo * arriba / 255 : 255 - 2 * (255 - abajo) * (255 - arriba) / 255;
    }

    /**
     * Como {@link #composeGarment(Identifier, int, NativeImage, int, Shading, Encima)}
     * pero con VARIAS capas apiladas, cada una con su propio color — a
     * pedido (2026-09-18, "para los tres patrones necesitaria orden y
     * cambio de color"). Se pintan EN ORDEN: la capa 0 primero, y cada
     * capa siguiente pinta ENCIMA de la anterior donde su máscara sea
     * opaca (no es una unión con un solo color — cada una gana su propio
     * pedazo, y donde se superponen gana la de más arriba).
     */
    public static Identifier composeGarmentCapas(Identifier baseTexture, int baseRgb,
                                                 java.util.List<CapaMascara> capas,
                                                 Shading shading,
                                                 @Nullable Encima encima) {
        return composeGarmentCapas(baseTexture, baseRgb, capas, shading, encima, false);
    }

    /**
     * {@code resolucionCompleta}: no achica la tela ya compuesta (pollera,
     * 2026-10-07, "agrandame la resolucion de las polleras, los motivos quedan
     * mal": con patrón se achicaba a 4x y el motivo salía borroso).
     */
    public static Identifier composeGarmentCapas(Identifier baseTexture, int baseRgb,
                                                 java.util.List<CapaMascara> capas,
                                                 Shading shading,
                                                 @Nullable Encima encima, boolean resolucionCompleta) {
        long t0 = com.modamod.render.Perf.ini();
        try {
            return composeGarmentCapas0(baseTexture, baseRgb, capas, shading, encima, resolucionCompleta);
        } finally {
            com.modamod.render.Perf.fin(com.modamod.render.Perf.Seccion.TEXTURAS, t0);
        }
    }

    public static Identifier composeGarmentCapas0(Identifier baseTexture, int baseRgb,
                                                 java.util.List<CapaMascara> capas,
                                                 Shading shading,
                                                 @Nullable Encima encima, boolean resolucionCompleta) {
        StringBuilder capasKey = new StringBuilder();
        for (CapaMascara c : capas) {
            // invertido tiene que estar en la clave — bug real (2026-09-21,
            // "invertir no funciona"): sin esto dos capas con la misma
            // máscara/color pero invertido distinto pegan la MISMA clave,
            // y la segunda llamada reusaba del cache la textura SIN
            // invertir de la primera en vez de recomponer.
            capasKey.append('|').append(c.mascara() == null ? "null" : System.identityHashCode(c.mascara()))
                    .append('#').append(Integer.toHexString(c.color()))
                    .append(c.invertido() ? "!" : "")
                    // Modo/opacidad también cambian el resultado (2026-09-27),
                    // y la paleta/contorno/variación (2026-09-28).
                    .append('~').append(c.modo().ordinal()).append('%').append(c.opacidad())
                    .append('&').append(c.claveColores());
            // Región tiene que estar en la clave igual que invertido — dos
            // capas con la misma máscara/color pero región distinta (o sin
            // región) no pueden pegar la misma clave de cache.
            if (c.region() != null) for (CajaSkin.Rect r : c.region()) {
                capasKey.append('@').append(r.x0()).append(',').append(r.y0())
                        .append(',').append(r.x1()).append(',').append(r.y1());
            }
        }
        String key = baseTexture + "#" + Integer.toHexString(baseRgb)
                + "@capas" + capasKey + ":" + shading
                + (encima == null ? "" : "+" + encima.clave())
                + (resolucionCompleta ? "^full" : "");
        Identifier cached = TINTED_CACHE.get(key);
        if (cached != null) return cached;

        NativeImage base = imagenBase(baseTexture);
        if (base == null) return baseTexture;

        // Cualquier capa (con máscara o lisa recortada a una región) pide
        // la resolución completa — una región lisa también tiene bordes.
        boolean hayAlgunaMascara = !capas.isEmpty();
        boolean translucida = false;
        for (CapaMascara c : capas) translucida |= c.translucida();
        NativeImage composite = new NativeImage(base.getWidth(), base.getHeight(), true);
        componiendoTranslucida = translucida;
        try {
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                float f = faceFactor(x, y, shading);
                int basePx = base.getColor(x, y);
                // Sin tela de la prenda en este pixel, nunca hay nada que
                // pintar — bug real (2026-09-21, "el patron se esta
                // escapando de la mascara de la prenda"): antes esto se
                // chequeaba DESPUÉS de decidir si el patrón pintaba, así
                // que un patrón cuya máscara fuera opaca ahí (algo normal,
                // ninguna Forma sabe dónde corta la prenda de verdad)
                // terminaba pintando sobre el hueco igual. Con el patrón
                // normal casi no se notaba (las bandas rara vez caen justo
                // en un hueco), pero invertido pinta "todo menos la banda"
                // — un área mucho más grande — y ahí sí se escapaba bien
                // visible por fuera de la silueta real.
                if (((basePx >> 24) & 0xFF) == 0) {
                    composite.setColor(x, y, 0);
                    continue;
                }
                // Ya se sabe que basePx tiene alfa (el chequeo de arriba
                // ya cortó el caso contrario) — siempre hay tela acá.
                int acumulado = tintPixel(basePx, baseRgb);
                // Capas en orden, cada una FUNDIDA sobre lo de abajo con su
                // modo y opacidad (2026-09-27, "controlar como se mezclan
                // las capas") — antes la última opaca simplemente ganaba.
                // Región, máscara e invertido: ver CapaMascara#cubre.
                for (CapaMascara capa : capas) {
                    int op = capa.opacidadEn(x, y);
                    if (op <= 0) continue;
                    acumulado = mezclar(acumulado, basePx, capa.colorEn(x, y), capa.modo(), op);
                }
                composite.setColor(x, y, tramar(shade(acumulado, f), x, y));
            }
        }

        if (encima != null && !encima.aplicar(composite)) {
            composite.close();
            return baseTexture;
        }
        } finally {
            componiendoTranslucida = false;
        }

        if (DEBUG_DUMP) volcarADisco(key, composite);

        // Translúcida: sin achicar — el achique mezcla los bordes con el
        // negro de los huecos y en modo translúcido se vería un halo oscuro.
        if (!translucida && !resolucionCompleta) composite = reducirSiHaceFalta(composite, encima != null, hayAlgunaMascara);

        Identifier id = Identifier.of("modamod", "dynamic/garment_" + Integer.toHexString(key.hashCode()));
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new NativeImageBackedTexture(composite));
        if (translucida) TRANSLUCIDAS.add(id);
        else TRANSLUCIDAS.remove(id);
        TINTED_CACHE.put(key, id);
        return id;
    }

    /**
     * Achica la imagen ya compuesta si no necesita toda su resolucion nativa.
     *
     * La composicion (tenido, patron, estampa) siempre corre a la resolucion
     * NATIVA de la textura base (`ESCALA_TELA`, 8x): tocar esa matematica por
     * el ahorro de memoria arriesgaria el sombreado y el recorte de la
     * estampa por nada, ya que estampar es justo el caso que SI necesita esa
     * resolucion. Achicar la imagen DESPUES de componerla, en vez de componer
     * a menos resolucion desde el principio, deja fuera de discusion romper
     * esa matematica.
     *
     * Sin foto y sin patron la prenda es un color plano: 2x. Con patron pero
     * sin foto, un poco mas de detalle para el borde del patron: 4x. Con
     * foto, la resolucion nativa completa — es la unica que de verdad la
     * necesita.
     *
     * `resizeSubRectTo` es de vainilla (usa STBIR, no vecino-mas-cercano), asi
     * que el degrade de {@link #faceFactor} sobrevive el achique, mezclado en
     * vez de cortado en bandas.
     */
    private static NativeImage reducirSiHaceFalta(NativeImage composite, boolean estampada, boolean tienePatron) {
        if (estampada) return composite;

        int escalaDestino = tienePatron ? CuerpoGeometria.ESCALA_TELA_PATRON : CuerpoGeometria.ESCALA_TELA_LISA;
        float factor = escalaDestino / (float) CuerpoGeometria.ESCALA_TELA;
        int anchoDestino = Math.max(1, Math.round(composite.getWidth() * factor));
        int altoDestino = Math.max(1, Math.round(composite.getHeight() * factor));
        if (anchoDestino >= composite.getWidth() && altoDestino >= composite.getHeight()) return composite;

        NativeImage reducida = new NativeImage(anchoDestino, altoDestino, true);
        composite.resizeSubRectTo(0, 0, composite.getWidth(), composite.getHeight(), reducida);
        composite.close();
        return reducida;
    }

    /**
     * Ancho en pixeles del refuerzo sólido en los bordes de una pieza de
     * red (cuello, mangas, puños, dobladillo) antes de que empiecen los
     * agujeros — a pedido (2026-09-20), "3-4 píxeles, más marcada".
     */
    private static final int RED_BORDE_PX = 4;

    /**
     * Perfora {@code img} en un enrejado tipo red/fishnet en TODO el
     * desdoblado de {@code parte} (frente, atrás, costados Y tapas — un
     * costado o una tapa NO son un borde real, son geometría continua del
     * cuboide) — a pedido (2026-09-20). {@code filaDesde}/{@code filaHasta}
     * son el rango [0,12) de filas VISIBLES de tela en la banda de caras
     * (las mismas unidades que {@code PiezasDelMod#recortarFilas}, ej.
     * {@code MediasLargo#filasVisibles}) — el único borde real que se
     * refuerza es ESE, el corte de verdad (dobladillo/puño/cintura/
     * ruedo), calculado en base a la máscara de corte vigente y no
     * "adivinado" mirando alpha a ciegas.
     *
     * <p>Historia (jugando, 2026-09-20):
     * <ol>
     *   <li>1ra versión: restringía el agujereado a solo frente/atrás a
     *   mano → costados/tapas quedaban siempre sólidos, mal.</li>
     *   <li>2da versión: agujereaba TODO el desdoblado pero el borde salía
     *   de "¿hay algún vecino transparente en la imagen ENTERA?" → tapas
     *   (4px de alto) y los bordes exteriores de la caja quedaban
     *   siempre "cerca" de contenido transparente de OTRA parte del atlas
     *   sin relación real, así que volvían a salir sólidas por accidente
     *   ("me volviste a pintar los bordes de las tapas y del costado").</li>
     *   <li>Esta versión: el vecino transparente solo cuenta si cae DENTRO
     *   del propio desdoblado de esta parte (nunca mira atlas ajeno), MÁS
     *   un borde virtual explícito en filaDesde/filaHasta — así cubre
     *   tanto los cortes que SÍ dejan alpha real (medias, manga, pantalón,
     *   calientabrazos, todos vía {@code recortarFilas} antes de esto)
     *   como el de remera (el largo de torso recorta por GEOMETRÍA de la
     *   Pieza, nunca por alpha, así que sin este borde virtual el
     *   dobladillo quedaba sin reforzar).</li>
     *   <li>2026-09-20, jugando de nuevo: "me seguis haciendo bordes en la
     *   tapa superior de remeras y la inferior de medias" — la versión de
     *   arriba trataba TODO el desdoblado (las 6 caras juntas) como una
     *   sola región para "¿hay un vecino transparente cerca?". El
     *   problema: la tapa de ARRIBA (cintura/sisa) y la de ABAJO (planta/
     *   ruedo) están pegadas en el archivo por pura conveniencia de
     *   empaquetado del formato de skin — en 3D son superficies
     *   completamente DISTINTAS y sin relación (la planta de una media no
     *   tiene nada que ver con la cintura, aunque compartan un borde en
     *   el atlas). Un agujero de verdad en una (ej. la cintura abierta)
     *   reforzaba de arrastre la otra (la planta, sólida de punta a
     *   punta) solo por estar dibujadas una al lado de la otra. Esta
     *   versión procesa las 6 caras (arriba/abajo/derecha/frente/
     *   izquierda/atras) COMPLETAMENTE POR SEPARADO: el vecino
     *   transparente de una nunca cuenta para la de al lado.</li>
     * </ol>
     *
     * <p>El corte de cuello (una silueta curva, no una fila recta) sigue
     * saliendo solo del alpha real de la textura base, ahora acotado a la
     * cara donde vive de verdad — eso no cambió en espíritu.
     *
     * <p><b>Importante:</b> llamar esto DESPUÉS de cualquier recorte real
     * (filas, manga) — ver el aviso de más abajo sobre el orden.
     */
    public static void perforarRed(NativeImage img, com.modamod.item.PatronRed tipo,
                                    com.modamod.garment.Parte parte, int filaDesde, int filaHasta) {
        // LayoutSkin.base(parte,false) sin escalar todavía, para leer el
        // alto REAL del cuboide (12 filas siempre, pero mejor no
        // hardcodearlo) — filaDesde/filaHasta en ese extremo natural NO es
        // un corte de verdad (ej. media que cubre desde arriba del todo:
        // fila 0 es donde el cuboide de la pierna empalma con la cadera,
        // no un dobladillo), así que no lleva refuerzo ahí.
        int altoTotal = LayoutSkin.base(parte, false).alto();
        int escala = CuerpoGeometria.ESCALA_TELA;
        CajaSkin caja = LayoutSkin.base(parte, false).escalada(escala);
        CajaSkin.Rect caras = caja.caras();
        int yDesde = filaDesde > 0 ? caras.y0() + filaDesde * escala : Integer.MIN_VALUE;
        int yHasta = filaHasta < altoTotal ? caras.y0() + filaHasta * escala : Integer.MAX_VALUE;

        // Las 4 caras "de siempre" SÍ llevan el borde virtual de
        // filaDesde/filaHasta (el corte real de manga/media/pantalón cae
        // ahí); las 2 tapas (arriba/abajo) nunca lo llevan — no tienen
        // filas, y el único borde que les toca es el de su propio alpha
        // real (ej. el cuello, si vive en la tapa de arriba).
        // Rol de arnés de cada cara (2026-09-28, "que haya tiras en los
        // hombros que siempre empalmen"): en el TORSO, frente y espalda
        // arrancan sus tiras de las anclas de hombro y la tapa de arriba
        // las cruza — ver ARNES_ANCLA_1/2.
        boolean torso = parte == com.modamod.garment.Parte.TORSO;
        CajaSkin.Rect[] costados = {caja.derecha(), caja.frente(), caja.izquierda(), caja.atras()};
        for (int i = 0; i < costados.length; i++) {
            CajaSkin.Rect cara = costados[i];
            int rol = torso && (i == 1 || i == 3) ? ARNES_ROL_TORSO : ARNES_ROL_COMUN;
            perforarRedEnCara(img, tipo, cara.x0(), cara.y0(), cara.x1(), cara.y1(), true, yDesde, yHasta, rol);
        }
        CajaSkin.Rect arriba = caja.arriba(), abajo = caja.abajo();
        boolean brazo = parte == com.modamod.garment.Parte.BRAZO_DER || parte == com.modamod.garment.Parte.BRAZO_IZQ;
        if (tipo.esArnes() && brazo && filaDesde == 0) {
            // Va DESPUÉS de los costados: lee cómo quedaron sus tiras.
            continuarTirasEnHombro(img, tipo, caja);
        } else {
            perforarRedEnCara(img, tipo, arriba.x0(), arriba.y0(), arriba.x1(), arriba.y1(),
                    false, Integer.MIN_VALUE, Integer.MAX_VALUE, torso ? ARNES_ROL_HOMBROS : ARNES_ROL_COMUN);
        }
        perforarRedEnCara(img, tipo, abajo.x0(), abajo.y0(), abajo.x1(), abajo.y1(),
                false, Integer.MIN_VALUE, Integer.MAX_VALUE, ARNES_ROL_COMUN);
    }

    private static void perforarRedEnCara(NativeImage img, com.modamod.item.PatronRed tipo,
                                     int x0, int y0, int x1, int y1,
                                     boolean llevaCorteVirtual, int yDesde, int yHasta, int rolArnes) {
        if (tipo.esMaterial()) {
            MaterialTela.aplicar(img, tipo, x0, y0, x1, y1);
            return;
        }
        int w = img.getWidth(), h = img.getHeight();
        x0 = Math.max(0, x0); y0 = Math.max(0, y0);
        x1 = Math.min(w, x1); y1 = Math.min(h, y1);

        boolean[][] esBorde = new boolean[x1 - x0][y1 - y0];
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (((img.getColor(x, y) >> 24) & 0xFF) == 0) continue;
                boolean cercaCorteVirtual = llevaCorteVirtual
                        && ((yDesde != Integer.MIN_VALUE && Math.abs(y - yDesde) < RED_BORDE_PX)
                            || (yHasta != Integer.MAX_VALUE && Math.abs(y - yHasta) < RED_BORDE_PX));
                // x0,y0,x1,y1 acá son los bordes de ESTA cara sola — el
                // vecino transparente de la cara de al lado (pegada en el
                // atlas pero sin relación en 3D) nunca entra en juego.
                esBorde[x - x0][y - y0] = cercaCorteVirtual || cercaDeBorde(img, x, y, x0, y0, x1, y1);
            }
        }
        if (tipo.esArnes()) {
            perforarArnes(img, tipo, x0, y0, x1, y1, esBorde, llevaCorteVirtual, yDesde, yHasta, rolArnes);
            return;
        }
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (esBorde[x - x0][y - y0]) continue;
                if (((img.getColor(x, y) >> 24) & 0xFF) == 0) continue;
                if (esHilo(x, y, tipo)) continue;
                img.setColor(x, y, 0);
            }
        }
    }

    /** Ancho de cada tira del arnés, en pixeles — mismo que el refuerzo de borde, así tiras y borde se leen como el mismo cuero. */
    private static final int ARNES_TIRA_PX = RED_BORDE_PX;
    /** Radios del anillo metálico (afuera / agujero del medio). */
    private static final double ARNES_ANILLO_EXT = 5.2, ARNES_ANILLO_INT = 2.2;
    /** Plateado fijo de los anillos — no se tiñe con la prenda (2026-09-28, "metal plateado"). */
    private static final int ARNES_METAL_R = 200, ARNES_METAL_G = 204, ARNES_METAL_B = 212;
    /** Menos que esto de alto visible y la cara no lleva tiras internas, solo el borde (no entran). */
    private static final int ARNES_ALTO_MINIMO = 12;
    /**
     * Anclas de hombro, en fracción del ancho del torso: por acá cruzan las
     * tiras la tapa de arriba, y de acá arrancan las del frente y la
     * espalda. Simétricas A PROPÓSITO: la espalda está espejada en el
     * atlas (su x=0 es el lado derecho visto de frente), así que el 28%
     * de la espalda cae justo sobre el 72% de la tapa y viceversa — con
     * anclas simétricas empalman siempre, sin tener que espejar nada.
     */
    private static final double ARNES_ANCLA_1 = 0.28, ARNES_ANCLA_2 = 0.72;
    /** Qué hace el arnés en cada cara: común (extremidades, costados), frente/espalda del torso, tapa de hombros. */
    private static final int ARNES_ROL_COMUN = 0, ARNES_ROL_TORSO = 1, ARNES_ROL_HOMBROS = 2;

    /**
     * Arnés (2026-09-28, "una textura que sea arnés... teniendo en cuenta
     * los bordes de corte"): lo contrario de una red — TODO es agujero
     * salvo las tiras. La tira de borde es el mismo refuerzo que ya
     * calcula la red ({@code esBorde}: cuello, sisa, dobladillo, y el
     * corte virtual del largo elegido); las tiras internas y los anillos
     * se ubican sobre el tramo VISIBLE de la cara (caja de sus pixeles
     * opacos, recortada al corte virtual), no sobre la caja entera — así
     * un crop o una media corta no quedan con la cruz partida al medio.
     * Las tapas (arriba/abajo) solo conservan su borde.
     */
    private static void perforarArnes(NativeImage img, com.modamod.item.PatronRed tipo,
                                      int x0, int y0, int x1, int y1, boolean[][] esBorde,
                                      boolean llevaCorteVirtual, int yDesde, int yHasta, int rol) {
        // Caja visible de esta cara.
        int vx0 = Integer.MAX_VALUE, vy0 = Integer.MAX_VALUE, vx1 = Integer.MIN_VALUE, vy1 = Integer.MIN_VALUE;
        for (int y = y0; y < y1; y++) {
            if (llevaCorteVirtual && (y < yDesde || y >= yHasta)) continue;
            for (int x = x0; x < x1; x++) {
                if (((img.getColor(x, y) >> 24) & 0xFF) == 0) continue;
                vx0 = Math.min(vx0, x); vy0 = Math.min(vy0, y);
                vx1 = Math.max(vx1, x + 1); vy1 = Math.max(vy1, y + 1);
            }
        }
        boolean hombros = rol == ARNES_ROL_HOMBROS && vx0 < vx1;
        boolean conTiras = hombros || (llevaCorteVirtual && vx0 < vx1 && vy1 - vy0 >= ARNES_ALTO_MINIMO);
        boolean torso = rol == ARNES_ROL_TORSO;
        double w = vx1 - vx0, h = vy1 - vy0;
        double[][] anillos = conTiras && !hombros ? anillosArnes(tipo.dibujo, w, h, torso) : new double[0][];

        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (((img.getColor(x, y) >> 24) & 0xFF) == 0) continue;
                double lx = x + 0.5 - vx0, ly = y + 0.5 - vy0;
                // Anillo primero: tapa tira y borde, y su agujero deja ver la piel.
                boolean resuelto = false;
                for (double[] a : anillos) {
                    double d = Math.hypot(lx - a[0], ly - a[1]);
                    if (d > ARNES_ANILLO_EXT) continue;
                    if (d <= ARNES_ANILLO_INT) {
                        img.setColor(x, y, 0);
                    } else {
                        // Brillo arriba-izquierda, filo exterior más oscuro.
                        double k = 1 + ((a[0] - lx) + (a[1] - ly)) / ARNES_ANILLO_EXT * 0.25;
                        if (d > ARNES_ANILLO_EXT - 0.8) k *= 0.78;
                        int r = (int) Math.min(255, ARNES_METAL_R * k), g = (int) Math.min(255, ARNES_METAL_G * k),
                                b = (int) Math.min(255, ARNES_METAL_B * k);
                        img.setColor(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
                    }
                    resuelto = true;
                    break;
                }
                if (resuelto) continue;
                if (esBorde[x - x0][y - y0]) continue;
                if (hombros && esTiraHombro(lx, w)) continue;
                if (conTiras && !hombros && esTiraArnes(tipo.dibujo, lx, ly, w, h, torso)) continue;
                img.setColor(x, y, 0);
            }
        }
    }

    /** Centros de los anillos, en coordenadas de la caja visible (w x h). */
    private static double[][] anillosArnes(com.modamod.item.PatronRed.Dibujo dibujo, double w, double h, boolean torso) {
        return switch (dibujo) {
            // En el torso las diagonales salen de las anclas: se cruzan en
            // el medio del ancho, a (ancla2-ancla1)/(2*(1-ancla1)) del alto.
            case ARNES_X -> torso
                    ? new double[][]{{w / 2, h * (ARNES_ANCLA_2 - ARNES_ANCLA_1) / (2 * (1 - ARNES_ANCLA_1))}}
                    : new double[][]{{w / 2, h / 2}};
            case ARNES_TIRANTES -> new double[][]{{w * ARNES_ANCLA_1, h / 2}, {w * ARNES_ANCLA_2, h / 2}};
            default -> new double[][]{{w / 2, h / 3}, {w / 2, 2 * h / 3}};
        };
    }

    private static boolean esTiraArnes(com.modamod.item.PatronRed.Dibujo dibujo, double x, double y, double w, double h,
                                       boolean torso) {
        double medio = ARNES_TIRA_PX / 2.0;
        return switch (dibujo) {
            // Dos diagonales de esquina a esquina del tramo visible — en el
            // torso salen de las anclas de hombro hacia las esquinas de abajo.
            case ARNES_X -> torso
                    ? distanciaASegmento(x, y, w * ARNES_ANCLA_1, 0, w, h) < medio
                            || distanciaASegmento(x, y, w * ARNES_ANCLA_2, 0, 0, h) < medio
                    : distanciaASegmento(x, y, 0, 0, w, h) < medio || distanciaASegmento(x, y, w, 0, 0, h) < medio;
            // Dos tirantes verticales (en las anclas) + una banda horizontal al medio.
            case ARNES_TIRANTES -> Math.abs(x - w * ARNES_ANCLA_1) < medio || Math.abs(x - w * ARNES_ANCLA_2) < medio
                    || Math.abs(y - h / 2) < medio;
            // Dos bandas horizontales a los tercios — en el torso, con dos
            // tiras desde las anclas de hombro hasta la primera banda.
            default -> Math.abs(y - h / 3) < medio || Math.abs(y - 2 * h / 3) < medio
                    || (torso && y < h / 3 && (Math.abs(x - w * ARNES_ANCLA_1) < medio || Math.abs(x - w * ARNES_ANCLA_2) < medio));
        };
    }

    /**
     * Tapa de arriba del BRAZO con arnés (2026-09-28, "los hombros no estan
     * pintando los arneses" / "tienen que continuar las tiras de arriba del
     * brazo"): el hombro que se ve es esta tapa (la del torso queda debajo
     * de la cabeza), y antes quedaba pelada entera — no tiene ningún borde
     * propio, así que el arnés la agujereaba toda. Ahora cada tira que
     * llega al borde de arriba de una cara del brazo sigue por la tapa
     * hasta la mitad; si la cara de enfrente tiene una tira en el mismo
     * lugar, las dos se juntan y cruzan el hombro de lado a lado.
     *
     * <p>2026-09-29 ("los hombros no se completaron" — con el arnés
     * cruzado solo quedaba un marquito, porque las diagonales llegan por
     * las esquinas): además la tapa lleva el MISMO dibujo del arnés que un
     * costado (X de esquina a esquina con su anillo, tirantes o bandas),
     * así el hombro se ve completo y empalma con lo que sube por el brazo.
     *
     * <p>Empalmes en el atlas (mismo layout que {@link CajaSkin}): la fila
     * de abajo de la tapa toca el frente (misma columna); la de arriba, la
     * espalda (columna espejada); la columna 0 toca la cara derecha (su
     * columna {@code j} es la fila {@code j} de la tapa); la última, la
     * izquierda (su columna 0 va con la fila de abajo).
     */
    private static void continuarTirasEnHombro(NativeImage img, com.modamod.item.PatronRed tipo, CajaSkin caja) {
        CajaSkin.Rect tapa = caja.arriba(), frente = caja.frente(), atras = caja.atras();
        CajaSkin.Rect der = caja.derecha(), izq = caja.izquierda();
        int ancho = tapa.x1() - tapa.x0(), prof = tapa.y1() - tapa.y0();
        if (ancho <= 0 || prof <= 0) return;
        boolean[][] tira = new boolean[ancho][prof];
        int mitadProf = (prof + 1) / 2, mitadAncho = (ancho + 1) / 2;
        for (int i = 0; i < ancho; i++) {
            if (opaco(img, frente.x0() + i, frente.y0())) {
                for (int d = 0; d < mitadProf; d++) tira[i][prof - 1 - d] = true;
            }
            if (opaco(img, atras.x1() - 1 - i, atras.y0())) {
                for (int d = 0; d < mitadProf; d++) tira[i][d] = true;
            }
        }
        for (int j = 0; j < prof; j++) {
            if (opaco(img, der.x0() + j, der.y0())) {
                for (int k = 0; k < mitadAncho; k++) tira[k][j] = true;
            }
            if (opaco(img, izq.x0() + j, izq.y0())) {
                for (int k = 0; k < mitadAncho; k++) tira[ancho - 1 - k][prof - 1 - j] = true;
            }
        }
        // El dibujo del arnés sobre la tapa entera, como en un costado.
        double[][] anillos = anillosArnes(tipo.dibujo, ancho, prof, false);
        for (int d = 0; d < prof; d++) {
            for (int i = 0; i < ancho; i++) {
                int x = tapa.x0() + i, y = tapa.y0() + d;
                if (!opaco(img, x, y)) continue;
                double lx = i + 0.5, ly = d + 0.5;
                boolean enAnillo = false;
                for (double[] a : anillos) {
                    double dist = Math.hypot(lx - a[0], ly - a[1]);
                    if (dist > ARNES_ANILLO_EXT) continue;
                    if (dist <= ARNES_ANILLO_INT) {
                        img.setColor(x, y, 0);
                    } else {
                        double k = 1 + ((a[0] - lx) + (a[1] - ly)) / ARNES_ANILLO_EXT * 0.25;
                        if (dist > ARNES_ANILLO_EXT - 0.8) k *= 0.78;
                        int r = (int) Math.min(255, ARNES_METAL_R * k), g = (int) Math.min(255, ARNES_METAL_G * k),
                                b = (int) Math.min(255, ARNES_METAL_B * k);
                        img.setColor(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
                    }
                    enAnillo = true;
                    break;
                }
                if (enAnillo) continue;
                if (tira[i][d] || esTiraArnes(tipo.dibujo, lx, ly, ancho, prof, false)) continue;
                img.setColor(x, y, 0);
            }
        }
    }

    private static boolean opaco(NativeImage img, int x, int y) {
        return x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()
                && ((img.getColor(x, y) >> 24) & 0xFF) != 0;
    }

    /** Tapa de hombros del torso: dos tiras de adelante hacia atrás, en las anclas — iguales para los tres arneses. */
    private static boolean esTiraHombro(double x, double w) {
        double medio = ARNES_TIRA_PX / 2.0;
        return Math.abs(x - w * ARNES_ANCLA_1) < medio || Math.abs(x - w * ARNES_ANCLA_2) < medio;
    }

    private static double distanciaASegmento(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, largo2 = dx * dx + dy * dy;
        double t = largo2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / largo2));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    /**
     * Enrejado diagonal: dos familias de diagonales (/ y \\) espaciadas
     * cada {@code tipo.celda} pixeles, cada una de {@code tipo.hilo}
     * pixeles de ancho — un rombo hueco entre cada cruce, en vez de
     * agujeros sueltos de un solo pixel.
     */
    private static boolean esHilo(int x, int y, com.modamod.item.PatronRed tipo) {
        return switch (tipo.dibujo) {
            case HEXAGONO -> esHiloHexagonal(x + 0.5, y + 0.5, tipo.celda / 2.0, tipo.hilo);
            case AGUJEROS -> !esAgujeroRedondo(x + 0.5, y + 0.5, tipo.celda, tipo.hilo);
            case ENCAJE -> {
                // Rombo diagonal de siempre + un punto sólido en el centro
                // de cada rombo (coordenadas rotadas u/v, escala √2).
                int u = Math.floorMod(x + y, tipo.celda), v = Math.floorMod(x - y, tipo.celda);
                if (u < tipo.hilo || v < tipo.hilo) yield true;
                double centro = tipo.celda / 2.0 + tipo.hilo / 2.0;
                double du = u - centro, dv = v - centro;
                yield du * du + dv * dv <= 8.0; // radio ~2px en espacio real
            }
            case RAYAS -> {
                // Franja abierta de alto hilo cada celda filas, con puentes
                // de 2px cada 8 (corridos 4 en filas alternas) para que la
                // tela no quede en tiras sueltas.
                if (Math.floorMod(y, tipo.celda) >= tipo.hilo) yield true;
                int fila = Math.floorDiv(y, tipo.celda);
                yield Math.floorMod(x + Math.floorMod(fila, 2) * 4, 8) < 2;
            }
            case CUADRICULA -> Math.floorMod(x, tipo.celda) < tipo.hilo || Math.floorMod(y, tipo.celda) < tipo.hilo;
            default -> {
                int d1 = Math.floorMod(x + y, tipo.celda);
                int d2 = Math.floorMod(x - y, tipo.celda);
                yield d1 < tipo.hilo || d2 < tipo.hilo;
            }
        };
    }

    private static final double RAIZ3 = Math.sqrt(3);

    /**
     * Panal (2026-09-28, "media red hexagonal"): hexágonos con vértice
     * arriba, de radio inscripto {@code r}. Busca el centro más cercano
     * (filas cada r·√3, las impares corridas r) y es hilo si el punto cae
     * a menos de {@code hilo} pixeles del borde de su hexágono — la
     * "norma hexagonal" max(|dx|, |dx|/2 + |dy|·√3/2) vale r justo en el borde.
     */
    private static boolean esHiloHexagonal(double x, double y, double r, int hilo) {
        double altoFila = r * RAIZ3;
        int filaCentro = (int) Math.floor(y / altoFila + 0.5);
        double mejor = Double.MAX_VALUE;
        for (int fila = filaCentro - 1; fila <= filaCentro + 1; fila++) {
            double cy = fila * altoFila;
            double corrimiento = Math.floorMod(fila, 2) == 1 ? r : 0;
            int colCentro = (int) Math.floor((x - corrimiento) / (2 * r) + 0.5);
            for (int col = colCentro - 1; col <= colCentro + 1; col++) {
                double cx = col * 2 * r + corrimiento;
                double dx = Math.abs(x - cx), dy = Math.abs(y - cy);
                double norma = Math.max(dx, dx / 2 + dy * RAIZ3 / 2);
                if (norma < mejor) mejor = norma;
            }
        }
        return mejor >= r - hilo;
    }

    /** Tresbolillo de agujeros redondos de radio {@code radio}, cada {@code paso} pixeles (filas alternas corridas medio paso). */
    private static boolean esAgujeroRedondo(double x, double y, int paso, int radio) {
        int fila = (int) Math.floor(y / paso);
        double corrimiento = Math.floorMod(fila, 2) == 1 ? paso / 2.0 : 0;
        double cx = Math.floor((x - corrimiento) / paso) * paso + corrimiento + paso / 2.0;
        double cy = fila * paso + paso / 2.0;
        double dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= (radio + 0.5) * (radio + 0.5);
    }

    /**
     * Vecino transparente REAL (cutout de cuello, o cualquier otro agujero
     * ya horneado en la textura base) — clampeado al propio desdoblado de
     * esta parte ({@code [px0,px1) x [py0,py1)}), nunca mira más allá:
     * eso es justo lo que causaba el bug de tapas/costados (ver el
     * javadoc de {@link #perforarRed}).
     */
    private static boolean cercaDeBorde(NativeImage img, int x, int y, int px0, int py0, int px1, int py1) {
        for (int dy = -RED_BORDE_PX; dy <= RED_BORDE_PX; dy++) {
            int ny = y + dy;
            if (ny < py0 || ny >= py1) continue;
            for (int dx = -RED_BORDE_PX; dx <= RED_BORDE_PX; dx++) {
                int nx = x + dx;
                if (nx < px0 || nx >= px1) continue;
                if (((img.getColor(nx, ny) >> 24) & 0xFF) == 0) return true;
            }
        }
        return false;
    }

    /** Como sombrear la tela segun a que parte del cuerpo va. */
    public enum Shading { NONE, LEGS, ARMS }

    /**
     * El png crudo de una textura del resource pack, cacheado.
     *
     * Publico porque el compositor del cuerpo base lee los mismos archivos
     * (el mapa de sombras del cuerpo, la ropa interior) y no tiene sentido
     * que cada compositor mantenga su propio cache de imagenes de disco.
     */
    /**
     * Da de alta en este cache una imagen que NO viene de un archivo del
     * resource pack — a pedido (2026-09-16), bug real jugando: cualquier
     * código que compone una textura y la registra dinámicamente en
     * {@code TextureManager} (ej. {@code EstampaTextures#cuerpoEstampado},
     * para cualquier remera teñida o con estampa/patrón) le da un
     * Identifier que NO tiene archivo real detrás — {@link #imagenBase}
     * intenta leerlo con {@code ResourceManager.getResource}, no lo
     * encuentra, devuelve null, y quien lo llamó (ej.
     * {@code PiezasDelMod#recortarMangaYCachear}) caía silenciosamente al
     * "sin recortar": la manga de una remera teñida SIEMPRE llegaba hasta
     * el puño sin importar el largo elegido. Llamar esto justo después de
     * registrar la textura deja que {@link #imagenBase} la encuentre
     * directo, sin tocar el resource pack.
     */
    public static void registrarImagenCompuesta(Identifier id, NativeImage img) {
        IMAGENES_PROPIAS.remove(id);   // si antes era del resource pack, ahora la posee la textura registrada
        BASE_IMAGE_CACHE.put(id, img);
    }

    public static NativeImage imagenBase(Identifier id) {
        if (BASE_IMAGE_CACHE.containsKey(id)) return BASE_IMAGE_CACHE.get(id);
        try {
            Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                BASE_IMAGE_CACHE.put(id, null);
                return null;
            }
            try (InputStream stream = resource.get().getInputStream()) {
                NativeImage img = NativeImage.read(stream);
                BASE_IMAGE_CACHE.put(id, img);
                IMAGENES_PROPIAS.add(id);
                return img;
            }
        } catch (IOException e) {
            BASE_IMAGE_CACHE.put(id, null);
            return null;
        }
    }
}
