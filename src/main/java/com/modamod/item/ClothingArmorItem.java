package com.modamod.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Prenda que ocupa un slot vanilla (helmet/chest/legs/boots) — pensada para
 * usarse en el slot COSMÉTICO que agrega Cosmetic Armor Updated, no en el
 * slot real de combate. Dyeable igual que el cuero.
 */
public class ClothingArmorItem extends ArmorItem {

    /** Las prendas que pasan por la sublimadora avisan si estan impresas. */
    @Override
    public void appendTooltip(net.minecraft.item.ItemStack stack, TooltipContext context,
                              java.util.List<net.minecraft.text.Text> tooltip,
                              net.minecraft.item.tooltip.TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        if (com.modamod.sublimadora.ModItems.esEstampable(stack)) {
            com.modamod.sublimadora.RemeraItem.tooltipEstampa(stack, tooltip);
        }
    }


    public final boolean dyeable;
    public final boolean skinFillTorso;

    public ClothingArmorItem(RegistryEntry<ArmorMaterial> material, ArmorItem.Type type, Settings settings,
                              boolean dyeable, boolean skinFillTorso) {
        super(material, type, settings);
        this.dyeable = dyeable;
        this.skinFillTorso = skinFillTorso;
    }

    public ClothingArmorItem(RegistryEntry<ArmorMaterial> material, ArmorItem.Type type, Settings settings,
                              boolean dyeable) {
        this(material, type, settings, dyeable, false);
    }

    public int getColor(ItemStack stack) {
        if (!dyeable) return 0xFFFFFF;
        DyedColorComponent c = stack.get(DataComponentTypes.DYED_COLOR);
        return c != null ? c.rgb() : 0xFFFFFF;
    }

    public static void setColor(ItemStack stack, int rgb) {
        stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(rgb, true));
    }
}
