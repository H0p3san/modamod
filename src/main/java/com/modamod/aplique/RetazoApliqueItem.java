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
 * y vuelve al quitarlo. Se tiñe en la Estación de Tintes (categoría Aplique):
 * un color por zona y, encima, tramas por zona ({@code CAPAS_TINTE}, ZONA_1..3).
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

    /** Las tramas de Tintes del retazo, cada una en su zona (ZONA_1..3). */
    public static List<com.modamod.region.RegionResolver.CapaPatron> capas(ItemStack stack) {
        return stack.getOrDefault(ModamodComponents.CAPAS_TINTE, List.of());
    }

    /**
     * La tela que le toca a un aplique: la zona {@code z} del aplique lleva el color y las tramas de la zona
     * {@code asignacion[z]} del retazo (se puede repetir una zona del retazo en varias del aplique).
     */
    public static com.mojang.datafixers.util.Pair<List<Integer>, List<com.modamod.region.RegionResolver.CapaPatron>> tela(
            ItemStack retazo, int[] asignacion) {
        List<Integer> c = colores(retazo);
        List<Integer> colores = List.of(c.get(asignacion[0]), c.get(asignacion[1]), c.get(asignacion[2]));
        int z1 = com.modamod.region.RegionPintura.ZONA_1.ordinal();
        List<com.modamod.region.RegionResolver.CapaPatron> capas = new java.util.ArrayList<>();
        for (int z = 0; z < 3; z++) {
            for (var capa : capas(retazo)) {
                if (capa.region().ordinal() - z1 == asignacion[z]) {
                    capas.add(com.modamod.region.RegionResolver.conRegion(capa, com.modamod.region.RegionPintura.values()[z1 + z]));
                }
            }
        }
        return com.mojang.datafixers.util.Pair.of(colores, List.copyOf(capas));
    }

    /** El retazo que vuelve al quitar un aplique: sus colores y sus tramas, tal cual. */
    public static ItemStack de(com.modamod.aplique.Aplique a) {
        ItemStack r = conColores(new ItemStack(com.modamod.item.ModamodItems.RETAZO_APLIQUE), a.color(0), a.color(1), a.color(2));
        if (!a.capas().isEmpty()) r.set(ModamodComponents.CAPAS_TINTE, a.capas());
        return r;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        com.modamod.item.PrendaLore.moldeEn(tooltip, "retazo", new com.modamod.item.PrendaLore.Maquina[]{
                com.modamod.item.PrendaLore.Maquina.TINTES, com.modamod.item.PrendaLore.Maquina.ESTILISTA});
        List<Integer> c = colores(stack);
        for (int i = 0; i < 3; i++) tooltip.add(linea(i, c.get(i)));
        if (!capas(stack).isEmpty()) com.modamod.prenda.PrendaItem.lineasDePintura(stack, tooltip);
        tooltip.add(Text.translatable("modamod.aplique.retazo.tooltip").formatted(Formatting.DARK_GRAY));
    }

    private static Text linea(int zona, int color) {
        return Text.translatable("modamod.aplique.zona", zona + 1).formatted(Formatting.GRAY)
                .append(Text.literal(" ■■■").styled(s -> s.withColor(TextColor.fromRgb(color))));
    }
}
