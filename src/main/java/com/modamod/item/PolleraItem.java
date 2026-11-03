package com.modamod.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Pollera. Comparte slot con {@link PantalonItem} (ver
 * {@code piernas/exterior.json}): es una alternativa al pantalón, no algo
 * que se lleve puesto a la vez.
 *
 * <p>Rehecha (2026-09-29, "resolveme la pollera que se ve horrible a veces
 * y quiero poder hacerle distintos largos y teñirla y sublimarla"): largo
 * ({@link PolleraLargo}) y forma ({@link PolleraForma}) como componentes,
 * malla propia en {@code render.PolleraMalla} y tela con el layout de la
 * caja del torso, así Tintes y la Sublimadora la tratan como a las demás.
 */
public class PolleraItem extends com.modamod.prenda.PrendaItem {

    public PolleraItem(Settings settings) {
        super(settings);
    }

    public static PolleraLargo largo(ItemStack stack) {
        PolleraLargo l = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.POLLERA_LARGO);
        return l == null ? PolleraLargo.MEDIO : l;
    }

    public static void setLargo(ItemStack stack, PolleraLargo largo) {
        if (largo == PolleraLargo.MEDIO) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.POLLERA_LARGO);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.POLLERA_LARGO, largo);
    }

    /** Altura de la cintura en px bajo el hombro (9 = cadera de siempre). */
    public static float cintura(ItemStack stack) {
        Integer n = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.POLLERA_CINTURA);
        if (n == null) return 9f;
        return Math.max(3f, Math.min(11f, 2f * n));
    }

    public static void setCintura(ItemStack stack, int nivel) {
        // 7 = "ninguna" (2026-10-12, molde en blanco): vuelve a la cadera de siempre.
        if (nivel < 0 || nivel > 6) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.POLLERA_CINTURA);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.POLLERA_CINTURA, nivel);
    }

    public static PolleraForma forma(ItemStack stack) {
        PolleraForma f = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.POLLERA_FORMA);
        return f == null ? PolleraForma.CAMPANA : f;
    }

    public static void setForma(ItemStack stack, PolleraForma forma) {
        if (forma == PolleraForma.CAMPANA) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.POLLERA_FORMA);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.POLLERA_FORMA, forma);
    }

    /** Volado del borde de abajo (2026-10-05), o null. */
    @org.jetbrains.annotations.Nullable
    public static PolleraVolado voladoRuedo(ItemStack stack) {
        return com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.POLLERA_VOLADO_RUEDO);
    }

    @org.jetbrains.annotations.Nullable
    public static PolleraVolado voladoTodo(ItemStack stack) {
        return com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.POLLERA_VOLADO_TODO);
    }

    public static void setVoladoRuedo(ItemStack stack, @org.jetbrains.annotations.Nullable PolleraVolado v) {
        if (v == null) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.POLLERA_VOLADO_RUEDO);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.POLLERA_VOLADO_RUEDO, v);
    }

    public static void setVoladoTodo(ItemStack stack, @org.jetbrains.annotations.Nullable PolleraVolado v) {
        if (v == null) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.POLLERA_VOLADO_TODO);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.POLLERA_VOLADO_TODO, v);
    }

    /** Borde decorativo del ruedo (2026-10-05) que sale del {@link Ruedo} de la pollera, o null (sin borde decorativo). */
    @org.jetbrains.annotations.Nullable
    public static PolleraBorde borde(ItemStack stack) {
        return Ruedos.get(stack, ZonaRuedo.POLLERA).borde();
    }

    /** Por la forma (2026-10-23): pollera tubo, tableada, circular, globo o campana. */
    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("item.modamod.pollera." + forma(stack).name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("modamod.pollera.tooltip",
                Text.translatable(forma(stack).traduccion()), Text.translatable(largo(stack).traduccion()))
                .formatted(Formatting.GRAY));
        PolleraVolado ruedo = voladoRuedo(stack), todo = voladoTodo(stack);
        if (ruedo != null) tooltip.add(Text.translatable("modamod.pollera.tooltip.volado_ruedo", Text.translatable(ruedo.traduccion()))
                .formatted(Formatting.GRAY));
        Ruedo hem = Ruedos.get(stack, ZonaRuedo.POLLERA);
        if (hem != Ruedo.RECTO) tooltip.add(Text.translatable("modamod.pollera.tooltip.borde", Text.translatable(hem.traduccion()))
                .formatted(Formatting.GRAY));
        if (todo != null) tooltip.add(Text.translatable("modamod.pollera.tooltip.volado_todo", Text.translatable(todo.traduccion()))
                .formatted(Formatting.GRAY));
    }
}
