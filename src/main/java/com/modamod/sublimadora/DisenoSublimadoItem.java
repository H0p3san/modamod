package com.modamod.sublimadora;

import com.modamod.item.ModamodComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Diseño de sublimado (2026-10-13, "vamos con la siguiente maquina", mismo sistema de diseños que las otras): el ajuste
 * de las dos caras (escala, posición, ángulo), cuáles están fijadas y la simetría, hechos ítem. Se crea con el "+"
 * gastando un "diseño en blanco" y no se consume al cargarlo.
 */
public class DisenoSublimadoItem extends Item {
    public DisenoSublimadoItem(Settings settings) { super(settings); }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtCompound d = stack.get(ModamodComponents.DISENO_SUBLIMADO);
        if (d == null) return;
        Identifier id = Identifier.tryParse(d.getString("Item"));
        if (id != null) {
            tooltip.add(Text.translatable("modamod.estilista.disenos.prenda", Text.translatable(Registries.ITEM.get(id).getTranslationKey()))
                    .formatted(Formatting.GRAY));
        }
        int caras = (d.getBoolean("FijadaF") ? 1 : 0) + (d.getBoolean("FijadaE") ? 1 : 0);
        tooltip.add(Text.translatable("modamod.sublimadora.gui.diseno.caras", caras).formatted(Formatting.DARK_GRAY));
    }
}
