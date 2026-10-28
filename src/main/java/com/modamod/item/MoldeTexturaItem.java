package com.modamod.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Molde de textura de tela (2026-10-01, "pongamos un par de moldes de
 * prueba"): en la Mesa de estilado, con una prenda puesta, el botón Textura
 * le pone (o le saca) este volumen a la tela. No se gasta.
 */
public class MoldeTexturaItem extends Item {

    public final TexturaTela textura;

    public MoldeTexturaItem(TexturaTela textura, Settings settings) {
        super(settings);
        this.textura = textura;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        PrendaLore.molde(tooltip, "textura", PrendaLore.Maquina.ESTILADO, "todas");
        tooltip.add(Text.translatable("modamod.textura_tela.molde.tooltip").formatted(Formatting.GRAY));
    }
}
