package com.modamod.item;

import com.modamod.sublimadora.RemeraItem;
import net.minecraft.item.ItemStack;

/** Helper compartido: leer/escribir el color base sin importar si la prenda es un ClothingTrinketItem o un ClothingArmorItem (legacy). */
public final class ModamodDye {

    public static boolean isClothing(ItemStack stack) {
        // La remera de la sublimadora tambien es prenda para el telar: ahi se
        // le cambia el corte con los moldes. No hereda de ClothingTrinketItem
        // a proposito, tiene su propio sistema de color y de estampas.
        return stack.getItem() instanceof ClothingTrinketItem
                || stack.getItem() instanceof ClothingArmorItem
                || stack.getItem() instanceof RemeraItem;
    }

    public static void setBaseColor(ItemStack stack, int rgb) {
        if (stack.getItem() instanceof ClothingTrinketItem) {
            ClothingTrinketItem.setColor(stack, rgb);
        } else if (stack.getItem() instanceof ClothingArmorItem) {
            ClothingArmorItem.setColor(stack, rgb);
        }
    }

    private ModamodDye() {}
}
