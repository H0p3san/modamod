package com.modamod.item;

import com.modamod.garment.Parte;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Banda (2026-10-05, "correas y cintos"): un aro de tela, cuero o metal que rodea la cintura (cinto) o el cuello
 * (choker). Un solo ítem; la zona, el ancho y el herraje son componentes que se eligen con los moldes de la
 * Modeladora, y cada zona va a su slot de Trinkets ({@code torso/cinto}, {@code head/choker}). Como el sombrero NO
 * es ropa de cuerpo ({@code Garments.esPrenda} falso). Tiene 3 zonas con color y dibujo (banda, borde, herraje) que
 * se ponen en la Mesa de estilado, que también le pone apliques como colgantes. La dibuja
 * {@link com.modamod.render.BandaRenderer}.
 */
public class BandaItem extends TrinketItem implements ZonasTenibles {

    /** Cuero oscuro, borde claro y herraje dorado de fábrica. */
    public static final List<Integer> DE_FABRICA = List.of(0x5A3A24, 0x8A6240, 0xC9A24A);

    public BandaItem(Settings settings) {
        super(settings);
    }

    public static BandaZona zona(ItemStack stack) {
        BandaZona z = stack.get(ModamodComponents.BANDA_ZONA);
        return z == null ? BandaZona.CINTURA : z;
    }

    public static BandaAncho ancho(ItemStack stack) {
        BandaAncho a = stack.get(ModamodComponents.BANDA_ANCHO);
        return a == null ? BandaAncho.MEDIO : a;
    }

    public static BandaHerraje herraje(ItemStack stack) {
        BandaHerraje h = stack.get(ModamodComponents.BANDA_HERRAJE);
        // De fábrica sin hebilla (2026-10-12, "defaulteemos el cinto sin hebilla"): la placa es un molde.
        return h == null ? BandaHerraje.NINGUNO : h;
    }

    public static void setZona(ItemStack stack, BandaZona v) {
        if (v == BandaZona.CINTURA) stack.remove(ModamodComponents.BANDA_ZONA);
        else stack.set(ModamodComponents.BANDA_ZONA, v);
    }

    public static void setAncho(ItemStack stack, BandaAncho v) {
        if (v == BandaAncho.MEDIO) stack.remove(ModamodComponents.BANDA_ANCHO);
        else stack.set(ModamodComponents.BANDA_ANCHO, v);
    }

    public static void setHerraje(ItemStack stack, BandaHerraje v) {
        if (v == BandaHerraje.NINGUNO) stack.remove(ModamodComponents.BANDA_HERRAJE);
        else stack.set(ModamodComponents.BANDA_HERRAJE, v);
    }

    public static List<Integer> colores(ItemStack stack) {
        List<Integer> c = stack.get(ModamodComponents.COLORES_BANDA);
        return c == null || c.size() < 3 ? DE_FABRICA : c;
    }

    public static List<SombreroPatron> patrones(ItemStack stack) {
        List<Integer> p = stack.get(ModamodComponents.PATRONES_BANDA);
        SombreroPatron[] v = SombreroPatron.values();
        List<SombreroPatron> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int o = p != null && i < p.size() ? p.get(i) : 0;
            out.add(o >= 0 && o < v.length ? v[o] : SombreroPatron.LISO);
        }
        return out;
    }

    /** Solo entra en el slot de su zona. */
    @Override
    public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        var tipo = slot.inventory().getSlotType();
        BandaZona z = zona(stack);
        return tipo.getGroup().equals(z.grupo) && tipo.getName().equals(z.slot) && super.canEquip(stack, slot, entity);
    }

    @Override public List<Integer> coloresDe(ItemStack stack) { return colores(stack); }

    @Override
    public ItemStack conColoresDe(ItemStack stack, int a, int b, int c) {
        stack.set(ModamodComponents.COLORES_BANDA, List.of(a, b, c));
        return stack;
    }

    @Override public List<SombreroPatron> patronesDe(ItemStack stack) { return patrones(stack); }

    @Override
    public ItemStack conPatronDe(ItemStack stack, int zona, SombreroPatron patron) {
        List<SombreroPatron> actuales = patrones(stack);
        int[] n = {actuales.get(0).ordinal(), actuales.get(1).ordinal(), actuales.get(2).ordinal()};
        n[zona] = patron.ordinal();
        if (n[0] == 0 && n[1] == 0 && n[2] == 0) stack.remove(ModamodComponents.PATRONES_BANDA);
        else stack.set(ModamodComponents.PATRONES_BANDA, List.of(n[0], n[1], n[2]));
        return stack;
    }

    @Override public String claveZona(ItemStack stack, int i) { return "modamod.banda.parte." + (i + 1); }

    @Override
    public Parte marco(ItemStack stack) {
        // El cinto va en el torso; el collar, en el pecho pero anclado a la cabeza como el de Player Collars
        // (2026-10-11, "si el original esta anclado a la cabeza mejor asi").
        return Parte.TORSO;
    }

    /** Cinto en la cintura y choker en el cuello (2026-10-23, "Banda" no decía qué era). */
    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(zona(stack) == BandaZona.CUELLO ? "item.modamod.banda.choker" : "item.modamod.banda.cinto");
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("modamod.banda.tooltip", Text.translatable(zona(stack).traduccion()),
                Text.translatable(ancho(stack).traduccion()), Text.translatable(herraje(stack).traduccion()))
                .formatted(Formatting.GRAY));
        List<Integer> c = colores(stack);
        for (int i = 0; i < 3; i++) {
            int color = c.get(i);
            tooltip.add(Text.translatable("modamod.banda.parte." + (i + 1)).formatted(Formatting.GRAY)
                    .append(Text.literal(" ■■■").styled(s -> s.withColor(TextColor.fromRgb(color)))));
        }
    }
}
