package com.modamod.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Muestra de color — a pedido (2026-09-30, "que la estacion de tintes
 * genere un mezcla de color por si la gente se quiere pasar colores"): un
 * frasquito con la mezcla C/M/Y/K/T de UN color, para pasárselo a otro
 * jugador o guardarlo. Es solo la receta del color, no trae tinta y no se
 * gasta: en la Estación de Tintes, "Envasar" gasta un frasco de vidrio y
 * "Usar muestra" copia la mezcla al color que se está editando.
 */
public class MuestraColorItem extends Item {

    /** Canales de la mezcla (C, M, Y, K, T), como {@code TinturasBlockEntity.CANALES}. */
    public static final int CANALES = 5;
    public static final int NIVELES = 21;

    public MuestraColorItem(Settings settings) {
        super(settings);
    }

    /** La mezcla guardada (5 niveles de 0 a 20), o null si el frasco está vacío. */
    @Nullable
    public static int[] mezcla(ItemStack stack) {
        List<Integer> l = stack.get(ModamodComponents.MEZCLA_COLOR);
        if (l == null || l.size() < CANALES) return null;
        int[] m = new int[CANALES];
        for (int i = 0; i < CANALES; i++) m[i] = Math.max(0, Math.min(NIVELES - 1, l.get(i)));
        return m;
    }

    public static ItemStack con(ItemStack stack, int[] mezcla) {
        java.util.List<Integer> l = new java.util.ArrayList<>(CANALES);
        for (int i = 0; i < CANALES; i++) l.add(i < mezcla.length ? mezcla[i] : 0);
        stack.set(ModamodComponents.MEZCLA_COLOR, List.copyOf(l));
        return stack;
    }

    /** RGB de la mezcla (misma cuenta CMYK que la Estación de Tintes), para teñir el líquido del ícono. */
    public static int rgb(int[] m) {
        float n = NIVELES - 1;
        int r = Math.round(255 * (1 - m[0] / n) * (1 - m[3] / n));
        int g = Math.round(255 * (1 - m[1] / n) * (1 - m[3] / n));
        int b = Math.round(255 * (1 - m[2] / n) * (1 - m[3] / n));
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.moldeEn(tooltip, "muestra", new PrendaLore.Maquina[]{PrendaLore.Maquina.TINTES, PrendaLore.Maquina.ESTILADO});
        int[] m = mezcla(stack);
        if (m == null) {
            tooltip.add(Text.translatable("modamod.muestra.vacia").formatted(Formatting.GRAY));
            return;
        }
        tooltip.add(Text.literal(String.format("#%06X", rgb(m))).styled(s -> s.withColor(rgb(m))));
        tooltip.add(Text.translatable("modamod.muestra.mezcla", m[0] * 5, m[1] * 5, m[2] * 5, m[3] * 5, m[4] * 5)
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("modamod.muestra.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
