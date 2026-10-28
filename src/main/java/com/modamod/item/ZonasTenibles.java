package com.modamod.item;

import com.modamod.garment.Parte;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Un ítem puesto con 3 zonas que se pintan y se dibujan en la Mesa de estilado (2026-10-05): el sombrero de bruja
 * (ala, cono, cinta) y la banda (banda, borde, herraje). Los dos se dibujan con cajas en el marco de una parte del
 * cuerpo ({@link #marco}) y llevan sus propios apliques.
 */
public interface ZonasTenibles {
    List<Integer> coloresDe(ItemStack stack);

    ItemStack conColoresDe(ItemStack stack, int a, int b, int c);

    List<SombreroPatron> patronesDe(ItemStack stack);

    ItemStack conPatronDe(ItemStack stack, int zona, SombreroPatron patron);

    /** Lo mismo del pie izquierdo (2026-10-11): solo los borcegos distinguen los dos pies; el resto repite lo de siempre. */
    default List<Integer> coloresDe(ItemStack stack, boolean izq) { return coloresDe(stack); }

    default ItemStack conColoresDe(ItemStack stack, int a, int b, int c, boolean izq) { return conColoresDe(stack, a, b, c); }

    default List<SombreroPatron> patronesDe(ItemStack stack, boolean izq) { return patronesDe(stack); }

    default ItemStack conPatronDe(ItemStack stack, int zona, SombreroPatron patron, boolean izq) { return conPatronDe(stack, zona, patron); }

    /** ¿Tiene dos lados que se pintan por separado (los borcegos)? */
    default boolean tienePares() { return false; }

    /** Clave de traducción del nombre de la zona {@code i} (0..2). */
    String claveZona(ItemStack stack, int i);

    /** La parte del cuerpo en cuyo marco se dibujan sus cajas. */
    Parte marco(ItemStack stack);
}
