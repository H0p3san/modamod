package com.modamod.telar;

import com.modamod.screen.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * Pantalla del Telar (2026-10-07): lana, hilo y la prenda tejida, más el inventario; las prendas se tildan con botones
 * ({@link TelarBlockEntity#BTN_PRENDA_BASE}) y el lote se configura debajo. Dibujada por código en {@link com.modamod.client.TelarScreen}.
 */
public class TelarScreenHandler extends ScreenHandler {

    // 2026-10-12 (plan de GUIs, "no veo que tenga un layout parecido" a las otras máquinas): mismo panel de 560x408 y mismas
    // columnas que Tintes/Sublimadora (izquierda: la prenda grande, centro: las prendas a tejer y, en la franja de y 184,
    // insumos → barra de progreso → Salida; derecha: Línea y lote; inventario abajo en x = M_MEDIO).
    public static final int ANCHO = 560, ALTO = 408;
    public static final int M_MEDIO = 119, M_MEDIO_ANCHO = 240, M_DERECHA = M_MEDIO + M_MEDIO_ANCHO + 20;
    // 2026-10-14 ("terminemos con telar"): la franja insumos → barra → Salida pasó a la altura de la cabecera común (y 332).
    public static final int Y_IO = 332;
    public static final int X_LANA = 308, X_HILO = 330, X_CUERO = 352;
    public static final int Y_LANA = Y_IO, Y_HILO = Y_IO, Y_CUERO = Y_IO;
    public static final int X_SALIDA = 528, Y_SALIDA = Y_IO;
    public static final int Y_INV = 324, X_INV = M_MEDIO;

    public final TelarBlockEntity host;

    public static TelarScreenHandler deCliente(int syncId, PlayerInventory inv, BlockPos pos) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        TelarBlockEntity be = client.world != null && client.world.getBlockEntity(pos) instanceof TelarBlockEntity e
                ? e : new TelarBlockEntity(pos, TelarMod.TELAR_BLOCK.getDefaultState());
        return new TelarScreenHandler(syncId, inv, be);
    }

    public TelarScreenHandler(int syncId, PlayerInventory inv, TelarBlockEntity host) {
        super(ModamodScreenHandlers.TELAR, syncId);
        this.host = host;
        addSlot(new Slot(host, TelarBlockEntity.SLOT_LANA, X_LANA, Y_LANA) {
            @Override public boolean canInsert(ItemStack stack) { return host.isValid(TelarBlockEntity.SLOT_LANA, stack); }
        });
        addSlot(new Slot(host, TelarBlockEntity.SLOT_HILO, X_HILO, Y_HILO) {
            @Override public boolean canInsert(ItemStack stack) { return host.isValid(TelarBlockEntity.SLOT_HILO, stack); }
        });
        addSlot(new Slot(host, TelarBlockEntity.SLOT_CUERO, X_CUERO, Y_CUERO) {
            @Override public boolean canInsert(ItemStack stack) { return host.isValid(TelarBlockEntity.SLOT_CUERO, stack); }
        });
        addSlot(new Slot(host, TelarBlockEntity.SLOT_SALIDA, X_SALIDA, Y_SALIDA) {
            @Override public boolean canInsert(ItemStack stack) { return false; }
        });
        for (int f = 0; f < 3; f++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + f * 9 + c, X_INV + c * 18, Y_INV + f * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, X_INV + c * 18, Y_INV + 58));
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        return id == TelarBlockEntity.BTN_TEJER ? host.tejerAhora(player) : host.boton(id);
    }

    @Override
    public boolean canUse(PlayerEntity player) { return host.canPlayerUse(player) && !host.trabajando(); }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot s = slots.get(index);
        if (s == null || !s.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = s.getStack();
        ItemStack copia = stack.copy();
        if (index < TelarBlockEntity.TAMANO) {
            if (!insertItem(stack, TelarBlockEntity.TAMANO, slots.size(), true)) return ItemStack.EMPTY;
        } else if (host.isValid(TelarBlockEntity.SLOT_LANA, stack)) {
            if (!insertItem(stack, TelarBlockEntity.SLOT_LANA, TelarBlockEntity.SLOT_LANA + 1, false)) return ItemStack.EMPTY;
        } else if (host.isValid(TelarBlockEntity.SLOT_HILO, stack)) {
            if (!insertItem(stack, TelarBlockEntity.SLOT_HILO, TelarBlockEntity.SLOT_HILO + 1, false)) return ItemStack.EMPTY;
        } else if (host.isValid(TelarBlockEntity.SLOT_CUERO, stack)) {
            if (!insertItem(stack, TelarBlockEntity.SLOT_CUERO, TelarBlockEntity.SLOT_CUERO + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) s.setStack(ItemStack.EMPTY);
        else s.markDirty();
        return copia;
    }
}
