package com.modamod.item;

import com.modamod.sublimadora.RemeraItem;
import net.minecraft.item.ItemStack;

/**
 * Los rasgos de "chaqueta" de un Top (2026-10-07, "un solo top": la remera y la chaqueta se fusionan en un solo ítem,
 * {@link RemeraItem}): frente abierto, capucha (con su tecla), solapas. Reemplaza a {@code ChaquetaItem}. Ninguno es
 * obligatorio: una remera sin capucha, con frente cerrado y sin solapas es la remera de siempre. Dónde se dibuja
 * (capa interior o exterior) lo decide el slot de Trinkets en que se lleva ({@code torso/prenda} o
 * {@code torso/chaqueta}), ver {@link #exterior}.
 */
public final class TopCorte {
    private TopCorte() {}

    public static boolean capuchaArriba(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModamodComponents.CAPUCHA_ARRIBA));
    }

    public static void setCapuchaArriba(ItemStack stack, boolean arriba) {
        if (arriba) stack.set(ModamodComponents.CAPUCHA_ARRIBA, true);
        else stack.remove(ModamodComponents.CAPUCHA_ARRIBA);
    }

    /** El frente del corte (el molde de la Modeladora), sin contar la tecla: cerrado (de siempre), abierto, etc. */
    public static ChaquetaFrente frenteDeCorte(ItemStack stack) {
        ChaquetaFrente f = stack.get(ModamodComponents.CHAQUETA_FRENTE);
        return f == null ? ChaquetaFrente.CERRADA : f;
    }

    /**
     * El frente que se ve ahora (2026-10-11, "un keybind para abrir y cerrar la chaqueta"): el del corte, salvo que la
     * tecla lo haya abierto (un corte cerrado se ve {@code ABIERTA}) o cerrado ({@code CERRADA}).
     */
    public static ChaquetaFrente frente(ItemStack stack) {
        Boolean ahora = stack.get(ModamodComponents.FRENTE_AHORA);
        ChaquetaFrente corte = frenteDeCorte(stack);
        if (ahora == null) return corte;
        if (!ahora) return ChaquetaFrente.CERRADA;
        return corte.abre() ? corte : ChaquetaFrente.ABIERTA;
    }

    /** Pone el frente del corte (lo usa la Modeladora) y suelta lo que había hecho la tecla. */
    public static void setFrente(ItemStack stack, ChaquetaFrente f) {
        stack.remove(ModamodComponents.FRENTE_AHORA);
        if (f == ChaquetaFrente.CERRADA) stack.remove(ModamodComponents.CHAQUETA_FRENTE);
        else stack.set(ModamodComponents.CHAQUETA_FRENTE, f);
    }

    /** Abre o cierra el frente ahora (la tecla); si queda como dice el corte, no guarda nada. */
    public static void setFrenteAbierto(ItemStack stack, boolean abierto) {
        if (frenteDeCorte(stack).abre() == abierto) stack.remove(ModamodComponents.FRENTE_AHORA);
        else stack.set(ModamodComponents.FRENTE_AHORA, abierto);
    }

    /** Nivel de rango (0..6) del faldón de malla, o −1 si no tiene (2026-10-11, "las chaquetas de malla"). */
    public static int faldon(ItemStack stack) {
        Integer n = stack.get(ModamodComponents.FALDON_TOP);
        return n == null ? -1 : n;
    }

    public static void setFaldon(ItemStack stack, int nivel) {
        if (nivel < 0 || nivel > 6) stack.remove(ModamodComponents.FALDON_TOP);
        else stack.set(ModamodComponents.FALDON_TOP, Math.min(6, nivel));
    }

    /** El largo del faldón: el nivel 0 es el {@code CERO} (3 px); del 1 al 6, de {@code MINIMO} a {@code MAXIMO}. */
    public static PolleraLargo largoFaldon(ItemStack stack) {
        int n = faldon(stack);
        return n <= 0 ? PolleraLargo.CERO : PolleraLargo.values()[n - 1];
    }

    /** Solapas: ninguna (de siempre), en pico, redondas o chal. */
    public static ChaquetaSolapa solapa(ItemStack stack) {
        ChaquetaSolapa v = stack.get(ModamodComponents.CHAQUETA_SOLAPA);
        return v == null ? ChaquetaSolapa.NINGUNA : v;
    }

    public static void setSolapa(ItemStack stack, ChaquetaSolapa v) {
        if (v == ChaquetaSolapa.NINGUNA) stack.remove(ModamodComponents.CHAQUETA_SOLAPA);
        else stack.set(ModamodComponents.CHAQUETA_SOLAPA, v);
    }

    /** ¿Lleva capucha? Por defecto no. Los cordones y el bolsillo canguro son aparte (2026-10-14). */
    /** Cuboides o malla de la capucha (2026-10-23): la del top y la de la capa; sin componente, malla. */
    public static SombreroEstilo estiloCapucha(ItemStack stack) {
        SombreroEstilo e = stack.get(ModamodComponents.CAPUCHA_ESTILO);
        return e == null ? SombreroEstilo.MALLA : e;
    }

    public static void setEstiloCapucha(ItemStack stack, SombreroEstilo v) {
        if (v == SombreroEstilo.MALLA) stack.remove(ModamodComponents.CAPUCHA_ESTILO);
        else stack.set(ModamodComponents.CAPUCHA_ESTILO, v);
    }

    public static boolean conCapucha(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModamodComponents.CON_CAPUCHA));
    }

    public static void setConCapucha(ItemStack stack, boolean con) {
        if (con) stack.set(ModamodComponents.CON_CAPUCHA, true);
        else stack.remove(ModamodComponents.CON_CAPUCHA);
    }

    /** Capucha que de verdad se dibuja o se sube con la tecla: un Top con capucha. */
    public static boolean tieneCapucha(ItemStack stack) {
        return stack.getItem() instanceof RemeraItem && conCapucha(stack);
    }

    /**
     * ¿Se dibuja en la capa exterior (la de las chaquetas, por encima de la remera y la pollera)? Es una marca que
     * solo llevan las COPIAS que se arman para dibujar (ver {@code GarmentFeatureRenderer#equipadas}) cuando el Top está
     * en el slot de Trinkets {@code torso/chaqueta}; nunca se guarda en la prenda real.
     */
    public static boolean exterior(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModamodComponents.CAPA_EXTERIOR));
    }

    /** Copia para dibujar en la capa exterior. */
    public static ItemStack comoExterior(ItemStack stack) {
        ItemStack copia = stack.copy();
        copia.set(ModamodComponents.CAPA_EXTERIOR, true);
        return copia;
    }

    /** ¿Tiene algún rasgo de chaqueta (capucha, solapas o frente abierto)? Si no, es la remera de siempre. */
    public static boolean conRasgosDeChaqueta(ItemStack stack) {
        return conCapucha(stack) || solapa(stack) != ChaquetaSolapa.NINGUNA || frenteDeCorte(stack) != ChaquetaFrente.CERRADA
                || faldon(stack) >= 0;
    }
}
