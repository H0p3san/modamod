package com.modamod.cinta;

import com.modamod.util.InventarioUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Un ítem por vez (cualquiera), entra por atrás, izquierda, derecha o arriba y sale por el frente. El turno rota:
 * después de aceptar por una entrada, la siguiente con algo esperando pasa primero.
 */
public class EmpalmeBlockEntity extends BlockEntity implements SidedInventory {

    /**
     * Filtros (2026-10-19, "el empalmador del conveyor tiene una nueva funcion filtros... una estructura arriba que se le pueden poner
     * items en los cuatro costados"): un casillero por costado respecto del frente del empalme, mas el de la tolva (arriba, al medio).
     * Un costado con filtro es una SALIDA que solo deja pasar lo que coincide; el de la tolva filtra lo que la tolva de abajo puede
     * sacar. Con Shift + click el item seteado lleva marco y filtra por tag (coincide con cualquier item que comparta un tag).
     */
    public static final int FRENTE = 0, ATRAS = 1, IZQUIERDA = 2, DERECHA = 3, TOLVA = 4, CASILLEROS = 5;
    private final ItemStack[] filtros = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final boolean[] porTag = new boolean[CASILLEROS];

    /** A 1 px por tick como las cintas (2026-10-05, "un viaje smooth"): recto 16, por un costado (arco) 13, desde arriba 8. */
    public static final int TICKS_RECTO = CintaBlockEntity.TICKS_RECTA, TICKS_LADO = CintaBlockEntity.TICKS_CURVA, TICKS_ARRIBA = 8;

    private ItemStack carga = ItemStack.EMPTY;
    private long llegada;
    private long pausadoDesde = -1;
    /** Índice en {@link #entradas} de la entrada con prioridad. */
    private int turno;
    /** La cara por la que se consultó la última inserción (el setStack que sigue es de esa entrada). */
    private Direction ultimaEntrada;
    /** Por qué cara entró lo que lleva ahora (null: a mano o de una versión vieja = como si viniera de atrás). */
    @Nullable private Direction entrada;

    public EmpalmeBlockEntity(BlockPos pos, BlockState state) {
        super(CintaMod.EMPALME_BLOCK_ENTITY, pos, state);
    }

    public ItemStack carga() { return carga; }

    public ItemStack filtro(int casillero) { return filtros[casillero]; }

    public boolean filtroPorTag(int casillero) { return porTag[casillero] && !filtros[casillero].isEmpty(); }

    /** La cara del mundo de un casillero de costado, o null para el de la tolva. */
    @Nullable
    public Direction ladoDe(int casillero) {
        Direction f = getCachedState().get(EmpalmeBlock.FACING);
        return switch (casillero) {
            case FRENTE -> f;
            case ATRAS -> f.getOpposite();
            case IZQUIERDA -> f.rotateYCounterclockwise();
            case DERECHA -> f.rotateYClockwise();
            default -> null;
        };
    }

    /** El casillero que corresponde a una cara del mundo (o -1 si es arriba/abajo). */
    public int casilleroDe(Direction cara) {
        for (int c = 0; c < TOLVA; c++) if (ladoDe(c) == cara) return c;
        return -1;
    }

    /** Pone (o borra, con vacio) el filtro del casillero; un item nuevo arranca como filtro exacto. */
    public void setFiltro(int casillero, ItemStack stack) {
        filtros[casillero] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        porTag[casillero] = false;
        sincronizar();
    }

    /** Shift + click: el item seteado pasa a filtrar por tag (con marco) y vuelve a exacto. */
    public boolean alternarTag(int casillero) {
        if (filtros[casillero].isEmpty()) return false;
        porTag[casillero] = !porTag[casillero];
        sincronizar();
        return true;
    }

    /**
     * ¿Pasa {@code stack} por el filtro de ese casillero? Sin filtro pasa todo; exacto: el mismo item; por tag: comparte al menos un tag
     * con el item seteado (si ese item no tiene tags, vale como exacto).
     */
    public boolean pasa(int casillero, ItemStack stack) {
        ItemStack f = filtros[casillero];
        if (f.isEmpty()) return true;
        if (f.isOf(stack.getItem())) return true;
        if (!porTag[casillero]) return false;
        java.util.Set<net.minecraft.registry.tag.TagKey<net.minecraft.item.Item>> mios = f.streamTags().collect(java.util.stream.Collectors.toSet());
        return stack.streamTags().anyMatch(mios::contains);
    }

