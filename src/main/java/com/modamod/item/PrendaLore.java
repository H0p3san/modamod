package com.modamod.item;

import java.util.List;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Arma el tooltip de los moldes, tramas, máscaras, plantillas y motivos (2026-10-05, "tooltip va a decir nombre: Cuello
 * en V, Capucha, etc, abajo Modelo de corte de cuello, Modelo de corte de calce etc, abajo prendas aplicables y abajo
 * aplicable en la maquina en q se usa"): tipo, prendas y máquina. Con más de tres prendas la lista se abrevia y con
 * Shift se ve completa ("2 si").
 *
 * <p>Existe porque un molde sin esto es indistinguible de otro en el inventario: esta línea dice qué es, a qué
 * prenda(s) aplica y en qué máquina se usa CADA ítem en particular.
 */
public final class PrendaLore {

    private PrendaLore() {}

    /** Dónde se usa el ítem, con el color del tema de cada máquina (cobre, verdín, oro, lila). */
    public enum Maquina {
        MODELADORA("modeladora", Formatting.GOLD),
        TINTES("tinturas", Formatting.GREEN),
        SUBLIMADORA("sublimadora", Formatting.YELLOW),
        ESTILADO("mesa_estilado", Formatting.LIGHT_PURPLE);

        final String bloque;
        final Formatting color;

        Maquina(String bloque, Formatting color) {
            this.bloque = bloque;
            this.color = color;
        }
    }

    /** Igual que {@link #molde} para un ítem que sirve en varias máquinas (retazo, muestra de color). */
    public static void moldeEn(List<Text> tooltip, String tipo, Maquina[] maquinas, String... prendas) {
        lineas(tooltip, tipo, prendas);
        MutableText donde = Text.translatable("modamod.tooltip.maquina", "").formatted(Formatting.GRAY);
        for (int i = 0; i < maquinas.length; i++) {
            if (i > 0) donde.append(Text.literal(", ").formatted(Formatting.GRAY));
            donde.append(Text.translatable("block.modamod." + maquinas[i].bloque).formatted(maquinas[i].color));
        }
        tooltip.add(donde);
    }

    /** Líneas de abajo del nombre: tipo, prendas aplicables y máquina. */
    public static void molde(List<Text> tooltip, String tipo, Maquina maquina, String... prendas) {
        lineas(tooltip, tipo, prendas);
        tooltip.add(Text.translatable("modamod.tooltip.maquina", Text.translatable("block.modamod." + maquina.bloque))
                .formatted(maquina.color));
    }

    private static void lineas(List<Text> tooltip, String tipo, String... prendas) {
        tooltip.add(Text.translatable("modamod.tipo." + tipo).formatted(Formatting.GRAY));
        if (prendas.length > 0) {
            boolean completa = prendas.length <= 3 || shift();
            int n = completa ? prendas.length : 3;
            StringBuilder lista = new StringBuilder();
            for (int i = 0; i < n; i++) {
                if (i > 0) lista.append(", ");
                lista.append(Text.translatable("modamod.prenda." + prendas[i]).getString());
            }
            if (!completa) lista.append("…");
            tooltip.add(Text.translatable("modamod.tooltip.prendas", lista.toString()).formatted(Formatting.DARK_GRAY));
            if (!completa) tooltip.add(Text.translatable("modamod.tooltip.shift").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        }
    }

    /** Shift apretado; solo existe en el cliente (el servidor nunca arma tooltips). */
    private static boolean shift() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() != net.fabricmc.api.EnvType.CLIENT) return false;
        return com.modamod.client.ShiftCliente.apretado();
    }

    /** Compatibilidad: la línea "Se usa en: X, Y" de antes. */
    public static MutableText seUsaEn(String... prendas) {
        StringBuilder lista = new StringBuilder();
        for (int i = 0; i < prendas.length; i++) {
            if (i > 0) lista.append(", ");
            lista.append(Text.translatable("modamod.prenda." + prendas[i]).getString());
        }
        return Text.translatable("modamod.tooltip.se_usa_en", lista.toString());
    }
}
