package com.modamod.estilista;

import com.modamod.render.LedGlowLayer;
import com.modamod.render.PrendaEnMaquinaLayer;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Registro (ClientModInitializer): BlockEntityRendererFactories.register(EstilistaMod.ESTILISTA_BLOCK_ENTITY, ctx -> new EstilistaRenderer()). */
public class EstilistaRenderer extends GeoBlockRenderer<EstilistaBlockEntity> {
    public EstilistaRenderer() {
        super(new EstilistaGeoModel());
        addRenderLayer(new LedGlowLayer<>(this, EstilistaGeoModel.ANCHO_ATLAS, EstilistaGeoModel.ALTO_ATLAS,
                EstilistaGeoModel.leds(), EstilistaGeoModel::coloresLed));
        addRenderLayer(new com.modamod.render.PantallaGlowLayer<>(this,
                be -> com.modamod.render.PantallaMaquina.glow(EstilistaGeoModel.atlas(be),
                        com.modamod.render.PantallaMaquina.PANEL_128, be.getPos())));
        // La prenda cargada con su ícono real, acostada sobre la cama de la tapa (centro del hueso "garment").
        addRenderLayer(new PrendaEnMaquinaLayer<>(this, "garment", EstilistaBlockEntity::prendaVisible,
                0.5f, 11.95f, -1.6f, 10f, PrendaEnMaquinaLayer.Apoyo.ACOSTADA_FRENTE_MENOS_Z));
        // La prenda terminada sobre la bandeja de salida (se desliza con el hueso "cargo").
        addRenderLayer(new PrendaEnMaquinaLayer<>(this, "cargo", EstilistaBlockEntity::salidaVisible,
                5.6f, 5.08f, 0f, 2.4f, PrendaEnMaquinaLayer.Apoyo.ACOSTADA_FRENTE_MENOS_Z));
    }
}
