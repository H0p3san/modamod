package com.modamod.aplique;

import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Molde de aplique (2026-10-01): define la FORMA (moño, mariposa, flor). Va en
 * la Mesa de estilado y no se gasta, como los moldes de patrón; los colores
 * salen del {@link RetazoApliqueItem}.
 */
public class MoldeApliqueItem extends Item {

    public final ModeloAplique modelo;
    /** Con qué fila sale puesto (2026-10-22: la botonera es el botón con 4 en fila). */
    public final Fila fila;

    public MoldeApliqueItem(Settings settings, ModeloAplique modelo) {
        this(settings, modelo, Fila.DEFECTO);
    }

    public MoldeApliqueItem(Settings settings, ModeloAplique modelo, Fila fila) {
        super(settings);
        this.modelo = modelo;
        this.fila = fila;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "aplique", PrendaLore.Maquina.ESTILADO, "todas");
        tooltip.add(Text.translatable("modamod.aplique.molde.tooltip").formatted(Formatting.GRAY));
    }
}
