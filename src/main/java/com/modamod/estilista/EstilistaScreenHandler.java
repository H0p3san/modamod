package com.modamod.estilista;

import com.modamod.estilado.EstiladoBlockEntity;
import com.modamod.estilado.EstiladoScreenHandler;
import com.modamod.screen.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * Pantalla de la Estilista (2026-10-05, etapa 3: "no quiero perder el tamaño del visualizador" + "que sea más como las
 * otras guis con un gran slot para input y output y un apply en el medio"): a la izquierda la vista 3D de la Mesa de
 * estilado —su editor interno, con la prenda de muestra— y a la derecha el panel de la máquina: entrada, Aplicar y
 * salida; Fijar y Borrar el diseño; los contadores de hilo y cuero; y el almacén de plantillas, retazos y objetos.
 */
public class EstilistaScreenHandler extends EstiladoScreenHandler {

    // Ventana de 560x408 (2026-10-12, plan de GUIs): cabecera Entrada → Aplicar → Salida a la derecha del inventario, el
    // almacén en la columna derecha (bajo las pestañas del editor) y los contadores de hilo y cuero abajo.
    public static final int X_ENTRADA = 308, X_SALIDA = 520, Y_IO = 326;
    public static final float ESCALA_IO = 2.5f;
    public static final int X_ALMACEN = com.modamod.client.DisposicionEstilista.X + 1, Y_ALMACEN = com.modamod.client.DisposicionEstilista.Y_INSUMOS + 1, COLUMNAS_ALMACEN = 9;
    /** Los contadores (se clickean con un ítem en el cursor para cargarlos). */
    public static final int X_HILO = 296, X_CUERO = 428, Y_CONTADOR = 372, ANCHO_CONTADOR = 124, ALTO_CONTADOR = 12;
    /** Panel de diseños, a la izquierda del inventario. */
    public static final int X_DISENOS = 8, ANCHO_DISENOS = 105, Y_DISENOS = 312;

    public static boolean esSlotGrande(int x, int y) {
        return (y == Y_IO && (x == X_ENTRADA || x == X_SALIDA)) || (x == X_OBJETO && y == Y_OBJETO);
    }

    /** Dónde empiezan los slots de la máquina (después de los del editor y del inventario del jugador). */
    private static final int BASE = EstiladoBlockEntity.TAMANO, INV_FIN = BASE + 36;

    /** Casillero de objeto de la Estilista creativa. */
    public static final int X_OBJETO = 583, Y_OBJETO = 310;   // en grande, abajo del panel de la creativa (2026-10-14)
    public final EstilistaBlockEntity host;
    /** La pestaña Bolsas está abierta (solo del cliente): vuelve visible el depósito. */
    public boolean verBolsas = false;
    /** Los Insumos solo se ven en la pestaña Bolsas (el depósito también en Diseños). */
    public boolean verInsumos = false;
    /** Depósito de las bolsas: el último de todos los slots (después de los Insumos). */
    public static final int X_DEPOSITO = com.modamod.client.DisposicionEstilista.X + 1, Y_DEPOSITO = com.modamod.client.DisposicionEstilista.Y_DEPOSITO + 1;
    private static final int SLOT_DEPOSITO = INV_FIN + 2 + EstilistaBlockEntity.ALMACEN;

    public static EstilistaScreenHandler deCliente(int syncId, PlayerInventory inv, BlockPos pos) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        EstilistaBlockEntity be = client.world != null && client.world.getBlockEntity(pos) instanceof EstilistaBlockEntity e
                ? e : new EstilistaBlockEntity(pos, EstilistaMod.ESTILISTA_BLOCK.getDefaultState());
        return new EstilistaScreenHandler(syncId, inv, be);
    }

    public EstilistaScreenHandler(int syncId, PlayerInventory inv, EstilistaBlockEntity host) {
        super(ModamodScreenHandlers.ESTILISTA, syncId, inv, host.editor());
        this.host = host;
        // El slot de prenda del editor ya no se usa (2026-10-08): la muestra es la prenda de la entrada.
        Slot muestra = new Slot(host.editor(), EstiladoBlockEntity.SLOT_PRENDA, -10000, -10000) {
            @Override public boolean canInsert(ItemStack stack) { return false; }
            @Override public boolean canTakeItems(PlayerEntity player) { return false; }
            @Override public boolean isEnabled() { return false; }
        };
        muestra.id = EstiladoBlockEntity.SLOT_PRENDA;
        slots.set(EstiladoBlockEntity.SLOT_PRENDA, muestra);
        // Los casilleros del editor (molde, retazo, objeto y material) tampoco se ven (2026-10-12, "confunde que esten arriba
        // esos cuatro slots todo el tiempo"): se eligen desde las bolsas y las listas de cada pestaña.
        for (int indice : new int[] { EstiladoBlockEntity.SLOT_MOLDE, EstiladoBlockEntity.SLOT_RETAZO,
                EstiladoBlockEntity.SLOT_MATERIAL }) {
            Slot oculto = new Slot(host.editor(), indice, -10000, -10000) {
                @Override public boolean canInsert(ItemStack stack) { return false; }
                @Override public boolean canTakeItems(PlayerEntity player) { return false; }
                @Override public boolean isEnabled() { return false; }
            };
            oculto.id = indice;
            slots.set(indice, oculto);
        }
        // Solo la Estilista creativa tiene casillero de objeto (2026-10-14, "no puedo cargar items para hacer apliques
        // custom"): el aplique que se pone pasa a ser ese ítem. Va sobre el inventario, a la izquierda de la cabecera.
        Slot objeto = new Slot(host.editor(), EstiladoBlockEntity.SLOT_OBJETO, X_OBJETO, Y_OBJETO) {
            @Override public boolean canInsert(ItemStack stack) { return host.editor().isValid(EstiladoBlockEntity.SLOT_OBJETO, stack); }
            @Override public int getMaxItemCount() { return 1; }
            @Override public boolean isEnabled() { return host.editor().creativa(); }
        };
        objeto.id = EstiladoBlockEntity.SLOT_OBJETO;
        slots.set(EstiladoBlockEntity.SLOT_OBJETO, objeto);
        addSlot(new Slot(host, EstilistaBlockEntity.SLOT_PRENDA, X_ENTRADA, Y_IO) {
            @Override public boolean canInsert(ItemStack stack) { return host.isValid(EstilistaBlockEntity.SLOT_PRENDA, stack); }
            @Override public int getMaxItemCount() { return 1; }
        });
        addSlot(new Slot(host, EstilistaBlockEntity.SLOT_SALIDA, X_SALIDA, Y_IO) {
            @Override public boolean canInsert(ItemStack stack) { return false; }
        });
        for (int i = 0; i < EstilistaBlockEntity.ALMACEN; i++) {
            int slot = EstilistaBlockEntity.SLOT_ALMACEN + i;
            addSlot(new Slot(host, slot, X_ALMACEN + (i % COLUMNAS_ALMACEN) * 18, Y_ALMACEN + (i / COLUMNAS_ALMACEN) * 18) {
                @Override public boolean canInsert(ItemStack stack) { return host.isValid(slot, stack); }
                @Override public boolean isEnabled() { return verInsumos; }
            });
        }
        addSlot(new SlotDeposito(this, host, X_DEPOSITO, Y_DEPOSITO));
        // El diseño en blanco para exportar (2026-10-25): último slot, después del depósito.
        addSlot(new com.modamod.util.DisenosMaquina.SlotBlanco(host.blancoDiseno, 9, 345));
    }

    /**
     * Depósito de las bolsas (2026-10-12): lo que se suelta acá se suma a la bolsa que le corresponde (apliques, moldes o
     * retazos); el casillero queda siempre vacío y solo se ve con la pestaña Bolsas abierta.
     */
    private static class SlotDeposito extends Slot {
        private final EstilistaScreenHandler handler;
        private final EstilistaBlockEntity host;

        SlotDeposito(EstilistaScreenHandler handler, EstilistaBlockEntity host, int x, int y) {
            super(new net.minecraft.inventory.SimpleInventory(1), 0, x, y);
            this.handler = handler;
            this.host = host;
        }

        /** Un diseño se copia a la lista y vuelve al cursor, sin gastarse (2026-10-25, "cuando uses una plantilla con diseño en la maquina lo copie sin gastarse"). */
        @Override
        public ItemStack insertStack(ItemStack stack, int count) {
            if (!(stack.getItem() instanceof DisenoEstiloItem)) return super.insertStack(stack, count);
            if (canInsert(stack) && host.getWorld() != null && !host.getWorld().isClient) host.absorberDiseno(stack);
            return stack;
        }

        @Override public boolean isEnabled() { return handler.verBolsas; }
        @Override public boolean canInsert(ItemStack stack) { return !host.trabajando() && EstilistaBlockEntity.esDeposito(stack); }
        @Override public ItemStack getStack() { return ItemStack.EMPTY; }
        @Override public boolean hasStack() { return false; }
        @Override public boolean canTakeItems(PlayerEntity player) { return false; }
        @Override public void setStackNoCallbacks(ItemStack stack) { }
        @Override public int getMaxItemCount() { return 64; }

        @Override
        public void setStack(ItemStack stack) {
            if (stack.isEmpty()) return;
            var mundo = host.getWorld();
            if (mundo != null && !mundo.isClient) {
                if (stack.getItem() instanceof DisenoEstiloItem) host.absorberDiseno(stack); else host.absorberEnBolsa(stack);
            }
        }
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == EstilistaBlockEntity.BTN_CARGAR_HILO || id == EstilistaBlockEntity.BTN_CARGAR_CUERO) {
            // Soltar el ítem del cursor sobre el contador: hilo en el de hilo, cuero en el de cuero.
            ItemStack cursor = getCursorStack();
            boolean hilo = id == EstilistaBlockEntity.BTN_CARGAR_HILO;
            if (!cursor.isOf(hilo ? net.minecraft.item.Items.STRING : net.minecraft.item.Items.LEATHER)) return false;
            return host.absorber(cursor) > 0;
        }
        if (id == EstilistaBlockEntity.BTN_LINEA || id == EstilistaBlockEntity.BTN_CATEGORIA_ATRAS || id == EstilistaBlockEntity.BTN_CATEGORIA_ADELANTE
                || (id >= EstilistaBlockEntity.BTN_APLICAR && id <= EstilistaBlockEntity.BTN_BORRAR)
                || (id >= EstilistaBlockEntity.BTN_DISENO_CARGAR_BASE && id < EstilistaBlockEntity.BTN_DISENO_BORRAR_BASE + EstilistaBlockEntity.MAX_NOMBRADOS)) return host.onButtonClick(player, id);
        return super.onButtonClick(player, id);
    }

    @Override
    public boolean canUse(PlayerEntity player) { return host.canPlayerUse(player) && !host.trabajando(); }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot s = slots.get(index);
        if (s == null || !s.hasStack() || !s.canTakeItems(player)) return ItemStack.EMPTY;
        ItemStack stack = s.getStack();
        ItemStack copia = stack.copy();
        if (index < EstiladoBlockEntity.SLOT_BIBLIOTECA || index == EstiladoBlockEntity.SLOT_MATERIAL) {
            // Del editor: al almacén (si es algo que se guarda) y si no al inventario.
            if (!insertItem(stack, INV_FIN + 2, SLOT_DEPOSITO, false) && !insertItem(stack, BASE, INV_FIN, true)) return ItemStack.EMPTY;
        } else if (index < BASE || index >= INV_FIN) {
            // De la máquina: molde, retazo y muestra al editor; el resto al inventario.
            if (!insertItem(stack, BASE, INV_FIN, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Del inventario: hilo y cuero a los contadores; después editor, entrada y almacén.
            if (host.absorber(stack) > 0 && stack.isEmpty()) {
                s.setStack(ItemStack.EMPTY);
                return copia;
            }
            if (com.modamod.util.DisenosMaquina.esBlanco(stack)) {
                if (!insertItem(stack, slots.size() - 1, slots.size(), false)) return ItemStack.EMPTY;
            } else if (stack.getItem() instanceof DisenoEstiloItem) {
                // Shift-click: el diseño se copia a la lista y se queda en el inventario (2026-10-25, "cuando uses una plantilla con diseño en la maquina lo copie sin gastarse").
                if (!player.getWorld().isClient) host.absorberDiseno(stack);
                return ItemStack.EMPTY;
            } else if (EstilistaBlockEntity.esDeposito(stack)) {
                // Plantillas, moldes y retazos van a su bolsa.
                if (!insertItem(stack, SLOT_DEPOSITO, SLOT_DEPOSITO + 1, false)) return ItemStack.EMPTY;
            } else if (!insertItem(stack, INV_FIN, INV_FIN + 1, false)
                    && !insertItem(stack, INV_FIN + 2, SLOT_DEPOSITO, false)) {
                return stack.getCount() != copia.getCount() ? copia : ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) s.setStack(ItemStack.EMPTY);
        else s.markDirty();
        return copia;
    }
}
