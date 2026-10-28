package com.modamod.receta;

import com.modamod.Modamod;
import com.modamod.telar.TelarPrenda;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Recetas de prendas que salen del color de las lanas (2026-10-12, "que salga del color de las lanas", respuesta a
 * la decisión 1 de la propuesta de recetas). Mismo JSON que {@code crafting_shaped} / {@code crafting_shapeless}
 * más el campo {@code prenda} (nombre de {@link TelarPrenda}): el resultado es {@code TelarPrenda.crear(color)} con
 * el promedio de las lanas puestas en la grilla.
 */
public final class ModamodRecetas {
    private ModamodRecetas() { }

    public static final RecipeSerializer<RecetaTenida> TENIDA = Registry.register(Registries.RECIPE_SERIALIZER,
            Identifier.of(Modamod.MOD_ID, "prenda_tenida"), new RecetaTenida.Serializer());

    public static void init() { }

    /** Promedio (por canal) del color de las lanas de la grilla; blanco si no hay ninguna. */
    static int colorDeLanas(CraftingRecipeInput in, int extraRgb) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (ItemStack s : in.getStacks()) {
            int c = s.isEmpty() ? -1 : TelarPrenda.colorDeLana(s.getItem());
            if (c < 0) continue;
            r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
        }
        if (extraRgb >= 0) { r += (extraRgb >> 16) & 255; g += (extraRgb >> 8) & 255; b += extraRgb & 255; n++; }
        if (n == 0) return 0xFFFFFF;
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }
}
