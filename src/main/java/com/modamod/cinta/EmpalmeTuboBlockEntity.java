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
 * El nudo de 6 caras (2026-10-20): un ítem por vez que entra por cualquier cara y sale por otra según los filtros, con las mismas
 * reglas que {@link EmpalmeBlockEntity}: un ítem registrado (lo deja pasar algún filtro) sale solo por esas caras; uno no registrado
 * se reparte por turnos entre las caras sin filtro; nunca sale por donde entró. Los filtros se indexan por {@link Direction#getId()}.
 */
public class EmpalmeTuboBlockEntity extends BlockEntity implements SidedInventory {

    /** Cruzar el nudo: desde la cara de entrada hasta el centro. */
    public static final int TICKS = 8;

    private final ItemStack[] filtros = new ItemStack[6];
    private final boolean[] porTag = new boolean[6];
    private ItemStack carga = ItemStack.EMPTY;
    private long llegada;
    @Nullable private Direction entrada;
    @Nullable private Direction ultimaEntrada;
    private int turnoLibres;
    private boolean salidasLibres;

    public EmpalmeTuboBlockEntity(BlockPos pos, BlockState state) {
        super(CintaMod.EMPALME_TUBO_BLOCK_ENTITY, pos, state);
        java.util.Arrays.fill(filtros, ItemStack.EMPTY);
    }

    public ItemStack carga() { return carga; }
    public long llegada() { return llegada; }
    @Nullable public Direction entrada() { return entrada; }
    public ItemStack filtro(Direction d) { return filtros[d.getId()]; }
    public boolean filtroPorTag(Direction d) { return porTag[d.getId()] && !filtros[d.getId()].isEmpty(); }

    public void setFiltro(Direction d, ItemStack stack) {
        filtros[d.getId()] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        porTag[d.getId()] = false;
        sincronizar();
    }

    public boolean alternarTag(Direction d) {
        if (filtros[d.getId()].isEmpty()) return false;
        porTag[d.getId()] = !porTag[d.getId()];
        sincronizar();
        return true;
    }

    /** Sin filtro pasa todo; exacto: el mismo ítem; por tag: comparte algún tag con el ítem seteado. */
    public boolean pasa(Direction d, ItemStack stack) {
        ItemStack f = filtros[d.getId()];
        if (f.isEmpty()) return true;
        if (f.isOf(stack.getItem())) return true;
        if (!porTag[d.getId()]) return false;
        java.util.Set<net.minecraft.registry.tag.TagKey<net.minecraft.item.Item>> mios = f.streamTags().collect(java.util.stream.Collectors.toSet());
        return stack.streamTags().anyMatch(mios::contains);
    }

    /** Las caras por las que puede salir {@code stack}, en orden de intento (ver la clase). */
    private List<Direction> salidasPara(ItemStack stack, @Nullable Direction deDondeEntro) {
        List<Direction> out = new ArrayList<>();
        for (Direction d : Direction.values()) {
            if (d != deDondeEntro && !filtros[d.getId()].isEmpty() && pasa(d, stack)) out.add(d);
        }
        salidasLibres = false;
        if (!out.isEmpty()) return out;
        List<Direction> libres = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != deDondeEntro && filtros[d.getId()].isEmpty()) libres.add(d);
        if (libres.isEmpty()) return out;
        int inicio = Math.floorMod(turnoLibres, libres.size());
        for (int k = 0; k < libres.size(); k++) out.add(libres.get((inicio + k) % libres.size()));
        salidasLibres = true;
        return out;
    }

    public static void tick(World world, BlockPos pos, BlockState state, EmpalmeTuboBlockEntity be) {
        if (be.carga.isEmpty() || world.getTime() - be.llegada < TICKS) return;
        List<Direction> salidas = be.salidasPara(be.carga, be.entrada);
        boolean libres = be.salidasLibres;
        for (int k = 0; k < salidas.size(); k++) {
            Direction salida = salidas.get(k);
            ItemStack resto = InventarioUtil.empujarA(world, pos.offset(salida), salida.getOpposite(), be.carga);
            if (resto.getCount() != be.carga.getCount()) {
                be.carga = resto;
                if (libres) be.turnoLibres += k + 1;
                be.sincronizar();
                return;
            }
        }
        // Registrado y con sus salidas llenas: sigue por las caras sin filtro, como el empalme de cintas (2026-10-21).
        if (!libres && !salidas.isEmpty()) {
            List<Direction> otras = new ArrayList<>();
            for (Direction d : Direction.values()) if (d != be.entrada && be.filtros[d.getId()].isEmpty()) otras.add(d);
            for (int k = 0; k < otras.size(); k++) {
                Direction d = otras.get(Math.floorMod(be.turnoLibres + k, otras.size()));
                ItemStack resto = InventarioUtil.empujarA(world, pos.offset(d), d.getOpposite(), be.carga);
                if (resto.getCount() != be.carga.getCount()) {
                    be.carga = resto;
                    be.turnoLibres += k + 1;
                    be.sincronizar();
                    return;
                }
            }
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
        sincronizar();
    }

    @Override public int getMaxCountPerStack() { return 1; }
    @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return false; }
    @Override public void clear() { carga = ItemStack.EMPTY; sincronizar(); }
    @Override public boolean isValid(int slot, ItemStack stack) { return carga.isEmpty() && !stack.isEmpty(); }
    @Override public int[] getAvailableSlots(Direction side) { return new int[]{0}; }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (!isValid(slot, stack)) return false;
        ultimaEntrada = dir;   // el setStack que sigue es de esa entrada
        return true;
    }

    @Override public boolean canExtract(int slot, ItemStack stack, Direction dir) { return false; }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!carga.isEmpty()) nbt.put("Carga", carga.encode(registries));
        nbt.putLong("Llegada", llegada);
        nbt.putInt("Entrada", entrada == null ? -1 : entrada.getId());
        nbt.putInt("TurnoLibres", turnoLibres);
        net.minecraft.nbt.NbtList lista = new net.minecraft.nbt.NbtList();
        for (int c = 0; c < 6; c++) {
            if (filtros[c].isEmpty()) continue;
            NbtCompound e = new NbtCompound();
            e.putInt("C", c);
            e.put("Item", filtros[c].encode(registries));
            e.putBoolean("Tag", porTag[c]);
            lista.add(e);
        }
        nbt.put("Filtros", lista);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        carga = nbt.contains("Carga") ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("Carga")) : ItemStack.EMPTY;
        llegada = nbt.getLong("Llegada");
        int e = nbt.contains("Entrada") ? nbt.getInt("Entrada") : -1;
        entrada = e < 0 ? null : Direction.byId(e);
        turnoLibres = nbt.getInt("TurnoLibres");
        java.util.Arrays.fill(filtros, ItemStack.EMPTY);
        java.util.Arrays.fill(porTag, false);
        net.minecraft.nbt.NbtList lista = nbt.getList("Filtros", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < lista.size(); i++) {
            NbtCompound en = lista.getCompound(i);
            int c = en.getInt("C");
            if (c < 0 || c >= 6) continue;
            filtros[c] = ItemStack.fromNbtOrEmpty(registries, en.getCompound("Item"));
            porTag[c] = en.getBoolean("Tag");
        }
    }

    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }
}
