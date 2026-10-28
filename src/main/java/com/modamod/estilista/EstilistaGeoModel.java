package com.modamod.estilista;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Ata la Estilista a su geometría, textura y animaciones (modelo generado por {@code tools/generar_modelo_estilista.py}:
 * la base de la Modeladora con la tapa del auto_styler). La barra "progress" se llena como en las otras máquinas; los
 * huesos {@code garment}, {@code held} y {@code placed_*} (la prenda y los apliques de juguete del pórtico) quedan
 * ocultos: la prenda real la dibuja {@link com.modamod.render.PrendaEnMaquinaLayer}.
 */
public class EstilistaGeoModel extends GeoModel<EstilistaBlockEntity> {

    static final Identifier GEO = Identifier.of(Modamod.MOD_ID, "geo/estilista.geo.json");
    static final Identifier TEX = Identifier.of(Modamod.MOD_ID, "textures/block/estilista_atlas.png");
    static final Identifier ANIM = Identifier.of(Modamod.MOD_ID, "animations/estilista.animation.json");
    /** El atlas de la Modeladora ocupa la mitad de arriba (los LED y la pantalla siguen en sus rects de siempre). */
    static final int ANCHO_ATLAS = 128, ALTO_ATLAS = 256;
    static final String[] HUESOS_DE_PRENDA = {"garment", "held", "placed_1", "placed_2", "placed_3", "placed_4"};
    private static final com.modamod.render.PantallaLed.Rect[] LEDS = {
            new com.modamod.render.PantallaLed.Rect(96, 16, 1, 1),
            new com.modamod.render.PantallaLed.Rect(112, 16, 1, 1),
            new com.modamod.render.PantallaLed.Rect(0, 32, 1, 1),
    };
    private static final int VERDE = 0xFF00FF00, ROJO = 0xFF0000FF, AMARILLO = 0xFF00FFFF;
    private static final float FACTOR_PROGRESO = 4.4f / 2.6371f;   // el mismo cubo de la Modeladora

    public static com.modamod.render.PantallaLed.Rect[] leds() { return LEDS; }

    static int[] coloresLed(EstilistaBlockEntity be) {
        int verde = 0, rojo = 0, amarillo = 0;
        if (be.estado() == EstilistaBlockEntity.Estado.LISTO) {
            verde = VERDE;
        } else if (be.estado() == EstilistaBlockEntity.Estado.PROCESANDO) {
            long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
            if (t % 20 < 10) rojo = ROJO;
        }
        long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
        if (be.linea() && t % 40 < 20) amarillo = AMARILLO;
        return new int[]{verde, rojo, amarillo};
    }

    @Override public Identifier getModelResource(EstilistaBlockEntity be) { return GEO; }

    @Override
    public Identifier getTextureResource(EstilistaBlockEntity be) {
        // Pantalla grande con la prenda, como la Modeladora y la Sublimadora (2026-10-05, "agrandar la pantalla como en las otras dos").
        return com.modamod.render.PantallaMaquina.con(atlas(be), com.modamod.render.PantallaMaquina.PANEL_128,
                be.vistaPreviaPersistente(), be.getPos());
    }

    private void indicador(String hueso, int valor) {
        GeoBone b = getAnimationProcessor().getBone(hueso);
        if (b == null) return;
        float nivel = valor / (float) EstilistaBlockEntity.TOPE_CONTADOR;
        b.setScaleY(Math.max(0.001f, nivel));
        b.setHidden(nivel <= 0.001f);
    }

    static Identifier atlas(EstilistaBlockEntity be) {
        return com.modamod.util.MaquinaCreativa.textura(TEX, com.modamod.util.MaquinaCreativa.es(be));
    }

    @Override public Identifier getAnimationResource(EstilistaBlockEntity be) { return ANIM; }

    @Override
    public void setCustomAnimations(EstilistaBlockEntity be, long instanceId, AnimationState<EstilistaBlockEntity> state) {
        super.setCustomAnimations(be, instanceId, state);
        GeoBone progreso = getAnimationProcessor().getBone("progress");
        if (progreso != null) {
            float nivel = switch (be.estado()) {
                case PROCESANDO -> be.progreso() / (float) EstilistaBlockEntity.TICKS_PROCESO;
                case LISTO -> 1f;
                default -> 0f;
            };
            progreso.setScaleX(Math.max(0.001f, nivel * FACTOR_PROGRESO));
            progreso.setHidden(nivel <= 0.001f);
        }
        // Indicadores de insumos en el frente (2026-10-05): dos barras verticales grandes, de hilo y de cuero, que bajan con el contador.
        indicador("hilo", be.hilo());
        indicador("cuero", be.cuero());
        // La prenda real la dibuja la capa; los juguetes del pórtico solo se ven mientras trabaja.
        boolean trabajando = be.estado() == EstilistaBlockEntity.Estado.PROCESANDO;
        for (String h : HUESOS_DE_PRENDA) {
            GeoBone b = getAnimationProcessor().getBone(h);
            if (b == null) continue;
            if (h.equals("garment")) b.setHidden(true);
            else b.setHidden(!trabajando);
        }
    }
}