    /** Rota entre las salidas libres: lo que ningun filtro registra se reparte de a uno por cada costado sin filtro. */
    private int turnoLibres;
    /** Si la ultima lista de {@link #salidasPara} es de salidas libres (para avanzar el turno al usarla). */
    private boolean salidasLibres;

    /**
     * Las caras por las que puede salir {@code stack}, en orden de intento (2026-10-19, y 2026-10-20 "el filtro de abajo no esta
     * funcionando" + "las cintas sin filtro se dividan el flujo de los items no registrados"): un item REGISTRADO (lo deja pasar algun
     * filtro) sale solo por esos filtros: los costados que coinciden y, si coincide el de la tolva, hacia abajo (la tolva o cofre de
     * abajo); nunca se cuela por una salida libre. Un item NO registrado se reparte, por turnos, entre los costados sin filtro. Nunca
     * sale por donde entro, y una cinta que mira al empalme rechaza lo que se le empuja, asi las salidas reales las marca su direccion.
     */
    private java.util.List<Direction> salidasPara(ItemStack stack, Direction deDondeEntro) {
        java.util.List<Direction> out = new java.util.ArrayList<>();
        for (int c : new int[]{FRENTE, IZQUIERDA, DERECHA, ATRAS}) {
            Direction d = ladoDe(c);
            if (d != deDondeEntro && !filtros[c].isEmpty() && pasa(c, stack)) out.add(d);
        }
        if (!filtros[TOLVA].isEmpty() && pasa(TOLVA, stack)) out.add(Direction.DOWN);
        salidasLibres = false;
        if (!out.isEmpty()) return out;
        // No registrado: los costados sin filtro, empezando por el que le toca.
        java.util.List<Direction> libres = new java.util.ArrayList<>();
        for (int c : new int[]{FRENTE, IZQUIERDA, DERECHA, ATRAS}) {
            Direction d = ladoDe(c);
            if (d != deDondeEntro && filtros[c].isEmpty()) libres.add(d);
        }
        if (libres.isEmpty()) return out;
        int inicio = Math.floorMod(turnoLibres, libres.size());
        for (int k = 0; k < libres.size(); k++) out.add(libres.get((inicio + k) % libres.size()));
        salidasLibres = true;
        return out;
    }

    /** Los costados sin filtro (menos por donde entró), empezando por el que le toca. */
    private java.util.List<Direction> salidasLibresPara(Direction deDondeEntro) {
        java.util.List<Direction> libres = new java.util.ArrayList<>();
        for (int c : new int[]{FRENTE, IZQUIERDA, DERECHA, ATRAS}) {
            Direction d = ladoDe(c);
            if (d != deDondeEntro && filtros[c].isEmpty()) libres.add(d);
        }
        java.util.List<Direction> out = new java.util.ArrayList<>();
        if (libres.isEmpty()) return out;
        int inicio = Math.floorMod(turnoLibres, libres.size());
        for (int k = 0; k < libres.size(); k++) out.add(libres.get((inicio + k) % libres.size()));
        return out;
    }

    // ── Items soltados sobre el empalme (2026-10-20, "que los items que viajen sobre las cintas droppeados no colocados tambien
    // apliquen los filtros de las junction") ──────────────────────────────────────────────────────────────────────────────

    /** Hacia donde se decidio mandar a cada item soltado que esta sobre el empalme (por id de entidad). */
    private record Decision(Direction dir, long tick) {}
    private final java.util.Map<Integer, Decision> decisiones = new java.util.HashMap<>();

    /** ¿Puede un item soltado seguir su camino por ese costado? Una cinta que no mira hacia aca, o otro empalme. */
    private boolean transitable(Direction d) {
        if (world == null) return false;
        BlockState vecino = world.getBlockState(pos.offset(d));
        if (vecino.getBlock() instanceof CintaBlock) return vecino.get(CintaBlock.FACING) != d.getOpposite();
        return vecino.getBlock() instanceof EmpalmeBlock;
    }

