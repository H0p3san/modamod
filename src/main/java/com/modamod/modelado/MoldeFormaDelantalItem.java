package com.modamod.modelado;

import com.modamod.item.DelantalForma;
import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Molde de forma del delantal (2026-10-11, "las formas entran como piña"): va en el pin Forma de la categoría Delantal
 * de la Modeladora. Exclusivo del delantal. Ver {@link DelantalForma}.
 */
public class MoldeFormaDelantalItem extends Item {

    public final DelantalForma valor;

    public MoldeFormaDelantalItem(Settings settings, DelantalForma valor) {
        super(settings);
        this.valor = valor;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "delantal", PrendaLore.Maquina.MODELADORA, "delantal");
        tooltip.add(Text.translatable("modamod.sublimadora.molde.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
