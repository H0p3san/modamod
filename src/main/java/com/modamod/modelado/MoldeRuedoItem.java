package com.modamod.modelado;

import com.modamod.item.PrendaLore;
import com.modamod.item.Ruedo;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Molde de ruedo (2026-10-07, "un ruedo ajustado que podria servir para ruedo de remera mangas botamangas y hasta
 * polleras" + "fusionemos borde pollera con ruedo"): va en el pin de ruedo de cada borde libre (ruedo del torso, puño de
 * cada manga, ruedo de la pollera...). Recto, ajustado y campana valen en cualquier tela; ondulado, festoneado y
 * pico solo en la pollera.
 */
public class MoldeRuedoItem extends Item {

    public final Ruedo valor;

    public MoldeRuedoItem(Settings settings, Ruedo valor) {
        super(settings);
        this.valor = valor;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        if (valor == Ruedo.COLA) {
            PrendaLore.molde(tooltip, "ruedo", PrendaLore.Maquina.MODELADORA, "capa");
        } else if (valor == Ruedo.REDONDEADO) {
            PrendaLore.molde(tooltip, "ruedo", PrendaLore.Maquina.MODELADORA, "capa", "delantal");
        } else if (valor == Ruedo.FRUNCIDO) {
            PrendaLore.molde(tooltip, "ruedo", PrendaLore.Maquina.MODELADORA, "medias", "calientabrazos");
        } else if (valor.valeEnTela()) {
            PrendaLore.molde(tooltip, "ruedo", PrendaLore.Maquina.MODELADORA, "remera", "chaqueta", "pantalon", "pollera");
        } else {
            PrendaLore.molde(tooltip, "ruedo", PrendaLore.Maquina.MODELADORA, "pollera");
        }
        tooltip.add(Text.translatable("modamod.ruedo.molde.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
