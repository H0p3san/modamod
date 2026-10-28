package com.modamod.tinturas;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** Misma geometría/textura que el bloque, para el ícono del inventario. */
public class TinturasItemGeoModel extends GeoModel<TinturasBlockItem> {
    private static final Identifier GEO =
            Identifier.of(Modamod.MOD_ID, "geo/dye_station.geo.json");
    private static final Identifier TEX =
            Identifier.of(Modamod.MOD_ID, "textures/block/dye_station_atlas.png");
    private static final Identifier ANIM =
            Identifier.of(Modamod.MOD_ID, "animations/dye_station.animation.json");

    @Override public Identifier getModelResource(TinturasBlockItem item) { return GEO; }
    @Override public Identifier getTextureResource(TinturasBlockItem item) { return com.modamod.util.MaquinaCreativa.textura(TEX, com.modamod.util.MaquinaCreativa.es(item)); }
    @Override public Identifier getAnimationResource(TinturasBlockItem item) { return ANIM; }
}
