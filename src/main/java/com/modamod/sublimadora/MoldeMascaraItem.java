package com.modamod.sublimadora;

import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Molde de máscara de sublimación (2026-10-02, "agregar un mecanismo a la
 * sublimadora mascaras" → "ítem molde de máscara"): va en el slot de máscara
 * de la Sublimadora y le da la forma a las capas que se fijan con él. No se
 * gasta.
 */
public class MoldeMascaraItem extends Item {

    public final FormaMascara forma;

    public MoldeMascaraItem(Settings settings, FormaMascara forma) {
        super(settings);
        this.forma = forma;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "mascara", PrendaLore.Maquina.SUBLIMADORA, "remera", "pantalon", "medias", "calientabrazos", "pollera", "capa");
        tooltip.add(Text.translatable("modamod.sublimadora.mascara.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
