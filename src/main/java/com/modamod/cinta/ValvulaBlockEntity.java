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

/** El ítem que cruza la válvula (2026-10-21): entra por atrás, tarda lo que un tramo de tubo y sale por el pico. */
public class ValvulaBlockEntity extends BlockEntity implements SidedInventory {

    private ItemStack carga = ItemStack.EMPTY;
    private long llegada;

    public ValvulaBlockEntity(BlockPos pos, BlockState state) { super(CintaMod.VALVULA_BLOCK_ENTITY, pos, state); }

    public static void tick(World world, BlockPos pos, BlockState state, ValvulaBlockEntity be) {
        if (be.carga.isEmpty() || world.getTime() - be.llegada < TuboBlockEntity.TICKS) return;
        Direction f = state.get(ValvulaBlock.FACING);
        ItemStack resto = InventarioUtil.empujarA(world, pos.offset(f), f.getOpposite(), be.carga);
        if (resto.getCount() != be.carga.getCount()) {
            be.carga = resto;
            be.markDirty();
        }
    }

    @Override public int size() { return 1; }
    @Override public boolean isEmpty() { return carga.isEmpty(); }
    @Override public ItemStack getStack(int slot) { return carga; }
    @Override public ItemStack removeStack(int slot, int cantidad) { return removeStack(slot); }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack r = carga;
        carga = ItemStack.EMPTY;
        markDirty();
        return r;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        carga = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        llegada = world != null ? world.getTime() : 0;
        markDirty();
    }

    @Override public int getMaxCountPerStack() { return 1; }
    @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return false; }
    @Override public void clear() { carga = ItemStack.EMPTY; markDirty(); }
    @Override public boolean isValid(int slot, ItemStack stack) { return carga.isEmpty() && !stack.isEmpty(); }
    @Override public int[] getAvailableSlots(Direction side) { return new int[]{0}; }

    /** Solo por atrás: lo que llega por el pico o por los costados rebota y el que lo empuja prueba por otro lado. */
    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return isValid(slot, stack) && dir == getCachedState().get(ValvulaBlock.FACING).getOpposite();
    }

    @Override public boolean canExtract(int slot, ItemStack stack, Direction dir) { return false; }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!carga.isEmpty()) nbt.put("Carga", carga.encode(registries));
        nbt.putLong("Llegada", llegada);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        carga = nbt.contains("Carga") ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("Carga")) : ItemStack.EMPTY;
        llegada = nbt.getLong("Llegada");
    }
}
