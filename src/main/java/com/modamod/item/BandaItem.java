package com.modamod.item;

import com.modamod.garment.Parte;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Banda (2026-10-05, "correas y cintos"): un aro de tela, cuero o metal que rodea la cintura (cinto) o el cuello
 * (choker). Un solo ítem; la zona, el ancho y el herraje son componentes que se eligen con los moldes de la
 * Modeladora, y cada zona va a su slot de Trinkets ({@code torso/cinto}, {@code head/choker}). Como el sombrero NO
 * es ropa de cuerpo ({@code com.modamod.prenda.Prendas.esRopaDeCuerpo} falso). Tiene 3 zonas con color y dibujo (banda, borde, herraje) que
 * se ponen en la Mesa de estilado, que también le pone apliques como colgantes. La dibuja
 * {@link com.modamod.render.BandaRenderer}.
 */
public class BandaItem extends com.modamod.prenda.PrendaItem implements ZonasTenibles {

    /** Cuero oscuro, borde claro y herraje dorado de fábrica. */
    private static final List<Integer> DE_FABRICA = List.of(0x5A3A24, 0x8A6240, 0xC9A24A);

    public BandaItem(Settings settings) {
        super(settings);
    }

    public static BandaZona zona(ItemStack stack) {
        BandaZona z = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.BANDA_ZONA);
        return z == null ? BandaZona.CINTURA : z;
    }

    public static BandaAncho ancho(ItemStack stack) {
        BandaAncho a = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.BANDA_ANCHO);
        return a == null ? BandaAncho.MEDIO : a;
    }

    public static BandaHerraje herraje(ItemStack stack) {
        BandaHerraje h = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.BANDA_HERRAJE);
        // De fábrica sin hebilla (2026-10-12, "defaulteemos el cinto sin hebilla"): la placa es un molde.
        return h == null ? BandaHerraje.NINGUNO : h;
    }

    public static void setZona(ItemStack stack, BandaZona v) {
        if (v == BandaZona.CINTURA) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.BANDA_ZONA);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.BANDA_ZONA, v);
    }

    public static void setAncho(ItemStack stack, BandaAncho v) {
        if (v == BandaAncho.MEDIO) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.BANDA_ANCHO);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.BANDA_ANCHO, v);
    }

    public static void setHerraje(ItemStack stack, BandaHerraje v) {
        if (v == BandaHerraje.NINGUNO) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.BANDA_HERRAJE);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.BANDA_HERRAJE, v);
    }








    @Override public List<Integer> coloresDeFabrica() { return DE_FABRICA; }

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
    }
}
