package com.modamod.cinta;

import com.modamod.util.InventarioUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** El filtro y el ritmo del extractor (2026-10-21). No es un inventario: no guarda nada, pasa los ítems de a uno. */
public class ExtractorBlockEntity extends BlockEntity {

    /** Un ítem cada 8 ticks, como la tolva de vanilla. */
    public static final int ESPERA = 8;

    private ItemStack filtro = ItemStack.EMPTY;
    private boolean porTag;
    private int espera;

    public ExtractorBlockEntity(BlockPos pos, BlockState state) { super(CintaMod.EXTRACTOR_BLOCK_ENTITY, pos, state); }

    public ItemStack filtro() { return filtro; }
    public boolean filtroPorTag() { return porTag && !filtro.isEmpty(); }

    public void setFiltro(ItemStack stack) {
        filtro = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        porTag = false;
        sincronizar();
    }

    public void alternarTag() {
        if (filtro.isEmpty()) return;
        porTag = !porTag;
        sincronizar();
    }

    /** Sin filtro pasa todo; exacto: el mismo ítem; por tag: comparte algún tag (las reglas de los empalmes). */
    public boolean pasa(ItemStack stack) {
        if (filtro.isEmpty() || filtro.isOf(stack.getItem())) return true;
        if (!porTag) return false;
        java.util.Set<net.minecraft.registry.tag.TagKey<net.minecraft.item.Item>> mios = filtro.streamTags().collect(java.util.stream.Collectors.toSet());
        return stack.streamTags().anyMatch(mios::contains);
    }

    public static void tick(World world, BlockPos pos, BlockState state, ExtractorBlockEntity be) {
        if (state.get(ExtractorBlock.POWERED) || ++be.espera < ESPERA) return;
        be.espera = 0;
        Direction f = state.get(ExtractorBlock.FACING);
        // getInventoryAt arma el cofre doble entero, como la tolva.
        Inventory origen = HopperBlockEntity.getInventoryAt(world, pos.offset(f.getOpposite()));
        if (origen == null) return;
        int[] slots = origen instanceof SidedInventory s ? s.getAvailableSlots(f) : java.util.stream.IntStream.range(0, origen.size()).toArray();
        for (int slot : slots) {
            ItemStack st = origen.getStack(slot);
            if (st.isEmpty() || !be.pasa(st)) continue;
            if (origen instanceof SidedInventory s && !s.canExtract(slot, st, f)) continue;
            ItemStack resto = InventarioUtil.empujarA(world, pos.offset(f), f.getOpposite(), st.copyWithCount(1));
            if (resto.isEmpty()) {
                origen.removeStack(slot, 1);
                origen.markDirty();
                return;
            }
        }
    }

    private void sincronizar() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!filtro.isEmpty()) nbt.put("Filtro", filtro.encode(registries));
        nbt.putBoolean("Tag", porTag);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        filtro = nbt.contains("Filtro") ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("Filtro")) : ItemStack.EMPTY;
        porTag = nbt.getBoolean("Tag");
    }

    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }
}
