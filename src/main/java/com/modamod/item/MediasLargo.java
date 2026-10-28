package com.modamod.item;

import com.modamod.Modamod;
import com.modamod.region.Lado;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Helpers de largo de las medias — ya NO es un enum propio (2026-09-23,
 * "botamanga sera aparte y solo funcionara para pantalones y medias"):
 * el eje de valores ahora es {@link Botamanga}, compartido con
 * {@link PantalonItem}. Esta clase queda como el lugar de las medias
 * (no hay un "MediasItem" separado, la prenda es un {@code Item} plano
 * — {@code ModamodItems.SOCKS_SOLID}), mismo patrón que
 * {@code PantalonItem} hospeda los suyos.
 *
 * <p>Dos anclajes que se intersecan (igual que antes, sin cambios de
 * comportamiento): SUPERIOR (muslo hacia abajo) e INFERIOR (tobillo
 * hacia arriba).
 */
public final class MediasLargo {
    private MediasLargo() {}

    /** Textura base COMPLETA de la media (12 filas) — el recorte real es en runtime, ver {@link #filasVisibles}. */
    public static final Identifier TEXTURA_BASE =
            Identifier.of(Modamod.MOD_ID, "textures/models/armor/medias_cancan_layer_1.png");

    // ── anclaje SUPERIOR: muslo hacia abajo ─────────────────────────────

    public static Botamanga superior(ItemStack stack, Lado lado) {
        return EjeBilateral.leer(stack, lado, ModamodComponents.MEDIAS_LARGO_SUPERIOR,
                ModamodComponents.RIGHT_MEDIAS_LARGO_SUPERIOR, Botamanga.PIE);
    }

    public static void setSuperior(ItemStack stack, Lado lado, Botamanga valor) {
        EjeBilateral.escribir(stack, lado, ModamodComponents.MEDIAS_LARGO_SUPERIOR,
                ModamodComponents.RIGHT_MEDIAS_LARGO_SUPERIOR, Botamanga.PIE, valor);
    }

    // ── anclaje INFERIOR: tobillo hacia arriba (el que ya existía) ──────

    public static Botamanga inferior(ItemStack stack, Lado lado) {
        return EjeBilateral.leer(stack, lado, ModamodComponents.MEDIAS_LARGO_INFERIOR,
                ModamodComponents.RIGHT_MEDIAS_LARGO_INFERIOR, Botamanga.PIE);
    }

    public static void setInferior(ItemStack stack, Lado lado, Botamanga valor) {
        EjeBilateral.escribir(stack, lado, ModamodComponents.MEDIAS_LARGO_INFERIOR,
                ModamodComponents.RIGHT_MEDIAS_LARGO_INFERIOR, Botamanga.PIE, valor);
    }

    /**
     * Filas [desde,hasta) con tela — intersección de los dos anclajes. Un
     * anclaje NUNCA tocado se trata como "12, abierto".
     */
    public static int[] filasVisibles(ItemStack stack, Lado lado) {
        boolean supAusente = !EjeBilateral.presente(stack, lado,
                ModamodComponents.MEDIAS_LARGO_SUPERIOR, ModamodComponents.RIGHT_MEDIAS_LARGO_SUPERIOR);
        int filasSup = supAusente ? 12 : superior(stack, lado).filas;
        int filasInf = inferior(stack, lado).filas; // PIE es el default correcto acá
        return EjeBilateral.interseccion(filasSup, filasInf);
    }
}
