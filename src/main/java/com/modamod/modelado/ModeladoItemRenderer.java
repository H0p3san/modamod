package com.modamod.modelado;

import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Dibuja el ítem del bloque con la malla de GeckoLib (icono = bloque real). */
public class ModeladoItemRenderer extends GeoItemRenderer<ModeladoBlockItem> {
    public ModeladoItemRenderer() {
        super(new ModeladoItemGeoModel());
    }
}
