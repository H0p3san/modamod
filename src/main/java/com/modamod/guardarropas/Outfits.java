package com.modamod.guardarropas;

import com.modamod.guardarropas.GuardarropasBlockEntity.Outfit;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;

/**
 * Las reglas de los outfits guardados con nombre (2026-10-21), compartidas por el Guardarropas y el Maletín (ver {@link AlmacenOutfits}):
 * guardar lo que lleva puesto el jugador MUEVE las piezas al almacén (nunca las copia), ponerse un outfit lo suelta entero y guarda lo
 * que se llevaba, y desarmarlo devuelve las piezas al inventario.
 */
public final class Outfits {

    private Outfits() {}

    public static final int MAXIMO = 12;

    /** El outfit especial que guarda lo que llevabas justo antes del último cambio: siempre el primero de la lista (ver {@link #poner}). */
    public static final String ANTERIOR = "Anterior";

    /** Un lugar del jugador donde va una pieza (slot de Trinkets, armadura real o cosmética). */
    private interface Lugar {
        ItemStack get();
        void set(ItemStack s);
    }

    /** {@code grupo/slot#i}, {@code armadura#zona} o {@code cosmetico#zona}. */
    public static String clave(String grupo, String slot, int i) { return grupo + "/" + slot + "#" + i; }

    /** Todos los lugares de Ropa del jugador (los mismos que su pantalla de Ropa), en orden. */
    private static LinkedHashMap<String, Lugar> lugares(PlayerEntity p) {
        LinkedHashMap<String, Lugar> out = new LinkedHashMap<>();
        dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(p).ifPresent(comp -> {
            for (String[] gs : com.modamod.ropa.RopaScreenHandler.SLOTS_DEL_MOD) {
                var grupo = comp.getInventory().get(gs[0]);
                dev.emi.trinkets.api.TrinketInventory ti = grupo == null ? null : grupo.get(gs[1]);
                if (ti == null) continue;
                for (int i = 0; i < ti.size(); i++) {
                    int idx = i;
                    out.put(clave(gs[0], gs[1], i), new Lugar() {
                        public ItemStack get() { return ti.getStack(idx); }
                        public void set(ItemStack s) { ti.setStack(idx, s); }
                    });
                }
            }
        });
        for (int z = 0; z < com.modamod.ropa.Cosmeticos.ZONAS.length; z++) {
            int zona = z;
            net.minecraft.entity.EquipmentSlot es = com.modamod.ropa.Cosmeticos.ZONAS[z];
            out.put("armadura#" + z, new Lugar() {
                public ItemStack get() { return p.getEquippedStack(es); }
                public void set(ItemStack s) { p.getInventory().armor.set(es.getEntitySlotId(), s); }
            });
            out.put("cosmetico#" + z, new Lugar() {
                public ItemStack get() { return com.modamod.ropa.Cosmeticos.de(p).pieza(zona); }
                public void set(ItemStack s) { com.modamod.ropa.Cosmeticos.guardar(p, com.modamod.ropa.Cosmeticos.de(p).conPieza(zona, s)); }
            });
        }
        return out;
    }

    /** Saca todo lo que lleva puesto el jugador (queda desnudo) y lo devuelve por clave. */
    private static LinkedHashMap<String, ItemStack> sacarTodo(PlayerEntity p) {
        LinkedHashMap<String, ItemStack> piezas = new LinkedHashMap<>();
        for (var e : lugares(p).entrySet()) {
            ItemStack st = e.getValue().get();
            if (st.isEmpty()) continue;
            piezas.put(e.getKey(), st.copy());
            e.getValue().set(ItemStack.EMPTY);
        }
        return piezas;
    }

    private static String sinColor(String nombre) {
        String n = nombre == null ? "" : nombre.strip();
        return n.length() > 24 ? n.substring(0, 24) : n;
    }

