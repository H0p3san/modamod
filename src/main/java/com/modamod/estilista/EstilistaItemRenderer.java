package com.modamod.estilista;

import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Dibuja el ítem del bloque con la malla de GeckoLib (icono = bloque real). */
public class EstilistaItemRenderer extends GeoItemRenderer<EstilistaBlockItem> {
    public EstilistaItemRenderer() {
        super(new EstilistaItemGeoModel());
    }
}
