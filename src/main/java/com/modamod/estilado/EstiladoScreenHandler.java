package com.modamod.estilado;

import com.modamod.screen.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * Mesa de estilado (2026-10-01): prenda, molde y retazo a la derecha de la
 * vista 3D, inventario abajo a la derecha. Mismas coordenadas que
 * {@code tools/generar_textura_estilado.py}.
 */
public class EstiladoScreenHandler extends ScreenHandler {

    // Ventana de 560x408 (2026-10-12, plan de GUIs): la columna de controles es la derecha (x 318) y el inventario va
    // abajo, a la derecha de los diseños (119,324), como en las otras máquinas.
    public static final int X_DERECHA = 318;
    public static final int Y_SLOTS = 30;
    public static final int X_INVENTARIO = 119, Y_INVENTARIO = 324;
    private static final int INV_START = EstiladoBlockEntity.TAMANO;

    public final EstiladoBlockEntity be;

    public static EstiladoScreenHandler deCliente(int syncId, PlayerInventory inv, BlockPos pos) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        EstiladoBlockEntity be = client.world != null && client.world.getBlockEntity(pos) instanceof EstiladoBlockEntity e
                ? e : new EstiladoBlockEntity(pos, EstiladoMod.ESTILADO_BLOCK.getDefaultState());
        return new EstiladoScreenHandler(syncId, inv, be);
    }

    public EstiladoScreenHandler(int syncId, PlayerInventory playerInventory, EstiladoBlockEntity be) {
        this(ModamodScreenHandlers.ESTILADO, syncId, playerInventory, be);
    }

    /** Con otro tipo de pantalla: la Estilista reusa estos slots (2026-10-05). */
    protected EstiladoScreenHandler(net.minecraft.screen.ScreenHandlerType<?> tipo, int syncId, PlayerInventory playerInventory,
                                    EstiladoBlockEntity be) {
        super(tipo, syncId);
        this.be = be;
        // Slot anónimo con canInsert → isValid (trampa conocida, CLAUDE.md).
        for (int i = 0; i < EstiladoBlockEntity.SLOT_BIBLIOTECA; i++) {
            int indice = i;
            addSlot(new Slot(be, i, X_DERECHA + i * 26, Y_SLOTS) {
                @Override
                public boolean canInsert(ItemStack stack) { return be.isValid(indice, stack); }

                /** El slot de objeto es solo de la Mesa creativa (2026-10-04). */
                @Override
                public boolean isEnabled() {
                    return indice != EstiladoBlockEntity.SLOT_OBJETO || be.creativa();
                }
            });
        }
        // La biblioteca de moldes: slots escondidos, solo para que el handler los sincronice al cliente (se leen para
        // mostrar la lista; nadie los toca ni les mete nada).
        for (int i = 0; i < EstiladoBlockEntity.BIBLIOTECA; i++) {
            addSlot(new Slot(be, EstiladoBlockEntity.SLOT_BIBLIOTECA + i, -10000, -10000) {
                @Override public boolean canInsert(ItemStack stack) { return false; }
                @Override public boolean canTakeItems(PlayerEntity player) { return false; }
                @Override public boolean isEnabled() { return false; }
            });
        }
        // El material del acabado (2026-10-06): al lado de los otros casilleros.
        addSlot(new Slot(be, EstiladoBlockEntity.SLOT_MATERIAL, X_DERECHA + 4 * 26, Y_SLOTS) {
            @Override
            public boolean canInsert(ItemStack stack) { return be.isValid(EstiladoBlockEntity.SLOT_MATERIAL, stack); }
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
        if (id == EstiladoBlockEntity.BTN_QUITAR) {
            be.quitar(player);
            return true;
        }
        // La biblioteca de moldes de la Mesa creativa: dar una copia o borrar (necesitan al jugador).
        if (id >= EstiladoBlockEntity.BTN_BIBLIO_DAR_BASE && id < EstiladoBlockEntity.BTN_BIBLIO_DAR_BASE + EstiladoBlockEntity.BIBLIOTECA) {
            ItemStack copia = be.copiaDeBiblioteca(id - EstiladoBlockEntity.BTN_BIBLIO_DAR_BASE);
            if (copia.isEmpty()) return false;
            player.getInventory().offerOrDrop(copia);
            return true;
        }
        if (id >= EstiladoBlockEntity.BTN_BIBLIO_BORRAR_BASE && id < EstiladoBlockEntity.BTN_BIBLIO_BORRAR_BASE + EstiladoBlockEntity.BIBLIOTECA) {
            return be.borrarDeBiblioteca(id - EstiladoBlockEntity.BTN_BIBLIO_BORRAR_BASE);
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
