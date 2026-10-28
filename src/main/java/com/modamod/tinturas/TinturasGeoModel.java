package com.modamod.tinturas;

import com.modamod.Modamod;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Ata el block entity de la Estación de Tintes a su geometría/textura/
 * animación (modelo "dye_station" — assets 3D reales bajados por el
 * usuario, ver {@code Downloads/Bloque de sublimadora Minecraft}) y
 * escala los 4 indicadores de nivel de tinta según el tanque — mismo
 * mecanismo que {@code SublimadoraGeoModel} (escala de hueso, pivote en
 * la base, oculto en 0).
 *
 * Desde 2026-09-21 ("que cada maquina tome su tiempo") también escala un
 * hueso "progress" — mismo mecanismo que
 * {@code ModeladoGeoModel}/{@code SublimadoraGeoModel} — reposicionado
 * el mismo día al costado izquierdo del frente (antes compartía lado con
 * la pantallita nueva, ver {@code PANEL_TINTURAS}) para quedar en el
 * mismo lugar relativo que en las otras dos máquinas.
 */
public class TinturasGeoModel extends GeoModel<TinturasBlockEntity> {
    private static final Identifier GEO =
            Identifier.of(Modamod.MOD_ID, "geo/dye_station.geo.json");
    /** No privado a propósito: {@link TinturasRenderer} lo necesita para el atlas real detrás de los LEDs. */
    static final Identifier TEX =
            Identifier.of(Modamod.MOD_ID, "textures/block/dye_station_atlas.png");
    private static final Identifier ANIM =
            Identifier.of(Modamod.MOD_ID, "animations/dye_station.animation.json");

    private static final String[] HUESOS = { "level_cyan", "level_magenta", "level_yellow", "level_key" };

    /**
     * Los 3 "LEDs falsos" del panel, ahora conectados de verdad
     * (2026-09-21, "que brillen de verdad, tipo lucecitas geckolib") —
     * este es el grupo que quedó al lado de los tubos CMYK (el usuario
     * confirmó que es éste, no el que ya se convirtió en barra de
     * progreso). Atlas de 64×64, a diferencia de los 128×128 de las
     * otras dos máquinas.
     */
    static final int ANCHO_ATLAS = 64, ALTO_ATLAS = 64;
    static final com.modamod.render.PantallaLed.Rect[] LEDS = {
            new com.modamod.render.PantallaLed.Rect(16, 16, 8, 8), // verde
            new com.modamod.render.PantallaLed.Rect(24, 16, 8, 8), // rojo
            new com.modamod.render.PantallaLed.Rect(32, 16, 8, 8), // amarillo
    };
    private static final int VERDE = 0xFF00FF00, ROJO = 0xFF0000FF, AMARILLO = 0xFF00FFFF;

    /**
     * Sin Estado.LISTO acá (a diferencia de Modeladora/Sublimadora) —
     * "listo" es REPOSO con algo en la salida esperando que lo saquen.
     * Rojo titilando igual que las otras dos; amarillo titilando más lento
     * cuando la línea de producción está encendida.
     */
    static int[] coloresLed(TinturasBlockEntity be) {
        int verde = 0, rojo = 0, amarillo = 0;
        if (be.estado() == TinturasBlockEntity.Estado.REPOSO && !be.getSalida().isEmpty()) {
            verde = VERDE;
        } else if (be.estado() == TinturasBlockEntity.Estado.TINIENDO) {
            long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
            if (t % 20 < 10) rojo = ROJO;
        }
        long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
        if (be.linea() && t % 40 < 20) amarillo = AMARILLO;
        return new int[]{verde, rojo, amarillo};
    }