    /** Un nombre que no repite ninguno de la lista: "Gala", "Gala 2", "Gala 3"... (cabe en 24 letras). */
    private static String unico(AlmacenOutfits a, String nombre) {
        String candidato = nombre;
        for (int k = 2; ; k++) {
            final String c = candidato;
            if (a.outfits().stream().noneMatch(o -> o.nombre().equalsIgnoreCase(c))) return candidato;
            String sufijo = " " + k;
            candidato = (nombre.length() + sufijo.length() > 24 ? nombre.substring(0, 24 - sufijo.length()) : nombre) + sufijo;
        }
    }

    /**
     * Guarda lo que lleva puesto el jugador con ese nombre (vacío: el del último outfit que se puso, o "Outfit N"). Las piezas se MUEVEN
     * al almacén. Devuelve false si no lleva nada o ya hay {@link #MAXIMO}.
     */
    public static boolean guardar(AlmacenOutfits a, PlayerEntity player, String nombre) {
        if (a.outfits().size() >= MAXIMO) {
            player.sendMessage(Text.translatable("modamod.guardarropas.lleno", MAXIMO), true);
            return false;
        }
        var piezas = sacarTodo(player);
        if (piezas.isEmpty()) {
            player.sendMessage(Text.translatable("modamod.guardarropas.sin_ropa"), true);
            return false;
        }
        String n = sinColor(nombre);
        if (n.isEmpty()) n = a.ultimos().getOrDefault(player.getUuid(), "Outfit " + (a.outfits().size() + 1));
        a.ultimos().remove(player.getUuid());
        a.outfits().add(new Outfit(unico(a, n), piezas));
        a.guardado();
        return true;
    }

    /**
     * Se pone el outfit {@code idx}: sus piezas pasan al jugador. Si lo que llevaba puesto era un outfit guardado (el último que se
     * puso, ver {@code ultimos}), vuelve a la lista con su nombre (2026-10-21, "con el anterior me parece que tendría que ser solo si
     * el outfit puesto no fue guardado"). Si no, queda guardado como el outfit {@link #ANTERIOR} (2026-10-21, "no se puede guardar
     * como outfit anterior", opción 1): siempre el primero de la lista. Un Anterior que todavía no se usó pasa a la lista normal como
     * "Anterior 2", así no se pierde nada. Una pieza cuyo lugar no existe (datapack distinto) va al inventario.
     */
    public static boolean poner(AlmacenOutfits a, PlayerEntity player, int idx) {
        if (idx < 0 || idx >= a.outfits().size()) return false;
        Outfit o = a.outfits().remove(idx);
        String puesto = a.ultimos().get(player.getUuid());
        // Volver al Anterior no es "ponerse un outfit con nombre": no se recuerda su nombre para el próximo guardado.
        if (ANTERIOR.equals(o.nombre())) a.ultimos().remove(player.getUuid());
        else a.ultimos().put(player.getUuid(), o.nombre());
        var llevaba = sacarTodo(player);
        var lugares = lugares(player);
        for (var e : o.piezas().entrySet()) {
            Lugar l = lugares.get(e.getKey());
            if (l != null && l.get().isEmpty()) l.set(e.getValue().copy());
            else player.getInventory().offerOrDrop(e.getValue().copy());
        }
        if (!llevaba.isEmpty() && puesto != null) {
            a.outfits().add(new Outfit(unico(a, puesto), llevaba));
        } else if (!llevaba.isEmpty()) {
            // El Anterior de antes (si lo hay) se renombra en su lugar; el nuevo ocupa el primer puesto.
            for (int i = 0; i < a.outfits().size(); i++) {
                Outfit x = a.outfits().get(i);
                if (ANTERIOR.equals(x.nombre())) a.outfits().set(i, new Outfit(unico(a, ANTERIOR), x.piezas()));
            }
            a.outfits().add(0, new Outfit(ANTERIOR, llevaba));
        }
        a.guardado();
        return true;
    }

    /** Desarma el outfit {@code idx}: todas sus piezas vuelven al inventario del jugador. */
    public static boolean desarmar(AlmacenOutfits a, PlayerEntity player, int idx) {
        if (idx < 0 || idx >= a.outfits().size()) return false;
        for (ItemStack st : a.outfits().remove(idx).piezas().values()) player.getInventory().offerOrDrop(st.copy());
        a.guardado();
        return true;
    }
}