    /**
     * Hacia donde empuja el empalme a este item soltado: las mismas reglas de filtros que para lo que lleva la cinta, la decision
     * se toma una vez (al tocarlo) y se mantiene mientras siga encima. Si le toca un cofre o la tolva de abajo, se lo mete directo.
     * Devuelve null si no hay que empujarlo (ya entro a un inventario, o es un item registrado que no tiene por donde salir y
     * espera). Un item sin filtros ni salidas libres con cinta sigue por el frente, como siempre.
     */
    @Nullable
    public Direction salidaParaEntidad(net.minecraft.entity.ItemEntity e) {
        if (world == null || world.isClient) return null;
        long ahora = world.getTime();
        decisiones.values().removeIf(d -> ahora - d.tick > 40);
        Decision previa = decisiones.get(e.getId());
        if (previa != null) return previa.dir;
        ItemStack stack = e.getStack();
        double dx = e.getX() - (pos.getX() + 0.5), dz = e.getZ() - (pos.getZ() + 0.5);
        // Por que costado llego: el que tiene mas cerca. Soltado en el medio no vino de ninguno: puede salir por cualquiera.
        Direction entrada = Math.max(Math.abs(dx), Math.abs(dz)) < 0.2 ? null
                : Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        java.util.List<Direction> lista = salidasPara(stack, entrada);
        boolean libres = salidasLibres;
        for (int k = 0; k < lista.size(); k++) {
            Direction d = lista.get(k);
            // Un inventario en esa salida (cofre, maquina, tolva de abajo): el item entra directo.
            if (world.getBlockEntity(pos.offset(d)) instanceof Inventory) {
                ItemStack resto = InventarioUtil.empujarA(world, pos.offset(d), d.getOpposite(), stack);
                if (resto.getCount() != stack.getCount()) {
                    if (resto.isEmpty()) e.discard(); else e.setStack(resto);
                    if (libres) turnoLibres += k + 1;
                    sincronizar();
                    return null;
                }
                continue;
            }
            if (d != Direction.DOWN && transitable(d)) {
                if (libres) turnoLibres += k + 1;
                decisiones.put(e.getId(), new Decision(d, ahora));
                sincronizar();
                return d;
            }
        }
        // Registrado y sin salida: espera. Sin filtros que apliquen: el frente de siempre.
        return libres || lista.isEmpty() ? getCachedState().get(EmpalmeBlock.FACING) : null;
    }

    public long llegada() { return llegada; }

    /** 0 = atrás, 1 = izquierda, 2 = derecha, 3 = arriba (respecto del frente del empalme). */
    public int lado() {
        if (entrada == null) return 0;
        if (entrada == Direction.UP) return 3;
        Direction f = getCachedState().get(EmpalmeBlock.FACING);
        if (entrada == f.rotateYCounterclockwise()) return 1;
        if (entrada == f.rotateYClockwise()) return 2;
        return 0;
    }

    public int ticks() {
        int base = switch (lado()) { case 1, 2 -> TICKS_LADO; case 3 -> TICKS_ARRIBA; default -> TICKS_RECTO; };
        // Acelerado por el hielo de abajo, como la cinta (2026-10-06).
        return Math.max(1, Math.round(base / (float) CintaFisica.multiplicador(world, pos)));
    }

    public float tiempoEfectivo(float tickDelta) {
        if (pausadoDesde >= 0) return pausadoDesde;
        return world == null ? 0f : world.getTime() + tickDelta;
    }

    private Direction[] entradas() {
        Direction f = getCachedState().get(EmpalmeBlock.FACING);
        return new Direction[]{f.getOpposite(), f.rotateYCounterclockwise(), f.rotateYClockwise(), Direction.UP};
    }

    private boolean hayAlgoEsperando(Direction lado) {
        if (world == null) return false;
        Inventory inv = world.getBlockEntity(pos.offset(lado)) instanceof Inventory i ? i : null;
        return inv != null && !inv.isEmpty();
    }

