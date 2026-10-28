package com.modamod.render;

import com.modamod.garment.Parte;

/**
 * Donde cae cada parte del cuerpo en una textura con layout de skin.
 *
 * Un solo lugar con estos numeros. Estaban repartidos entre SkinRegions (como
 * rectangulos literales), los dos renderers (como llamadas a {@code uv(...)})
 * y el compositor de estampas, y las tres copias tenian que coincidir sin que
 * nada lo verificara.
 *
 * Verificado contra PlayerEntityModel.getTexturedModelData.
 */
public final class LayoutSkin {

    /** Lado de la textura de skin, en pixeles, a escala 1. */
    public static final int LADO = 64;

    private LayoutSkin() {}

    /**
     * El cuboide de esa parte en la capa BASE.
     *
     * @param slim el modelo de brazos finos (Alex). Cambia el ancho del brazo
     *             de 4 a 3; sin esto la manga queda visiblemente ancha,
     *             flotando por fuera del brazo.
     */
    public static CajaSkin base(Parte parte, boolean slim) {
        int brazo = slim ? 3 : 4;
        return switch (parte) {
            case CABEZA     -> new CajaSkin(0, 0, 8, 8, 8);
            case TORSO      -> new CajaSkin(16, 16, 8, 12, 4);
            case BRAZO_DER  -> new CajaSkin(40, 16, brazo, 12, 4);
            case BRAZO_IZQ  -> new CajaSkin(32, 48, brazo, 12, 4);
            case PIERNA_DER -> new CajaSkin(0, 16, 4, 12, 4);
            case PIERNA_IZQ -> new CajaSkin(16, 48, 4, 12, 4);
        };
    }

    /**
     * El mismo cuboide en la SEGUNDA capa (hat, jacket, sleeves, pants).
     *
     * Es la que hay que borrar donde va una prenda: vanilla la dibuja inflada
     * 0.25 y 3D Skin Layers la convierte en geometria 3D real, asi que una
     * remera pintada ahi le pasa por encima a la prenda del mod.
     */
    public static CajaSkin overlay(Parte parte, boolean slim) {
        CajaSkin b = base(parte, slim);
        return switch (parte) {
            case CABEZA     -> b.en(32, 0);
            case TORSO      -> b.en(16, 32);
            case BRAZO_DER  -> b.en(40, 32);
            case BRAZO_IZQ  -> b.en(48, 48);
            case PIERNA_DER -> b.en(0, 32);
            case PIERNA_IZQ -> b.en(0, 48);
        };
    }
}
