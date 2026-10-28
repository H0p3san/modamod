package com.modamod.modelado;

import com.modamod.item.ChaquetaFrente;
import com.modamod.item.ChaquetaSolapa;
import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Moldes de la chaqueta (2026-10-07, "como se te ocurre que mejor hacemos las chaquetas"): el frente (cerrada o
 * abierta), la capucha (con o sin) son moldes de la categoría
 * Chaqueta de la Modeladora, cada uno en su pin. El cuello, las mangas, el largo y el calce usan los moldes de
 * siempre. Exclusivos de la chaqueta.
 */
public class MoldeChaquetaItem extends Item {

    public enum Tipo {
        FRENTE_CERRADA(ChaquetaFrente.CERRADA, null, null), FRENTE_ABIERTA(ChaquetaFrente.ABIERTA, null, null),
        // Solapas (2026-10-07, "traje separado... varios tipos de solapa"): nuevos al final.
        SOLAPA_NINGUNA(null, null, ChaquetaSolapa.NINGUNA), SOLAPA_PICO(null, null, ChaquetaSolapa.PICO),
        SOLAPA_REDONDA(null, null, ChaquetaSolapa.REDONDA), SOLAPA_CHAL(null, null, ChaquetaSolapa.CHAL),
        // Frentes nuevos (2026-10-08): al final.
        FRENTE_CRUZADA(ChaquetaFrente.CRUZADA, null, null), FRENTE_ABIERTA_PECHO(ChaquetaFrente.ABIERTA_PECHO, null, null);

        public final ChaquetaFrente frente;
        public final Boolean capucha;
        public final ChaquetaSolapa solapa;

        Tipo(ChaquetaFrente frente, Boolean capucha, ChaquetaSolapa solapa) {
            this.frente = frente;
            this.capucha = capucha;
            this.solapa = solapa;
        }

        public boolean esSolapa() { return solapa != null; }

        public boolean esFrente() { return frente != null; }
        public boolean esCapucha() { return capucha != null; }
    }

    public final Tipo tipo;

    public MoldeChaquetaItem(Settings settings, Tipo tipo) {
        super(settings);
        this.tipo = tipo;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "chaqueta", PrendaLore.Maquina.MODELADORA, "chaqueta");
        tooltip.add(Text.translatable("modamod.sublimadora.molde.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
