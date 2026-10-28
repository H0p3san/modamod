package com.modamod.ropa;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/** Los 4 slots cosméticos como un {@link Inventory}: lee y escribe el dato adjunto del jugador. */
public class CosmeticosInventario implements Inventory {

    private final PlayerEntity jugador;

    public CosmeticosInventario(PlayerEntity jugador) {
        this.jugador = jugador;
    }

    @Override public int size() { return Cosmeticos.ZONAS.length; }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < size(); i++) if (!getStack(i).isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getStack(int slot) { return Cosmeticos.de(jugador).pieza(slot); }

    @Override
    public ItemStack removeStack(int slot, int cantidad) { return removeStack(slot); }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack r = getStack(slot);
        if (!r.isEmpty()) Cosmeticos.guardar(jugador, Cosmeticos.de(jugador).conPieza(slot, ItemStack.EMPTY));
        return r.copy();
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        Cosmeticos.guardar(jugador, Cosmeticos.de(jugador).conPieza(slot, stack));
    }

    @Override public int getMaxCountPerStack() { return 1; }

    @Override public void markDirty() {}

    @Override public boolean canPlayerUse(PlayerEntity player) { return true; }

    @Override
    public void clear() {
        Cosmeticos.guardar(jugador, new Cosmeticos(Cosmeticos.VACIO.piezas(), Cosmeticos.de(jugador).ocultar(), Cosmeticos.de(jugador).ver()));
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        // La pieza que corresponde: armadura/ropa de ese slot, élitros en el pecho, calabazas y cabezas en el casco.
        return stack.isEmpty() || jugador.getPreferredEquipmentSlot(stack) == Cosmeticos.ZONAS[slot];
    }
}
