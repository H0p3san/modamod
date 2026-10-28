package com.modamod.modelado;

import com.modamod.item.BandaAncho;
import com.modamod.item.BandaHerraje;
import com.modamod.item.BandaZona;
import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Moldes de la banda (2026-10-05, "correas y cintos"): la zona (cintura o cuello), el ancho (fino, medio, ancho) y el
 * herraje (ninguno, placa o aro) son moldes de la categoría Banda de la Modeladora, cada uno en su pin. Exclusivos
 * de la banda.
 */
public class MoldeBandaItem extends Item {

    public enum Tipo {
        ZONA_CINTURA(BandaZona.CINTURA, null, null), ZONA_CUELLO(BandaZona.CUELLO, null, null),
        ANCHO_FINO(null, BandaAncho.FINO, null), ANCHO_MEDIO(null, BandaAncho.MEDIO, null),
        ANCHO_ANCHO(null, BandaAncho.ANCHO, null),
        HERRAJE_NINGUNO(null, null, BandaHerraje.NINGUNO), HERRAJE_PLACA(null, null, BandaHerraje.PLACA),
        HERRAJE_ARO(null, null, BandaHerraje.ARO),
        HERRAJE_CAMPANA(null, null, BandaHerraje.CAMPANA), HERRAJE_HUESO(null, null, BandaHerraje.HUESO),
        HERRAJE_CORAZON(null, null, BandaHerraje.CORAZON), HERRAJE_MEDALLA(null, null, BandaHerraje.MEDALLA);

        public final BandaZona zona;
        public final BandaAncho ancho;
        public final BandaHerraje herraje;

        Tipo(BandaZona zona, BandaAncho ancho, BandaHerraje herraje) {
            this.zona = zona;
            this.ancho = ancho;
            this.herraje = herraje;
        }

        public boolean esZona() { return zona != null; }
        public boolean esAncho() { return ancho != null; }
        public boolean esHerraje() { return herraje != null; }
    }

    public final Tipo tipo;

    public MoldeBandaItem(Settings settings, Tipo tipo) {
        super(settings);
        this.tipo = tipo;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "banda", PrendaLore.Maquina.MODELADORA, "banda");
        tooltip.add(Text.translatable("modamod.sublimadora.molde.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
