package com.modamod.modelado;

import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Moldes de Capa — a pedido (2026-09-29, "uy podemos agregar todo eso como
 * patrones de corte?"): el ruedo, la capucha y el cuello alto son moldes de
 * la categoría Capa de la Modeladora, cada uno en su pin (el largo usa el
 * molde de rango de siempre). Los "sin" sacan la capucha o el cuello a una
 * capa que ya los tiene. Exclusivos de la capa.
 */
public class MoldeCapaItem extends Item {

    public enum Tipo {
        CON_CAPUCHA, SIN_CAPUCHA, CUELLO_ALTO, SIN_CUELLO,
        /** Embozo sobre los hombros (2026-10-11): el largo sale del molde de rango; este lo quita. */
        EMBOZO_NINGUNO, BORDE_EMBOZO, BORDE_NINGUNO,
        /** Capucha de malla o de cajas (2026-10-23): ponen la capucha con ese estilo, en el top o en la capa. Al final. */
        CAPUCHA_MALLA, CAPUCHA_CAJAS;

        public boolean esCapucha() { return this == CON_CAPUCHA || this == SIN_CAPUCHA || esEstiloCapucha(); }
        public boolean esEstiloCapucha() { return this == CAPUCHA_MALLA || this == CAPUCHA_CAJAS; }
        public boolean esEmbozo() { return this == EMBOZO_NINGUNO; }
        public boolean esBordeEmbozo() { return this == BORDE_EMBOZO || this == BORDE_NINGUNO; }
        public boolean esCuello() { return this == CUELLO_ALTO || this == SIN_CUELLO; }
    }

    public final Tipo tipo;

    public MoldeCapaItem(Settings settings, Tipo tipo) {
        super(settings);
        this.tipo = tipo;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "capa", PrendaLore.Maquina.MODELADORA, "capa");
        tooltip.add(Text.translatable("modamod.sublimadora.molde.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
