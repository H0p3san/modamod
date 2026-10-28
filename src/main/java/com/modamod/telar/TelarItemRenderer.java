package com.modamod.telar;

import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Dibuja el ítem del bloque con la malla de GeckoLib (icono = bloque real). */
public class TelarItemRenderer extends GeoItemRenderer<TelarBlockItem> {
    public TelarItemRenderer() {
        super(new TelarItemGeoModel());
    }
}
