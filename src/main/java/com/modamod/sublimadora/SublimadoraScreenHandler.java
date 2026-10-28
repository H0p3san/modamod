package com.modamod.sublimadora;

import com.modamod.screen.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * Pantalla de la Sublimadora en la ventana de 560×408 del plan de GUIs (2026-10-13, "vamos con la siguiente maquina"):
 * mismo esqueleto que la Estilista. Los slots de foto, el almacén de 27 y el de la máscara ya no se muestran: las fotos,
 * las máscaras y los diseños viven en bolsas de referencia (ver {@link SublimadoraBlockEntity#absorber}) y elegir una
 * pone una copia en el casillero interno de la cara. Solo quedan la Entrada, la Salida y el depósito de las bolsas.
 */
public class SublimadoraScreenHandler extends ScreenHandler {

    /** Inventario del jugador (x) — igual que las otras máquinas. */
    public static final int M_MEDIO = 119;
    /** Cabecera: Entrada y Salida grandes a la derecha del inventario, igual que la Estilista. */
    public static final int X_ENTRADA = 308, X_SALIDA = 520, Y_IO = 326;
    public static final float ESCALA_IO = 2.5f;
    /** Panel de diseños a la izquierda del inventario. */
    public static final int X_DISENOS = 8, Y_DISENOS = 312, ANCHO_DISENOS = 105;
    /** El depósito de las bolsas (pestañas Bolsas y Diseños): 1 px adentro del marco de la disposición. */
    public static final int X_DEPOSITO = com.modamod.client.DisposicionSublimadora.X + 1,
            Y_DEPOSITO = com.modamod.client.DisposicionSublimadora.Y_DEPOSITO + 1;

    private static final int SLOT_ENTRADA = 0, SLOT_SALIDA = 1, SLOT_DEPOSITO = 2;
    private static final int INV_START = 3, INV_END = INV_START + 36;

    public final SublimadoraBlockEntity be;
    /** Las pestañas Bolsas y Diseños (solo del cliente): vuelven visible el depósito. */
    public boolean verDeposito = false;

    public static boolean esSlotGrande(int x, int y) {
        return y == Y_IO && (x == X_ENTRADA || x == X_SALIDA);
    }

    /** Factory del lado del CLIENTE: mismo motivo que TinturasScreenHandler#deCliente. */
    public static SublimadoraScreenHandler deCliente(int syncId, PlayerInventory inv, BlockPos pos) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        SublimadoraBlockEntity be = client.world != null && client.world.getBlockEntity(pos) instanceof SublimadoraBlockEntity s
                ? s
                : new SublimadoraBlockEntity(pos, ModBlocks.SUBLIMADORA.getDefaultState());
        return new SublimadoraScreenHandler(syncId, inv, be);
    }

    public SublimadoraScreenHandler(int syncId, PlayerInventory playerInventory, SublimadoraBlockEntity be) {
        super(ModamodScreenHandlers.SUBLIMADORA, syncId);
        this.be = be;
        if (be.getWorld() != null && !be.getWorld().isClient()) be.alAbrirGui();

        addSlot(new Slot(be, SublimadoraBlockEntity.SLOT_REMERA, X_ENTRADA, Y_IO) {
            @Override
            public boolean canInsert(ItemStack stack) { return be.isValid(SublimadoraBlockEntity.SLOT_REMERA, stack); }

            @Override
            public int getMaxItemCount() { return 1; }
        });
        addSlot(new Slot(be, SublimadoraBlockEntity.SLOT_SALIDA, X_SALIDA, Y_IO) {
            @Override
            public boolean canInsert(ItemStack stack) { return false; }
        });
        addSlot(new SlotDeposito(this, be, X_DEPOSITO, Y_DEPOSITO));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlot(new Slot(playerInventory, j + i * 9 + 9, M_MEDIO + j * 18, 324 + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(playerInventory, i, M_MEDIO + i * 18, 382));
        }
        // El diseño en blanco para exportar (2026-10-25): último slot, así no corre los demás.
        addSlot(new com.modamod.util.DisenosMaquina.SlotBlanco(be.blancoDiseno, 9, 345));
    }

    /**
     * Depósito de las bolsas (2026-10-13): lo que se suelta acá se suma a la bolsa que le corresponde (fotos, máscaras o
     * diseños); el casillero queda siempre vacío y solo se ve con las pestañas Bolsas y Diseños abiertas.
     */
    private static class SlotDeposito extends Slot {
        private final SublimadoraScreenHandler handler;
        private final SublimadoraBlockEntity be;

        SlotDeposito(SublimadoraScreenHandler handler, SublimadoraBlockEntity be, int x, int y) {
            super(new net.minecraft.inventory.SimpleInventory(1), 0, x, y);
            this.handler = handler;
            this.be = be;
        }

        /** Un diseño se copia a la lista y vuelve al cursor, sin gastarse (2026-10-25, "cuando uses una plantilla con diseño en la maquina lo copie sin gastarse"). */
        @Override
        public ItemStack insertStack(ItemStack stack, int count) {
            if (!(stack.getItem() instanceof DisenoSublimadoItem)) return super.insertStack(stack, count);
            if (canInsert(stack) && be.getWorld() != null && !be.getWorld().isClient) be.absorber(stack);
            return stack;
        }

        @Override public boolean isEnabled() { return handler.verDeposito; }
        @Override public boolean canInsert(ItemStack stack) { return !be.trabajando() && SublimadoraBlockEntity.esDeposito(stack); }
        @Override public ItemStack getStack() { return ItemStack.EMPTY; }
        @Override public boolean hasStack() { return false; }
        @Override public boolean canTakeItems(PlayerEntity player) { return false; }
        @Override public void setStackNoCallbacks(ItemStack stack) { }
        @Override public int getMaxItemCount() { return 64; }

        @Override
        public void setStack(ItemStack stack) {
            if (stack.isEmpty()) return;
            var mundo = be.getWorld();
            if (mundo != null && !mundo.isClient) be.absorber(stack);
        }
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == SublimadoraBlockEntity.BTN_PRENSAR) {
            // Solo el servidor arranca (gasta tinta y papel); el cliente se
            // entera por la sincronización. Mismo criterio que Teñir.
            if (player.getWorld().isClient) return true;
            net.minecraft.text.Text motivo = be.prensarDesdeGui();
            if (motivo != null) player.sendMessage(motivo.copy().formatted(net.minecraft.util.Formatting.GOLD), true);
            return motivo == null;
        }
        return be.onButtonClick(id);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!player.getWorld().isClient()) be.alCerrarGui();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return be.canPlayerUse(player) && !be.trabajando();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        Slot clickedSlot = this.slots.get(slot);
        if (clickedSlot == null || !clickedSlot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = clickedSlot.getStack();
        ItemStack result = stack.copy();
        if (slot < INV_START || slot >= INV_END) {
            if (!this.insertItem(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else if (com.modamod.util.DisenosMaquina.esBlanco(stack)) {
            if (!this.insertItem(stack, this.slots.size() - 1, this.slots.size(), false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof DisenoSublimadoItem) {
            // Shift-click: el diseño se copia a la lista y se queda en el inventario (2026-10-25, "cuando uses una plantilla con diseño en la maquina lo copie sin gastarse").
            if (!player.getWorld().isClient) be.absorber(stack);
            return ItemStack.EMPTY;
        } else if (SublimadoraBlockEntity.esDeposito(stack)) {
            // Fotos, máscaras y diseños van a su bolsa (el casillero del depósito las absorbe).
            if (!this.insertItem(stack, SLOT_DEPOSITO, SLOT_DEPOSITO + 1, false)) return ItemStack.EMPTY;
        } else if (ModItems.esEstampable(stack)) {
            if (!this.insertItem(stack, SLOT_ENTRADA, SLOT_ENTRADA + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) clickedSlot.setStack(ItemStack.EMPTY);
        else clickedSlot.markDirty();
        return result;
    }
}
