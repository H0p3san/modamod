package com.modamod.garment;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Que items son prendas del sistema nuevo.
 *
 * Es un registro y no un {@code instanceof}: la remera es un RemeraItem del
 * paquete de la sublimadora y las medias un ClothingTrinketItem del de
 * prendas, y no tienen —ni conviene que tengan— una superclase comun. Un
 * mapa deja que cada paquete registre lo suyo sin que ninguno dependa del
 * otro.
 *
 * Registrar es requisito para que el sistema de capas dibuje la prenda. Un
 * item que no este aca simplemente no se ve puesto.
 */
public final class Garments {

    private static final Map<Item, Garment> REGISTRO = new HashMap<>();

    private Garments() {}

    public static void registrar(Item item, Garment garment) {
        REGISTRO.put(item, garment);
    }

    @Nullable
    public static Garment de(Item item) {
        return REGISTRO.get(item);
    }

    @Nullable
    public static Garment de(ItemStack stack) {
        return stack.isEmpty() ? null : REGISTRO.get(stack.getItem());
    }

    public static boolean esPrenda(ItemStack stack) {
        return de(stack) != null;
    }

    /** Las partes que cubre este conjunto de prendas, sin repetir. */
    public static List<Parte> partesCubiertas(List<ItemStack> prendas) {
        List<Parte> out = new ArrayList<>();
        for (ItemStack stack : prendas) {
            Garment g = de(stack);
            if (g == null) continue;
            for (Parte p : g.partes(stack)) {
                if (!out.contains(p)) out.add(p);
            }
        }
        return out;
    }
}
