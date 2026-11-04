package com.modamod.pruebas;

import com.modamod.guardarropas.GuardarropasBlockEntity;
import com.modamod.ropa.ListaDeRopa;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/**
 * Lugares fijos de la lista de ropa para las pruebas (de afuera hacia adentro): delantal, calzado, chaqueta ×3,
 * remera ×3, calientabrazos ×3, pantalón ×3 y medias ×2. Cada categoría se ve como un inventario chico sobre su tramo.
 */
final class PruebaRopa {

    private PruebaRopa() {}

    static Inventory delantal(TrinketComponent comp) { return tramo(comp, 0, 1); }

    static Inventory calzado(TrinketComponent comp) { return tramo(comp, 1, 1); }

    /** Una categoría de {@link GuardarropasBlockEntity} (REMERA, PANTALON, MEDIAS, CALIENTABRAZOS, CHAQUETA). */
    static Inventory lugar(TrinketComponent comp, int categoria) {
        return switch (categoria) {
            case GuardarropasBlockEntity.CHAQUETA -> tramo(comp, 2, 3);
            case GuardarropasBlockEntity.REMERA -> tramo(comp, 5, 3);
            case GuardarropasBlockEntity.CALIENTABRAZOS -> tramo(comp, 8, 3);
            case GuardarropasBlockEntity.PANTALON -> tramo(comp, 11, 3);
            case GuardarropasBlockEntity.MEDIAS -> tramo(comp, 14, 2);
            default -> throw new IllegalArgumentException("categoria " + categoria);
        };
    }

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
