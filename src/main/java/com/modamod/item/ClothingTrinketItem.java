package com.modamod.item;

import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import com.modamod.region.Lado;
import com.modamod.region.RegionResolver;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Prenda "pegada al cuerpo" (medias, shorts, croptop, etc). A diferencia
 * de ClothingArmorItem, esta NO usa la geometría de armadura (más ancha)
 * — se registra en un slot custom de Trinkets y se dibuja directo sobre
 * las ModelPart reales del jugador vía GarmentFeatureRenderer, así que
 * queda pegada al cuerpo como una "segunda piel" en vez de verse como
 * una bota/peto puestos encima.
 */
public class ClothingTrinketItem extends TrinketItem {

    public final boolean dyeable;

    public ClothingTrinketItem(Settings settings, boolean dyeable) {
        super(settings);
        this.dyeable = dyeable;
    }

    public int getColor(ItemStack stack) {
        if (!dyeable) return 0xFFFFFF;
        DyedColorComponent c = stack.get(DataComponentTypes.DYED_COLOR);
        return c != null ? c.rgb() : 0xFFFFFF;
    }

    public static void setColor(ItemStack stack, int rgb) {
        stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(rgb, true));
    }

    /**
     * Muestra que patron tiene puesto la prenda, y de que color. Sin esto la
     * unica forma de saberlo era ponersela.
     *
     * El nombre del patron se pinta CON el color del patron, asi el tooltip
     * dice las dos cosas de un saque. Si las piernas estan configuradas
     * distinto, se listan por separado.
     */
    /** Las medias, según dónde empieza y termina la tela (2026-10-23): soquetes, bucaneras, calzas, calientapiernas... */
    @Override
    public Text getName(ItemStack stack) {
        if (this != ModamodItems.SOCKS_SOLID) return super.getName(stack);
        int[] der = MediasLargo.filasVisibles(stack, com.modamod.region.Lado.DERECHA);
        int[] izq = MediasLargo.filasVisibles(stack, com.modamod.region.Lado.IZQUIERDA);
        return Text.translatable("item.modamod.socks_solid." + (java.util.Arrays.equals(der, izq) ? NombresDePrenda.medias(der) : "medias"));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);

        if (RegionResolver.parejo(stack)) {
            tooltip.add(patternLine(stack, Lado.IZQUIERDA, null));
        } else {
            tooltip.add(patternLine(stack, Lado.IZQUIERDA, "modamod.tooltip.left"));
            tooltip.add(patternLine(stack, Lado.DERECHA, "modamod.tooltip.right"));
        }
    }

    /**
     * Publico porque RemeraItem tambien lo necesita: el patron ya no es
     * exclusivo de las prendas bilaterales. Remera lo llama con
     * Lado.AMBAS y sideKey null (una sola linea, sin "izquierda/derecha"
     * porque no tiene sentido para una prenda que no es por lado).
     */
    public static Text patternLine(ItemStack stack, Lado lado, String sideKey) {
        Identifier pattern = RegionResolver.patronId(stack, lado);
        Text name = pattern == null
                ? Text.translatable("modamod.pattern.none").formatted(Formatting.DARK_GRAY)
                : Text.translatable("modamod.pattern." + pattern.getPath())
                        .styled(st -> st.withColor(RegionResolver.colorPatron(stack, lado)));

        if (sideKey == null) return name;
        return Text.translatable(sideKey).formatted(Formatting.GRAY).append(" ").append(name);
    }
}
