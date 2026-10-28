package com.modamod.guardarropas;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** El maletín apoyado (2026-10-23): solo guarda el ítem, que viaja al cliente para dibujarlo con sus colores y apliques. */
public class MaletinBlockEntity extends BlockEntity {

    private ItemStack maletin = ItemStack.EMPTY;

    public MaletinBlockEntity(BlockPos pos, BlockState state) {
        super(GuardarropasMod.MALETIN_BLOCK_ENTITY, pos, state);
    }

    public ItemStack maletin() { return maletin; }

    void poner(ItemStack stack) {
        maletin = stack;
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!maletin.isEmpty()) nbt.put("maletin", maletin.encode(registries));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        maletin = nbt.contains("maletin") ? ItemStack.fromNbt(registries, nbt.getCompound("maletin")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
