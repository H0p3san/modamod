package com.modamod.estilado;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El historial de moldes de aplique que cada jugador fabricó en una Mesa de estilado creativa (2026-10-04, "si perdi un
 * molde de aplique que cree con la mesa de estilado donde lo encuentro" → "vamos con las dos... dame un comando para
 * ver moldes hechos por otros players"): vive en el mundo, no en la máquina, así que sobrevive aunque la Mesa se
 * pierda. Se consulta con {@code /modamod moldes} ({@link ComandoMoldes}). Hasta {@link #MAXIMO_POR_JUGADOR} por
 * jugador; al pasarse se va el más viejo.
 */
public final class MoldesGuardados extends PersistentState {

    public static final int MAXIMO_POR_JUGADOR = 50;

    /** Un molde guardado: quién lo hizo (id y nombre de ese momento), cuándo y el ítem. */
    public record Entrada(UUID autor, String nombreAutor, long hora, ItemStack molde) {}

    private final List<Entrada> entradas = new ArrayList<>();

    public static final PersistentState.Type<MoldesGuardados> TIPO =
            new PersistentState.Type<>(MoldesGuardados::new, MoldesGuardados::leer, null);

    public static MoldesGuardados de(MinecraftServer servidor) {
        return servidor.getOverworld().getPersistentStateManager().getOrCreate(TIPO, "modamod_moldes");
    }

    public static void registrar(MinecraftServer servidor, ServerPlayerEntity jugador, ItemStack molde) {
        MoldesGuardados m = de(servidor);
        m.entradas.add(new Entrada(jugador.getUuid(), jugador.getGameProfile().getName(), System.currentTimeMillis(), molde));
        // Un tope por jugador: se va el más viejo de ese jugador.
        List<Entrada> suyas = m.de(jugador.getUuid());
        while (suyas.size() > MAXIMO_POR_JUGADOR) {
            m.entradas.remove(suyas.remove(0));
        }
        m.markDirty();
    }

    /** Los moldes de un jugador, del más viejo al más nuevo (la lista es una copia). */
    public List<Entrada> de(UUID autor) {
        List<Entrada> out = new ArrayList<>();
        for (Entrada e : entradas) if (e.autor().equals(autor)) out.add(e);
        return out;
    }

    /** Los nombres de quienes hicieron moldes (para sugerir en el comando), sin repetir. */
    public List<String> autores() {
        List<String> out = new ArrayList<>();
        for (Entrada e : entradas) if (!out.contains(e.nombreAutor())) out.add(e.nombreAutor());
        return out;
    }

    /** El autor por su nombre (el último con ese nombre), sin importar mayúsculas. */
    @Nullable
    public UUID autorPorNombre(String nombre) {
        UUID id = null;
        for (Entrada e : entradas) if (e.nombreAutor().equalsIgnoreCase(nombre)) id = e.autor();
        return id;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registros) {
        NbtList lista = new NbtList();
        var ops = registros.getOps(NbtOps.INSTANCE);
        for (Entrada e : entradas) {
            NbtCompound c = new NbtCompound();
            c.putUuid("Autor", e.autor());
            c.putString("Nombre", e.nombreAutor());
            c.putLong("Hora", e.hora());
            ItemStack.CODEC.encodeStart(ops, e.molde()).result().ifPresent(n -> c.put("Molde", n));
            lista.add(c);
        }
        nbt.put("Moldes", lista);
        return nbt;
    }

    private static MoldesGuardados leer(NbtCompound nbt, RegistryWrapper.WrapperLookup registros) {
        MoldesGuardados m = new MoldesGuardados();
        var ops = registros.getOps(NbtOps.INSTANCE);
        NbtList lista = nbt.getList("Moldes", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < lista.size(); i++) {
            NbtCompound c = lista.getCompound(i);
            if (!c.contains("Molde")) continue;
            ItemStack.CODEC.parse(ops, c.get("Molde")).result().ifPresent(molde ->
                    m.entradas.add(new Entrada(c.getUuid("Autor"), c.getString("Nombre"), c.getLong("Hora"), molde)));
        }
        return m;
    }
}
