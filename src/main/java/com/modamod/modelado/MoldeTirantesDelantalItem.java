package com.modamod.modelado;

import com.modamod.item.DelantalTirantes;
import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Molde de tirantes del delantal (2026-10-11): sin tirantes, rectos o cruzados en la espalda. */
public class MoldeTirantesDelantalItem extends Item {

    public final DelantalTirantes valor;

    public MoldeTirantesDelantalItem(Settings settings, DelantalTirantes valor) {
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
