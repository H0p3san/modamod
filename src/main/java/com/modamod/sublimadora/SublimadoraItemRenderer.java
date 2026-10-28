package com.modamod.sublimadora;

import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Dibuja el item del bloque con la malla de GeckoLib.
 *
 * Lo engancha GeckoLib solo: su mixin sobre BuiltinModelItemRenderer consulta
 * el GeoRenderProvider del item, y el modelo JSON del item hereda de
 * "builtin/entity" para que vanilla llegue hasta ese renderer.
 */
public class SublimadoraItemRenderer extends GeoItemRenderer<SublimadoraBlockItem> {
    public SublimadoraItemRenderer() {
        super(new SublimadoraItemGeoModel());
    }
}
