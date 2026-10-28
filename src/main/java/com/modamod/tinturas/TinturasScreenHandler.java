package com.modamod.tinturas;

import com.modamod.screen.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * Handler de la Estación de Tintes (2026-10-12, plan de GUIs: "visor enfocado" como la Modeladora). Slots, en orden: el
 * depósito de la bolsa de patrones ({@link #SLOT_BOLSA}, solo activo en la pestaña Bolsa), la prenda de la Entrada, la
 * Salida y el inventario. Los cuadraditos ya NO son slots: se les elige el patrón desde la bolsa (ver
 * {@link TinturasBlockEntity#elegirPatron}). La mezcla CMYK, el modo, el orden y lo demás siguen siendo botones.
 */
public class TinturasScreenHandler extends ScreenHandler {

    private static final int SLOT_BOLSA = 0;
    private static final int SLOT_ENTRADA = 1;
    private static final int SLOT_SALIDA = 2;
    private static final int INV_START = 3;

    // Layout de 560x408, el mismo de la Modeladora: A visor 8,24 304x278; C columna derecha 318,24 234x278; cabecera a la
    // derecha del inventario; inventario en 119,324.
    public static final int ANCHO = 560, ALTO = 408;
    public static final int X_COL = 318, ANCHO_COL = 234, Y_FILAS = 64, ALTO_FILA = 22;
    public static final int X_INV = 119, Y_INV = 324;
    public static final int X_ENTRADA = 308, X_SALIDA = 520, Y_IO = 326;
    public static final float ESCALA_IO = 2.5f;
    /** Depósito de la bolsa: abajo de la grilla de la pestaña Bolsa. */
    public static final int X_BOLSA = X_COL + 8, Y_BOLSA = 262;

    /** Pestañas de los controles del cuadradito (solo del cliente). */
    /** Mezcla (modo, opacidad, orden) y el panel de capas comparten la pestaña CAPAS (2026-10-12, "pueden ir en la misma pestaña"). */
    public static final int PESTANA_COLOR = 0, PESTANA_PATRON = 1, PESTANA_CAPAS = 2, PESTANA_BOLSA = 3, PESTANA_DISENOS = 4;

    /** Zona activa y pestaña (solo del cliente: el servidor no usa {@code isEnabled}). */
    public int zonaActiva = 0;
    public int pestana = PESTANA_COLOR;

    public static boolean esSlotGrande(int x, int y) {
        return y == Y_IO && (x == X_ENTRADA || x == X_SALIDA);
    }

    public final TinturasBlockEntity be;

    /**
     * Factory del lado del CLIENTE: busca el block entity REAL en la posición que mandó el servidor (las fijadas y la
     * bolsa viajan por NBT del block entity, no caben en un {@code PropertyDelegate}).
     */
    public static TinturasScreenHandler deCliente(int syncId, PlayerInventory inv, BlockPos pos) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        TinturasBlockEntity be = client.world != null && client.world.getBlockEntity(pos) instanceof TinturasBlockEntity t
                ? t
                : new TinturasBlockEntity(pos, TinturasMod.TINTURAS_BLOCK.getDefaultState());
        return new TinturasScreenHandler(syncId, inv, be);
    }

    public TinturasScreenHandler(int syncId, PlayerInventory playerInventory, TinturasBlockEntity be) {
        super(ModamodScreenHandlers.TINTURAS, syncId);
        this.be = be;

        addSlot(new SlotBolsa(this, be, X_BOLSA, Y_BOLSA));

        addSlot(new Slot(be, TinturasBlockEntity.SLOT_PRENDA_ENTRADA, X_ENTRADA, Y_IO) {
            @Override
            public boolean canInsert(ItemStack stack) { return be.isValid(TinturasBlockEntity.SLOT_PRENDA_ENTRADA, stack); }

            /** De a uno: el retazo de aplique se apila a 64 y el resto se perdería (2026-10-04). */
            @Override
            public int getMaxItemCount() { return 1; }

            // Desde la GUI la prenda solo se CARGA — arranca con el botón Teñir o al cerrar (2026-09-28, "sigue empezando
            // a funcionar apenas pongo la prenda"). El hopper/click derecho siguen arrancando solos (pasan por be.setStack).
            @Override
            public void setStackNoCallbacks(ItemStack stack) {
                be.cargarEntradaSinArrancar(stack);
                this.markDirty();
            }
        });
        addSlot(new Slot(be, TinturasBlockEntity.SLOT_SALIDA, X_SALIDA, Y_IO) {
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
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == TinturasBlockEntity.BTN_TENIR) {
            // Solo el servidor arranca de verdad (gasta tinta); el cliente se entera por la sincronización del block entity.
            if (player.getWorld().isClient) return true;
            net.minecraft.text.Text motivo = be.reintentarTenido();
            if (motivo != null) player.sendMessage(motivo, true);
            return motivo == null;
        }
        if (id == TinturasBlockEntity.BTN_ENVASAR || id == TinturasBlockEntity.BTN_USAR_MUESTRA) {
            if (player.getWorld().isClient) return true;
            net.minecraft.text.Text motivo = id == TinturasBlockEntity.BTN_ENVASAR ? envasar(player) : usarMuestra(player);
            if (motivo != null) player.sendMessage(motivo, true);
            return motivo == null;
        }
        return be.onButtonClick(id);
    }

    /**
     * Envasar (2026-09-30, "que la estacion de tintes genere un mezcla de color por si la gente se quiere pasar colores"):
     * gasta un frasco de vidrio del inventario y da una muestra con el color que se está editando. No gasta tinta.
     */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.text.Text envasar(PlayerEntity player) {
        var inv = player.getInventory();
        int frasco = -1;
        for (int i = 0; i < inv.size() && frasco < 0; i++) {
            if (inv.getStack(i).isOf(net.minecraft.item.Items.GLASS_BOTTLE)) frasco = i;
        }
        boolean gratis = player.isCreative() || com.modamod.util.MaquinaCreativa.es(be);
        if (frasco < 0 && !gratis) {
            return net.minecraft.text.Text.translatable("modamod.muestra.sin_frasco");
        }
        if (frasco >= 0 && !gratis) inv.getStack(frasco).decrement(1);
        ItemStack muestra = com.modamod.item.MuestraColorItem.con(
                new ItemStack(com.modamod.item.ModamodItems.TINTE_MEZCLA), be.mezclaEnEdicion());
        inv.offerOrDrop(muestra);
        return null;
    }

    /** Usar muestra: la del cursor, o si no la primera del inventario; no se gasta. */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.text.Text usarMuestra(PlayerEntity player) {
        int[] mezcla = com.modamod.item.MuestraColorItem.mezcla(getCursorStack());
        var inv = player.getInventory();
        for (int i = 0; i < inv.size() && mezcla == null; i++) {
            if (inv.getStack(i).isOf(com.modamod.item.ModamodItems.TINTE_MEZCLA)) {
                mezcla = com.modamod.item.MuestraColorItem.mezcla(inv.getStack(i));
            }
        }
        if (mezcla == null) return net.minecraft.text.Text.translatable("modamod.muestra.sin_muestra");
        be.ponerMezclaEnEdicion(mezcla);
        return null;
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
        if (slot < INV_START) {
            if (!this.insertItem(stack, INV_START, this.slots.size() - 1, true)) return ItemStack.EMPTY;
        } else if (clickedSlot instanceof com.modamod.util.DisenosMaquina.SlotBlanco) {
            if (!this.insertItem(stack, INV_START, this.slots.size() - 1, true)) return ItemStack.EMPTY;
        } else if (com.modamod.util.DisenosMaquina.esBlanco(stack)) {
            if (!this.insertItem(stack, this.slots.size() - 1, this.slots.size(), false)) return ItemStack.EMPTY;
        } else if (TinturasBlockEntity.esDiseno(stack)) {
            // Shift-click: el diseño se copia a la lista y se queda en el inventario (2026-10-25, "cuando uses una plantilla con diseño en la maquina lo copie sin gastarse").
            if (!player.getWorld().isClient) be.absorberDiseno(stack);
            return ItemStack.EMPTY;
        } else if (TinturasBlockEntity.esPatron(stack) || TinturasBlockEntity.esDiseno(stack)) {
            // Un patrón va a la bolsa.
            if (!this.insertItem(stack, SLOT_BOLSA, SLOT_BOLSA + 1, false)) return ItemStack.EMPTY;
        } else if (TinturasBlockEntity.aceptaEntrada(stack)) {
            if (!this.insertItem(stack, SLOT_ENTRADA, SLOT_ENTRADA + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) clickedSlot.setStack(ItemStack.EMPTY);
        else clickedSlot.markDirty();
        return result;
    }

    /**
     * Depósito de la bolsa de patrones: lo que se suelta acá se suma a la bolsa del block entity (solo en el servidor) y
     * el casillero queda siempre vacío. Solo se ve y se puede usar con la pestaña Bolsa abierta (el shift-click del
     * servidor no mira {@code isEnabled}: el jugador puede mandar patrones a la bolsa desde cualquier pestaña).
     */
    private static class SlotBolsa extends Slot {
        private final TinturasScreenHandler handler;
        private final TinturasBlockEntity be;

        SlotBolsa(TinturasScreenHandler handler, TinturasBlockEntity be, int x, int y) {
            super(new SimpleInventory(1), 0, x, y);
            this.handler = handler;
            this.be = be;
        }

        /** Un diseño se copia a la lista y vuelve al cursor, sin gastarse (2026-10-25, "cuando uses una plantilla con diseño en la maquina lo copie sin gastarse"). */
        @Override
        public ItemStack insertStack(ItemStack stack, int count) {
            if (!(TinturasBlockEntity.esDiseno(stack))) return super.insertStack(stack, count);
            if (canInsert(stack) && be.getWorld() != null && !be.getWorld().isClient) be.absorberDiseno(stack);
            return stack;
        }

        @Override public boolean isEnabled() { return handler.pestana == PESTANA_BOLSA || handler.pestana == PESTANA_DISENOS; }
        @Override public boolean canInsert(ItemStack stack) {
            if (be.trabajando()) return false;
            if (TinturasBlockEntity.esDiseno(stack)) return be.disenosItem().size() < TinturasBlockEntity.DISENOS_ITEM_MAXIMO;
            return TinturasBlockEntity.esPatron(stack);
        }
        @Override public ItemStack getStack() { return ItemStack.EMPTY; }
        @Override public boolean hasStack() { return false; }
        @Override public boolean canTakeItems(PlayerEntity player) { return false; }
        @Override public void setStackNoCallbacks(ItemStack stack) { }
        @Override public int getMaxItemCount() { return 64; }

        @Override
        public void setStack(ItemStack stack) {
            if (stack.isEmpty()) return;
            var mundo = be.getWorld();
            if (mundo == null || mundo.isClient) return;
            if (TinturasBlockEntity.esDiseno(stack)) be.absorberDiseno(stack); else be.absorberPatron(stack);
        }
    }
}
