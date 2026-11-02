package com.modamod.prenda;

import com.modamod.garment.Parte;
import com.modamod.item.ModamodItems;
import com.modamod.sublimadora.ModItems;
import net.minecraft.item.BannerItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El registro de qué es cada ítem del mod como prenda: una sola tabla, leída de un vistazo, en lugar de preguntas
 * {@code instanceof} repartidas por las máquinas y el render.
 */
public final class Prendas {
    private static final Map<Item, DefinicionDePrenda> REGISTRO = new HashMap<>();
    private static final DefinicionDePrenda BANNER = new DefinicionDePrenda(CategoriaDePrenda.BANNER, FormaDeDibujo.NINGUNA);

    private Prendas() {}

    public static void init() {
        // Ropa de cuerpo pegada a la piel.
        registrar(ModamodItems.SOCKS_SOLID, new DefinicionDePrenda(CategoriaDePrenda.MEDIAS, FormaDeDibujo.FORRO,
                Parte.PIERNA_IZQ, Parte.PIERNA_DER));
        registrar(ModamodItems.PANTALON, new DefinicionDePrenda(CategoriaDePrenda.PANTALON, FormaDeDibujo.FORRO,
                Parte.PIERNA_IZQ, Parte.PIERNA_DER));
        // La remera tapa los brazos aunque sea musculosa: así la manga pintada en la skin no asoma.
        registrar(ModItems.REMERA, new DefinicionDePrenda(CategoriaDePrenda.REMERA, FormaDeDibujo.FORRO,
                Parte.TORSO, Parte.BRAZO_IZQ, Parte.BRAZO_DER));
        registrar(ModamodItems.CALIENTABRAZOS, new DefinicionDePrenda(CategoriaDePrenda.CALIENTABRAZOS, FormaDeDibujo.FORRO,
                Parte.BRAZO_IZQ, Parte.BRAZO_DER));
        // Mallas: se dibujan encima sin tapar partes (taparlas dejaría huecos de piel donde la malla no llega).
        registrar(ModamodItems.POLLERA, new DefinicionDePrenda(CategoriaDePrenda.POLLERA, FormaDeDibujo.MALLA));
        registrar(ModamodItems.CAPA, new DefinicionDePrenda(CategoriaDePrenda.CAPA, FormaDeDibujo.MALLA));
        registrar(ModamodItems.DELANTAL, new DefinicionDePrenda(CategoriaDePrenda.DELANTAL, FormaDeDibujo.MALLA));
        // Accesorios.
        registrar(ModamodItems.SOMBRERO_BRUJA, new DefinicionDePrenda(CategoriaDePrenda.SOMBRERO, FormaDeDibujo.ACCESORIO));
        registrar(ModamodItems.BANDA, new DefinicionDePrenda(CategoriaDePrenda.BANDA, FormaDeDibujo.ACCESORIO));
        registrar(ModamodItems.BORCEGOS, new DefinicionDePrenda(CategoriaDePrenda.CALZADO, FormaDeDibujo.ACCESORIO));
        for (Item cara : List.of(ModamodItems.ANTIFAZ, ModamodItems.MEDIA_MASCARA, ModamodItems.MASCARA_COMPLETA)) {
            registrar(cara, new DefinicionDePrenda(CategoriaDePrenda.MASCARA, FormaDeDibujo.ACCESORIO));
        }
        registrar(ModamodItems.BARBIJO, new DefinicionDePrenda(CategoriaDePrenda.BARBIJO, FormaDeDibujo.ACCESORIO));
        // Lo que no se viste pero las máquinas trabajan.
        registrar(ModamodItems.RETAZO_APLIQUE, new DefinicionDePrenda(CategoriaDePrenda.APLIQUE, FormaDeDibujo.NINGUNA));
        registrar(ModamodItems.MALETIN, new DefinicionDePrenda(CategoriaDePrenda.MALETIN, FormaDeDibujo.NINGUNA));
    }

    public static void registrar(Item item, DefinicionDePrenda definicion) {
        REGISTRO.put(item, definicion);
    }

    @Nullable
    public static DefinicionDePrenda de(Item item) {
        DefinicionDePrenda d = REGISTRO.get(item);
        if (d == null && item instanceof BannerItem) return BANNER;
        return d;
    }

    @Nullable
    public static DefinicionDePrenda de(ItemStack stack) {
        return stack.isEmpty() ? null : de(stack.getItem());
    }

    @Nullable
    public static CategoriaDePrenda categoria(ItemStack stack) {
        DefinicionDePrenda d = de(stack);
        return d == null ? null : d.categoria();
    }

    public static boolean es(ItemStack stack, CategoriaDePrenda categoria) {
        return categoria(stack) == categoria;
    }

    /** Lo dibuja el recorrido de ropa del cuerpo (forro o malla). Era {@code com.modamod.prenda.Prendas.esRopaDeCuerpo}. */
    public static boolean esRopaDeCuerpo(ItemStack stack) {
        DefinicionDePrenda d = de(stack);
        return d != null && d.forma().esDeCuerpo();
    }

    /** Las partes que tapa este conjunto de prendas, sin repetir. */
    public static List<Parte> partesCubiertas(List<ItemStack> prendas) {
        List<Parte> out = new ArrayList<>();
        for (ItemStack stack : prendas) {
            DefinicionDePrenda d = de(stack);
            if (d == null) continue;
            for (Parte p : d.partesDe(stack)) {
                if (!out.contains(p)) out.add(p);
            }
        }
        return out;
    }

    public static Set<Item> items() {
        return REGISTRO.keySet();
    }
}
