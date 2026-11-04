package com.modamod.maniqui;

import com.modamod.ropa.GrillaDeRopa;
import com.modamod.ropa.ListaDeRopa;
import com.modamod.screen.ModamodScreenHandlers;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.function.BooleanSupplier;

/**
 * Maniquí: la grilla de Ropa ({@link GrillaDeRopa}, la misma que la pantalla de Ropa y el Guardarropas) con la armadura
 * del maniquí en la fila de la armadura, las dos manos al lado y el inventario del jugador.
 */
public class ManiquiScreenHandler extends ScreenHandler {

    /** Ventana de 560x408 como las otras maquinas (2026-10-19, "hacela mas grande como las otras maquinas"). */
    public static final int ANCHO = 560, ALTO = 408;
    /** La grilla, en la columna del medio; las manos a su derecha. */
    public static final int X_GRILLA = 200, Y_GRILLA = 30, X_MANOS = 310;
    public static final int X_INV = 172, Y_INV = 324, Y_HOTBAR = 382;

    public final ManiquiBlockEntity be;
    private final PlayerEntity jugador;
    /** Estado del candado para ESTE jugador, sincronizado al cliente (bits de {@code Candado#estado}). */
    private int estado;

    /** Factory del lado del CLIENTE: busca el block entity REAL en la posición que mandó el servidor. */
    public static ManiquiScreenHandler deCliente(int syncId, PlayerInventory inv, BlockPos pos) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        ManiquiBlockEntity be = client.world != null && client.world.getBlockEntity(pos) instanceof ManiquiBlockEntity m
                ? m
                : new ManiquiBlockEntity(pos, ManiquiMod.MANIQUI_BLOCK.getDefaultState());
        return new ManiquiScreenHandler(syncId, inv, be);
    }

    public ManiquiScreenHandler(int syncId, PlayerInventory playerInventory, ManiquiBlockEntity be) {
        super(ModamodScreenHandlers.MANIQUI, syncId);
        this.be = be;
        this.jugador = playerInventory.player;
        addProperty(new net.minecraft.screen.Property() {
            @Override
            public int get() { return jugador.getWorld().isClient ? estado : be.candado().estado(jugador); }

            @Override
            public void set(int valor) { estado = valor; }
        });

        // Los slots del maniquí van en orden de índice (lugares, armadura, manos) antes del inventario: el shift-click los usa así.
        GrillaDeRopa.armar(X_GRILLA, Y_GRILLA, be::fila,
                (i, x, y, visible) -> new DeLugar(i, x, y, visible),
                (z, x, y, visible) -> slotPropio(ManiquiBlockEntity.ARMADURA_INICIO + z, x, y, visible, GrillaDeRopa.FONDO_ARMADURA[z]),
                this::addSlot);
        // Las manos (2026-11-03): la derecha y la izquierda (el escudo).
        addSlot(slotPropio(ManiquiBlockEntity.SLOT_MANO_DER, X_MANOS, Y_GRILLA, () -> true, null));
        addSlot(slotPropio(ManiquiBlockEntity.SLOT_MANO_IZQ, X_MANOS, Y_GRILLA + GrillaDeRopa.PASO_FILA, () -> true,
                PlayerScreenHandler.EMPTY_OFFHAND_ARMOR_SLOT));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlot(new Slot(playerInventory, j + i * 9 + 9, X_INV + j * 18, Y_INV + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(playerInventory, i, X_INV + i * 18, Y_HOTBAR));
        }
    }

    /** Un lugar de la lista: con el mismo hover que los de la pantalla de Ropa ({@link com.modamod.ropa.SlotDeRopa}). */
    private final class DeLugar extends Slot implements com.modamod.ropa.SlotDeRopa {
        private final BooleanSupplier visible;

        DeLugar(int index, int x, int y, BooleanSupplier visible) {
            super(be, index, x, y);
            this.visible = visible;
        }

        @Override public String grupoDeRopa() { return ListaDeRopa.GRUPO; }
        @Override public String slotDeRopa() { return ListaDeRopa.SLOT; }
        @Override public int lugaresDeRopa() { return ListaDeRopa.LUGARES; }
        @Override public boolean isEnabled() { return visible.getAsBoolean(); }
        @Override public boolean canInsert(ItemStack stack) { return puedeTocar() && be.isValid(getIndex(), stack); }
        @Override public boolean canTakeItems(PlayerEntity player) { return puedeTocar(); }
        @Override public int getMaxItemCount() { return 1; }
    }

    /** Un slot del maniquí (armadura o mano) que respeta el candado y {@link ManiquiBlockEntity#isValid}, con la silueta de vanilla vacío. */
    private Slot slotPropio(int index, int x, int y, BooleanSupplier visible, @org.jetbrains.annotations.Nullable Identifier fondo) {
        return new Slot(be, index, x, y) {
            @Override
            public boolean isEnabled() { return visible.getAsBoolean(); }

            @Override
            public boolean canInsert(ItemStack stack) { return puedeTocar() && be.isValid(index, stack); }

            @Override
            public boolean canTakeItems(PlayerEntity player) { return puedeTocar(); }

            @Override
            public int getMaxItemCount() { return 1; }

            @Override
            public Pair<Identifier, Identifier> getBackgroundSprite() {
                return fondo == null ? null : Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, fondo);
            }
        };
    }

    /** Cliente: viene de la propiedad sincronizada; servidor: se calcula en el momento. */
    public int estado() { return jugador.getWorld().isClient ? estado : be.candado().estado(jugador); }

    public boolean puedeTocar() { return com.modamod.util.Candado.puedeTocar(estado()); }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        return be.onButtonClick(player, id);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return be.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        Slot clickedSlot = this.slots.get(slot);
        if (clickedSlot == null || !clickedSlot.hasStack()) return ItemStack.EMPTY;
        if (!puedeTocar()) return ItemStack.EMPTY;
        ItemStack stack = clickedSlot.getStack();
        ItemStack result = stack.copy();
        int inventario = slots.size() - 36;
        if (slot < inventario) {
            if (!this.insertItem(stack, inventario, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.insertItem(stack, 0, inventario, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) clickedSlot.setStack(ItemStack.EMPTY);
        else clickedSlot.markDirty();
        return result;
    }
}
