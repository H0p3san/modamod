package com.modamod.item;

import com.modamod.prenda.Parte;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Un accesorio de cajas con 3 zonas (por pie, en el calzado): el sombrero (ala, copa, cinta), la banda (banda, borde,
 * herraje), los borcegos y el maletín. Se tiñen en la Estación de Tintes, una región ZONA_n por zona. Los dos se dibujan con cajas en el marco de una parte del
 * cuerpo ({@link #marco}) y llevan sus propios apliques.
 */
public interface ZonasTenibles {
    /** El color de cada zona (0..2) sin teñir; lo que se tiñe en la Estación de Tintes va encima como capas. */
    List<Integer> coloresDeFabrica();

    /** Tiñe todas las zonas de un color liso (lo que sale del telar): una capa lisa por zona. */
    default void tenirEntero(ItemStack stack, int rgb) {
        List<com.modamod.region.RegionResolver.CapaPatron> capas = new java.util.ArrayList<>();
        int zonas = tienePares() ? 6 : 3;
        for (int z = 0; z < zonas; z++) {
            capas.add(new com.modamod.region.RegionResolver.CapaPatron(null, rgb & 0xFFFFFF, TamanoPatron.GRANDE, 0f, 0.5f,
                    com.modamod.render.PatronGenerador.Forma.ALTERNADO, false,
                    com.modamod.region.RegionPintura.values()[com.modamod.region.RegionPintura.ZONA_1.ordinal() + z]));
        }
        stack.set(ModamodComponents.CAPAS_TINTE, List.copyOf(capas));
    }

    /** ¿Tiene dos lados que se pintan por separado (los borcegos)? */
    default boolean tienePares() { return false; }

    /** Clave de traducción del nombre de la zona {@code i} (0..2). */
    String claveZona(ItemStack stack, int i);

    /** La parte del cuerpo en cuyo marco se dibujan sus cajas. */
    Parte marco(ItemStack stack);
}
