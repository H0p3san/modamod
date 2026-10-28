package com.modamod.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Delantal (2026-10-11, "un delantal que sea de malla, un apron para poder complementar un maid outfit"): un paño de
 * malla que cuelga al frente del cuerpo, del pecho hacia abajo, con el mismo cuerpo que la pollera (tubo recortado al
 * arco del frente). Slot propio {@code torso/delantal}, por encima del top y de la pollera. Sus medidas salen de los
 * moldes de rango: el nivel de {@link #inicio} (de dónde arranca) y el de {@link #largo} (hasta dónde llega, de la
 * cadera a los pies); la silueta ({@link DelantalForma}) y las esquinas de abajo ({@link CapaRuedo}) se recortan sobre
 * la tela.
 */
public class DelantalItem extends ClothingTrinketItem {

    public DelantalItem(Settings settings) {
        super(settings, true);
    }

    /** Nivel de rango por defecto del inicio (más alto = pecho) y del largo (3 = a la rodilla). */
    public static final int INICIO_DE_FABRICA = 0, LARGO_DE_FABRICA = 3;

    public static int nivelInicio(ItemStack stack) {
        Integer n = stack.get(ModamodComponents.DELANTAL_INICIO);
        return n == null ? INICIO_DE_FABRICA : n;
    }

    public static int nivelLargo(ItemStack stack) {
        Integer n = stack.get(ModamodComponents.DELANTAL_LARGO);
        return n == null ? LARGO_DE_FABRICA : n;
    }

    public static void setInicio(ItemStack stack, int nivel) {
        int n = Math.max(0, Math.min(6, nivel));
        if (n == INICIO_DE_FABRICA) stack.remove(ModamodComponents.DELANTAL_INICIO);
        else stack.set(ModamodComponents.DELANTAL_INICIO, n);
    }

    public static void setLargo(ItemStack stack, int nivel) {
        int n = Math.max(0, Math.min(6, nivel));
        if (n == LARGO_DE_FABRICA) stack.remove(ModamodComponents.DELANTAL_LARGO);
        else stack.set(ModamodComponents.DELANTAL_LARGO, n);
    }

    /** Altura del borde de arriba, en px bajo el hombro: del pecho (1) a la cadera baja (10). */
    public static float inicio(ItemStack stack) {
        return 1f + 1.5f * nivelInicio(stack);
    }

    /** Altura del ruedo, en px bajo el hombro: de la cadera (9) a los pies (24). */
    public static float ruedo(ItemStack stack) {
        return 9f + 2.5f * nivelLargo(stack);
    }

    /** Alto del paño en px (al menos 3). */
    public static float largo(ItemStack stack) {
        return Math.max(3f, ruedo(stack) - inicio(stack));
    }

    public static DelantalForma forma(ItemStack stack) {
        DelantalForma f = stack.get(ModamodComponents.DELANTAL_FORMA);
        return f == null ? DelantalForma.RECTO : f;
    }

    public static void setForma(ItemStack stack, DelantalForma forma) {
        if (forma == DelantalForma.RECTO) stack.remove(ModamodComponents.DELANTAL_FORMA);
        else stack.set(ModamodComponents.DELANTAL_FORMA, forma);
    }

    /** ¿Las esquinas de abajo van redondeadas? (los moldes de ruedo de la capa: recto / redondeado). */
    public static boolean esquinasRedondas(ItemStack stack) {
        return stack.get(ModamodComponents.DELANTAL_ESQUINAS) == CapaRuedo.REDONDEADO;
    }

    public static void setEsquinas(ItemStack stack, @Nullable CapaRuedo ruedo) {
        if (ruedo == CapaRuedo.REDONDEADO) stack.set(ModamodComponents.DELANTAL_ESQUINAS, CapaRuedo.REDONDEADO);
        else stack.remove(ModamodComponents.DELANTAL_ESQUINAS);
    }

    /** Cobertura (2026-10-11): ENVOLVENTE si no tiene. */
    public static DelantalCobertura cobertura(ItemStack stack) {
        DelantalCobertura c = stack.get(ModamodComponents.DELANTAL_COBERTURA);
        return c == null ? DelantalCobertura.ENVOLVENTE : c;
    }

    public static void setCobertura(ItemStack stack, @Nullable DelantalCobertura c) {
        if (c == null || c == DelantalCobertura.ENVOLVENTE) stack.remove(ModamodComponents.DELANTAL_COBERTURA);
        else stack.set(ModamodComponents.DELANTAL_COBERTURA, c);
    }

    /** Tirantes (2026-10-11); SIN si no lleva. */
    public static DelantalTirantes tirantes(ItemStack stack) {
        DelantalTirantes t = stack.get(ModamodComponents.DELANTAL_TIRANTES);
        return t == null ? DelantalTirantes.SIN : t;
    }

    public static void setTirantes(ItemStack stack, @Nullable DelantalTirantes t) {
        if (t == null || t == DelantalTirantes.SIN) stack.remove(ModamodComponents.DELANTAL_TIRANTES);
        else stack.set(ModamodComponents.DELANTAL_TIRANTES, t);
    }

    /** Volado del ruedo (los de la pollera), o null. */
    @Nullable
    public static PolleraVolado volado(ItemStack stack) {
        return stack.get(ModamodComponents.DELANTAL_VOLADO);
    }

    public static void setVolado(ItemStack stack, @Nullable PolleraVolado v) {
        if (v == null) stack.remove(ModamodComponents.DELANTAL_VOLADO);
        else stack.set(ModamodComponents.DELANTAL_VOLADO, v);
    }

    @Nullable
    public static PolleraVolado voladoLateral(ItemStack stack) {
        return stack.get(ModamodComponents.DELANTAL_VOLADO_LATERAL);
    }

    @Nullable
    public static PolleraVolado voladoSuperior(ItemStack stack) {
        return stack.get(ModamodComponents.DELANTAL_VOLADO_SUPERIOR);
    }

    public static void setVoladoLateral(ItemStack stack, @Nullable PolleraVolado v) {
        if (v == null) stack.remove(ModamodComponents.DELANTAL_VOLADO_LATERAL);
        else stack.set(ModamodComponents.DELANTAL_VOLADO_LATERAL, v);
    }

    public static void setVoladoSuperior(ItemStack stack, @Nullable PolleraVolado v) {
        if (v == null) stack.remove(ModamodComponents.DELANTAL_VOLADO_SUPERIOR);
        else stack.set(ModamodComponents.DELANTAL_VOLADO_SUPERIOR, v);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("modamod.delantal.tooltip",
                Text.translatable(forma(stack).traduccion()),
                Text.translatable("modamod.rango.nivel." + nivelInicio(stack)),
                Text.translatable("modamod.rango.nivel." + nivelLargo(stack))).formatted(Formatting.GRAY));
        PolleraVolado v = volado(stack);
        if (v != null) tooltip.add(Text.translatable("modamod.pollera.tooltip.volado_ruedo", Text.translatable(v.traduccion()))
                .formatted(Formatting.GRAY));
    }
}
