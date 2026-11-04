package com.modamod.ropa;

import com.modamod.prenda.CategoriaDePrenda;
import com.modamod.prenda.Prendas;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * La ropa puesta (rediseño, tanda 3): una sola lista de {@link #LUGARES} lugares, el slot {@code ropa/lugar} de Trinkets.
 * El orden es el de dibujo al revés: el lugar 0 (arriba a la izquierda en la pantalla de Ropa) es lo de más afuera y el
 * último, lo de más adentro. Cualquier prenda o accesorio entra en cualquier lugar.
 */
public final class ListaDeRopa {

    private ListaDeRopa() {}

    public static final String GRUPO = "ropa", SLOT = "lugar";
    public static final int LUGARES = 16, COLUMNAS = 4;

    /** Los lugares de ropa de la entidad, o null si no tiene (no es un jugador, o Trinkets no está listo). */
    @Nullable
    public static TrinketInventory de(LivingEntity entidad) {
        var comp = TrinketsApi.getTrinketComponent(entidad);
        if (comp.isEmpty()) return null;
        var grupo = comp.get().getInventory().get(GRUPO);
        return grupo == null ? null : grupo.get(SLOT);
    }

    /** Lo que admite un lugar de la lista: los ítems del tag {@code trinkets:ropa/lugar}. */
    public static final TagKey<Item> ENTRA = TagKey.of(RegistryKeys.ITEM, Identifier.of("trinkets", GRUPO + "/" + SLOT));

    public static boolean entra(ItemStack stack) {
        return !stack.isEmpty() && stack.isIn(ENTRA);
    }

    /** Lo puesto en cada lugar, de afuera hacia adentro (vacío si no tiene la lista). */
    public static List<ItemStack> lugares(@Nullable Inventory inv) {
        List<ItemStack> out = new ArrayList<>(LUGARES);
        for (int i = 0; i < LUGARES; i++) out.add(inv != null && i < inv.size() ? inv.getStack(i) : ItemStack.EMPTY);
        return out;
    }

    /**
     * La ropa de cuerpo de {@code lugares} en el orden de dibujo, de adentro hacia afuera. Un Top con otro Top más adentro
     * se dibuja como chaqueta ({@link #esTopDeAfuera}).
     */
    public static List<ItemStack> paraDibujar(List<ItemStack> lugares) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = lugares.size() - 1; i >= 0; i--) {
            ItemStack stack = lugares.get(i);
            if (!Prendas.esRopaDeCuerpo(stack)) continue;
            out.add(esTopDeAfuera(lugares, i) ? com.modamod.item.TopCorte.comoExterior(stack) : stack);
        }
        return out;
    }

    /**
     * ¿El Top del lugar {@code indice} se lleva como prenda de afuera (chaqueta)? Sí cuando más adentro en la lista hay otro
     * Top: el de afuera se dibuja en la capa exterior.
     */
    public static boolean esTopDeAfuera(List<ItemStack> lugares, int indice) {
        if (!Prendas.es(lugares.get(indice), CategoriaDePrenda.REMERA)) return false;
        for (int i = indice + 1; i < lugares.size(); i++) {
            if (Prendas.es(lugares.get(i), CategoriaDePrenda.REMERA)) return true;
        }
        return false;
    }

    /**
     * Cuántas prendas de cuerpo van por encima de la armadura: las de los lugares de las filas de arriba de la fila de la
     * armadura. Son las últimas de {@link #paraDibujar}, que va de adentro hacia afuera.
     */
    public static int prendasSobreLaArmadura(List<ItemStack> lugares, int filaArmadura) {
        int hasta = Math.min(lugares.size(), filaArmadura * COLUMNAS), n = 0;
        for (int i = 0; i < hasta; i++) if (Prendas.esRopaDeCuerpo(lugares.get(i))) n++;
        return n;
    }

    /** Lo primero puesto (de afuera hacia adentro) que cumple {@code cual}, o vacío. */
    public static ItemStack primera(List<ItemStack> lugares, Predicate<ItemStack> cual) {
        for (ItemStack s : lugares) if (!s.isEmpty() && cual.test(s)) return s;
        return ItemStack.EMPTY;
    }

    /** La fila de la pantalla de Ropa en la que se ve el lugar {@code indice}, con la fila de la armadura en {@code filaArmadura}. */
    public static int filaEnPantalla(int indice, int filaArmadura) {
        int fila = indice / COLUMNAS;
        return fila >= filaArmadura ? fila + 1 : fila;
    }

    /** {@link #prendasSobreLaArmadura} de un jugador, con su fila de la armadura ({@link Cosmeticos#fila}). */
    public static int prendasSobreLaArmadura(LivingEntity entidad) {
        if (!(entidad instanceof net.minecraft.entity.player.PlayerEntity jugador)) return 0;
        return prendasSobreLaArmadura(lugares(de(entidad)), Cosmeticos.de(jugador).fila());
    }

    /** Lo que tiene puesto en ese lugar, o vacío. */
    public static ItemStack en(LivingEntity entidad, int indice) {
        TrinketInventory l = de(entidad);
        return l == null || indice < 0 || indice >= l.size() ? ItemStack.EMPTY : l.getStack(indice);
    }
}
