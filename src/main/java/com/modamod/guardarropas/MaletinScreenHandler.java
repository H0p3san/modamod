package com.modamod.guardarropas;

import com.modamod.screen.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;

import java.util.List;
import java.util.UUID;

/**
 * La lista del Maletín (2026-10-21): sin slots, sin vista previa y sin guardar: solo los outfits de la colección vinculada para
 * ponértelos. El botón {@code BTN_PONER_BASE + i} te pone el outfit i.
 */
public class MaletinScreenHandler extends ScreenHandler implements VisorColeccion {

    /** De dónde sale el maletín: la mano, o el que está apoyado en el mundo (2026-10-23, "colocable en el mundo"). */
    private final java.util.function.Supplier<ItemStack> fuente;
    private OutfitsDatos datos = OutfitsDatos.VACIO;

    public MaletinScreenHandler(int syncId, PlayerInventory inv, java.util.function.Supplier<ItemStack> fuente) {
        super(ModamodScreenHandlers.MALETIN, syncId);
        this.fuente = fuente;
    }

    /** Del lado del cliente: 0/1 = la mano; otro valor = un maletín apoyado (el cliente no lo necesita, solo muestra la lista). */
    public static MaletinScreenHandler delCliente(int syncId, PlayerInventory inv, int dato) {
        Hand[] manos = Hand.values();
        return new MaletinScreenHandler(syncId, inv, dato >= 0 && dato < manos.length ? () -> inv.player.getStackInHand(manos[dato]) : () -> ItemStack.EMPTY);
    }

    private ItemStack maletin() { return fuente.get(); }

    @Override public UUID coleccion() { return MaletinItem.coleccionDe(maletin()); }
    @Override public void setDatos(OutfitsDatos d) { datos = d; }

    /** Cliente: lo que mandó el servidor. */
    public List<GuardarropasBlockEntity.Outfit> outfits() { return datos.outfits(); }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        UUID col = coleccion();
        if (col == null || player.getServer() == null) return false;
        if (id < GuardarropasBlockEntity.BTN_PONER_BASE || id >= GuardarropasBlockEntity.BTN_PONER_BASE + Outfits.MAXIMO) return false;
        return MaletinItem.ponerConEfecto(player, Colecciones.almacen(player.getServer(), col), id - GuardarropasBlockEntity.BTN_PONER_BASE);
    }

    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

    @Override public boolean canUse(PlayerEntity player) { return maletin().getItem() instanceof MaletinItem; }
}