    /**
     * Mismo bug que {@code SublimadoraGeoModel.FACTOR_HUESOS}/{@code FACTOR_PROGRESO}
     * (2026-09-21, "los tubos de cmyk... deberian llegar hasta arriba con
     * el 100%"): nunca se le había puesto factor de calibración a estos 4
     * — el cubo de cada nivel mide 2.6 de alto (tamaño DEMO), pero el tubo
     * de vidrio real detrás (el cubo de "base" en la misma zona, Y
     * 11.5→15.1) mide 3.6. Sin este factor, {@code setScaleY(1)} solo
     * llegaba al ~72% del tubo real. Un solo factor para las 4: los 4
     * cubos de nivel y sus 4 marcos miden exactamente lo mismo (ver
     * inspección real del .geo.json), a diferencia de la Sublimadora
     * donde cada barra tenía su propio tamaño DEMO distinto.
     */
    private static final float FACTOR_NIVEL = 3.6f / 2.6f;

    // ── viales: últimos 2 colores usados (2026-09-21, "que muestren los
    // ultimos dos colores utilizados") ──────────────────────────────────
    /** Rects UV reales de cada vial (relevados a mano con Python) — vial_1 el más reciente, vial_2 el anterior. */
    // Movidos a una zona propia del atlas (2026-09-29, "cuando solo tenia dos
    // tintes... el magenta se veia blanco en el indicador"): compartían UV con
    // los tubos level_magenta (48,8) y level_cyan (24,8), así que pintar el
    // vial con el último color pisaba también el tubo.
    private static final com.modamod.render.PantallaLed.Rect VIAL_1_UV = new com.modamod.render.PantallaLed.Rect(40, 16, 8, 8);
    private static final com.modamod.render.PantallaLed.Rect VIAL_2_UV = new com.modamod.render.PantallaLed.Rect(48, 16, 8, 8);
    /**
     * Último color pintado en cada vial, por máquina — para no repintar
     * (y volver a subir a la GPU) la textura entera cada frame, mismo
     * criterio de "comparar antes de pintar" que {@link com.modamod.render.PantallaMaquina}/
     * {@link com.modamod.render.PantallaLed} (el bug real que causó
     * el crash de memoria nativa de esta sesión era, en el fondo, no
     * comparar esto).
     */
    private static final java.util.Map<net.minecraft.util.math.BlockPos, int[]> ULTIMO_COLOR_VIAL = new java.util.HashMap<>();

    // ── prenda girando en el rodillo (2026-09-23, "tinturas girando si"
    // — de vuelta la versión de cubos simples por categoría que colgaba
    // de "roller", la que el usuario confirmó que andaba bien) ──────────
    /** Mismo orden que {@code TinturasBlockEntity.Categoria}. */
    private static final String[] HUESOS_PRENDA = {
            "prenda_remera", "prenda_pantalon", "prenda_medias", "prenda_calientabrazos", "prenda_pollera",
    };
    private static final com.modamod.render.PantallaLed.Rect RECT_PRENDA =
            new com.modamod.render.PantallaLed.Rect(48, 32, 16, 16);
    private static final java.util.Map<net.minecraft.util.math.BlockPos, Integer> ULTIMO_COLOR_PRENDA = new java.util.HashMap<>();

    @Override public Identifier getModelResource(TinturasBlockEntity be) { return GEO; }

    /** Pantallita de vista previa (2026-09-21, "haceme la pantallita") — ver {@link com.modamod.render.PantallaMaquina}. */
    @Override
    public Identifier getTextureResource(TinturasBlockEntity be) {
        // La VISTA PREVIA (salida real, o entrada con el borrador ya
        // pintado encima) — a pedido (2026-09-21, "que muestre el
        // preview del setting de la ultima prenda seteada"); ya prioriza
        // salida > entrada+borrador > representativo, ver su javadoc.
        Identifier id = com.modamod.render.PantallaMaquina.con(com.modamod.util.MaquinaCreativa.textura(TEX, com.modamod.util.MaquinaCreativa.es(be)), com.modamod.render.PantallaMaquina.PANEL_TINTURAS, be.vistaPreviaPersistente(), be.getPos());
        pintarViales(be, id);
        return id;
    }

