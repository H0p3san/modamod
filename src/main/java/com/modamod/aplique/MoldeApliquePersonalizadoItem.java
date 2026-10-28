package com.modamod.aplique;

import com.modamod.item.ModamodComponents;
import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Molde de aplique personalizado (2026-10-04, "que pueda producir un molde ahí para que se use en la normal como
 * los moldes de aplique normal"): lo fabrica la Mesa de estilado creativa con el aplique elegido y guarda su
 * plantilla entera (modelo u objeto, colocación, movimiento, giro y tamaño) en {@code modamod:aplique_plantilla}.
 * Va en el slot del molde de la Mesa normal, como los demás: no se gasta ni gasta el objeto; el color sale del retazo
 * (o de la muestra de color, en los objetos). El nombre lo pone el jugador (yunque).
 */
public class MoldeApliquePersonalizadoItem extends Item {

    public MoldeApliquePersonalizadoItem(Settings settings) {
        super(settings);
    }

    @org.jetbrains.annotations.Nullable
    public static Aplique plantilla(ItemStack stack) {
        return stack.get(ModamodComponents.APLIQUE_PLANTILLA);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "aplique", PrendaLore.Maquina.ESTILADO, "todas");
        Aplique a = plantilla(stack);
        if (a == null) return;
        Text que = a.objeto() != null ? a.objeto().item().getName() : Text.translatable(a.modelo().traduccion());
        tooltip.add(que.copy().formatted(Formatting.GOLD));
        if (a.objeto() != null) {
            tooltip.add(Text.translatable("modamod.aplique.molde_personalizado.cara",
                    Text.translatable("modamod.estilado.cara." + nombreCara(a.colocacion().caraBase()))).formatted(Formatting.GRAY));
        }
        tooltip.add(Text.translatable("modamod.aplique.molde_personalizado.blandura", Math.round(a.blandura() * 100))
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("modamod.aplique.molde_personalizado.ayuda").formatted(Formatting.DARK_GRAY));
    }

    private static String nombreCara(net.minecraft.util.math.Direction d) {
        return switch (d) {
            case UP -> "arriba";
            case DOWN -> "abajo";
            case EAST -> "izquierda";
            case WEST -> "derecha";
            case NORTH -> "frente";
            default -> "atras";
        };
    }
}
