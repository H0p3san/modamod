package com.modamod.tinturas;

import com.modamod.render.LedGlowLayer;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Registro (ClientModInitializer): BlockEntityRendererFactories.register(TinturasMod.TINTURAS_BLOCK_ENTITY, ctx -> new TinturasRenderer()). */
public class TinturasRenderer extends GeoBlockRenderer<TinturasBlockEntity> {
    public TinturasRenderer() {
        super(new TinturasGeoModel());
        addRenderLayer(new LedGlowLayer<>(this, TinturasGeoModel.ANCHO_ATLAS, TinturasGeoModel.ALTO_ATLAS,
                TinturasGeoModel.LEDS, TinturasGeoModel::coloresLed));
        // La prenda enrollada en el rodillo con su ícono real (2026-09-29,
        // "reemplazar esos huesos por el item nuevo" / "enredar el icono
        // alrededor del rodillo"): ancla en un hijo de "roller", así hereda
        // el giro. Solo mientras tiñe.
        // Pantallita siempre iluminada (2026-09-29).
        addRenderLayer(new com.modamod.render.PantallaGlowLayer<>(this,
                be -> com.modamod.render.PantallaMaquina.glow(TinturasGeoModel.TEX,
                        com.modamod.render.PantallaMaquina.PANEL_TINTURAS, be.getPos())));
        addRenderLayer(new com.modamod.render.PrendaEnMaquinaLayer<>(this, "prenda_remera",
                // Sigue en el rodillo mientras espera en la Salida (2026-09-29,
                // "desaparece cuando se termina de procesar en vez de cuando se retira").
                be -> be.estado() == TinturasBlockEntity.Estado.TINIENDO ? be.getPrendaEntrada() : be.getSalida(),
                // Enrollada (2026-09-29; z 3.2 → -3.2 al girar el modelo 2026-10-15): eje del rodillo en (y 12.2, z -3.2), largo 12.4;
                // su sección (dos cuadrados de 2.9 cruzados) llega a ~2.05 del eje.
                0f, 12.2f, -3.2f, 12f, 2.2f, com.modamod.render.PrendaEnMaquinaLayer.Apoyo.ENROLLADA_EJE_X_FRENTE_MENOS_Z));
        // La prenda terminada sobre la bandeja de salida, que se desliza hacia la
        // cinta con el hueso "cargo" (2026-10-04, "el cargo tiene una prenda random
        // habria q ponerle el icono de la prenda y empalmarla con la animacion de la
        // cinta"): reemplaza al cubito de color que traía el modelo.
        addRenderLayer(new com.modamod.render.PrendaEnMaquinaLayer<>(this, "cargo",
                be -> be.getSalida(), -5.6f, 5.08f, 0f, 2.4f,
                com.modamod.render.PrendaEnMaquinaLayer.Apoyo.ACOSTADA_CUELLO_MENOS_Z));
    }
}
