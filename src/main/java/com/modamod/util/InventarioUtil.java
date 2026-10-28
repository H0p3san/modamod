package com.modamod.util;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Empujar un ítem a un inventario vecino — a pedido (2026-09-20, "las 3
 * maquinas hacen como el crafter y depositan el resultado en el bloque
 * siguiente si es una de las 3 maquinas o si es contenedor"): mismo
 * mecanismo que un Crafter/hopper vanilla (apilar primero en slots que ya
 * tengan el mismo ítem, después usar un slot vacío), pero expuesto como
 * un helper chico para que las 3 estaciones lo llamen igual en vez de
 * reimplementarlo cada una.
 *
 * No hace falta que el destino sea OTRA estación del mod: cualquier
 * {@link Inventory} (cofre, barril, hopper, otra estación) sirve — si es
 * {@link SidedInventory} se respeta qué slots acepta ESE lado en
 * particular (ej. el slot Izquierdo de prenda de la máquina de al lado),
 * si es un {@link Inventory} liso (un cofre) se prueban todos los slots.
 */
public final class InventarioUtil {

    private InventarioUtil() {}

    /**
     * Intenta depositar {@code stack} en el bloque de {@code destino},
     * entrando por la cara {@code caraDestino} (la cara del bloque VECINO
     * que da hacia nosotros — ej. si empujamos hacia el Este, la cara del
     * vecino es Oeste). Devuelve lo que NO se pudo meter (vacío si entró
     * todo) — {@code stack} en sí no se modifica.
     */
    public static ItemStack empujarA(World world, BlockPos destino, Direction caraDestino, ItemStack stack) {
        boolean antes = enCadena;
        enCadena = true;
        try {
            return empujar(world, destino, caraDestino, stack);
        } finally {
            enCadena = antes;
        }
    }

    /**
     * Verdadero mientras una máquina empuja su salida al vecino (2026-10-04,
     * "activemos la linea de produccion textil" + "me parece perfecto que
     * saltee"): el {@code setStack} de la máquina que recibe lo mira para
     * saber que la prenda llega por la cadena y, si no tiene diseño fijado,
     * pasarla de largo en vez de dejarla esperando. Solo servidor, un hilo.
     */
    public static boolean enCadena = false;

    private static ItemStack empujar(World world, BlockPos destino, Direction caraDestino, ItemStack stack) {
        if (stack.isEmpty()) return stack;
        BlockEntity be = world.getBlockEntity(destino);
        if (!(be instanceof Inventory inv)) return stack;

        int[] slots = inv instanceof SidedInventory sided
                ? sided.getAvailableSlots(caraDestino)
                : rangoCompleto(inv.size());

        ItemStack restante = stack.copy();

        // 1ra pasada: apilar en slots que YA tengan el mismo ítem.
        for (int slot : slots) {
            if (restante.isEmpty()) break;
            if (!puedeInsertar(inv, slot, restante, caraDestino)) continue;
            ItemStack actual = inv.getStack(slot);
            if (actual.isEmpty() || !ItemStack.areItemsAndComponentsEqual(actual, restante)) continue;
            int espacio = Math.min(tope(inv, slot), actual.getMaxCount()) - actual.getCount();
            if (espacio <= 0) continue;
            int mover = Math.min(espacio, restante.getCount());
            ItemStack nuevo = actual.copy();
            nuevo.increment(mover);
            inv.setStack(slot, nuevo);
            restante.decrement(mover);
        }

        // 2da pasada: un slot vacío.
        for (int slot : slots) {
            if (restante.isEmpty()) break;
            if (!puedeInsertar(inv, slot, restante, caraDestino)) continue;
            if (!inv.getStack(slot).isEmpty()) continue;
            int mover = Math.min(tope(inv, slot), restante.getCount());
            inv.setStack(slot, restante.copyWithCount(mover));
            restante.decrement(mover);
        }

        if (restante.getCount() != stack.getCount()) inv.markDirty();
        return restante;
    }

    private static int tope(Inventory inv, int slot) {
        return inv instanceof LimiteEntrada l ? Math.min(l.topeEntrada(slot), inv.getMaxCountPerStack()) : inv.getMaxCountPerStack();
    }

    private static boolean puedeInsertar(Inventory inv, int slot, ItemStack stack, @Nullable Direction cara) {
        if (inv instanceof SidedInventory sided) return sided.canInsert(slot, stack, cara);
        return inv.isValid(slot, stack);
    }

    private static int[] rangoCompleto(int size) {
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = i;
        return out;
    }
}
