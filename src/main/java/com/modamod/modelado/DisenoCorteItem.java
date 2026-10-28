package com.modamod.modelado;

import com.modamod.item.ModamodComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

/**
 * Diseño de corte (2026-10-12): los cortes fijados de una categoría de la Modeladora hechos ítem, para sacarlos de la
 * máquina, guardarlos en la bolsa de diseños e intercambiarlos. Se crea con el "+" de la Modeladora gastando un
 * "diseño en blanco" y no se consume al cargarlo.
 */
public class DisenoCorteItem extends Item {
    public DisenoCorteItem(Settings settings) { super(settings); }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        DisenoCorte d = stack.get(ModamodComponents.DISENO_CORTE);
        if (d == null) return;
        tooltip.add(Text.translatable("modamod.modelado.diseno.categoria",
                Text.translatable("modamod.modelado.categoria." + d.categoria().toLowerCase(Locale.ROOT))).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("modamod.modelado.diseno.cortes", d.combos().size()).formatted(Formatting.DARK_GRAY));
    }
}
