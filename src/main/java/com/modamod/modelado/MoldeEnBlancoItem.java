package com.modamod.modelado;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Los ítems en blanco (2026-10-12, "agregar un molde en blanco, patron en blanco, mascara en blanco, trama en
 * blanco y despues perfilarlo" + "que todos los moldes 'sin' no existan"): materia prima de los moldes (se perfilan
 * por crafteo con un ingrediente) y, puestos en un pin de la Modeladora, "quitar este rasgo": el molde en blanco
 * restablece el valor de fábrica de ese rasgo ({@code ModeladoBlockEntity#comboEnBlanco}) y la trama en blanco
 * deja la tela sin trama. Patrón, máscara y plantilla en blanco solo son materia prima.
 */
public class MoldeEnBlancoItem extends Item {

    /** Nuevos al final (no viajan por red, pero por prolijidad). */
    public enum Tipo { MOLDE, TRAMA, PATRON, MASCARA, PLANTILLA;
        public String clave() { return name().toLowerCase(); }
    }

    public final Tipo tipo;

    public MoldeEnBlancoItem(Settings settings, Tipo tipo) {
        super(settings);
        this.tipo = tipo;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("modamod.blanco." + tipo.clave() + ".ayuda").formatted(Formatting.GRAY));
        if (tipo == Tipo.MOLDE || tipo == Tipo.TRAMA) {
            tooltip.add(Text.translatable("modamod.blanco.quitar").formatted(Formatting.DARK_GRAY));
        }
    }
}
