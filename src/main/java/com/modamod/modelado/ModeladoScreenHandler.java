package com.modamod.modelado;

import com.modamod.screen.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * Handler de la Mesa de Modelado (2026-10-12, "visor enfocado" + bolsas de moldes). Slots, en orden: el depósito de la
 * bolsa ({@link #SLOT_BOLSA}), la prenda de la Entrada, la Salida y el inventario del jugador. Los casilleros de corte
 * (pines) ya NO son slots: se eligen con el desplegable de la pantalla, que lee la bolsa del block entity.
 */
public class ModeladoScreenHandler extends ScreenHandler {

    // Índices dentro de la lista de Slots de ESTE handler (no los del block entity).
    private static final int SLOT_BOLSA = 0;
    private static final int SLOT_PRENDA = 1;
    private static final int SLOT_SALIDA = 2;
    private static final int INV_START = 3;

    // Layout de 560x408 (2026-10-12, "visor enfocado": A visor 8,24 304x256; D tira de zonas 8,284; C columna derecha
    // 318,24 234x278; cabecera a la derecha del inventario; inventario en 119,324 como el Telar).
    public static final int ANCHO = 560, ALTO = 408;
    public static final int X_COL = 318, ANCHO_COL = 234, Y_FILAS = 64, ALTO_FILA = 22;
    /** Casilleros de la zona: el ítem de 16x16 va a esta x, centrado en la fila de {@link #ALTO_FILA}. */
    public static final int X_SLOT = X_COL + 3;
    public static final int X_INV = 119, Y_INV = 324;
    /** Depósito de la bolsa (soltar moldes acá), abajo de la columna de la zona. */
    public static final int X_BOLSA = X_COL + 8, Y_BOLSA = 272;
    /** Cabecera: Entrada → Modelar → barra → Salida, a la derecha del inventario (x 287..552, y 312..400). */
    public static final int X_ENTRADA = 308, X_SALIDA = 520, Y_IO = 326;
    /** Entrada y Salida son protagonistas (2026-10-12, "bastante mas grandes"): marco de 40x40 con el ítem a 2,5x. */
    public static final float ESCALA_IO = 2.5f;

    public static boolean esSlotGrande(int x, int y) {
        return y == Y_IO && (x == X_ENTRADA || x == X_SALIDA);
    }

    /** Origen (absoluto en la ventana) del ítem del casillero {@code fila} de la columna de la zona. */
    public static int yFila(int fila) { return Y_FILAS + fila * ALTO_FILA + 3; }

    /**
     * Posición del ítem de cada pin por categoría y pin (orden de {@code ModeladoBlockEntity#ROLES}): todos los de una
     * misma fila de zona comparten sitio (solo está activa una zona a la vez); los pines NINGUNO quedan fuera de
     * pantalla. Público: ModeladoScreen lo usa.
     */
    public static final int[][][] PIN_POS = armarPosiciones();

    private static int[][][] armarPosiciones() {
        ModeladoBlockEntity.Categoria[] cats = ModeladoBlockEntity.Categoria.values();
        int[][][] r = new int[cats.length][ModeladoBlockEntity.PINES_POR_CATEGORIA][];
        for (int c = 0; c < cats.length; c++) {
            for (int i = 0; i < r[c].length; i++) {
                int fila = ZonasModelado.filaDe(cats[c], i);
                r[c][i] = fila < 0 ? new int[]{-2000, -2000} : new int[]{X_SLOT, yFila(fila)};
            }
        }
        return r;
    }

    /** Zona activa (índice dentro de {@link ZonasModelado#de}); solo del cliente. */
    public int zonaActiva = 0;

    public final ModeladoBlockEntity be;

    /**
     * Factory del lado del CLIENTE: busca el block entity REAL en la posición que mandó el servidor, en vez de crear
     * uno de mentira — así la bolsa y las fijadas (que viajan por NBT del block entity) llegan actualizadas.
     */
    public static ModeladoScreenHandler deCliente(int syncId, PlayerInventory inv, BlockPos pos) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        ModeladoBlockEntity be = client.world != null && client.world.getBlockEntity(pos) instanceof ModeladoBlockEntity m
                ? m
                : new ModeladoBlockEntity(pos, ModeladoMod.MODELADO_BLOCK.getDefaultState());
        return new ModeladoScreenHandler(syncId, inv, be);
    }

    public ModeladoScreenHandler(int syncId, PlayerInventory playerInventory, ModeladoBlockEntity be) {
        super(ModamodScreenHandlers.MODELADO, syncId);
        this.be = be;

        addSlot(new SlotBolsa(be, X_BOLSA, Y_BOLSA));

        addSlot(new Slot(be, ModeladoBlockEntity.PRENDA, X_ENTRADA, Y_IO) {
            @Override
            public boolean canInsert(ItemStack stack) { return ModeladoBlockEntity.esPrendaModelable(stack); }

            // Una prenda por vez (2026-10-08, hallazgo H02 ampliado): una pila entera se cortaría junta con un solo uso de tijera.
            @Override
            public int getMaxItemCount() { return 1; }
        });
        addSlot(new Slot(be, ModeladoBlockEntity.SALIDA, X_SALIDA, Y_IO) {
            @Override
            public boolean canInsert(ItemStack stack) { return false; }
        });

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlot(new Slot(playerInventory, j + i * 9 + 9, X_INV + j * 18, Y_INV + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(playerInventory, i, X_INV + i * 18, Y_INV + 58));
        }
        // El diseño en blanco para exportar (2026-10-25): último slot, así no corre los demás.
        addSlot(new com.modamod.util.DisenosMaquina.SlotBlanco(be.blancoDiseno, 9, 377));

        addProperties(be.getPropertyDelegate());
        if (be.getWorld() != null && !be.getWorld().isClient()) be.guiAbierta();
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        return be.onButtonClick(id);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return be.canPlayerUse(player) && !be.trabajando();
    }

    /**
     * Cerrar la pantalla ya no enciende la máquina (2026-10-15, "vamos a desactivar la activacion de las maquinas por
     * cerrado de la gui"): arranca con el botón Modelar o al cargar la prenda por arriba con algo fijado.
     */
    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!player.getWorld().isClient) be.guiCerrada();
    }

    /**
     * Shift-click desde el inventario: un molde va a la bolsa, las tijeras al casillero invisible y una prenda a la
     * Entrada; desde la Entrada o la Salida, al inventario. (El depósito de la bolsa nunca retiene nada.)
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        ItemStack result = ItemStack.EMPTY;
        Slot clickedSlot = this.slots.get(slot);
        if (clickedSlot != null && clickedSlot.hasStack()) {
            ItemStack stack = clickedSlot.getStack();
            result = stack.copy();
            if (slot < INV_START) {
                if (!this.insertItem(stack, INV_START, this.slots.size() - 1, true)) return ItemStack.EMPTY;
        } else if (clickedSlot instanceof com.modamod.util.DisenosMaquina.SlotBlanco) {
            if (!this.insertItem(stack, INV_START, this.slots.size() - 1, true)) return ItemStack.EMPTY;
        } else if (com.modamod.util.DisenosMaquina.esBlanco(stack)) {
            if (!this.insertItem(stack, this.slots.size() - 1, this.slots.size(), false)) return ItemStack.EMPTY;
            } else if (stack.isOf(net.minecraft.item.Items.SHEARS)) {
                // Las tijeras al casillero invisible (2026-10-05).
                if (!be.cargarTijera(stack)) return ItemStack.EMPTY;
            } else if (ModeladoBlockEntity.esDiseno(stack)) {
                // Shift-click: el diseño se copia a la lista y se queda en el inventario (2026-10-25, "cuando uses una plantilla con diseño en la maquina lo copie sin gastarse").
                if (!player.getWorld().isClient) be.absorberDiseno(stack);
                return ItemStack.EMPTY;
            } else if (ModeladoBlockEntity.esMoldeDeModelado(stack) || ModeladoBlockEntity.esDiseno(stack)) {
                if (!this.insertItem(stack, SLOT_BOLSA, SLOT_BOLSA + 1, false)) return ItemStack.EMPTY;
            } else if (ModeladoBlockEntity.esPrendaModelable(stack)) {
                if (!this.insertItem(stack, SLOT_PRENDA, SLOT_PRENDA + 1, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                clickedSlot.setStack(ItemStack.EMPTY);
            } else {
                clickedSlot.markDirty();
            }
            if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
            clickedSlot.onTakeItem(player, stack);
        }
        return result;
    }

    /**
     * Depósito de la bolsa de moldes (2026-10-12): lo que se suelta acá se suma a la bolsa del block entity (solo en el
     * servidor; el cliente espera el sync) y el casillero queda siempre vacío. Solo acepta moldes de la Modeladora.
     */
    private static class SlotBolsa extends Slot {
        private final ModeladoBlockEntity be;

        SlotBolsa(ModeladoBlockEntity be, int x, int y) {
            super(new SimpleInventory(1), 0, x, y);
            this.be = be;
        }

        /** Un diseño se copia a la lista y vuelve al cursor, sin gastarse (2026-10-25, "cuando uses una plantilla con diseño en la maquina lo copie sin gastarse"). */
        @Override
        public ItemStack insertStack(ItemStack stack, int count) {
            if (!(ModeladoBlockEntity.esDiseno(stack))) return super.insertStack(stack, count);
            if (canInsert(stack) && be.getWorld() != null && !be.getWorld().isClient) be.absorberDiseno(stack);
            return stack;
        }

        @Override public boolean canInsert(ItemStack stack) {
            if (be.encendida()) return false;
            if (ModeladoBlockEntity.esDiseno(stack)) return be.disenosItem().size() < ModeladoBlockEntity.DISENOS_ITEM_MAXIMO;
            return ModeladoBlockEntity.esMoldeDeModelado(stack);
        }
        @Override public ItemStack getStack() { return ItemStack.EMPTY; }
        @Override public boolean hasStack() { return false; }
        @Override public boolean canTakeItems(PlayerEntity player) { return false; }
        @Override public void setStackNoCallbacks(ItemStack stack) { }

        @Override
        public void setStack(ItemStack stack) {
            if (stack.isEmpty()) return;
            var mundo = be.getWorld();
            if (mundo == null || mundo.isClient) return;
            if (ModeladoBlockEntity.esDiseno(stack)) be.absorberDiseno(stack); else be.absorber(stack);
        }

        @Override public int getMaxItemCount() { return 64; }
    }
}
