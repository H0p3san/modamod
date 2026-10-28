package com.modamod.telar;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/** Misma geometría y textura que el bloque, para el ícono del inventario. */
public class TelarItemGeoModel extends GeoModel<TelarBlockItem> {
    @Override public Identifier getModelResource(TelarBlockItem item) { return TelarGeoModel.GEO; }

    @Override
    public Identifier getTextureResource(TelarBlockItem item) {
        return com.modamod.util.MaquinaCreativa.textura(TelarGeoModel.TEX, com.modamod.util.MaquinaCreativa.es(item));
    }

    @Override public Identifier getAnimationResource(TelarBlockItem item) { return TelarGeoModel.ANIM; }

    @Override
    public void setCustomAnimations(TelarBlockItem item, long instanceId, AnimationState<TelarBlockItem> state) {
        super.setCustomAnimations(item, instanceId, state);
        // Pose fija del ícono: la barra a mitad de camino y las piezas del pórtico escondidas.
        GeoBone progreso = getAnimationProcessor().getBone("progress");
        if (progreso != null) {
            progreso.setScaleX(0.5f);
            progreso.setHidden(false);
        }
        for (String h : TelarGeoModel.HUESOS_DE_PRENDA) {
            GeoBone b = getAnimationProcessor().getBone(h);
            if (b != null) b.setHidden(true);
        }
    }
}
