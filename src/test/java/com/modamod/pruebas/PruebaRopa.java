package com.modamod.pruebas;

import com.modamod.ropa.ListaDeRopa;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/**
 * Lugares fijos de la lista de ropa para las pruebas (de afuera hacia adentro), los mismos para el jugador y el maniquí:
 * sombrero, collar, capa, delantal, cinto, chaqueta ×2, remera ×2, calientabrazos, calzado, pantalón ×2 y medias ×2. Cada
 * categoría de prenda se ve como un inventario chico sobre su tramo.
 */
final class PruebaRopa {

    private PruebaRopa() {}

    /** Categorías de prenda de las pruebas. */
    static final int REMERA = 0, PANTALON = 1, MEDIAS = 2, CALIENTABRAZOS = 3, CHAQUETA = 4;
    /** Lugares de los accesorios y de lo que va de a uno. */
    static final int SOMBRERO = 0, COLLAR = 1, CAPA = 2, DELANTAL = 3, CINTO = 4, CALZADO = 10;

    /** El lugar de la capa {@code capa} (0 = la de afuera) de esa categoría. */
    static int indice(int categoria, int capa) {
        return switch (categoria) {
            case CHAQUETA -> 5;
            case REMERA -> 7;
            case CALIENTABRAZOS -> 9;
            case PANTALON -> 11;
            case MEDIAS -> 13;
            default -> throw new IllegalArgumentException("categoria " + categoria);
        } + capa;
    }

    private static int largo(int categoria) { return categoria == CALIENTABRAZOS ? 1 : 2; }

    static Inventory delantal(TrinketComponent comp) { return tramo(comp, DELANTAL, 1); }

    static Inventory calzado(TrinketComponent comp) { return tramo(comp, CALZADO, 1); }

    static Inventory lugar(TrinketComponent comp, int categoria) { return tramo(comp, indice(categoria, 0), largo(categoria)); }

    /** Cuántas prendas lleva puestas en la lista. */
    static int puestas(PlayerEntity jugador) {
        TrinketInventory inv = ListaDeRopa.de(jugador);
        int n = 0;
        for (int i = 0; inv != null && i < inv.size(); i++) if (!inv.getStack(i).isEmpty()) n++;
        return n;
    }

    private static Inventory tramo(TrinketComponent comp, int base, int largo) {
        TrinketInventory inv = comp.getInventory().get(ListaDeRopa.GRUPO).get(ListaDeRopa.SLOT);
        return new Inventory() {
            public int size() { return largo; }
            public boolean isEmpty() {
                for (int i = 0; i < largo; i++) if (!getStack(i).isEmpty()) return false;
                return true;
            }
            public ItemStack getStack(int i) { return inv.getStack(base + i); }
            public ItemStack removeStack(int i, int n) { return inv.removeStack(base + i, n); }
            public ItemStack removeStack(int i) { return inv.removeStack(base + i); }
            public void setStack(int i, ItemStack s) { inv.setStack(base + i, s); }
            public void markDirty() { inv.markDirty(); }
            public boolean canPlayerUse(PlayerEntity p) { return true; }
            public void clear() { for (int i = 0; i < largo; i++) setStack(i, ItemStack.EMPTY); }
        };
    }
}
