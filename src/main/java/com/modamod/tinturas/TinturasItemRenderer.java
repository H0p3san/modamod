package com.modamod.tinturas;

import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Dibuja el ítem del bloque con la malla de GeckoLib (icono = bloque real). */
public class TinturasItemRenderer extends GeoItemRenderer<TinturasBlockItem> {
    public TinturasItemRenderer() {
        super(new TinturasItemGeoModel());
    }
}
