package com.modamod.guardarropas;

import com.modamod.screen.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * Guardarropas: 16 slots de borrador (4 por categoría: remera/pantalón/
 * medias/calientabrazos) + inventario del jugador. Extended (con
 * BlockPos): la lista de outfits guardados no cabe en slots simples,
 * viaja por NBT del block entity — mismo motivo que
 * {@code TinturasScreenHandler}/{@code SublimadoraScreenHandler}.
 */
public class GuardarropasScreenHandler extends com.modamod.ropa.RopaScreenHandler implements VisorColeccion {

    private static final int PX = 100;
    public static final int M_MEDIO = 19 + PX;
    /** X de la columna de armadura: después de las 5 categorías, con un hueco de 6. */
    public static final int X_ARMADURA = M_MEDIO + GuardarropasBlockEntity.CATEGORIAS * 20 + 6;

    private static final net.minecraft.util.Identifier[] FONDO_ARMADURA = {
            net.minecraft.screen.PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE,
            net.minecraft.screen.PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
            net.minecraft.screen.PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE,
            net.minecraft.screen.PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE,
    };

    /**
     * Los 4 slots de la columna de armadura (2026-09-30), para el Guardarropas
     * y el Maniquí: canInsert delega en isValid (trampa conocida, CLAUDE.md),
     * uno por ítem, y el dibujito vanilla de la pieza cuando está vacío.
     */
    public static Slot slotArmadura(net.minecraft.inventory.Inventory inv, int i) {
        return slotArmadura(inv, i, () -> true);
    }

    /** Con candado (2026-10-04): {@code puedeTocar} dice si el jugador de este handler puede meter/sacar. */
    public static Slot slotArmadura(net.minecraft.inventory.Inventory inv, int i, java.util.function.BooleanSupplier puedeTocar) {
        int index = GuardarropasBlockEntity.ARMADURA_INICIO + i;
        return new Slot(inv, index, X_ARMADURA, 20 + i * 20) {
            @Override
            public boolean canInsert(ItemStack stack) { return puedeTocar.getAsBoolean() && inv.isValid(index, stack); }

            @Override
            public boolean canTakeItems(PlayerEntity player) { return puedeTocar.getAsBoolean(); }

            @Override
            public int getMaxItemCount() { return 1; }

            @Override
            public com.mojang.datafixers.util.Pair<net.minecraft.util.Identifier, net.minecraft.util.Identifier> getBackgroundSprite() {
                return com.mojang.datafixers.util.Pair.of(
                        net.minecraft.screen.PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, FONDO_ARMADURA[i]);
            }
        };
    }

    /** El slot del sombrero (2026-10-05), debajo de la columna de armadura; el marco lo dibuja la pantalla por código. */
    public static final int Y_SOMBRERO = 20 + GuardarropasBlockEntity.SLOTS_ARMADURA.length * 20;

    public static Slot slotSombrero(net.minecraft.inventory.Inventory inv, java.util.function.BooleanSupplier puedeTocar) {
        int index = GuardarropasBlockEntity.SLOT_SOMBRERO;
        return new Slot(inv, index, X_ARMADURA, Y_SOMBRERO) {
            @Override
            public boolean canInsert(ItemStack stack) { return puedeTocar.getAsBoolean() && inv.isValid(index, stack); }

            @Override
            public boolean canTakeItems(PlayerEntity player) { return puedeTocar.getAsBoolean(); }

            @Override
            public int getMaxItemCount() { return 1; }
        };
    }

    /** El slot del calzado (2026-10-08), debajo del del sombrero; el marco lo dibuja la pantalla por código. */
    public static final int Y_CALZADO = Y_SOMBRERO + 20;

    public static Slot slotCalzado(net.minecraft.inventory.Inventory inv, java.util.function.BooleanSupplier puedeTocar) {
        int index = GuardarropasBlockEntity.SLOT_CALZADO;
        return new Slot(inv, index, X_ARMADURA, Y_CALZADO) {
            @Override
            public boolean canInsert(ItemStack stack) { return puedeTocar.getAsBoolean() && inv.isValid(index, stack); }

            @Override
            public boolean canTakeItems(PlayerEntity player) { return puedeTocar.getAsBoolean(); }

            @Override
            public int getMaxItemCount() { return 1; }
        };
    }

    /** El slot del delantal (2026-10-11), debajo del del calzado; el marco lo dibuja la pantalla por código. */
    public static final int Y_DELANTAL = Y_CALZADO + 20;

    public static Slot slotDelantal(net.minecraft.inventory.Inventory inv, java.util.function.BooleanSupplier puedeTocar) {
        int index = GuardarropasBlockEntity.SLOT_DELANTAL;
        return new Slot(inv, index, X_ARMADURA, Y_DELANTAL) {
            @Override
            public boolean canInsert(ItemStack stack) { return puedeTocar.getAsBoolean() && inv.isValid(index, stack); }

            @Override
            public boolean canTakeItems(PlayerEntity player) { return puedeTocar.getAsBoolean(); }

            @Override
            public int getMaxItemCount() { return 1; }
        };
    }

    /** Los slots de Ropa del jugador van pegados a la izquierda de la pantalla; la lista de outfits, a la derecha. */
    public static final int ANCHO_ROPA = com.modamod.ropa.RopaScreenHandler.ANCHO;

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
        // 0..7: los interruptores de la armadura cosmética de la pantalla de Ropa.
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
