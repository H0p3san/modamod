package com.modamod.guardarropas;

import com.modamod.screen.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.BlockPos;

/**
 * Guardarropas: los slots de la pantalla de Ropa del jugador (los suyos) y la lista de outfits guardados del mueble, que llega
 * aparte ({@link OutfitsPayload}).
 */
public class GuardarropasScreenHandler extends com.modamod.ropa.RopaScreenHandler implements VisorColeccion {

    public final GuardarropasBlockEntity be;
    private final PlayerEntity jug;
    /** Estado del candado para ESTE jugador, sincronizado al cliente (bits de {@code Candado#estado}). */
    private int estado;
    /** Cliente: la lista de outfits que mandó el servidor ({@link OutfitsPayload}). */
    private OutfitsDatos datos = OutfitsDatos.VACIO;

    /** Factory del lado del CLIENTE: busca el block entity REAL en la posición que mandó el servidor. */
    public static GuardarropasScreenHandler deCliente(int syncId, PlayerInventory inv, BlockPos pos) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        GuardarropasBlockEntity be = client.world != null && client.world.getBlockEntity(pos) instanceof GuardarropasBlockEntity g
                ? g
                : new GuardarropasBlockEntity(pos, GuardarropasMod.GUARDARROPAS_BLOCK.getDefaultState());
        return new GuardarropasScreenHandler(syncId, inv, be);
    }

    /**
     * Los mismos slots que la pantalla de Ropa del jugador (2026-10-21, "tiene que tener los mismos slots que la pantalla de ropa del
     * jugador y tiene que poder guardar con nombre el outfit del jugador"): son los del propio jugador (Trinkets del mod, armadura real,
     * cosmética e inventario); el mueble guarda outfits enteros con nombre, no prendas sueltas.
     */
    public GuardarropasScreenHandler(int syncId, PlayerInventory playerInventory, GuardarropasBlockEntity be) {
        super(ModamodScreenHandlers.GUARDARROPAS, syncId, playerInventory, 0, 0);
        this.be = be;
        this.jug = playerInventory.player;
        addProperty(new net.minecraft.screen.Property() {
            @Override
            public int get() { return jug.getWorld().isClient ? estado : be.candado().estado(jug); }

            @Override
            public void set(int valor) { estado = valor; }
        });
        // Abre la puerta del modelo (2026-09-30) — mismo par onOpen/onClose que usan los cofres vanilla.
        be.onOpen(playerInventory.player);
    }

    @Override public java.util.UUID coleccion() { return be.coleccion(); }
    @Override public void setDatos(OutfitsDatos d) { datos = d; }

    /** Cliente: lo que mandó el servidor; servidor: la colección del mundo. */
    public java.util.List<GuardarropasBlockEntity.Outfit> outfits() {
        return jug.getWorld().isClient ? datos.outfits() : be.almacen().outfits();
    }

    @org.jetbrains.annotations.Nullable
    public String ultimoDe(java.util.UUID jugador) {
        return jug.getWorld().isClient ? datos.ultimos().get(jugador) : be.almacen().ultimos().get(jugador);
    }

    public int estado() { return jug.getWorld().isClient ? estado : be.candado().estado(jug); }

    public boolean puedeTocar() { return com.modamod.util.Candado.puedeTocar(estado()); }

    /** Servidor: guardar lo que lleva puesto el jugador con ese nombre (lo manda {@link GuardarOutfitPayload}). */
    public void guardar(String nombre) {
        if (!canUse(jug) || !be.candado().puedeTocar(jug)) return;
        Outfits.guardar(be.almacen(), jug, nombre);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == GuardarropasBlockEntity.BTN_CANDADO) return be.alternarCandado(player);
        // Los botones de la pantalla de Ropa: Ocultar y las flechas de la fila de la armadura.
        if (id < GuardarropasBlockEntity.BTN_PONER_BASE) return super.onButtonClick(player, id);
        if (!be.candado().puedeTocar(player)) return false;
        if (id >= GuardarropasBlockEntity.BTN_BORRAR_BASE && id < GuardarropasBlockEntity.BTN_BORRAR_BASE + Outfits.MAXIMO) {
            return Outfits.desarmar(be.almacen(), player, id - GuardarropasBlockEntity.BTN_BORRAR_BASE);
        }
        if (id >= GuardarropasBlockEntity.BTN_PONER_BASE && id < GuardarropasBlockEntity.BTN_PONER_BASE + Outfits.MAXIMO) {
            return Outfits.poner(be.almacen(), player, id - GuardarropasBlockEntity.BTN_PONER_BASE);
        }
        return false;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        be.onClose(player);
    }

    @Override
    public boolean canUse(PlayerEntity player) { return be.canPlayerUse(player); }
}
