package com.modamod.cinta;

import com.modamod.util.InventarioUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * El ítem que viaja por un tubo: entra por una cara y sale por otra que se decide al entrar (y se vuelve a mirar si no puede salir).
 * Sube con la hora del mundo ({@link #llegada}) y el cliente calcula la posición con ella. Subir tarda más que el resto (el agua
 * lo empuja más despacio).
 */
public class TuboBlockEntity extends BlockEntity implements SidedInventory {

    /** Un bloque en 16 ticks como la cinta recta; hacia arriba, 24. */
    public static final int TICKS = CintaBlockEntity.TICKS_RECTA, TICKS_SUBE = 24;

    private ItemStack carga = ItemStack.EMPTY;
    private long llegada;
    @Nullable private Direction entrada, salida, ultimaEntrada;
    private int turno;
    /** Caras cerradas a mano (bit {@link Direction#getId()}): ver {@link TuboBlock#onUse}. */
    private int cerradas;

    public TuboBlockEntity(BlockPos pos, BlockState state) { super(CintaMod.TUBO_BLOCK_ENTITY, pos, state); }

    public boolean cerrada(Direction d) { return (cerradas & 1 << d.getId()) != 0; }

    /** Cierra o abre esa cara; devuelve si quedó cerrada. */
    public boolean alternar(Direction d) {
        cerradas ^= 1 << d.getId();
        sincronizar();
        return cerrada(d);
    }

    public ItemStack carga() { return carga; }
    public long llegada() { return llegada; }
    @Nullable public Direction entrada() { return entrada; }
    @Nullable public Direction salida() { return salida; }

    public int ticks() { return salida == Direction.UP ? TICKS_SUBE : TICKS; }

    /** ¿Tiene tubos o empalmes arriba o abajo? Entonces el agua manda el sentido (sube con agua, baja sin ella). */
    private boolean vertical() {
        return TuboBlock.conecta(world, pos, Direction.UP) || TuboBlock.conecta(world, pos, Direction.DOWN);
    }

    private Direction sentidoAgua() { return getCachedState().get(TuboBlock.AGUA) ? Direction.UP : Direction.DOWN; }

    /**
     * Por dónde sale lo que entró por {@code e}: en un tramo vertical, hacia donde lo manda el agua si ahí hay conexión (o si no hay
     * otra salida: entonces entrega al inventario de arriba o de abajo); si no, por el lado opuesto al de entrada, o por el codo; con
     * varias salidas posibles, por turnos.
     */
    private Direction calcularSalida(@Nullable Direction e) {
        List<Direction> cand = new ArrayList<>();
        for (Direction d : Direction.values()) {
            if (d != e && TuboBlock.conecta(world, pos, d) && !TuboBlock.valvulaContra(world, pos, d)) cand.add(d);
        }
        boolean vertical = vertical();
        Direction agua = sentidoAgua();
        if (vertical) {
            cand.remove(agua.getOpposite());
            if (cand.contains(agua)) return agua;
            if (cand.isEmpty()) return agua;
        } else if (cand.isEmpty()) {
            return e == null ? Direction.NORTH : e.getOpposite();
        }
        if (e != null && cand.contains(e.getOpposite())) return e.getOpposite();
        return cand.get(Math.floorMod(turno++, cand.size()));
    }

    public static void tick(World world, BlockPos pos, BlockState state, TuboBlockEntity be) {
        if (be.carga.isEmpty()) return;
        if (be.salida == null) be.salida = be.calcularSalida(be.entrada);   // venía de guardado sin salida elegida
        if (world.getTime() - be.llegada < be.ticks()) return;
        // Por una cara cerrada no entrega (queda como un tubo sin salida).
        ItemStack resto = be.cerrada(be.salida) ? be.carga
                : InventarioUtil.empujarA(world, pos.offset(be.salida), be.salida.getOpposite(), be.carga);
        if (resto.getCount() != be.carga.getCount()) {
            be.carga = resto;
            be.sincronizar();
            return;
        }
        // No pudo salir: tal vez cambiaron los vecinos o el agua; se vuelve a elegir.
        Direction nueva = be.calcularSalida(be.entrada);
        if (nueva != be.salida) {
            be.salida = nueva;
            be.sincronizar();
        }
    }

    private void sincronizar() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override public int size() { return 1; }
    @Override public boolean isEmpty() { return carga.isEmpty(); }
    @Override public ItemStack getStack(int slot) { return carga; }
    @Override public ItemStack removeStack(int slot, int cantidad) { return removeStack(slot); }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack r = carga;
        carga = ItemStack.EMPTY;
        if (!r.isEmpty()) sincronizar();
        return r;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        carga = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        llegada = world != null ? world.getTime() : 0;
        entrada = ultimaEntrada;
        ultimaEntrada = null;
        salida = carga.isEmpty() || world == null ? null : calcularSalida(entrada);
        sincronizar();
    }

    @Override public int getMaxCountPerStack() { return 1; }
    @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return false; }
    @Override public void clear() { carga = ItemStack.EMPTY; sincronizar(); }
    @Override public boolean isValid(int slot, ItemStack stack) { return carga.isEmpty() && !stack.isEmpty(); }
    @Override public int[] getAvailableSlots(Direction side) { return new int[]{0}; }

    /**
     * No recibe lo que iría contra el agua (de arriba con agua, de abajo sin ella) ni por una cara cerrada: el que lo empuja prueba por
     * otro lado.
     */
    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (!isValid(slot, stack)) return false;
        if (dir != null && cerrada(dir)) return false;
        if (dir != null && world != null && vertical() && dir == sentidoAgua()) return false;
        ultimaEntrada = dir;
        return true;
    }

    @Override public boolean canExtract(int slot, ItemStack stack, Direction dir) { return false; }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!carga.isEmpty()) nbt.put("Carga", carga.encode(registries));
        nbt.putLong("Llegada", llegada);
        nbt.putInt("Entrada", entrada == null ? -1 : entrada.getId());
        nbt.putInt("Salida", salida == null ? -1 : salida.getId());
        nbt.putInt("Turno", turno);
        nbt.putInt("Cerradas", cerradas);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        carga = nbt.contains("Carga") ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("Carga")) : ItemStack.EMPTY;
        llegada = nbt.getLong("Llegada");
        int e = nbt.contains("Entrada") ? nbt.getInt("Entrada") : -1, s = nbt.contains("Salida") ? nbt.getInt("Salida") : -1;
        entrada = e < 0 ? null : Direction.byId(e);
        salida = s < 0 ? null : Direction.byId(s);
        turno = nbt.getInt("Turno");
        cerradas = nbt.getInt("Cerradas");
    }

    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }
}
