package com.modamod.item;


/**
 * Máscaras y barbijo (2026-10-24, "quiero una mascara y un barbijo tinteables y sublimables"): antifaz, media máscara y
 * máscara completa van en el slot {@code head/mascara}; el barbijo, en {@code head/barbijo} (se pueden usar juntos). El
 * modelo sale de Blockbench ({@code docs/modelo_cara/cara.bbmodel} → {@code assets/modamod/cara/<modelo>.json}) y lo
 * dibuja {@code render.CaraRenderer}: tela blanca con las capas de la Estación de Tintes ({@code CAPAS_TINTE}) y la foto
 * de la Sublimadora en el frente ({@code Estampas#frente}).
 */
public class CaraItem extends com.modamod.prenda.PrendaItem {

    /** El nombre del modelo ({@code cara/<modelo>.json}) y del ítem. */
    public final String modelo;
    public final boolean barbijo;

    /**
     * El modelo del casco para apliques (2026-11-12, "una mascara mas que sea tipo casco y cubra toda la cabeza
     * especificamente para apliques"): una caja alrededor de toda la cabeza para apoyar apliques en cualquier lado. Sin
     * teñir ni foto no dibuja su tela, y nunca borra el relieve de la cabeza de abajo (no está en {@code ComposedSkin.TAPA}).
     */
    public static final String CASCO = "casco";

    public CaraItem(Settings settings, String modelo, boolean barbijo) {
        super(settings);
        this.modelo = modelo;
        this.barbijo = barbijo;
    }
}
