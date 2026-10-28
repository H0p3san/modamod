package com.modamod.telar;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Ata el Telar a su geometría, textura y animaciones (modelo generado por {@code tools/generar_modelo_telar.py}: la base
 * de la Estilista con la tapa del telar). La barra "progress" se llena como en las otras máquinas; la prenda tejida
 * la dibuja {@link com.modamod.render.PrendaEnMaquinaLayer} sobre la bandeja de salida.
 */
public class TelarGeoModel extends GeoModel<TelarBlockEntity> {

    static final Identifier GEO = Identifier.of(Modamod.MOD_ID, "geo/telar.geo.json");
    static final Identifier TEX = Identifier.of(Modamod.MOD_ID, "textures/block/telar_atlas.png");
    static final Identifier ANIM = Identifier.of(Modamod.MOD_ID, "animations/telar.animation.json");
    /** El atlas de la Modeladora ocupa la mitad de arriba (los LED y la pantalla siguen en sus rects de siempre). */
    static final int ANCHO_ATLAS = 128, ALTO_ATLAS = 256;
    static final String[] HUESOS_DE_PRENDA = {"garment"};
    private static final com.modamod.render.PantallaLed.Rect[] LEDS = {
            new com.modamod.render.PantallaLed.Rect(96, 16, 1, 1),
            new com.modamod.render.PantallaLed.Rect(112, 16, 1, 1),
            new com.modamod.render.PantallaLed.Rect(0, 32, 1, 1),
    };
    private static final int VERDE = 0xFF00FF00, ROJO = 0xFF0000FF, AMARILLO = 0xFF00FFFF;
    private static final float FACTOR_PROGRESO = 4.4f / 2.6371f;   // el mismo cubo de la Modeladora

    public static com.modamod.render.PantallaLed.Rect[] leds() { return LEDS; }

    static int[] coloresLed(TelarBlockEntity be) {
        int verde = 0, rojo = 0, amarillo = 0;
        if (be.estado() == TelarBlockEntity.Estado.LISTO) {
            verde = VERDE;
        } else if (be.estado() == TelarBlockEntity.Estado.PROCESANDO) {
            long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
            if (t % 20 < 10) rojo = ROJO;
        }
        long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
        if (be.linea() && t % 40 < 20) amarillo = AMARILLO;
        return new int[]{verde, rojo, amarillo};
    }

    @Override public Identifier getModelResource(TelarBlockEntity be) { return GEO; }

    @Override
    public Identifier getTextureResource(TelarBlockEntity be) {
        // Pantalla grande con la prenda elegida (teñida con la lana), como las otras máquinas.
        return com.modamod.render.PantallaMaquina.con(atlas(be), com.modamod.render.PantallaMaquina.PANEL_128,
                be.vistaPrevia(), be.getPos());
    }

    private void indicador(String hueso, int valor) {
        GeoBone b = getAnimationProcessor().getBone(hueso);
        if (b == null) return;
        float nivel = Math.min(1f, valor / 64f);
        b.setScaleY(Math.max(0.001f, nivel));
        b.setHidden(nivel <= 0.001f);
    }

    static Identifier atlas(TelarBlockEntity be) {
        return com.modamod.util.MaquinaCreativa.textura(TEX, com.modamod.util.MaquinaCreativa.es(be));
    }

    @Override public Identifier getAnimationResource(TelarBlockEntity be) { return ANIM; }

    @Override
    public void setCustomAnimations(TelarBlockEntity be, long instanceId, AnimationState<TelarBlockEntity> state) {
        super.setCustomAnimations(be, instanceId, state);
        GeoBone progreso = getAnimationProcessor().getBone("progress");
        if (progreso != null) {
            float nivel = switch (be.estado()) {
                case PROCESANDO -> be.progreso() / (float) be.duracion();
                case LISTO -> 1f;
                default -> 0f;
            };
            progreso.setScaleX(Math.max(0.001f, nivel * FACTOR_PROGRESO));
            progreso.setHidden(nivel <= 0.001f);
        }
        // Tres barras verticales de insumos en el frente (2026-10-07, 2026-10-12 + cuero): lana, hilo y cuero, sobre 64.
        indicador("lana", be.lana());
        indicador("hilo", be.hilo());
        indicador("cuero", be.cuero());
        GeoBone prenda = getAnimationProcessor().getBone("garment");
        if (prenda != null) prenda.setHidden(true);
    }
}
