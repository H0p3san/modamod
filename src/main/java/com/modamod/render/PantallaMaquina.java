package com.modamod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Pantallita de vista previa en el frente-derecho de las 3 máquinas
 * (2026-09-21, "haceme la pantallita"; corregido el mismo día, "no estan
 * mostrando mas que un color... tienen q mostrar un preview del ultimo
 * setting") — el hueso "design" de los 3 modelos era puro adorno sin
 * usar; ahora vive sobre una franja LIBRE de cada atlas (encontrada por
 * inspección real de los PNG, no adivinada: bloques de 80/80/40 filas
 * completamente transparentes) y acá se le pinta encima el ÍCONO REAL de
 * la prenda (silueta + color + estampa, no un color plano).
 *
 * <h2>Por qué se movió el UV del hueso "design" en vez de pintar donde
 * ya estaba</h2>
 * Su mapeo original (heredado de Blockbench) reutilizaba pixeles del
 * MISMO atlas que otras partes del modelo (madera/metal del cuerpo) —
 * confirmado mirando los colores reales del PNG ahí. Pintar encima
 * habría manchado esas otras partes también, no solo el panel.
 *
 * <h2>Por qué el ÍCONO del ítem y no un recorte del wrap de piel
 * (2026-09-21, segunda vuelta: "no me aparece la prenda solo la
 * imagen... falta mucha definicion")</h2>
 * La v1 recortaba la cara de "frente" de la textura ya compuesta en
 * layout de skin (la que se usa para vestir al jugador de verdad) — eso
 * sirve para VESTIR, pero cropeado a un rectángulo de 128×128 no tiene
 * ninguna silueta reconocible como "una prenda": una remera de cobertura
 * completa con estampa llena esa cara ENTERA de punta a punta, así que
 * el recorte era indistinguible de una foto cualquiera pegada en un
 * cuadrado. El ÍCONO del ítem en cambio (el mismo sprite
 * {@code item/corte_*}/{@code item/pantalon}/etc que ya se ve en el
 * inventario) SÍ tiene la silueta real recortada en su alfa —cuello,
 * mangas, dobladillo— y para la remera con estampa full-print existe
 * {@link EstampaTextures#iconoParaPantalla} que ya la compone así (mismo
 * mecanismo que usa {@code RemeraItemRenderer} para el ícono del
 * inventario). Se lee el sprite real vía {@code ClothingTextureCache
 * #imagenBase} (funciona para archivos de recurso Y para texturas
 * compuestas ya registradas, ver su javadoc) en vez de recortar el wrap
 * de piel.
 *
 * <h2>Por qué NO se recrea la textura cada frame (2026-09-21, crash real:
 * "Unable to allocate texture of size 128x128", memoria nativa agotada)</h2>
 * La v1 armaba un {@link NativeImage} nuevo (la copia COMPLETA del atlas
 * de 128×128 más el recorte) en CADA llamada — y este método se llama en
 * cada frame que se renderiza el bloque, GeckoLib mediante — y lo
 * registraba bajo un Identifier con un hash que cambiaba en cada llamada
 * (porque el recorte también era un {@code NativeImage} nuevo cada vez,
 * de identidad distinta). Nada se cerraba nunca: cientos de imágenes
 * nativas de 64KB por segundo, por máquina visible, hasta reventar la
 * asignación nativa. Ahora hay UNA sola {@link NativeImageBackedTexture}
 * por máquina colocada (cacheada por {@link BlockPos}, no por contenido),
 * creada una vez, y sus píxeles se REPINTAN in-place solo cuando la
 * prenda de vista previa cambia de verdad (comparación por
 * {@link ItemStack#areItemsAndComponentsEqual}, no por identidad — la
 * prenda de vista previa de las 3 máquinas se reconstruye de cero en
 * cada llamada aunque el contenido sea el mismo). El recorte intermedio
 * sigue siendo un {@code NativeImage} de usar-y-tirar, pero ahora se
 * cierra con {@code close()} apenas se usa, y solo se crea cuando hace
 * falta repintar (no en cada frame).
 */
public final class PantallaMaquina {

    private PantallaMaquina() {}

    private static final class Entrada {
        final Identifier id;
        final NativeImageBackedTexture textura;
        /** null = todavía no se pintó nunca — fuerza el primer repintado aunque la vista previa esté vacía. */
        @Nullable ItemStack ultimaVistaPrevia;
        /** El ícono todavía esperaba texturas la última vez: se reintenta (a lo sumo 1 vez por segundo). */
        boolean incompleta;
        long ultimoIntento;
        /**
         * Copia con SOLO la pantalla opaca (el resto transparente), para la
         * capa emisiva — 2026-09-29, "que las pantallitas de las maquinas
         * siempre esten iluminadas". Ver {@code PantallaGlowLayer}.
         */
        @Nullable Identifier glowId;
        @Nullable NativeImageBackedTexture glow;

        Entrada(Identifier id, NativeImageBackedTexture textura) {
            this.id = id;
            this.textura = textura;
        }
    }

    private static final Map<String, Entrada> CACHE = new HashMap<>();

    /** Rectángulo en PÍXELES del atlas donde vive la pantalla — ver los .geo.json. */
    public record Rect(int u, int v, int ancho, int alto) {}

    // Agrandada (2026-09-21, "podes hacer mas grande la pantallita" —
    // ahora que muestra tela de verdad en vez de un color plano, vale la
    // pena que se note): el hueso "design" también se agrandó a juego en
    // los 3 .geo.json (mismo centro, más ancho/alto). Sigue sobrando de
    // sobra la franja libre real de cada atlas (80/80/40 filas).
    // Sincronizados 1:1 con el uv_size real del hueso "design" en los 3
    // .geo.json (2026-09-21, "mas resolucion a la pantalla"): estos
    // valores ESTABAN desincronizados de lo que el modelo 3D sampleaba de
    // verdad (acá decía 42×26/20×12, el hueso solo mapeaba 32×20/16×10) —
    // una parte de lo pintado ya se perdía, invisible. De paso se
    // aprovechó la banda libre real del atlas (confirmada por inspección:
    // 128×80 px completamente transparentes en garment_shaper/sublimator,
    // 64×40 en dye_station) para subir la densidad de verdad, no solo
    // agrandar el hueco ya usado.
    //
    // 90×74 (2026-09-21, segunda vuelta — "es el otro el panel que tiene
    // cosas no deseadas entre la pantalla y el panel"): el hueco real
    // resultó ser un solo marco recesado de 4 capas ("base", heredado de
    // Blockbench) con la pantalla metida en el medio — se sacaron las 3
    // capas intermedias (quedan solo el marco de afuera + la pantalla) y
    // esta se agrandó a juego, ~90% del hueco del marco (6.6×5.4),
    // guardando su misma proporción.
    public static final Rect PANEL_128 = new Rect(0, 48, 90, 74);
    // Tintes al molde de sus hermanas (2026-09-29, "el panel de la estacion
    // de tintes, donde esta la pantallita tiene cosas atras que no van y un
    // hueso que no se usa. mira la estructura de las otras dos"): se sacaron
    // las 2 placas intermedias, las dos ventanitas 2x2 de adorno y la tira
    // de barra de progreso sin uso (el progreso real está en el panel de los
    // viales); queda el marco de afuera + la pantalla, que se agrandó a
    // ~90% del marco como en la Modeladora/Sublimadora (5.94×4.884). 49×40
    // guarda esa proporción dentro de la franja libre del atlas de 64.
    public static final Rect PANEL_TINTURAS = new Rect(0, 24, 49, 40);

    /**
     * Margen alrededor de la prenda dentro de la pantalla — a pedido
     * (2026-09-21, "reducir el tamaño de la remera"): antes
     * {@link #pegarReescalado} estiraba el recorte hasta pisar los 4
     * bordes del panel entero, sin aire. Ahora el panel se pinta
     * SIEMPRE con el fondo primero (llene o no), y la prenda se pega en
     * un recuadro más chico centrado adentro — 20% del panel de margen a
     * cada lado, la prenda ocupa el 60% central.
     */
    // 8% (2026-09-29): el ícono de 64x64 ya trae aire propio alrededor de la prenda.
    private static final float MARGEN = 0.08f;

    /**
     * @param posInstancia la posición del bloque dueño de esta pantalla — cachea UNA
     *                      textura por máquina colocada (no una por (atlas,rect):
     *                      dos Modeladoras en el mismo mundo muestran cada una lo
     *                      suyo, no se pisan).
     * @param vistaPrevia   la prenda YA con el seteo actual aplicado (fijadas/estampa/borrador
     *                      — ver {@code ModeladoBlockEntity#previsualizar}/{@code prendaDeVistaPrevia}
     *                      de Sublimadora/Tinturas), o vacía para "apagada".
     */
    public static Identifier con(Identifier atlasBase, Rect rect, ItemStack vistaPrevia, BlockPos posInstancia) {
        String key = atlasBase + "|" + rect.u() + "," + rect.v() + "," + rect.ancho() + "," + rect.alto()
                + "|" + posInstancia.asLong();
        Entrada entrada = CACHE.get(key);
        if (entrada == null) {
            NativeImage base = ClothingTextureCache.imagenBase(atlasBase);
            if (base == null) return atlasBase;

            NativeImage copia = new NativeImage(base.getWidth(), base.getHeight(), true);
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    copia.setColor(x, y, base.getColor(x, y));
                }
            }

            Identifier id = Identifier.of("modamod", "dynamic/pantalla_" + Integer.toHexString(key.hashCode()));
            NativeImageBackedTexture textura = new NativeImageBackedTexture(copia);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, textura);
            entrada = new Entrada(id, textura);
            NativeImage vacia = new NativeImage(base.getWidth(), base.getHeight(), true);
            for (int y = 0; y < base.getHeight(); y++) for (int x = 0; x < base.getWidth(); x++) vacia.setColor(x, y, 0);
            entrada.glow = new NativeImageBackedTexture(vacia);
            entrada.glowId = Identifier.of("modamod", "dynamic/pantalla_glow_" + Integer.toHexString(key.hashCode()));
            MinecraftClient.getInstance().getTextureManager().registerTexture(entrada.glowId, entrada.glow);
            CACHE.put(key, entrada);
        }

        boolean cambio = entrada.ultimaVistaPrevia == null
                || !ItemStack.areItemsAndComponentsEqual(entrada.ultimaVistaPrevia, vistaPrevia);
        // El ícono nuevo puede estar esperando una textura (la foto bajando):
        // se repinta hasta que quede completo.
        long ahora = System.currentTimeMillis();
        if (cambio || (entrada.incompleta && ahora - entrada.ultimoIntento > 1000)) {
            entrada.ultimoIntento = ahora;
            repintar(entrada, rect, vistaPrevia);
            entrada.ultimaVistaPrevia = vistaPrevia.copy();
            entrada.incompleta = !vistaPrevia.isEmpty() && !IconoPrenda.completo(vistaPrevia);
        }
        return entrada.id;
    }

    /** Repinta SOLO el rectángulo de la pantalla, in-place, sobre la textura ya registrada — sin allocar una nueva. */
    private static void repintar(Entrada entrada, Rect rect, ItemStack vistaPrevia) {
        NativeImage destino = entrada.textura.getImage();
        NativeImage recorte = vistaPrevia.isEmpty() ? null : iconoDePrenda(vistaPrevia);
        try {
            pintarPlano(destino, rect, FONDO); // fondo siempre — también detrás del margen cuando SÍ hay prenda
            if (recorte != null) {
                pegarReescalado(destino, cuadradoCentrado(conMargen(rect)), recorte);
            }
        } finally {
            if (recorte != null) recorte.close();
        }
        entrada.textura.upload();
        if (entrada.glow != null && entrada.glow.getImage() != null) {
            NativeImage g = entrada.glow.getImage();
            int x1 = Math.min(destino.getWidth(), rect.u() + rect.ancho());
            int y1 = Math.min(destino.getHeight(), rect.v() + rect.alto());
            for (int y = rect.v(); y < y1; y++) {
                for (int x = rect.u(); x < x1; x++) g.setColor(x, y, destino.getColor(x, y) | 0xFF000000);
            }
            entrada.glow.upload();
        }
    }

    /**
     * La textura emisiva de la pantalla de esa máquina (misma clave que
     * {@link #con}; hay que llamarla después de {@code con} en el mismo frame),
     * o null si todavía no existe.
     */
    @Nullable
    public static Identifier glow(Identifier atlasBase, Rect rect, BlockPos posInstancia) {
        String key = atlasBase + "|" + rect.u() + "," + rect.v() + "," + rect.ancho() + "," + rect.alto()
                + "|" + posInstancia.asLong();
        Entrada entrada = CACHE.get(key);
        return entrada == null ? null : entrada.glowId;
    }

    /** El ícono es cuadrado: se centra un cuadrado del lado más corto para no estirarlo. */
    private static Rect cuadradoCentrado(Rect r) {
        int lado = Math.min(r.ancho(), r.alto());
        return new Rect(r.u() + (r.ancho() - lado) / 2, r.v() + (r.alto() - lado) / 2, lado, lado);
    }

    private static Rect conMargen(Rect rect) {
        int mx = Math.round(rect.ancho() * MARGEN);
        int my = Math.round(rect.alto() * MARGEN);
        return new Rect(rect.u() + mx, rect.v() + my, rect.ancho() - 2 * mx, rect.alto() - 2 * my);
    }

    private static void pintarPlano(NativeImage destino, Rect rect, int abgr) {
        int x1 = Math.min(destino.getWidth(), rect.u() + rect.ancho());
        int y1 = Math.min(destino.getHeight(), rect.v() + rect.alto());
        for (int y = rect.v(); y < y1; y++) {
            for (int x = rect.u(); x < x1; x++) {
                destino.setColor(x, y, abgr);
            }
        }
    }

    /** Fondo fijo de la pantalla — mismo negro que "apagada" (ver {@link #pintarPlano}). */
    private static final int FONDO = 0xFF000000;

    /**
     * Copia {@code fuente} completa dentro de {@code rect} de {@code destino}, vecino-más-cercano.
     *
     * <h2>Por qué se respeta el alfa real (2026-09-21, "solo muestra
     * colores... quiero un preview real de la prenda")</h2>
     * La v1 forzaba cada píxel a opaco ({@code px | 0xFF000000}) — la
     * excusa era no dejar ver el atlas de la máquina por detrás, pero el
     * costo real fue borrar la SILUETA: el recorte de "frente" de una
     * pieza viene con alfa 0 donde la tela no tapa (escote, sisa de
     * manga, la parte del torso que no cubre una musculosa, contrato de
     * {@link Pieza}) — con eso aplastado a opaco, CUALQUIER prenda pintaba
     * un rectángulo sólido sin forma, indistinguible de "un color plano"
     * aunque la composición de verdad tuviera patrón. Ahora se mezcla el
     * píxel real contra {@link #FONDO} según su alfa real: donde la tela
     * no tapa se ve el fondo (recorta la silueta de verdad), donde tapa
     * se ve la tela — la pantalla sigue siendo 100% opaca en el resultado
     * final (nunca dejaría ver el atlas real detrás), pero ahora esa
     * opacidad es CONTRA UN FONDO FIJO, no contra la tela estampada.
     */
    private static void pegarReescalado(NativeImage destino, Rect rect, NativeImage fuente) {
        int fw = fuente.getWidth(), fh = fuente.getHeight();
        if (fw <= 0 || fh <= 0) { pintarPlano(destino, rect, FONDO); return; }
        int x1 = Math.min(destino.getWidth(), rect.u() + rect.ancho());
        int y1 = Math.min(destino.getHeight(), rect.v() + rect.alto());
        int fondoB = (FONDO >> 16) & 0xFF, fondoG = (FONDO >> 8) & 0xFF, fondoR = FONDO & 0xFF;
        for (int y = rect.v(); y < y1; y++) {
            int fy = Math.min(fh - 1, (y - rect.v()) * fh / rect.alto());
            for (int x = rect.u(); x < x1; x++) {
                int fx = Math.min(fw - 1, (x - rect.u()) * fw / rect.ancho());
                int px = fuente.getColor(fx, fy);
                int alfa = (px >>> 24) & 0xFF;
                int color;
                if (alfa == 0) {
                    color = FONDO;
                } else if (alfa == 255) {
                    color = px | 0xFF000000;
                } else {
                    int b = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, r = px & 0xFF;
                    int rb = (b * alfa + fondoB * (255 - alfa)) / 255;
                    int rg = (g * alfa + fondoG * (255 - alfa)) / 255;
                    int rr = (r * alfa + fondoR * (255 - alfa)) / 255;
                    color = 0xFF000000 | (rb << 16) | (rg << 8) | rr;
                }
                destino.setColor(x, y, color);
            }
        }
    }

    /**
     * El ícono real de {@code stack} — silueta + color + estampa si
     * corresponde —, siempre una copia nueva de propiedad del que llama
     * (quien la cierra con {@code close()}), o {@code null} si el ítem no
     * es una prenda reconocida (cae a "apagada").
     */
    @Nullable
    private static NativeImage iconoDePrenda(ItemStack stack) {
        // El ícono de 64x64 armado con la tela real (2026-09-29, "igual el item
        // que aparece en la pantallita?"): el mismo que el inventario y la mesa.
        NativeImage nuevo = IconoPrenda.copia(stack);
        if (nuevo != null) return nuevo;
        if (stack.getItem() instanceof com.modamod.sublimadora.RemeraItem) {
            return com.modamod.sublimadora.EstampaTextures.iconoParaPantalla(stack);
        }
        Identifier iconoBase;
        int color;
        if (stack.getItem() == com.modamod.item.ModamodItems.PANTALON) {
            iconoBase = Identifier.of("modamod", "textures/item/pantalon.png");
            color = com.modamod.region.RegionResolver.colorBase(stack, com.modamod.region.Lado.IZQUIERDA);
        } else if (stack.getItem() == com.modamod.item.ModamodItems.SOCKS_SOLID) {
            iconoBase = Identifier.of("modamod", "textures/item/socks_solid.png");
            color = com.modamod.region.RegionResolver.colorBase(stack, com.modamod.region.Lado.IZQUIERDA);
        } else if (stack.getItem() == com.modamod.item.ModamodItems.CALIENTABRAZOS) {
            iconoBase = Identifier.of("modamod", "textures/item/calientabrazos.png");
            color = com.modamod.region.RegionResolver.colorBase(stack, com.modamod.region.Lado.IZQUIERDA);
        } else {
            return null;
        }

        NativeImage base = ClothingTextureCache.imagenBase(iconoBase);
        if (base == null) return null;
        return teñida(base, color);
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
