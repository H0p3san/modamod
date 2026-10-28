package com.modamod.modelado;

import com.modamod.render.LedGlowLayer;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Registro (ClientModInitializer): BlockEntityRendererFactories.register(ModeladoMod.MODELADO_BLOCK_ENTITY, ctx -> new ModeladoRenderer()). */
public class ModeladoRenderer extends GeoBlockRenderer<ModeladoBlockEntity> {
    public ModeladoRenderer() {
        super(new ModeladoGeoModel());
        addRenderLayer(new LedGlowLayer<>(this, ModeladoGeoModel.ANCHO_ATLAS, ModeladoGeoModel.ALTO_ATLAS,
                ModeladoGeoModel.LEDS, ModeladoGeoModel::coloresLed));
        // La prenda cargada, con su ícono real acostado sobre la mesa (2026-09-29,
        // "reemplazar esos huesos por el item nuevo") — centro del hueso REMERA.
        // Pantallita siempre iluminada (2026-09-29).
        addRenderLayer(new com.modamod.render.PantallaGlowLayer<>(this,
                be -> com.modamod.render.PantallaMaquina.glow(ModeladoGeoModel.TEX,
                        com.modamod.render.PantallaMaquina.PANEL_128, be.getPos())));
        addRenderLayer(new com.modamod.render.PrendaEnMaquinaLayer<>(this, "REMERA",
                be -> be.prendaVisible(), -0.4f, 11.85f, -0.95f, 10f,
                com.modamod.render.PrendaEnMaquinaLayer.Apoyo.ACOSTADA_FRENTE_MENOS_Z));
        // La prenda terminada sobre la bandeja de salida, que se desliza hacia la
        // cinta con el hueso "cargo" (2026-10-04, "el cargo tiene una prenda random
        // habria q ponerle el icono de la prenda y empalmarla con la animacion de la
        // cinta"): reemplaza al cubito de color que traía el modelo.
        addRenderLayer(new com.modamod.render.PrendaEnMaquinaLayer<>(this, "cargo",
                be -> be.salidaVisible(), 5.6f, 5.08f, 0f, 2.4f,
                com.modamod.render.PrendaEnMaquinaLayer.Apoyo.ACOSTADA_FRENTE_MENOS_Z));
    }
}
