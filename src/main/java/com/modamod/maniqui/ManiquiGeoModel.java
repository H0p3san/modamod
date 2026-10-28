package com.modamod.maniqui;

import com.modamod.bloque.ModeloGeo;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Modelo {@code mannequin}: gira el hueso {@code turntable} (plato + figura)
 * con el ángulo que ya calculó {@link ManiquiRenderer} para este frame, en
 * vez de usar la animación {@code girar} del zip — así la ropa, que se
 * dibuja aparte, gira exactamente igual que la figura.
 */
public class ManiquiGeoModel extends ModeloGeo<ManiquiBlockEntity> {

    public ManiquiGeoModel() {
        super("mannequin");
    }

    @Override
    public void setCustomAnimations(ManiquiBlockEntity be, long instanceId, AnimationState<ManiquiBlockEntity> state) {
        super.setCustomAnimations(be, instanceId, state);
        GeoBone plato = getAnimationProcessor().getBone("turntable");
        if (plato != null) plato.setRotY(be.anguloVisible());
        // La figura rígida del zip se reemplaza por la articulada de
        // ManiquiRenderer (2026-09-30, poses). El ícono del ítem la sigue
        // mostrando: usa el ModeloGeo genérico, no este.
        GeoBone figura = getAnimationProcessor().getBone("figura");
        if (figura != null) figura.setHidden(true);
    }
}
