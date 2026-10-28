package com.modamod.tinturas;

import com.modamod.item.ModamodComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

/**
 * Diseño de tinte (2026-10-12, "te falto convertir diseños de la estacion de tintes"): los cuadraditos de una categoría de
 * la Estación de Tintes (qué está fijado, con qué color/patrón/modo) y su orden de capas, hechos ítem para sacarlos de la
 * máquina e intercambiarlos. Se crea con el "+" gastando un "diseño en blanco" y no se consume al cargarlo.
 */
public class DisenoTintaItem extends Item {
    public DisenoTintaItem(Settings settings) { super(settings); }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtCompound d = stack.get(ModamodComponents.DISENO_TINTE);
        if (d == null) return;
        tooltip.add(Text.translatable("modamod.modelado.diseno.categoria",
                Text.translatable("modamod.modelado.categoria." + d.getString("Categoria").toLowerCase(Locale.ROOT))).formatted(Formatting.GRAY));
        int fijadas = 0;
        var casillas = d.getList("Casillas", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < casillas.size(); i++) if (casillas.getCompound(i).getBoolean("Fijada")) fijadas++;
        tooltip.add(Text.translatable("modamod.tinturas.diseno.capas", fijadas).formatted(Formatting.DARK_GRAY));
    }
}
