package com.modamod.modelado;

import com.modamod.item.PatronRed;
import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Molde de Red (Fina/Gruesa) — a pedido (2026-09-20): mismo criterio que
 * {@link MoldeCalceItem}, transversal a las 4 categorías, sin anclaje ni
 * lado. Ver {@link PatronRed}.
 */
public class MoldeRedItem extends Item {

    public final PatronRed valor;

    public MoldeRedItem(Settings settings, PatronRed valor) {
        super(settings);
        this.valor = valor;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "trama", PrendaLore.Maquina.MODELADORA, "remera", "pantalon", "medias", "calientabrazos");
        tooltip.add(Text.translatable("modamod.sublimadora.molde.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
