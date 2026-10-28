package com.modamod.correa;

import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Molde de correa (2026-10-05, "correas libres" + "molde de cadenas"): elige el estilo de la correa que se pone en la
 * Mesa de estilado (lisa, cadena, cadena fina, ojalillos o cordón). No se gasta.
 */
public class MoldeCorreaItem extends Item {

    public final EstiloCorrea estilo;

    public MoldeCorreaItem(Settings settings, EstiloCorrea estilo) {
        super(settings);
        this.estilo = estilo;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "correa", PrendaLore.Maquina.ESTILADO, "todas");
        tooltip.add(Text.translatable("modamod.correa.molde.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
