package com.modamod.estilista;

import com.modamod.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * La parte del editor de la pantalla de la Estilista (la que era la Mesa de estilado): molde, retazo y material a la derecha
 * de la vista 3D, la biblioteca de moldes escondida y el inventario abajo. {@code EstilistaScreenHandler} le suma la máquina.
 */
public abstract class EditorEstiloScreenHandler extends ScreenHandler {

    // Ventana de 560x408 (2026-10-12, plan de GUIs): la columna de controles es la derecha (x 318) y el inventario va
    // abajo, a la derecha de los diseños (119,324), como en las otras máquinas.
    public static final int X_DERECHA = 318;
    public static final int Y_SLOTS = 30;
    public static final int X_INVENTARIO = 119, Y_INVENTARIO = 324;
    private static final int INV_START = EditorEstilo.TAMANO;

    public final EditorEstilo be;

    protected EditorEstiloScreenHandler(net.minecraft.screen.ScreenHandlerType<?> tipo, int syncId, PlayerInventory playerInventory,
                                    EditorEstilo be) {
        super(tipo, syncId);
        this.be = be;
        // Slot anónimo con canInsert → isValid (trampa conocida, CLAUDE.md).
        for (int i = 0; i < EditorEstilo.SLOT_BIBLIOTECA; i++) {
            int indice = i;
            addSlot(new Slot(be, i, X_DERECHA + i * 26, Y_SLOTS) {
                @Override
                public boolean canInsert(ItemStack stack) { return be.isValid(indice, stack); }

                /** El casillero de objeto es solo de la Estilista creativa (2026-10-04). */
                @Override
                public boolean isEnabled() {
                    return indice != EditorEstilo.SLOT_OBJETO || be.creativa();
                }
            });
        }
        // La biblioteca de moldes: slots escondidos, solo para que el handler los sincronice al cliente (se leen para
        // mostrar la lista; nadie los toca ni les mete nada).
        for (int i = 0; i < EditorEstilo.BIBLIOTECA; i++) {
            addSlot(new Slot(be, EditorEstilo.SLOT_BIBLIOTECA + i, -10000, -10000) {
                @Override public boolean canInsert(ItemStack stack) { return false; }
                @Override public boolean canTakeItems(PlayerEntity player) { return false; }
                @Override public boolean isEnabled() { return false; }
            });
        }
        // El material del acabado (2026-10-06): al lado de los otros casilleros.
        addSlot(new Slot(be, EditorEstilo.SLOT_MATERIAL, X_DERECHA + 4 * 26, Y_SLOTS) {
            @Override
            public boolean canInsert(ItemStack stack) { return be.isValid(EditorEstilo.SLOT_MATERIAL, stack); }
        });
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlot(new Slot(playerInventory, j + i * 9 + 9, X_INVENTARIO + j * 18, Y_INVENTARIO + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) addSlot(new Slot(playerInventory, i, X_INVENTARIO + i * 18, Y_INVENTARIO + 58));
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == EditorEstilo.BTN_QUITAR) {
            be.quitar(player);
            return true;
        }
        // La biblioteca de moldes: dar una copia o borrar (necesitan al jugador).
        if (id >= EditorEstilo.BTN_BIBLIO_DAR_BASE && id < EditorEstilo.BTN_BIBLIO_DAR_BASE + EditorEstilo.BIBLIOTECA) {
            ItemStack copia = be.copiaDeBiblioteca(id - EditorEstilo.BTN_BIBLIO_DAR_BASE);
            if (copia.isEmpty()) return false;
            player.getInventory().offerOrDrop(copia);
            return true;
        }
        if (id >= EditorEstilo.BTN_BIBLIO_BORRAR_BASE && id < EditorEstilo.BTN_BIBLIO_BORRAR_BASE + EditorEstilo.BIBLIOTECA) {
            return be.borrarDeBiblioteca(id - EditorEstilo.BTN_BIBLIO_BORRAR_BASE);
        }
        return be.onButtonClick(id);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return be.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        Slot clicked = this.slots.get(slot);
        if (clicked == null || !clicked.hasStack() || !clicked.canTakeItems(player)) return ItemStack.EMPTY;
        ItemStack stack = clicked.getStack();
        ItemStack result = stack.copy();
        if (slot < INV_START) {
            if (!this.insertItem(stack, INV_START, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.insertItem(stack, 0, INV_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) clicked.setStack(ItemStack.EMPTY);
        else clicked.markDirty();
        return result;
    }
}
