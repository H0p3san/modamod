package com.modamod.estilista;

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
 * Diseño de estilo (2026-10-13, "instaura el mismo sistema de guardado de diseño que venimos haciendo"): lo que lleva la
 * muestra de la Estilista (apliques, correas, acabado, colores) hecho ítem para sacarlo de la máquina e intercambiarlo.
 * Se crea con el "+" gastando un "diseño en blanco" y no se consume al cargarlo.
 */
public class DisenoEstiloItem extends Item {
    public DisenoEstiloItem(Settings settings) { super(settings); }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtCompound d = stack.get(ModamodComponents.DISENO_ESTILO);
        if (d == null) return;
        Identifier id = Identifier.tryParse(d.getString("Prenda"));
        if (id != null) {
            tooltip.add(Text.translatable("modamod.estilista.disenos.prenda", Text.translatable(Registries.ITEM.get(id).getTranslationKey()))
                    .formatted(Formatting.GRAY));
        }
        String resumen = d.getString("Resumen");
        if (!resumen.isEmpty()) tooltip.add(Text.literal(resumen).formatted(Formatting.DARK_GRAY));
    }
}
