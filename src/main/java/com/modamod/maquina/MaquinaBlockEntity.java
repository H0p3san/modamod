package com.modamod.maquina;

import com.modamod.util.ConSalida;
import com.modamod.util.InventarioUtil;
import com.modamod.util.MaquinaCreativa;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * La base del block entity de una {@link MaquinaBlock}. Reúne lo que las cinco máquinas repetían: los lados de la cadena
 * (la prenda entra por la izquierda, la materia prima por atrás y el resultado sale por la derecha), el interruptor de la
 * línea de producción, el envío del estado al cliente y si es la versión creativa.
 *
 * <p>Línea de producción: apagada de fábrica, la máquina no empuja su salida al vecino ni saltea las prendas que le llegan
 * por la cadena; la prenda queda para sacarla a mano. El resto (inventario, ciclo de trabajo, pantalla) es de cada máquina.
 */
public abstract class MaquinaBlockEntity extends BlockEntity implements ConSalida {

    /** Id del botón de la pantalla que alterna la línea de producción. */
    public static final int BTN_LINEA = 900;

    private boolean linea;

    protected MaquinaBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean linea() {
        return linea;
    }

    /** Alterna la línea de producción y avisa al cliente. */
    public void alternarLinea() {
        linea = !linea;
        sincronizar();
    }

    public boolean creativa() {
        return MaquinaCreativa.es(this);
    }

    // --- Los lados, vistos desde el frente de la máquina ---

    protected final Direction ladoAtras() {
        return getCachedState().get(MaquinaBlock.FACING).getOpposite();
    }

    protected final Direction ladoIzquierdo() {
        return ladoAtras().rotateYCounterclockwise();
    }

    protected final Direction ladoDerecho() {
        return ladoIzquierdo().getOpposite();
    }

    @Override
    public Direction ladoSalida() {
        return ladoDerecho();
    }

    /**
     * Empuja {@code salida} al inventario que está del lado derecho. Devuelve lo que no entró (todo, si la línea está
     * apagada o no hay nada del otro lado); quien llama se queda con eso en su casillero de salida.
     */
    protected final ItemStack empujarALaDerecha(ItemStack salida) {
        if (salida.isEmpty() || world == null || !linea) return salida;
        Direction derecha = ladoDerecho();
        return InventarioUtil.empujarA(world, pos.offset(derecha), derecha.getOpposite(), salida);
    }

    // --- Sincronización con el cliente ---

    /** Guarda y manda el estado a los clientes cercanos; {@code markDirty()} sola no lo hace. */
    protected void sincronizar() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putBoolean("linea", linea);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        linea = nbt.getBoolean("linea");
    }

    /** El cliente necesita todo el NBT para dibujar los displays, los LED y los niveles. */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
