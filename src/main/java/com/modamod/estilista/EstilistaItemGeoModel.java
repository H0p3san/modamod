package com.modamod.estilista;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/** Misma geometría y textura que el bloque, para el ícono del inventario. */
public class EstilistaItemGeoModel extends GeoModel<EstilistaBlockItem> {
    @Override public Identifier getModelResource(EstilistaBlockItem item) { return EstilistaGeoModel.GEO; }

    @Override
    public Identifier getTextureResource(EstilistaBlockItem item) {
        return com.modamod.util.MaquinaCreativa.textura(EstilistaGeoModel.TEX, com.modamod.util.MaquinaCreativa.es(item));
    }

    @Override public Identifier getAnimationResource(EstilistaBlockItem item) { return EstilistaGeoModel.ANIM; }

    @Override
    public void setCustomAnimations(EstilistaBlockItem item, long instanceId, AnimationState<EstilistaBlockItem> state) {
        super.setCustomAnimations(item, instanceId, state);
        // Pose fija del ícono: la barra a mitad de camino y las piezas del pórtico escondidas.
        GeoBone progreso = getAnimationProcessor().getBone("progress");
        if (progreso != null) {
            progreso.setScaleX(0.5f);
            progreso.setHidden(false);
        }
        for (String h : EstilistaGeoModel.HUESOS_DE_PRENDA) {
            GeoBone b = getAnimationProcessor().getBone(h);
            if (b != null) b.setHidden(true);
        }
    }
}
