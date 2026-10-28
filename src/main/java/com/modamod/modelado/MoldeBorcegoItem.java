package com.modamod.modelado;

import com.modamod.item.BorcegoBotamanga;
import com.modamod.item.BorcegoCana;
import com.modamod.item.BorcegoSuela;
import com.modamod.item.CalzadoCierre;
import com.modamod.item.CalzadoDetalle;
import com.modamod.item.CalzadoPunta;
import com.modamod.item.PrendaLore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Moldes del calzado (2026-10-08, "modelame unos borcegos y agreguemos un slot de calzado"; ampliados 2026-10-14 con el
 * zip de Claude Design): la capellada (caña), la suela, la punta, el cierre y el detalle, y cómo se lleva el pantalón (la
 * botamanga por dentro o por fuera) son moldes de la categoría Calzado de la Modeladora, cada uno en su pin (uno por pie).
 * Exclusivos del calzado. El molde en blanco de cada eje vuelve al valor de fábrica de los borcegos.
 */
public class MoldeBorcegoItem extends Item {

    public enum Tipo {
        CANA_BAJA(BorcegoCana.BAJA), CANA_MEDIA(BorcegoCana.MEDIA), CANA_ALTA(BorcegoCana.ALTA),
        SUELA_CHATA(BorcegoSuela.CHATA), SUELA_PLATAFORMA(BorcegoSuela.PLATAFORMA),
        BOTAMANGA_ADENTRO(BorcegoBotamanga.ADENTRO), BOTAMANGA_AFUERA(BorcegoBotamanga.AFUERA),
        // Capelladas del zip (2026-10-14).
        CANA_BOTA_ALTA(BorcegoCana.BOTA_ALTA), CANA_ZAPATO(BorcegoCana.ZAPATO), CANA_MOCASIN(BorcegoCana.MOCASIN),
        CANA_BAILARINA(BorcegoCana.BAILARINA), CANA_TACO(BorcegoCana.TACO), CANA_TENIS_BAJA(BorcegoCana.TENIS_BAJA),
        CANA_TENIS_ALTA(BorcegoCana.TENIS_ALTA), CANA_PANTUFLA(BorcegoCana.PANTUFLA), CANA_NINGUNA(BorcegoCana.NINGUNA),
        // Suelas.
        SUELA_FINA(BorcegoSuela.FINA), SUELA_TACO_CHICO(BorcegoSuela.TACO_CHICO), SUELA_CUNA(BorcegoSuela.CUNA),
        SUELA_GOMA_TENIS(BorcegoSuela.GOMA_TENIS), SUELA_PLATAFORMA_TENIS(BorcegoSuela.PLATAFORMA_TENIS),
        SUELA_CHANCLA(BorcegoSuela.CHANCLA), SUELA_SANDALIA(BorcegoSuela.SANDALIA), SUELA_TELA(BorcegoSuela.TELA),
        // Puntas.
        PUNTA_REDONDA(CalzadoPunta.REDONDA), PUNTA_ALARGADA(CalzadoPunta.ALARGADA), PUNTA_CUADRADA(CalzadoPunta.CUADRADA),
        PUNTA_FINA(CalzadoPunta.FINA), PUNTA_ANCHA(CalzadoPunta.ANCHA), PUNTA_GOMA(CalzadoPunta.GOMA),
        PUNTA_CUERO(CalzadoPunta.CUERO), PUNTA_DESCUBIERTA(CalzadoPunta.DESCUBIERTA),
        // Cierres.
        CIERRE_CORDONES(CalzadoCierre.CORDONES), CIERRE_VELCRO(CalzadoCierre.VELCRO),
        CIERRE_TIRA_HEBILLA(CalzadoCierre.TIRA_HEBILLA), CIERRE_TIRA_V(CalzadoCierre.TIRA_V),
        CIERRE_TOBILLERA(CalzadoCierre.TOBILLERA), CIERRE_GLADIADORA(CalzadoCierre.GLADIADORA),
        CIERRE_COSTURA_LATERAL(CalzadoCierre.COSTURA_LATERAL), CIERRE_SIN(CalzadoCierre.SIN),
        // Detalles.
        DETALLE_LENGUETA(CalzadoDetalle.LENGUETA), DETALLE_PARCHE(CalzadoDetalle.PARCHE),
        DETALLE_COSTURA_EMPEINE(CalzadoDetalle.COSTURA_EMPEINE);

        public final BorcegoCana cana;
        public final BorcegoSuela suela;
        public final BorcegoBotamanga botamanga;
        public final CalzadoPunta punta;
        public final CalzadoCierre cierre;
        public final CalzadoDetalle detalle;

        Tipo(BorcegoCana cana, BorcegoSuela suela, BorcegoBotamanga botamanga, CalzadoPunta punta, CalzadoCierre cierre,
             CalzadoDetalle detalle) {
            this.cana = cana;
            this.suela = suela;
            this.botamanga = botamanga;
            this.punta = punta;
            this.cierre = cierre;
            this.detalle = detalle;
        }

        Tipo(BorcegoCana cana) { this(cana, null, null, null, null, null); }
        Tipo(BorcegoSuela suela) { this(null, suela, null, null, null, null); }
        Tipo(BorcegoBotamanga botamanga) { this(null, null, botamanga, null, null, null); }
        Tipo(CalzadoPunta punta) { this(null, null, null, punta, null, null); }
        Tipo(CalzadoCierre cierre) { this(null, null, null, null, cierre, null); }
        Tipo(CalzadoDetalle detalle) { this(null, null, null, null, null, detalle); }

        public boolean esCana() { return cana != null; }
        public boolean esSuela() { return suela != null; }
        public boolean esBotamanga() { return botamanga != null; }
        public boolean esPunta() { return punta != null; }
        public boolean esCierre() { return cierre != null; }
        public boolean esDetalle() { return detalle != null; }
    }

    public final Tipo tipo;

    public MoldeBorcegoItem(Settings settings, Tipo tipo) {
        super(settings);
        this.tipo = tipo;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        PrendaLore.molde(tooltip, "borcegos", PrendaLore.Maquina.MODELADORA, "borcegos");
        tooltip.add(Text.translatable("modamod.sublimadora.molde.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