    /**
     * Pinta el color ACTIVO (viejo mientras se vacía, nuevo mientras se
     * llena — ver {@code TinturasBlockEntity#colorVialActivo}) sobre la
     * MISMA textura ya compuesta de la pantallita, in-place — SOLO para
     * el/los vial(es) que ya vieron algún teñido real
     * ({@code TinturasBlockEntity#vialUsado}); mientras no, se deja el
     * arte original horneado en el atlas (2026-09-22, bug real: sin
     * este chequeo pintaba negro sólido desde el primer frame, tapando
     * el diseño de fábrica antes de que el jugador tiñera nada).
     */
    private static void pintarViales(TinturasBlockEntity be, Identifier texturaId) {
        boolean usado0 = be.vialUsado(0), usado1 = be.vialUsado(1);
        if (!usado0 && !usado1) return;

        int[] actual = {
                usado0 ? abgrOpaco(be.colorVialActivo(0)) : 0,
                usado1 ? abgrOpaco(be.colorVialActivo(1)) : 0,
        };
        int[] previo = ULTIMO_COLOR_VIAL.get(be.getPos());
        if (previo != null && previo[0] == actual[0] && previo[1] == actual[1]) return;

        net.minecraft.client.texture.AbstractTexture tex =
                net.minecraft.client.MinecraftClient.getInstance().getTextureManager().getTexture(texturaId);
        if (!(tex instanceof net.minecraft.client.texture.NativeImageBackedTexture nibt)) return;
        net.minecraft.client.texture.NativeImage img = nibt.getImage();
        if (usado0) pintarRect(img, VIAL_1_UV, actual[0]);
        if (usado1) pintarRect(img, VIAL_2_UV, actual[1]);
        nibt.upload();
        ULTIMO_COLOR_VIAL.put(be.getPos(), actual);
    }

