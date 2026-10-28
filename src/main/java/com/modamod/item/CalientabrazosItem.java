package com.modamod.item;

import com.modamod.region.Lado;
import com.modamod.sublimadora.Variante;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Calentadores de brazo: prenda base del BRAZO, análoga a las medias en la
 * pierna — se pone debajo de la manga de la remera.
 *
 * Cobertura de extremidad con dos anclajes que se intersecan (mismo modelo
 * que {@link PantalonItem}/{@link MediasLargo}): SUPERIOR (hombro hacia
 * abajo, el que ya existía — reusa el molde de manga de remera,
 * {@code MoldeItem.Eje.MANGA}) e INFERIOR (muñeca hacia arriba, eje nuevo).
 *
 * NO tiene tiro — sigue sin tenerlo, tiro es exclusivo de prendas
 * inferiores (ver docs/archivo/MAQUINAS.md §"Categorías de patrones de modelado").
 */
public class CalientabrazosItem extends ClothingTrinketItem {

    public CalientabrazosItem(Settings settings) {
        super(settings, true);
    }

    // ── anclaje SUPERIOR: hombro hacia abajo (el que ya existía) ────────

    public static Variante.Manga coberturaSuperior(ItemStack stack, Lado lado) {
        return EjeBilateral.leer(stack, lado, ModamodComponents.CALIENTABRAZOS_COBERTURA_SUPERIOR,
                ModamodComponents.RIGHT_CALIENTABRAZOS_COBERTURA_SUPERIOR, Variante.Manga.LARGA);
    }

    public static void setCoberturaSuperior(ItemStack stack, Lado lado, Variante.Manga valor) {
        EjeBilateral.escribir(stack, lado, ModamodComponents.CALIENTABRAZOS_COBERTURA_SUPERIOR,
                ModamodComponents.RIGHT_CALIENTABRAZOS_COBERTURA_SUPERIOR, Variante.Manga.LARGA, valor);
    }

    // ── anclaje INFERIOR: muñeca hacia arriba (nuevo) ───────────────────

    public static Variante.Manga coberturaInferior(ItemStack stack, Lado lado) {
        return EjeBilateral.leer(stack, lado, ModamodComponents.CALIENTABRAZOS_COBERTURA_INFERIOR,
                ModamodComponents.RIGHT_CALIENTABRAZOS_COBERTURA_INFERIOR, Variante.Manga.LARGA);
    }

    public static void setCoberturaInferior(ItemStack stack, Lado lado, Variante.Manga valor) {
        EjeBilateral.escribir(stack, lado, ModamodComponents.CALIENTABRAZOS_COBERTURA_INFERIOR,
                ModamodComponents.RIGHT_CALIENTABRAZOS_COBERTURA_INFERIOR, Variante.Manga.LARGA, valor);
    }

    /** Filas [desde,hasta) con tela — intersección de los dos anclajes. */
    public static int[] filasVisibles(ItemStack stack, Lado lado) {
        boolean supAusente = !EjeBilateral.presente(stack, lado,
                ModamodComponents.CALIENTABRAZOS_COBERTURA_SUPERIOR, ModamodComponents.RIGHT_CALIENTABRAZOS_COBERTURA_SUPERIOR);
        boolean infAusente = !EjeBilateral.presente(stack, lado,
                ModamodComponents.CALIENTABRAZOS_COBERTURA_INFERIOR, ModamodComponents.RIGHT_CALIENTABRAZOS_COBERTURA_INFERIOR);
        int filasSup = supAusente ? 12 : coberturaSuperior(stack, lado).filas;
        int filasInf = infAusente ? 12 : coberturaInferior(stack, lado).filas;
        return EjeBilateral.interseccion(filasSup, filasInf);
    }

    /** Guantes, guantes largos, brazalete o calentadores, según el largo (2026-10-23, "calientabrazos tambien como guantes brazaletes etc"). */
    @Override
    public Text getName(ItemStack stack) {
        int[] der = filasVisibles(stack, Lado.DERECHA), izq = filasVisibles(stack, Lado.IZQUIERDA);
        return Text.translatable("item.modamod.calientabrazos." + (java.util.Arrays.equals(der, izq) ? NombresDePrenda.calientabrazos(der) : "calientabrazos"));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        Variante.Manga sup = coberturaSuperior(stack, Lado.IZQUIERDA);
        if (sup != Variante.Manga.LARGA) {
            tooltip.add(Text.translatable("modamod.cobertura.superior",
                            Text.translatable("modamod.sublimadora.manga." + sup.clave))
                    .formatted(Formatting.GRAY));
        }
        Variante.Manga inf = coberturaInferior(stack, Lado.IZQUIERDA);
        if (inf != Variante.Manga.LARGA) {
            tooltip.add(Text.translatable("modamod.cobertura.inferior",
                            Text.translatable("modamod.sublimadora.manga." + inf.clave))
                    .formatted(Formatting.GRAY));
        }
    }
}
