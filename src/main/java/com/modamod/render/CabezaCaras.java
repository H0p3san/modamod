package com.modamod.render;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Las cabezas del mod CARAS (2026-10-25, "quiero q la capucha se vea por encima de las cabezas del faces mod" + "que los
 * sombreros también se vean sobre las cabezas de caras"): CARAS apaga la cabeza del modelo ({@code visible = false}) y
 * dibuja la suya, más grande (hasta ~1,3 px por arriba y ~1,2 px por los costados; el robot 2,8 px por arriba).
 */
public final class CabezaCaras {
    private CabezaCaras() {}

    /** CARAS es el único que además la marca {@code hidden}: así se distingue de una cabeza apagada de verdad. */
    public static boolean puesta(BipedEntityModel<?> biped) {
        return !biped.head.visible && biped.head.hidden;
    }

    /** La cabeza se ve, sea la de la skin o la de CARAS. */
    public static boolean visible(BipedEntityModel<?> biped) {
        return biped.head.visible || puesta(biped);
    }

    /** Con una cabeza de CARAS, agranda lo que se dibuje después {@code k} veces desde el cuello (el pivote de la cabeza). */
    public static void agrandar(MatrixStack matrices, BipedEntityModel<?> biped, float k) {
        agrandar(matrices, biped, k, 1F);
    }

    /** Lo mismo, y al menos {@code minimo} veces aunque no haya cabeza de CARAS (el casco, 2026-10-28): el mayor, no se suman. */
    public static void agrandar(MatrixStack matrices, BipedEntityModel<?> biped, float k, float minimo) {
        k = Math.max(puesta(biped) ? k : 1F, minimo);
        if (k <= 1F) return;
        float px = biped.head.pivotX / 16F, py = biped.head.pivotY / 16F, pz = biped.head.pivotZ / 16F;
        matrices.translate(px, py, pz);
        matrices.scale(k, k, k);
        matrices.translate(-px, -py, -pz);
    }
}
