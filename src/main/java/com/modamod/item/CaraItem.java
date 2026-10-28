package com.modamod.item;

import dev.emi.trinkets.api.TrinketItem;

/**
 * Máscaras y barbijo (2026-10-24, "quiero una mascara y un barbijo tinteables y sublimables"): antifaz, media máscara y
 * máscara completa van en el slot {@code head/mascara}; el barbijo, en {@code head/barbijo} (se pueden usar juntos). El
 * modelo sale de Blockbench ({@code docs/modelo_cara/cara.bbmodel} → {@code assets/modamod/cara/<modelo>.json}) y lo
 * dibuja {@code render.CaraRenderer}: tela blanca con las capas de la Estación de Tintes ({@code CAPAS_TINTE}) y la foto
 * de la Sublimadora en el frente ({@code ESTAMPA_FRENTE}).
 */
public class CaraItem extends TrinketItem {

    /** El nombre del modelo ({@code cara/<modelo>.json}) y del ítem. */
    public final String modelo;
    public final boolean barbijo;

    public CaraItem(Settings settings, String modelo, boolean barbijo) {
        super(settings);
        this.modelo = modelo;
        this.barbijo = barbijo;
    }
}
