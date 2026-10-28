package com.modamod.modelado;

import com.modamod.item.PrendaLore;
import com.modamod.item.SombreroAla;
import com.modamod.item.SombreroEstilo;
import com.modamod.item.SombreroGrosor;
import com.modamod.item.SombreroPlanta;
import com.modamod.item.SombreroPunta;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Moldes del sombrero (2026-10-05, "segunda tanda del sombrero"; ampliados 2026-10-14 con las mallas): el ala, la copa, la
 * planta de las alas planas, el estilo (malla o cuboides), el grosor del ala y la banda son moldes de la categoría Sombrero
 * de la Modeladora, cada uno en su pin. Exclusivos del sombrero.
 */
public class MoldeSombreroItem extends Item {

    public enum Tipo {
        ALA_ANCHA(SombreroAla.ANCHA), ALA_CORTA(SombreroAla.CORTA),
        PUNTA_RECTA(SombreroPunta.RECTA), PUNTA_DOBLADA(SombreroPunta.DOBLADA),
        ALA_VINCHA(SombreroAla.VINCHA), ALA_COFIA(SombreroAla.COFIA),
        ALA_VAQUERA(SombreroAla.VAQUERA), ALA_ANGOSTA(SombreroAla.ANGOSTA),
        ALA_VISERA_CURVA(SombreroAla.VISERA_CURVA), ALA_VISERA_CHATA(SombreroAla.VISERA_CHATA),
        ALA_SIN_ALA(SombreroAla.SIN_ALA), ALA_CAIDA(SombreroAla.CAIDA), ALA_PLANA(SombreroAla.PLANA),
        COPA_CILINDRO_ALTO(SombreroPunta.CILINDRO_ALTO), COPA_HENDIDURA(SombreroPunta.HENDIDURA),
        COPA_CUPULA(SombreroPunta.CUPULA), COPA_CUPULA_CUADRADA(SombreroPunta.CUPULA_CUADRADA),
        COPA_PLATO(SombreroPunta.PLATO), COPA_GORRO_CAIDO(SombreroPunta.GORRO_CAIDO),
        // Tanda de las mallas (2026-10-14): alas, copas y la planta cuadrada.
        ALA_MEDIA(SombreroAla.MEDIA), ALA_TRICORNIO(SombreroAla.TRICORNIO), ALA_VISERA_CORTA(SombreroAla.VISERA_CORTA),
        COPA_CHARRO(SombreroPunta.CHARRO), COPA_TOQUE(SombreroPunta.TOQUE),
        COPA_PANADERO(SombreroPunta.PANADERO), COPA_MARINERO(SombreroPunta.MARINERO),
        PLANTA_CUADRADA(SombreroPlanta.CUADRADA),
        // Cuboides en vez de malla (2026-10-14).
        ESTILO_CAJAS(SombreroEstilo.CAJAS),
        // Segundo lote (2026-10-14): ala larga, planta redonda, ala gruesa y la banda.
        ALA_LARGA(SombreroAla.LARGA), PLANTA_REDONDA(SombreroPlanta.REDONDA),
        GROSOR_GRUESA(SombreroGrosor.GRUESA), BANDA_PONER(true);

        public final SombreroAla ala;
        public final SombreroPunta punta;
        public final SombreroPlanta planta;
        public final SombreroEstilo estilo;
        public final SombreroGrosor grosor;
        /** Solo el molde de la banda: true = ponerla. */
        public final Boolean banda;

        Tipo(SombreroAla ala, SombreroPunta punta, SombreroPlanta planta, SombreroEstilo estilo, SombreroGrosor grosor, Boolean banda) {
            this.ala = ala;
            this.punta = punta;
            this.planta = planta;
            this.estilo = estilo;
            this.grosor = grosor;
            this.banda = banda;
        }

        Tipo(SombreroAla ala) { this(ala, null, null, null, null, null); }
        Tipo(SombreroPunta punta) { this(null, punta, null, null, null, null); }
        Tipo(SombreroPlanta planta) { this(null, null, planta, null, null, null); }
        Tipo(SombreroEstilo estilo) { this(null, null, null, estilo, null, null); }
        Tipo(SombreroGrosor grosor) { this(null, null, null, null, grosor, null); }
        Tipo(boolean banda) { this(null, null, null, null, null, banda); }

        public boolean esAla() { return ala != null; }
        public boolean esPunta() { return punta != null; }
        public boolean esPlanta() { return planta != null; }
        public boolean esEstilo() { return estilo != null; }
        public boolean esGrosor() { return grosor != null; }
        public boolean esBanda() { return banda != null; }
    }

    public final Tipo tipo;

    public MoldeSombreroItem(Settings settings, Tipo tipo) {
        super(settings);
        this.tipo = tipo;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "sombrero", PrendaLore.Maquina.MODELADORA, "sombrero");
        tooltip.add(Text.translatable("modamod.sublimadora.molde.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