    public static void tick(World world, BlockPos pos, BlockState state, EmpalmeBlockEntity be) {
        boolean pausada = state.get(EmpalmeBlock.POWERED);
        if (pausada && be.pausadoDesde < 0) {
            be.pausadoDesde = world.getTime();
            be.sincronizar();
        } else if (!pausada && be.pausadoDesde >= 0) {
            be.llegada += world.getTime() - be.pausadoDesde;
            be.pausadoDesde = -1;
            be.sincronizar();
        }
        if (pausada || be.carga.isEmpty() || world.getTime() - be.llegada < be.ticks()) return;
        Direction entrada = be.entrada != null ? be.entrada : state.get(EmpalmeBlock.FACING).getOpposite();
        java.util.List<Direction> salidas = be.salidasPara(be.carga, entrada);
        boolean libres = be.salidasLibres;
        for (int k = 0; k < salidas.size(); k++) {
            Direction salida = salidas.get(k);
            BlockPos destino = pos.offset(salida);
            if (salida != Direction.DOWN) {
                BlockState abajo = world.getBlockState(destino.down());
                if (!(world.getBlockEntity(destino) instanceof Inventory) && abajo.getBlock() instanceof CintaBlock
                        && abajo.get(CintaBlock.FORMA) == CintaBlock.Forma.RAMPA_BAJA && abajo.get(CintaBlock.FACING) == salida) {
                    destino = destino.down();
                }
            }
            ItemStack resto = InventarioUtil.empujarA(world, destino, salida.getOpposite(), be.carga);
            if (resto.getCount() != be.carga.getCount()) {
                be.carga = resto;
                if (libres) be.turnoLibres += k + 1;
                be.sincronizar();
                return;
            }
        }
        // Registrado y con sus salidas llenas (la tolva o el cofre de su filtro): no se queda atorado, sigue por las libres
        // (2026-10-21, "si un hopper bajo una cinta está lleno los items no deberían atorarse arriba, deberían seguir").
        if (!libres && !salidas.isEmpty()) {
            java.util.List<Direction> otras = be.salidasLibresPara(entrada);
            for (int k = 0; k < otras.size(); k++) {
                Direction salida = otras.get(k);
                ItemStack resto = InventarioUtil.empujarA(world, pos.offset(salida), salida.getOpposite(), be.carga);
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
        if (ultimaEntrada != null && !carga.isEmpty()) aceptoPor(ultimaEntrada);
        entrada = ultimaEntrada;
        ultimaEntrada = null;
        pausadoDesde = getCachedState().get(EmpalmeBlock.POWERED) && world != null ? world.getTime() : -1;
        sincronizar();
    }

    @Override public int getMaxCountPerStack() { return 1; }
    @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return false; }
    @Override public void clear() { carga = ItemStack.EMPTY; sincronizar(); }
    @Override public boolean isValid(int slot, ItemStack stack) { return carga.isEmpty() && !stack.isEmpty(); }

    @Override
    public int[] getAvailableSlots(Direction side) {
        if (side == getCachedState().get(EmpalmeBlock.FACING)) return new int[0];   // por abajo solo se saca (2026-10-14)
        return new int[]{0};
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (dir == Direction.DOWN || !isValid(slot, stack) || getCachedState().get(EmpalmeBlock.POWERED)) return false;
        if (dir == null) return true;
        ultimaEntrada = dir;
        // Rotativo: si la entrada con el turno tiene algo esperando, pasa ella; si no, cualquiera.
        Direction[] e = entradas();
        Direction prioritaria = e[turno % e.length];
        return dir == prioritaria || !hayAlgoEsperando(prioritaria);
    }

    /** Al aceptar algo por una entrada, el turno pasa a la siguiente. */
    public void aceptoPor(Direction dir) {
        Direction[] e = entradas();
        for (int i = 0; i < e.length; i++) if (e[i] == dir) turno = (i + 1) % e.length;
    }

    /** La tolva de abajo puede sacar lo que espera, también con redstone (2026-10-14). */
    @Override public boolean canExtract(int slot, ItemStack stack, Direction dir) { return dir == Direction.DOWN && pasa(TOLVA, stack); }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!carga.isEmpty()) nbt.put("Carga", carga.encode(registries));
        nbt.putLong("Llegada", llegada);
        nbt.putLong("PausadoDesde", pausadoDesde);
        nbt.putInt("Turno", turno);
        nbt.putInt("TurnoLibres", turnoLibres);
        nbt.putInt("Entrada", entrada == null ? -1 : entrada.getId());
        net.minecraft.nbt.NbtList lista = new net.minecraft.nbt.NbtList();
        for (int c = 0; c < CASILLEROS; c++) {
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
        pausadoDesde = nbt.contains("PausadoDesde") ? nbt.getLong("PausadoDesde") : -1;
        turno = nbt.getInt("Turno");
        turnoLibres = nbt.getInt("TurnoLibres");
        int e = nbt.contains("Entrada") ? nbt.getInt("Entrada") : -1;
        entrada = e < 0 ? null : Direction.byId(e);
        java.util.Arrays.fill(filtros, ItemStack.EMPTY);
        java.util.Arrays.fill(porTag, false);
        net.minecraft.nbt.NbtList lista = nbt.getList("Filtros", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < lista.size(); i++) {
            NbtCompound en = lista.getCompound(i);
            int c = en.getInt("C");
            if (c < 0 || c >= CASILLEROS) continue;
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