    /** Color plano de {@code stack} — mismos 5 tipos que {@code TinturasBlockEntity.Categoria}, 0 si no es ninguno. */
    private static int colorDePrenda(net.minecraft.item.ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.getItem() instanceof com.modamod.sublimadora.RemeraItem) {
            return com.modamod.sublimadora.RemeraItem.color(stack);
        }
        return com.modamod.region.RegionResolver.colorBase(stack, com.modamod.region.Lado.IZQUIERDA);
    }

    /** Pinta el color plano de la prenda en curso sobre el rect dedicado — comparar antes de pintar, mismo criterio que {@link #pintarViales}. */
    private static void pintarColorPrenda(TinturasBlockEntity be, Identifier texturaId, int abgr) {
        Integer previo = ULTIMO_COLOR_PRENDA.get(be.getPos());
        if (previo != null && previo == abgr) return;

        net.minecraft.client.texture.AbstractTexture tex =
                net.minecraft.client.MinecraftClient.getInstance().getTextureManager().getTexture(texturaId);
        if (!(tex instanceof net.minecraft.client.texture.NativeImageBackedTexture nibt)) return;
        pintarRect(nibt.getImage(), RECT_PRENDA, abgr);
        nibt.upload();
        ULTIMO_COLOR_PRENDA.put(be.getPos(), abgr);
    }

    private static void pintarRect(net.minecraft.client.texture.NativeImage img, com.modamod.render.PantallaLed.Rect r, int abgr) {
        for (int y = r.v(); y < r.v() + r.alto(); y++) {
            for (int x = r.u(); x < r.u() + r.ancho(); x++) {
                img.setColor(x, y, abgr);
            }
        }
    }

    /** RGB plano (0xRRGGBB, lo que devuelve {@code TinteFijado#color}) a ABGR opaco — mismo formato que {@code NativeImage}. */
    private static int abgrOpaco(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    @Override public Identifier getAnimationResource(TinturasBlockEntity be) { return ANIM; }

    /**
     * Translúcido y no cutout (a pedido, 2026-09-18: "los tubos... hay
     * que hacerlos alpha" — probado 20%, el pedido final quedó en 80%)
     * — cutout es prueba binaria de alfa (0 o 1), no deja pasar alfa
     * parcial. El resto del modelo sigue opaco (alfa 255 en el atlas),
     * así que se ve exactamente igual bajo esta capa; solo los 4
     * cuadraditos del atlas bajados a 80% (ver
     * {@code dye_station_atlas.png}, zonas level_cyan/magenta/yellow/key)
     * se ven de verdad semitransparentes.
     */
    @Override
    public net.minecraft.client.render.RenderLayer getRenderType(TinturasBlockEntity be, Identifier texture) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void setCustomAnimations(TinturasBlockEntity be, long instanceId, AnimationState<TinturasBlockEntity> state) {
        super.setCustomAnimations(be, instanceId, state);
        for (int i = 0; i < HUESOS.length; i++) {
            GeoBone barra = getAnimationProcessor().getBone(HUESOS[i]);
            if (barra == null) continue;
            // La textura YA tiene el color correcto horneado en esta zona
            // del atlas (confirmado a mano: cyan/magenta/amarillo/negro) —
            // no hace falta teñir nada, solo revelar más o menos con la
            // escala. Mismo mecanismo que las barras de tinta de la
            // Sublimadora (pivote en la base).
            //
            // Se probó (2026-09-18) sumar setRotZ sobre el MISMO hueso para
            // el efecto "aguja que gira" — descartado: es el mismo cubo
            // que representa el líquido (el modelo no trae un hueso de
            // aguja aparte, solo level_cyan/magenta/yellow/key), así que
            // rotarlo lo saca de su alojamiento en vez de simular una
            // aguja ("el tubo se gira y sale, no se ve cuando está
            // adentro"). Vuelve a ser solo escala.
            float nivel = be.carga(i) / (float) TinturasBlockEntity.CARGA_MAXIMA;
            barra.setScaleY(Math.max(0.001f, nivel * FACTOR_NIVEL));
            barra.setHidden(nivel <= 0.001f);
        }

        GeoBone progreso = getAnimationProcessor().getBone("progress");
        if (progreso != null) {
            // Normalizada con las hermanas (2026-09-29, "las barras de
            // progreso la q mas me gusta es la de la tintura porque tiene
            // fondo negro nomas que crece para el lado contrario que
            // deberia. las normalicemos"): el pivote del hueso pasó al
            // otro borde en el .geo.json (el modelo mira a +z, al revés que
            // las otras dos) y queda llena con la prenda lista en la salida.
            boolean lista = be.estado() == TinturasBlockEntity.Estado.REPOSO && !be.getSalida().isEmpty();
            float nivel = lista ? 1f : be.progresoFraccion();
            progreso.setScaleX(Math.max(0.001f, nivel));
            progreso.setHidden(nivel <= 0.001f);
        }

        // Vaciado/llenado de los viales al cambiar de color — mismo
        // mecanismo que level_cyan etc (pivote en la base, setScaleY).
        String[] vialHuesos = { "vial_1", "vial_2" };
        for (int i = 0; i < vialHuesos.length; i++) {
            GeoBone vial = getAnimationProcessor().getBone(vialHuesos[i]);
            if (vial == null) continue;
            vial.setScaleY(Math.max(0.001f, be.fraccionVial(i)));
        }

        // Prenda girando en el rodillo (2026-09-23, "tinturas girando
        // si") — hijos de "roller", ya giran solos con la animación
        // "trabajo"; acá solo se elige cuál mostrar y de qué color. Solo
        // visible mientras TINIENDO — a diferencia de Modeladora/
        // Sublimadora (muestran la prenda cargada aunque esté en
        // reposo), acá no hay "prenda cargada quieta".
        // Siempre ocultos (2026-09-29): la prenda la dibuja PrendaEnMaquinaLayer
        // con su ícono real, anclada en "prenda_remera" para girar con el rodillo.
        for (int i = 0; i < HUESOS_PRENDA.length; i++) {
            GeoBone hueso = getAnimationProcessor().getBone(HUESOS_PRENDA[i]);
            if (hueso != null) hueso.setHidden(true);
        }
    }
}
