package com.modamod.render;

import com.modamod.Modamod;
import com.modamod.garment.Parte;
import com.modamod.item.PolleraItem;
import com.modamod.region.Lado;
import com.modamod.region.RegionResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ícono de 64x64 de una prenda armado con su tela REAL — a pedido
 * (2026-09-29, "haceme una propuesta de items de cada prenda superior a la
 * actual y que representen mejor a la prenda" → "ok pero los hagamos
 * 64x64" → "B"): el color de cada píxel sale de la misma textura que se
 * dibuja sobre el cuerpo ({@link PiezasDePrenda}), así el ícono muestra
 * todo lo que se le hizo en las máquinas — capas y patrones de Tintes,
 * cada pierna/manga distinta, redes y arneses, fotos de la Sublimadora — y
 * el largo real: las filas que la pieza no tapa ({@code filaDesde}/
 * {@code filaHasta}) se recortan (manga corta, crop, shorts, zoquetes).
 *
 * <p>Por ícono hay dos PNG en {@code textures/item/icono/}, generados por
 * {@code tools/generar_iconos_prendas.py}: {@code _sombra} (silueta + relieve
 * en gris, plano = {@link #GRIS_PLANO}) y {@code _mapa} (R = código de cara
 * × 20, G/B = u/v dentro de esa cara, v = fila de la parte). El contorno lo
 * pone este código, así también bordea lo recortado; los agujeros de la
 * tela (redes) quedan sin contorno.
 */
public final class IconoPrenda {

    public static final int LADO = 64;
    /** Gris del relieve en una zona plana: el color de la tela sale tal cual. */
    private static final float GRIS_PLANO = 219f;
    private static final float OSCURO_CONTORNO = 0.3f;
    /** Cada cuánto se vuelven a leer las piezas de un mismo stack (la foto puede terminar de bajar). */
    private static final long REVISAR_MS = 1000;
    private static final int MAXIMO_ICONOS = 256;

    // Códigos del mapa — mismos que tools/generar_iconos_prendas.py.
    private static final int TORSO = 1, BRAZO_DER = 2, BRAZO_IZQ = 3, PIERNA_DER = 4, PIERNA_IZQ = 5,
            TORSO_ATRAS = 6, POLLERA = 7, CAPA = 8;

    /**
     * La pollera (2026-09-29): su tela tiene el layout de la caja del torso
     * (ver PolleraMalla) — el ícono muestra el frente (20..28 x 20..32 en
     * coordenadas de 64). El largo recorta el dibujo desde abajo.
     */
    private static final float POLLERA_X = 20f, POLLERA_Y = 20f, POLLERA_ANCHO = 8f, POLLERA_ALTO = 12f;
    /** La capa (2026-09-29): el exterior (u 1..11, v 1..17), visto de atrás; el largo recorta desde abajo. */
    private static final float CAPA_X = 1f, CAPA_Y = 1f, CAPA_ANCHO = 10f, CAPA_ALTO = 16f;

    private record Plantilla(NativeImage sombra, NativeImage mapa) {}

    private static final class Icono {
        final Identifier id;
        final NativeImageBackedTexture textura;
        boolean completo;
        long ultimoIntento;

        Icono(Identifier id, NativeImageBackedTexture textura) {
            this.id = id;
            this.textura = textura;
        }
    }

    private record PorStack(String clave, long cuando) {}

    private static final Map<String, Plantilla> PLANTILLAS = new HashMap<>();
    private static final Map<Integer, PorStack> POR_STACK = new HashMap<>();
    private static final LinkedHashMap<String, Icono> ICONOS = new LinkedHashMap<>(64, 0.75f, true);

    private IconoPrenda() {}

    /** Nombre de la plantilla de cada prenda, o null si la prenda no tiene ícono armado. */
    @Nullable
    private static String plantillaDe(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof com.modamod.sublimadora.RemeraItem) { // la chaqueta usa la plantilla de la remera
            var cuello = com.modamod.sublimadora.RemeraItem.variante(stack).cuello();
            // La camisa usa la plantilla del cuello en V (2026-10-07).
            if (cuello == com.modamod.sublimadora.Variante.Cuello.CAMISA) cuello = com.modamod.sublimadora.Variante.Cuello.V;
            // El cerrado de fábrica usa la plantilla del redondo (2026-10-12).
            if (cuello == com.modamod.sublimadora.Variante.Cuello.CERRADO) cuello = com.modamod.sublimadora.Variante.Cuello.REDONDO;
            return "remera_" + cuello.asString();
        }
        if (item == com.modamod.item.ModamodItems.PANTALON) return "pantalon";
        if (item instanceof PolleraItem) return "pollera";
        if (item instanceof com.modamod.item.CapaItem) return "capa";
        if (item == com.modamod.item.ModamodItems.SOCKS_SOLID) return "medias";
        if (item == com.modamod.item.ModamodItems.CALIENTABRAZOS) return "calientabrazos";
        return null;
    }

    public static boolean tieneIcono(ItemStack stack) {
        return stack.getItem() instanceof com.modamod.item.DelantalItem || plantillaDe(stack) != null;
    }

    /** La textura del ícono de este stack, o null si no se pudo armar (sin plantilla). */
    @Nullable
    public static Identifier de(ItemStack stack) {
        if (stack.getItem() instanceof com.modamod.item.DelantalItem) return deDelantal(stack);
        String nombre = plantillaDe(stack);
        if (nombre == null) return null;
        long ahora = System.currentTimeMillis();

        int hash = ItemStack.hashCode(stack);
        PorStack cacheado = POR_STACK.get(hash);
        String clave;
        List<Pieza> piezas = null;
        Identifier texturaPollera = null;
        if (cacheado != null && ahora - cacheado.cuando() < REVISAR_MS) {
            clave = cacheado.clave();
        } else {
            piezas = PiezasDePrenda.de(stack, null);
            texturaPollera = texturaSuelta(stack);
            clave = claveDe(nombre, piezas, texturaPollera);
            // El largo de la pollera no cambia su textura, pero sí el recorte del ícono.
            if (stack.getItem() instanceof PolleraItem) clave += "|largo:" + PolleraItem.largo(stack);
            if (stack.getItem() instanceof com.modamod.item.CapaItem) clave += "|largo:" + com.modamod.item.CapaItem.largo(stack);
            if (POR_STACK.size() > 4 * MAXIMO_ICONOS) POR_STACK.clear();
            POR_STACK.put(hash, new PorStack(clave, ahora));
        }

        Icono icono = ICONOS.get(clave);
        if (icono != null && (icono.completo || ahora - icono.ultimoIntento < REVISAR_MS)) return icono.id;

        Plantilla plantilla = plantilla(nombre);
        if (plantilla == null) return null;
        if (piezas == null) {
            piezas = PiezasDePrenda.de(stack, null);
            texturaPollera = texturaSuelta(stack);
        }

        if (icono == null) {
            NativeImage img = new NativeImage(LADO, LADO, true);
            NativeImageBackedTexture textura = new NativeImageBackedTexture(img);
            Identifier id = Identifier.of(Modamod.MOD_ID, "icono/" + Integer.toHexString(clave.hashCode()));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, textura);
            icono = new Icono(id, textura);
            ICONOS.put(clave, icono);
            recortarCache();
        }
        icono.completo = componer(icono.textura.getImage(), plantilla, stack, piezas, texturaPollera);
        icono.ultimoIntento = ahora;
        icono.textura.upload();
        return icono.id;
    }

    /**
     * El ícono del delantal (2026-10-11): sin plantilla de sombra y mapa, directo de su tela ya recortada a la silueta
     * (u 18..30, v 20 hacia abajo), ajustada al cuadro con un contorno oscuro.
     */
    @Nullable
    private static Identifier deDelantal(ItemStack stack) {
        long ahora = System.currentTimeMillis();
        int hash = ItemStack.hashCode(stack);
        PorStack cacheado = POR_STACK.get(hash);
        String clave;
        if (cacheado != null && ahora - cacheado.cuando() < REVISAR_MS) {
            clave = cacheado.clave();
        } else {
            clave = "delantal|" + GarmentFeatureRenderer.texturaDelantal(stack) + "|" + com.modamod.item.DelantalItem.largo(stack);
            if (POR_STACK.size() > 4 * MAXIMO_ICONOS) POR_STACK.clear();
            POR_STACK.put(hash, new PorStack(clave, ahora));
        }
        Icono icono = ICONOS.get(clave);
        if (icono != null && (icono.completo || ahora - icono.ultimoIntento < REVISAR_MS)) return icono.id;
        if (icono == null) {
            NativeImage img = new NativeImage(LADO, LADO, true);
            NativeImageBackedTexture textura = new NativeImageBackedTexture(img);
            Identifier id = Identifier.of(Modamod.MOD_ID, "icono/" + Integer.toHexString(clave.hashCode()));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, textura);
            icono = new Icono(id, textura);
            ICONOS.put(clave, icono);
            recortarCache();
        }
        icono.completo = componerDelantal(icono.textura.getImage(), stack);
        icono.ultimoIntento = ahora;
        icono.textura.upload();
        return icono.id;
    }

    private static boolean componerDelantal(NativeImage destino, ItemStack stack) {
        for (int y = 0; y < LADO; y++) for (int x = 0; x < LADO; x++) destino.setColor(x, y, 0);
        NativeImage tela = imagen(GarmentFeatureRenderer.texturaDelantal(stack));
        if (tela == null) return false;
        float largo = com.modamod.item.DelantalItem.largo(stack);
        float ancho = 12f;
        float k = Math.min(56f / ancho, 56f / largo);
        float ox = (LADO - ancho * k) / 2f, oy = (LADO - largo * k) / 2f;
        float esc = tela.getWidth() / 64f;
        boolean[] lleno = new boolean[LADO * LADO];
        for (int y = 0; y < LADO; y++) {
            for (int x = 0; x < LADO; x++) {
                float u = 18f + (x + 0.5f - ox) / k, v = 20f + (y + 0.5f - oy) / k;
                if (u < 18f || u >= 30f || v < 20f || v >= 20f + largo) continue;
                int texel = muestra(tela, u * esc, v * esc);
                if ((texel >>> 24) < 128) continue;
                destino.setColor(x, y, 0xFF000000 | (texel & 0xFFFFFF));
                lleno[y * LADO + x] = true;
            }
        }
        for (int y = 0; y < LADO; y++) {
            for (int x = 0; x < LADO; x++) {
                if (lleno[y * LADO + x]) continue;
                boolean toca = (x > 0 && lleno[y * LADO + x - 1]) || (x < LADO - 1 && lleno[y * LADO + x + 1])
                        || (y > 0 && lleno[(y - 1) * LADO + x]) || (y < LADO - 1 && lleno[(y + 1) * LADO + x]);
                if (toca) destino.setColor(x, y, 0xFF202028);
            }
        }
        return true;
    }

    /** La tela de las prendas que no van por piezas (pollera, capa), o null. */
    @Nullable
    private static Identifier texturaSuelta(ItemStack stack) {
        if (stack.getItem() instanceof PolleraItem) return GarmentFeatureRenderer.texturaPollera(stack);
        if (stack.getItem() instanceof com.modamod.item.CapaItem) return GarmentFeatureRenderer.texturaCapa(stack);
        return null;
    }

    /**
     * Copia del ícono armado de {@code stack} (la cierra quien la pide), o
     * null si la prenda no tiene ícono — para la pantallita de las máquinas
     * (2026-09-29, "igual el item que aparece en la pantallita?").
     */
    @Nullable
    public static NativeImage copia(ItemStack stack) {
        Icono icono = iconoDe(stack);
        if (icono == null || icono.textura.getImage() == null) return null;
        NativeImage fuente = icono.textura.getImage();
        NativeImage salida = new NativeImage(LADO, LADO, true);
        salida.copyFrom(fuente);
        return salida;
    }

    /** ¿El ícono de {@code stack} ya tiene todas sus texturas? (Si no, conviene volver a pedirlo en un rato.) */
    public static boolean completo(ItemStack stack) {
        Icono icono = iconoDe(stack);
        return icono == null || icono.completo;
    }

    @Nullable
    private static Icono iconoDe(ItemStack stack) {
        if (de(stack) == null) return null;
        PorStack p = POR_STACK.get(ItemStack.hashCode(stack));
        return p == null ? null : ICONOS.get(p.clave());
    }

    private static String claveDe(String nombre, List<Pieza> piezas, @Nullable Identifier pollera) {
        StringBuilder sb = new StringBuilder(nombre);
        for (Pieza p : piezas) {
            sb.append('|').append(p.parte().ordinal()).append(':').append(p.textura())
                    .append(':').append(p.filaDesde()).append('-').append(p.filaHasta());
        }
        if (pollera != null) sb.append("|pollera:").append(pollera);
        return sb.toString();
    }

    private static void recortarCache() {
        while (ICONOS.size() > MAXIMO_ICONOS) {
            var it = ICONOS.entrySet().iterator();
            Icono viejo = it.next().getValue();
            it.remove();
            MinecraftClient.getInstance().getTextureManager().destroyTexture(viejo.id);
        }
    }

    /** Recarga de recursos (F3+T): se tiran las plantillas y los íconos armados. */
    public static void limpiar() {
        PLANTILLAS.clear();
        POR_STACK.clear();
        for (Icono icono : ICONOS.values()) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(icono.id);
        }
        ICONOS.clear();
    }

    @Nullable
    private static Plantilla plantilla(String nombre) {
        if (PLANTILLAS.containsKey(nombre)) return PLANTILLAS.get(nombre);
        NativeImage sombra = ClothingTextureCache.imagenBase(
                Identifier.of(Modamod.MOD_ID, "textures/item/icono/" + nombre + "_sombra.png"));
        NativeImage mapa = ClothingTextureCache.imagenBase(
                Identifier.of(Modamod.MOD_ID, "textures/item/icono/" + nombre + "_mapa.png"));
        Plantilla p = sombra == null || mapa == null ? null : new Plantilla(sombra, mapa);
        PLANTILLAS.put(nombre, p);
        return p;
    }

    /** La imagen de una textura de prenda: la compuesta en memoria o la del resource pack. */
    @Nullable
    private static NativeImage imagen(Identifier id) {
        AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getOrDefault(id, null);
        if (tex instanceof NativeImageBackedTexture backed && backed.getImage() != null) return backed.getImage();
        return ClothingTextureCache.imagenBase(id);
    }

    /** @return false si faltó alguna textura (todavía componiéndose): se reintenta en un rato. */
    private static boolean componer(NativeImage destino, Plantilla plantilla, ItemStack stack,
                                    List<Pieza> piezas, @Nullable Identifier texturaPollera) {
        boolean completo = true;
        // La pieza de más arriba de cada parte (la remera tiene una por parte;
        // si hubiera dos, la de capa mayor es la que se ve).
        Map<Parte, Pieza> porParte = new HashMap<>();
        for (Pieza p : piezas) {
            Pieza actual = porParte.get(p.parte());
            if (actual == null || p.capa() >= actual.capa()) porParte.put(p.parte(), p);
        }
        Map<Identifier, NativeImage> imagenes = new HashMap<>();
        NativeImage pollera = texturaPollera == null ? null : imagen(texturaPollera);
        if (texturaPollera != null && pollera == null) completo = false;
        int colorLiso = RegionResolver.colorBase(stack, Lado.IZQUIERDA);

        boolean[] cortado = new boolean[LADO * LADO];
        for (int y = 0; y < LADO; y++) {
            for (int x = 0; x < LADO; x++) {
                destino.setColor(x, y, 0);
                int s = plantilla.sombra().getColor(x, y);
                if ((s >>> 24) == 0) continue;
                int m = plantilla.mapa().getColor(x, y);
                int codigo = Math.round((m & 0xFF) / 20f);
                float u = (((m >> 8) & 0xFF) + 0.5f) / 256f;
                float v = (((m >> 16) & 0xFF) + 0.5f) / 256f;

                int texel;   // ABGR
                if (codigo == CAPA) {
                    float fraccion = com.modamod.item.CapaItem.largo(stack).fraccion();
                    if (v > 0.1f && v > fraccion) {
                        cortado[y * LADO + x] = true;
                        continue;
                    }
                    v = v <= 0.1f ? v : Math.min(0.999f, v / fraccion);
                    if (pollera == null) {
                        texel = abgrDe(colorLiso);
                    } else {
                        float esc = pollera.getWidth() / 64f;
                        texel = muestra(pollera, (CAPA_X + u * CAPA_ANCHO) * esc, (CAPA_Y + v * CAPA_ALTO) * esc);
                    }
                } else if (codigo == POLLERA) {
                    // Largo: el dibujo es la pollera más larga; lo que queda
                    // por debajo del ruedo real se recorta (y lleva contorno).
                    float fraccion = com.modamod.item.PolleraItem.largo(stack).fraccion();
                    if (v > 0.1f && v > fraccion) {
                        cortado[y * LADO + x] = true;
                        continue;
                    }
                    v = v <= 0.1f ? v : Math.min(0.999f, v / fraccion);
                    if (pollera == null) {
                        texel = abgrDe(colorLiso);
                    } else {
                        float esc = pollera.getWidth() / 64f;
                        texel = muestra(pollera, (POLLERA_X + u * POLLERA_ANCHO) * esc, (POLLERA_Y + v * POLLERA_ALTO) * esc);
                    }
                } else {
                    Parte parte = parteDe(codigo);
                    Pieza pieza = parte == null ? null : porParte.get(parte);
                    float fila = v * 12f;
                    if (pieza == null || fila < pieza.filaDesde() || fila >= pieza.filaHasta()) {
                        cortado[y * LADO + x] = true;
                        continue;
                    }
                    NativeImage img = imagenes.computeIfAbsent(pieza.textura(), IconoPrenda::imagen);
                    if (img == null) {
                        completo = false;
                        texel = abgrDe(colorLiso);
                    } else {
                        CajaSkin caja = LayoutSkin.base(parte, false);
                        CajaSkin.Rect cara = codigo == TORSO_ATRAS ? caja.atras() : caja.frente();
                        float esc = img.getWidth() / 64f;
                        texel = muestra(img, (cara.x0() + u * cara.anchoRect()) * esc,
                                (cara.y0() + v * cara.altoRect()) * esc);
                    }
                }
                // Agujero de la tela (red, arnés): transparente y sin contorno.
                if ((texel >>> 24) < 128) continue;
                float f = (s & 0xFF) / GRIS_PLANO;
                destino.setColor(x, y, escalar(texel, f));
            }
        }

        // Contorno: todo píxel con tela que toca afuera de la silueta o algo recortado.
        int[] oscurecer = new int[LADO * LADO];
        int n = 0;
        for (int y = 0; y < LADO; y++) {
            for (int x = 0; x < LADO; x++) {
                if ((destino.getColor(x, y) >>> 24) == 0) continue;
                if (borde(plantilla.sombra(), cortado, x - 1, y) || borde(plantilla.sombra(), cortado, x + 1, y)
                        || borde(plantilla.sombra(), cortado, x, y - 1) || borde(plantilla.sombra(), cortado, x, y + 1)) {
                    oscurecer[n++] = y * LADO + x;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            int x = oscurecer[i] % LADO, y = oscurecer[i] / LADO;
            destino.setColor(x, y, escalar(destino.getColor(x, y), OSCURO_CONTORNO));
        }
        return completo;
    }

    private static boolean borde(NativeImage sombra, boolean[] cortado, int x, int y) {
        if (x < 0 || y < 0 || x >= LADO || y >= LADO) return true;
        return (sombra.getColor(x, y) >>> 24) == 0 || cortado[y * LADO + x];
    }

    @Nullable
    private static Parte parteDe(int codigo) {
        return switch (codigo) {
            case TORSO, TORSO_ATRAS -> Parte.TORSO;
            case BRAZO_DER -> Parte.BRAZO_DER;
            case BRAZO_IZQ -> Parte.BRAZO_IZQ;
            case PIERNA_DER -> Parte.PIERNA_DER;
            case PIERNA_IZQ -> Parte.PIERNA_IZQ;
            default -> null;
        };
    }

    private static int muestra(NativeImage img, float x, float y) {
        // getColor solo anda en RGBA: una textura en otro formato se ve blanca en vez de crashear.
        if (img.getFormat() != NativeImage.Format.RGBA) return 0xFFFFFFFF;
        int ix = Math.min(img.getWidth() - 1, Math.max(0, (int) x));
        int iy = Math.min(img.getHeight() - 1, Math.max(0, (int) y));
        return img.getColor(ix, iy);
    }

    /** 0xRRGGBB → ABGR opaco. */
    private static int abgrDe(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /** Multiplica RGB (ABGR) por f, alfa opaco. */
    private static int escalar(int abgr, float f) {
        int r = Math.min(255, Math.round((abgr & 0xFF) * f));
        int g = Math.min(255, Math.round(((abgr >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round(((abgr >> 16) & 0xFF) * f));
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }
}
