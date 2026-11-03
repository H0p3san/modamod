package com.modamod.item;

import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Sombrero de bruja (2026-10-04, "modelemos y agreguemos un sombrero de bruja"): va en su propio slot de Trinkets
 * ({@code head/sombrero}), junto al casco. NO extiende {@link ClothingTrinketItem} a propósito: las máquinas de
 * prendas (Modeladora, Sublimadora, Guardarropas) lo tratarían como ropa de cuerpo. Tiene 3 zonas teñibles (ala,
 * cono y cinta, en {@code modamod:colores_sombrero}), un ala (ancha/corta) y una punta (recta/doblada) y la
 * dibuja {@link com.modamod.render.SombreroRenderer}; la punta se mece con la tela blanda de los apliques.
 */
public class SombreroBrujaItem extends TrinketItem implements ZonasTenibles {

    /** Blanco de fábrica (2026-10-05, "sale blanco de fabrica"): igual que el retazo sin teñir; los colores se ponen en la Mesa de estilado. */
    private static final List<Integer> DE_FABRICA = List.of(0xF2F2F6, 0xF2F2F6, 0xF2F2F6);

    public SombreroBrujaItem(Settings settings) {
        super(settings);
    }

    public static SombreroAla ala(ItemStack stack) {
        SombreroAla a = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.SOMBRERO_ALA);
        return a == null ? SombreroAla.ANCHA : a;
    }

    public static SombreroPunta punta(ItemStack stack) {
        SombreroPunta p = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.SOMBRERO_PUNTA);
        return p == null ? SombreroPunta.RECTA : p;
    }

    public static SombreroGrosor grosor(ItemStack stack) {
        SombreroGrosor g = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.SOMBRERO_GROSOR);
        return g == null ? SombreroGrosor.FINA : g;
    }

    public static void setGrosor(ItemStack stack, SombreroGrosor v) {
        if (v == SombreroGrosor.FINA) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.SOMBRERO_GROSOR);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.SOMBRERO_GROSOR, v);
    }

    /** ¿Lleva banda? Sí de fábrica (los sombreros de antes la tienen). */
    public static boolean banda(ItemStack stack) {
        return !Boolean.FALSE.equals(com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.SOMBRERO_BANDA));
    }

    public static void setBanda(ItemStack stack, boolean con) {
        if (con) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.SOMBRERO_BANDA);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.SOMBRERO_BANDA, false);
    }

    public static SombreroEstilo estilo(ItemStack stack) {
        SombreroEstilo e = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.SOMBRERO_ESTILO);
        return e == null ? SombreroEstilo.MALLA : e;
    }

    public static void setEstilo(ItemStack stack, SombreroEstilo v) {
        if (v == SombreroEstilo.MALLA) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.SOMBRERO_ESTILO);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.SOMBRERO_ESTILO, v);
    }

    public static SombreroPlanta planta(ItemStack stack) {
        SombreroPlanta p = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.SOMBRERO_PLANTA);
        return p == null ? SombreroPlanta.REDONDA : p;
    }

    public static void setPlanta(ItemStack stack, SombreroPlanta v) {
        if (v == SombreroPlanta.REDONDA) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.SOMBRERO_PLANTA);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.SOMBRERO_PLANTA, v);
    }

    public static void setAla(ItemStack stack, SombreroAla v) {
        if (v == SombreroAla.ANCHA) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.SOMBRERO_ALA);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.SOMBRERO_ALA, v);
    }

    public static void setPunta(ItemStack stack, SombreroPunta v) {
        if (v == SombreroPunta.RECTA) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.SOMBRERO_PUNTA);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.SOMBRERO_PUNTA, v);
    }





    @Override public List<Integer> coloresDeFabrica() { return DE_FABRICA; }

    @Override public String claveZona(ItemStack stack, int i) { return "modamod.sombrero.zona." + (i + 1); }
    @Override public com.modamod.garment.Parte marco(ItemStack stack) { return com.modamod.garment.Parte.CABEZA; }

    /** Vincha o cofia (2026-10-11): el mismo ítem con otra "ala" lleva su nombre. */
    @Override
    public Text getName(ItemStack stack) {
        SombreroAla a = ala(stack);
        // El resto por la copa o el ala que más lo define (2026-10-23): gorra, boina, galera, sombrero vaquero...
        return Text.translatable("item.modamod.sombrero_bruja." + (a.esDiadema() ? a.clave : NombresDePrenda.sombrero(a, punta(stack))));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("modamod.sombrero.tooltip",
                Text.translatable(ala(stack).traduccion()), Text.translatable(punta(stack).traduccion()))
                .formatted(Formatting.GRAY));
        ClothingTrinketItem.lineasDePintura(stack, tooltip);
    }
}
