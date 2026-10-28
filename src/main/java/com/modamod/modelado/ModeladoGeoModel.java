package com.modamod.modelado;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Ata el block entity de la Mesa de Modelado a su geometría/textura/
 * animación (modelo "garment_shaper" v2, `bloques de moda 2.0/HUESOS.md`).
 * Clips vía {@link ModeladoBlockEntity#registerControllers}; acá solo lo que
 * no es keyframe: el hueso "progress" escala en X desde su borde izquierdo
 * según {@code progreso/TICKS_PROCESO}, igual criterio que las barras de
 * tinta de la sublimadora (pivote en la base, `setScaleX`, oculto en 0).
 *
 * <p>{@link #getTextureResource} devuelve una textura COMPUESTA en vez de
 * {@link #TEX} directo — a pedido (2026-09-21, "haceme la pantallita"),
 * ver {@link com.modamod.render.PantallaMaquina}.
 */
public class ModeladoGeoModel extends GeoModel<ModeladoBlockEntity> {
    private static final Identifier GEO =
            Identifier.of(Modamod.MOD_ID, "geo/garment_shaper.geo.json");
    /** No privado a propósito: {@link ModeladoRenderer} lo necesita para el atlas real detrás de los LEDs. */
    static final Identifier TEX =
            Identifier.of(Modamod.MOD_ID, "textures/block/garment_shaper_atlas.png");
    private static final Identifier ANIM =
            Identifier.of(Modamod.MOD_ID, "animations/garment_shaper.animation.json");

    /**
     * Los 3 "LEDs falsos" del panel — cubos decorativos sin lógica que
     * ya traía el modelo de Blockbench, ahora conectados de verdad
     * (2026-09-21, "que brillen de verdad, tipo lucecitas geckolib").
     * Rects UV reales del atlas (relevados a mano con Python), mismo
     * orden que en Sublimadora (comparten panel-plantilla): verde,
     * rojo, amarillo.
     */
    static final int ANCHO_ATLAS = 128, ALTO_ATLAS = 128;
    static final com.modamod.render.PantallaLed.Rect[] LEDS = {
            new com.modamod.render.PantallaLed.Rect(96, 16, 1, 1),  // verde
            new com.modamod.render.PantallaLed.Rect(112, 16, 1, 1), // rojo
            new com.modamod.render.PantallaLed.Rect(0, 32, 1, 1),   // amarillo
    };
    private static final int VERDE = 0xFF00FF00, ROJO = 0xFF0000FF, AMARILLO = 0xFF00FFFF;

    /**
     * Verde fijo cuando la prenda está lista, rojo titilando mientras
     * procesa. El amarillo titila más lento cuando la línea de producción
     * está encendida.
     */
    static int[] coloresLed(ModeladoBlockEntity be) {
        int verde = 0, rojo = 0, amarillo = 0;
        if (be.estado() == ModeladoBlockEntity.Estado.LISTO) {
            verde = VERDE;
        } else if (be.estado() == ModeladoBlockEntity.Estado.PROCESANDO) {
            long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
            if (t % 20 < 10) rojo = ROJO;
        }
        long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
        if (be.linea() && t % 40 < 20) amarillo = AMARILLO;
        return new int[]{verde, rojo, amarillo};
    }

    /**
     * Mismo bug que {@code SublimadoraGeoModel.FACTOR_PROGRESO} (2026-09-21,
     * "la barrita de progreso... se llena hasta la mitad"): el cubo
     * "progress" mide 2.6371 de ancho (tamaño DEMO) pero el marco
     * estático detrás (mismo cubo compartido de "base", X≈0.95→5.35, ancho
     * 4.4 — este modelo comparte la misma geometría base que Sublimadora)
     * mide 4.4. Sin este factor llegaba solo al ~60%.
     */
    private static final float FACTOR_PROGRESO = 4.4f / 2.6371f;

    @Override public Identifier getModelResource(ModeladoBlockEntity be) { return GEO; }

    @Override
    public Identifier getTextureResource(ModeladoBlockEntity be) {
        // La VISTA PREVIA (con las fijadas ya aplicadas), no la prenda
        // cruda del slot — a pedido (2026-09-21, "que muestre el preview
        // del setting de la ultima prenda seteada").
        Identifier id = com.modamod.render.PantallaMaquina.con(com.modamod.util.MaquinaCreativa.textura(TEX, com.modamod.util.MaquinaCreativa.es(be)), com.modamod.render.PantallaMaquina.PANEL_128, be.vistaPreviaPersistente(), be.getPos());
        // La pantallita de la tijera (2026-10-05): la creativa no usa tijera, así que no la muestra.
        com.modamod.render.PantallaTijera.pintar(id, be.getPos(), !be.tijera().isEmpty(), be.durabilidadTijera());
        return id;
    }

    @Override public Identifier getAnimationResource(ModeladoBlockEntity be) { return ANIM; }

    @Override
    public void setCustomAnimations(ModeladoBlockEntity be, long instanceId, AnimationState<ModeladoBlockEntity> state) {
        super.setCustomAnimations(be, instanceId, state);
        GeoBone progreso = getAnimationProcessor().getBone("progress");
        if (progreso != null) {
            // Llena mientras la prenda espera en la salida, igual que la
            // Sublimadora (2026-09-29, "las barras de progreso... las
            // normalicemos").
            float nivel = switch (be.estado()) {
                case PROCESANDO -> be.progreso() / (float) ModeladoBlockEntity.TICKS_PROCESO;
                case LISTO -> 1f;
                default -> 0f;
            };
            progreso.setScaleX(Math.max(0.001f, nivel * FACTOR_PROGRESO));
            progreso.setHidden(nivel <= 0.001f);
        }

        // La remera YA modelada en 3D que traía "base" (2026-09-23,
        // agrupada y nombrada "REMERA" a mano en Blockbench por el
        // usuario — no era un hueso propio, eran cubos sueltos mezclados
        // con el resto de la carcasa) + las otras 3 categorías armadas en
        // cubos simples con el mismo criterio ("no aparecen las otras
        // prendas") — solo una visible a la vez, según la prenda cargada.
        for (String huesoCat : HUESOS_CATEGORIA) {
            GeoBone hueso = getAnimationProcessor().getBone(huesoCat);
            // Siempre ocultos (2026-09-29): la prenda la dibuja PrendaEnMaquinaLayer con su ícono real.
            if (hueso != null) hueso.setHidden(true);
        }
    }

    private static final String[] HUESOS_CATEGORIA = { "REMERA", "PANTALON", "MEDIAS", "CALIENTABRAZOS" };
}
