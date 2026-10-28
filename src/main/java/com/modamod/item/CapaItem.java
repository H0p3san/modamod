package com.modamod.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Capa — a pedido (2026-09-29, "capas que se puedan procesar por todas las
 * maquinas que sean como las capas vanilla (misma dinamica de tela) pero
 * usable y personalizable, seria una nueva categoria de ropa"). Va en su
 * propio slot de Trinkets ({@code espalda/capa}). Se mueve igual que la capa
 * vanilla ({@code render.CapaMalla}); los ejes de corte — largo, ruedo,
 * capucha y cuello alto — salen de la Modeladora; la tela tiene exterior y
 * forro aparte para Tintes y la Sublimadora.
 */
public class CapaItem extends ClothingTrinketItem {

    public CapaItem(Settings settings) {
        super(settings, true);
    }

    public static CapaLargo largo(ItemStack stack) {
        CapaLargo l = stack.get(ModamodComponents.CAPA_LARGO);
        return l == null ? CapaLargo.MEDIO : l;
    }

    public static void setLargo(ItemStack stack, CapaLargo v) {
        if (v == CapaLargo.MEDIO) stack.remove(ModamodComponents.CAPA_LARGO);
        else stack.set(ModamodComponents.CAPA_LARGO, v);
    }

    public static CapaRuedo ruedo(ItemStack stack) {
        CapaRuedo r = stack.get(ModamodComponents.CAPA_RUEDO);
        return r == null ? CapaRuedo.RECTO : r;
    }

    public static void setRuedo(ItemStack stack, CapaRuedo v) {
        if (v == CapaRuedo.RECTO) stack.remove(ModamodComponents.CAPA_RUEDO);
        else stack.set(ModamodComponents.CAPA_RUEDO, v);
    }

    public static boolean capucha(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModamodComponents.CAPA_CAPUCHA));
    }

    public static void setCapucha(ItemStack stack, boolean v) {
        if (!v) stack.remove(ModamodComponents.CAPA_CAPUCHA);
        else stack.set(ModamodComponents.CAPA_CAPUCHA, true);
    }

    public static boolean cuelloAlto(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModamodComponents.CAPA_CUELLO));
    }

    public static void setCuelloAlto(ItemStack stack, boolean v) {
        if (!v) stack.remove(ModamodComponents.CAPA_CUELLO);
        else stack.set(ModamodComponents.CAPA_CUELLO, true);
    }

    /** Nivel de rango (0..6) del embozo sobre los hombros, o −1 si no lleva (2026-10-11). */
    public static int embozo(ItemStack stack) {
        Integer n = stack.get(ModamodComponents.CAPA_EMBOZO);
        return n == null ? -1 : n;
    }

    public static void setEmbozo(ItemStack stack, int nivel) {
        if (nivel < 0) {
            stack.remove(ModamodComponents.CAPA_EMBOZO);
            stack.remove(ModamodComponents.FRENTE_AHORA);
        } else stack.set(ModamodComponents.CAPA_EMBOZO, Math.min(6, nivel));
    }

    /** Cuánto baja el embozo desde el hombro, en px: del 2 hasta el piso (2026-10-23, "pensaba bajarlo como maximo hasta el piso"). */
    private static final float[] LARGOS_EMBOZO = {2f, 5f, 8f, 12f, 16f, 20f, 23.5f};

    public static float profundidadEmbozo(ItemStack stack) {
        return LARGOS_EMBOZO[Math.max(0, Math.min(LARGOS_EMBOZO.length - 1, embozo(stack)))];
    }

    /** Embozo largo (del 12 px para abajo): tapa el paño de la capa, que no se dibuja (2026-10-23, "cuando este el emboso largo sacamos la capa"). */
    public static boolean embozoLargo(ItemStack stack) {
        return embozo(stack) >= 3;
    }

    public static boolean bordeEmbozo(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModamodComponents.CAPA_BORDE_EMBOZO));
    }

    public static void setBordeEmbozo(ItemStack stack, boolean v) {
        if (!v) stack.remove(ModamodComponents.CAPA_BORDE_EMBOZO);
        else stack.set(ModamodComponents.CAPA_BORDE_EMBOZO, true);
    }

    /** ¿El frente del embozo está abierto un poco? (la tecla de abrir el frente, la misma de la chaqueta). */
    public static boolean embozoAbierto(ItemStack stack) {
        return embozo(stack) >= 0 && Boolean.TRUE.equals(stack.get(ModamodComponents.FRENTE_AHORA));
    }

    /** Capelet con el largo cero y capa con capucha si la tiene (2026-10-23). */
    @Override
    public Text getName(ItemStack stack) {
        if (largo(stack) == CapaLargo.CERO) return Text.translatable("item.modamod.capa.capelet");
        return capucha(stack) ? Text.translatable("item.modamod.capa.con_capucha") : super.getName(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("modamod.capa.tooltip",
                Text.translatable(largo(stack).traduccion()), Text.translatable(ruedo(stack).traduccion()))
                .formatted(Formatting.GRAY));
        if (capucha(stack)) tooltip.add(Text.translatable("modamod.capa.con_capucha").formatted(Formatting.GRAY));
        if (cuelloAlto(stack)) tooltip.add(Text.translatable("modamod.capa.con_cuello").formatted(Formatting.GRAY));
        if (embozo(stack) >= 0) tooltip.add(Text.translatable(bordeEmbozo(stack) ? "modamod.capa.con_embozo_borde" : "modamod.capa.con_embozo").formatted(Formatting.GRAY));
    }
}
