package com.modamod.item;

import com.modamod.region.Lado;

/**
 * Cómo se llama cada prenda según su forma (2026-10-23, "como es la convencion de nombrado de prendas?" → "me gusta todo,
 * solo agregaria que si las medias tienen recortada la parte de abajo pasen a llamarse calientapiernas o calzas depende
 * del caso y calientabrazos tambien como guantes brazaletes etc"). La regla es la de la remera: el rasgo que más la define
 * da el nombre y el resto va al tooltip. Las que tienen un largo por lado usan el derecho; si los dos lados no coinciden,
 * queda el nombre genérico.
 *
 * <p>Las filas son las de {@link EjeBilateral#interseccion}: [desde, hasta) de las 12 de la pierna o el brazo, 0 arriba.
 */
public final class NombresDePrenda {

    private NombresDePrenda() {}

    /** Pantalón por dónde termina la pierna: shorts, bermudas, 3/4, 7/8 o largo. */
    public static String pantalon(int[] der, int[] izq) {
        if (der[1] != izq[1]) return "pantalon";
        int hasta = der[1];
        return hasta >= 10 ? "pantalon" : hasta >= 8 ? "siete_octavos" : hasta >= 6 ? "tres_cuartos" : hasta >= 4 ? "bermudas" : "shorts";
    }

    /**
     * Medias: con pie (la tela llega a la fila 12) son soquetes, medias, medias 3/4 o bucaneras según dónde empiezan; sin pie
     * son calzas si arrancan arriba del muslo (cortas si no pasan la rodilla) y calientapiernas si no.
     */
    public static String medias(int[] f) {
        int desde = f[0], hasta = f[1];
        if (desde >= hasta) return "medias";
        if (hasta >= 12) return desde >= 10 ? "soquetes" : desde >= 6 ? "medias" : desde >= 4 ? "medias_tres_cuartos" : "bucaneras";
        if (desde <= 2) return hasta <= 6 ? "calzas_cortas" : "calzas";
        return "calientapiernas";
    }

    /**
     * Calentadores de brazo: si llegan a la mano y no al hombro son guantes (largos si pasan el antebrazo); una tira angosta
     * que no llega a la mano es un brazalete; lo demás, calentadores.
     */
    public static String calientabrazos(int[] f) {
        int desde = f[0], hasta = f[1];
        if (desde >= hasta) return "calientabrazos";
        if (hasta >= 12 && desde >= 10) return "guantes";
        if (hasta >= 12 && desde >= 6) return "guantes_largos";
        if (hasta < 12 && hasta - desde <= 2) return "brazalete";
        return "calientabrazos";
    }

    /** Sombrero por la copa o el ala que más lo define. */
    public static String sombrero(SombreroAla ala, SombreroPunta copa) {
        return switch (copa) {
            case PANADERO -> "gorra_panadero";
            case TOQUE -> "gorro_cocinero";
            case MARINERO -> "gorro_marinero";
            case PLATO -> "boina";
            case CHARRO -> "sombrero_charro";
            case CILINDRO_ALTO -> "galera";
            default -> switch (ala) {
                case VISERA_CURVA, VISERA_CHATA, VISERA_CORTA -> "gorra";
                case TRICORNIO -> "tricornio";
                case VAQUERA -> "sombrero_vaquero";
                default -> copa == SombreroPunta.RECTA || copa == SombreroPunta.DOBLADA ? (ala == SombreroAla.SIN_ALA ? "gorro" : "sombrero_bruja")
                        : copa == SombreroPunta.GORRO_CAIDO || ala == SombreroAla.SIN_ALA ? "gorro" : "sombrero";
            };
        };
    }

    /** Las filas del lado derecho y del izquierdo de una prenda con un largo por lado. */
    public static int[][] lados(java.util.function.BiFunction<net.minecraft.item.ItemStack, Lado, int[]> filas,
                                net.minecraft.item.ItemStack stack) {
        return new int[][] {filas.apply(stack, Lado.DERECHA), filas.apply(stack, Lado.IZQUIERDA)};
    }
}
