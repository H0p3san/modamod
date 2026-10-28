package com.modamod.item;

import com.modamod.region.Lado;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Pantalón: la prenda LARGA de pierna, análoga a la remera del torso — se
 * craftea de una sola longitud (largo completo) y se recorta después con
 * el molde de rango en la Mesa de Modelado.
 *
 * Es un {@link ClothingTrinketItem} normal —el tinte, el slot y el resto del
 * mecanismo son los mismos que cualquier otra prenda bilateral (medias)—;
 * lo propio son DOS ejes: la cobertura de pierna (dos anclajes que se
 * intersecan, ver {@link #filasVisibles}) y el tiro
 * ({@code PANTALON_TIRO}, cuánto sube la cintura sobre el torso — sin
 * cambios, sigue siendo un solo valor, no es cobertura de extremidad).
 */
public class PantalonItem extends ClothingTrinketItem {

    public PantalonItem(Settings settings) {
        super(settings, true);
    }

    /** Textura base COMPLETA del pantalón (12 filas) — el recorte real es en runtime, ver {@link #filasVisibles}. */
    public static final net.minecraft.util.Identifier TEXTURA_BASE =
            net.minecraft.util.Identifier.of(com.modamod.Modamod.MOD_ID, "textures/models/armor/pantalon_pantalon_layer_1.png");

    // ── anclaje SUPERIOR: cintura hacia abajo (el que escribe la Mesa desde el 2026-10-04) ──

    public static Botamanga largoSuperior(ItemStack stack, Lado lado) {
        return EjeBilateral.leer(stack, lado, ModamodComponents.PANTALON_LARGO_SUPERIOR,
                ModamodComponents.RIGHT_PANTALON_LARGO_SUPERIOR, Botamanga.PIE);
    }

    public static void setLargoSuperior(ItemStack stack, Lado lado, Botamanga valor) {
        EjeBilateral.escribir(stack, lado, ModamodComponents.PANTALON_LARGO_SUPERIOR,
                ModamodComponents.RIGHT_PANTALON_LARGO_SUPERIOR, Botamanga.PIE, valor);
    }

    // ── anclaje INFERIOR: tobillo hacia arriba (el de los pantalones hechos antes del 2026-10-04) ──

    public static Botamanga largoInferior(ItemStack stack, Lado lado) {
        return EjeBilateral.leer(stack, lado, ModamodComponents.PANTALON_LARGO_INFERIOR,
                ModamodComponents.RIGHT_PANTALON_LARGO_INFERIOR, Botamanga.PIE);
    }

    public static void setLargoInferior(ItemStack stack, Lado lado, Botamanga valor) {
        EjeBilateral.escribir(stack, lado, ModamodComponents.PANTALON_LARGO_INFERIOR,
                ModamodComponents.RIGHT_PANTALON_LARGO_INFERIOR, Botamanga.PIE, valor);
    }

    /**
     * Filas [desde,hasta) de las 12 con tela, para el lado dado: la intersección de los dos anclajes (como medias).
     * Desde el 2026-10-04 ("el anclaje de corte fija inferior cuando tiene que ser siempre superior") la Mesa
     * escribe el SUPERIOR (hasta dónde baja la pierna desde la cintura); el inferior queda para los pantalones
     * hechos antes. Un anclaje nunca tocado se trata como "12, abierto". {@code hasta} es exclusivo.
     */
    public static int[] filasVisibles(ItemStack stack, Lado lado) {
        boolean supAusente = !EjeBilateral.presente(stack, lado,
                ModamodComponents.PANTALON_LARGO_SUPERIOR, ModamodComponents.RIGHT_PANTALON_LARGO_SUPERIOR);
        boolean infAusente = !EjeBilateral.presente(stack, lado,
                ModamodComponents.PANTALON_LARGO_INFERIOR, ModamodComponents.RIGHT_PANTALON_LARGO_INFERIOR);
        int filasSup = supAusente ? 12 : largoSuperior(stack, lado).filas;
        int filasInf = infAusente ? 12 : largoInferior(stack, lado).filas;
        return EjeBilateral.interseccion(filasSup, filasInf);
    }

    /** El tiro actual. Sin componente, MEDIO (cintura natural). No es cobertura de extremidad, sigue igual. */
    public static PantalonTiro tiro(ItemStack stack) {
        PantalonTiro t = stack.get(ModamodComponents.PANTALON_TIRO);
        return t == null ? PantalonTiro.MEDIO : t;
    }

    public static void setTiro(ItemStack stack, PantalonTiro tiro) {
        if (tiro == PantalonTiro.MEDIO) stack.remove(ModamodComponents.PANTALON_TIRO);
        else stack.set(ModamodComponents.PANTALON_TIRO, tiro);
    }

    /**
     * El nombre ya NO varía con el largo — con dos anclajes independientes
     * (y potencialmente asimétricos) no hay un único "valor" que nombrar.
     * El detalle va en el tooltip.
     */
    @Override
    public Text getName(ItemStack stack) {
        // Por dónde termina la pierna, si las dos coinciden (2026-10-23, "me gusta todo"): shorts, bermudas, 3/4, 7/8.
        return Text.translatable("item.modamod.pantalon_" + NombresDePrenda.pantalon(
                filasVisibles(stack, com.modamod.region.Lado.DERECHA), filasVisibles(stack, com.modamod.region.Lado.IZQUIERDA)));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("modamod.pantalon.tiro", Text.translatable(tiro(stack).traduccion()))
                .formatted(Formatting.GRAY));
        // Sin rama de anclaje superior (2026-09-23, "pantalones se fija
        // solo el corte inferior") — el pantalón ya no tiene ese eje.
        Botamanga inf = largoInferior(stack, Lado.IZQUIERDA);
        if (inf != Botamanga.PIE) {
            tooltip.add(Text.translatable("modamod.cobertura.inferior", Text.translatable(inf.traduccion()))
                    .formatted(Formatting.GRAY));
        }
    }
}
