package com.modamod.aplique;

import com.modamod.item.ModamodComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Retazo de aplique (2026-10-01): la tela con los 3 colores de las zonas del
 * aplique ({@code modamod:colores_aplique}). Se gasta uno por aplique puesto
 * y vuelve al quitarlo. Por ahora sus colores vienen de fábrica (pestaña
 * creativa); la categoría Aplique de la Estación de Tintes (fase 4) los va a
 * teñir con patrones.
 */
public class RetazoApliqueItem extends Item {

    public static final List<Integer> BLANCO = List.of(0xF2F2F6, 0xF2F2F6, 0xF2F2F6);

    public RetazoApliqueItem(Settings settings) {
        super(settings);
    }

    public static List<Integer> colores(ItemStack stack) {
        List<Integer> c = stack.get(ModamodComponents.COLORES_APLIQUE);
        return c == null || c.size() < 3 ? BLANCO : c;
    }

    public static ItemStack conColores(ItemStack stack, int zona1, int zona2, int zona3) {
        stack.set(ModamodComponents.COLORES_APLIQUE, List.of(zona1, zona2, zona3));
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        com.modamod.item.PrendaLore.moldeEn(tooltip, "retazo", new com.modamod.item.PrendaLore.Maquina[]{
                com.modamod.item.PrendaLore.Maquina.TINTES, com.modamod.item.PrendaLore.Maquina.ESTILADO});
        List<Integer> c = colores(stack);
        for (int i = 0; i < 3; i++) tooltip.add(linea(i, c.get(i)));
        tooltip.add(Text.translatable("modamod.aplique.retazo.tooltip").formatted(Formatting.DARK_GRAY));
    }

    private static Text linea(int zona, int color) {
        return Text.translatable("modamod.aplique.zona", zona + 1).formatted(Formatting.GRAY)
                .append(Text.literal(" ■■■").styled(s -> s.withColor(TextColor.fromRgb(color))));
    }
}
