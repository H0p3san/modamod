package com.modamod.prenda;

import com.modamod.item.ClothingPatternItem;
import com.modamod.item.ModamodComponents;
import com.modamod.region.RegionResolver;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * La base de toda prenda que se pone en un slot de Trinkets: ropa de cuerpo, sombreros, bandas, calzado y máscaras.
 * Lo común vive acá: el color base ({@code dyed_color}, con el de fábrica de cada prenda) y la descripción de la
 * pintura de Tintes. Qué es cada prenda (categoría, forma de dibujo, partes) lo dice su {@link DefinicionDePrenda} en
 * {@link Prendas}; el corte, {@link Corte}; el acabado, {@link Acabado}.
 */
public class PrendaItem extends TrinketItem {

    public PrendaItem(Settings settings) {
        super(settings);
    }

    /** El color sin teñir. */
    public int colorDeFabrica() {
        return 0xFFFFFF;
    }

    public int getColor(ItemStack stack) {
        DyedColorComponent c = stack.get(DataComponentTypes.DYED_COLOR);
        return c != null ? c.rgb() : colorDeFabrica();
    }

    public static void setColor(ItemStack stack, int rgb) {
        stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(rgb & 0xFFFFFF, false));
    }

    public DefinicionDePrenda definicion() {
        return Prendas.de(this);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        lineasDePintura(stack, tooltip);
    }

    /** Una línea por capa de Tintes, en el orden en que se pintan: "Vichy · Sup. Izq." en el color de la capa. */
    public static void lineasDePintura(ItemStack stack, List<Text> tooltip) {
        List<RegionResolver.CapaPatron> capas = stack.getOrDefault(ModamodComponents.CAPAS_TINTE, List.of());
        if (capas.isEmpty()) {
            tooltip.add(Text.translatable("modamod.pattern.none").formatted(Formatting.DARK_GRAY));
            return;
        }
        int max = 6;
        for (int i = 0; i < Math.min(max, capas.size()); i++) {
            RegionResolver.CapaPatron c = capas.get(i);
            ClothingPatternItem molde = c.lisa() ? null : ClothingPatternItem.porId(c.patronId());
            Text nombre = molde != null ? molde.getName() : Text.translatable("modamod.pattern.liso");
            int color = c.color() & 0xFFFFFF;
            tooltip.add(Text.literal("■ ").styled(st -> st.withColor(color))
                    .append(nombre.copy().formatted(Formatting.GRAY))
                    .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable(c.region().traduccion()).formatted(Formatting.DARK_GRAY)));
        }
        if (capas.size() > max) tooltip.add(Text.translatable("modamod.pattern.mas", capas.size() - max).formatted(Formatting.DARK_GRAY));
    }
}
