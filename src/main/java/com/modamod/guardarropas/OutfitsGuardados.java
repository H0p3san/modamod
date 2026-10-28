package com.modamod.guardarropas;

import com.modamod.guardarropas.GuardarropasBlockEntity.Outfit;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los outfits guardados, en el mundo (2026-10-21, "que comparta almacenamiento con el guardarropas"): cada {@code colección} (el UUID que
 * lleva cada Guardarropas y al que se vincula un Maletín) tiene su lista de outfits. Viven acá y no dentro del bloque para que el
 * Maletín pueda vestirte aunque el mueble esté lejos o su chunk descargado.
 */
public class OutfitsGuardados extends PersistentState {

    /** La lista y los últimos nombres de una colección (mutables: {@link Outfits} los modifica y avisa con {@code guardado}). */
    public static final class Coleccion {
        public final List<Outfit> outfits = new ArrayList<>();
        public final Map<UUID, String> ultimos = new HashMap<>();
    }

    public static final PersistentState.Type<OutfitsGuardados> TIPO =
            new PersistentState.Type<>(OutfitsGuardados::new, OutfitsGuardados::leer, null);

    private final Map<UUID, Coleccion> colecciones = new HashMap<>();

    public static OutfitsGuardados de(MinecraftServer server) {
        PersistentStateManager m = server.getOverworld().getPersistentStateManager();
        return m.getOrCreate(TIPO, "modamod_outfits");
    }

    public Coleccion coleccion(UUID id) { return colecciones.computeIfAbsent(id, k -> new Coleccion()); }

    public boolean tiene(UUID id) { return colecciones.containsKey(id) && !colecciones.get(id).outfits.isEmpty(); }

    public OutfitsDatos datos(UUID id) {
        Coleccion c = colecciones.get(id);
        return c == null ? OutfitsDatos.VACIO : new OutfitsDatos(List.copyOf(c.outfits), Map.copyOf(c.ultimos));
    }

    private static OutfitsGuardados leer(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        OutfitsGuardados st = new OutfitsGuardados();
        NbtList lista = nbt.getList("Colecciones", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < lista.size(); i++) {
            NbtCompound c = lista.getCompound(i);
            var dato = OutfitsDatos.CODEC.parse(registries.getOps(NbtOps.INSTANCE), c.get("Datos")).result();
            if (dato.isEmpty()) continue;
            Coleccion col = st.coleccion(c.getUuid("Id"));
            col.outfits.addAll(dato.get().outfits());
            col.ultimos.putAll(dato.get().ultimos());
        }
        return st;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList lista = new NbtList();
        for (var e : colecciones.entrySet()) {
            Coleccion c = e.getValue();
            if (c.outfits.isEmpty() && c.ultimos.isEmpty()) continue;
            NbtCompound nc = new NbtCompound();
            nc.putUuid("Id", e.getKey());
            var dato = new OutfitsDatos(List.copyOf(c.outfits), Map.copyOf(c.ultimos));
            nc.put("Datos", OutfitsDatos.CODEC.encodeStart(registries.getOps(NbtOps.INSTANCE), dato).getOrThrow());
            lista.add(nc);
        }
        nbt.put("Colecciones", lista);
        return nbt;
    }
}
