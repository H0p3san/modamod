package com.modamod.util;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Los diseños de las cuatro máquinas (2026-10-25, "en la pestaña de diseños de todas las maquinas quede y queda un poco de
 * espacio abajo de donde estan los botones de guardar diseño para poner ahi un slot para poner un diseño en blanco y un
 * botoncito de exportar"): el + guarda el diseño directo en la lista de la máquina, sin gastar nada; para llevárselo como
 * ítem se exporta el elegido, gastando un diseño en blanco del casillero de la máquina.
 */
public final class DisenosMaquina {
    private DisenosMaquina() {}

    /** El casillero del diseño en blanco: solo acepta diseños en blanco; avisa a la máquina para que se guarde. */
    public static SimpleInventory casillero(BlockEntity be) {
        SimpleInventory inv = new SimpleInventory(1) {
            @Override
            public boolean isValid(int slot, ItemStack stack) { return esBlanco(stack); }
        };
        inv.addListener(i -> be.markDirty());
        return inv;
    }

    public static boolean esBlanco(ItemStack s) {
        return s.isOf(com.modamod.modelado.ModeladoMod.DISENO_EN_BLANCO);
    }

    public static void escribir(NbtCompound nbt, SimpleInventory blanco, RegistryWrapper.WrapperLookup registros) {
        if (!blanco.getStack(0).isEmpty()) nbt.put("blanco_diseno", blanco.getStack(0).encode(registros));
    }

    public static void leer(NbtCompound nbt, SimpleInventory blanco, RegistryWrapper.WrapperLookup registros) {
        ItemStack s = nbt.contains("blanco_diseno")
                ? ItemStack.fromNbt(registros, nbt.get("blanco_diseno")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        blanco.heldStacks.set(0, s);   // sin el aviso: la máquina se está leyendo, no cambiando
    }

    /** Guarda {@code item} al final de la lista de la máquina; falso (y avisa) si está llena. */
    public static boolean guardar(PlayerEntity jugador, List<ItemStack> lista, int maximo, ItemStack item) {
        if (lista.size() >= maximo) {
            jugador.sendMessage(Text.translatable("modamod.disenos.lleno"), true);
            return false;
        }
        lista.add(item);
        jugador.sendMessage(Text.translatable("modamod.disenos.guardado", item.getName()), true);
        return true;
    }

    /** El mismo diseño (ítem y componentes) ya está en la lista: soltarlo de nuevo no lo repite. */
    public static boolean yaEsta(List<ItemStack> lista, ItemStack s) {
        for (ItemStack e : lista) if (ItemStack.areItemsAndComponentsEqual(e, s)) return true;
        return false;
    }

    /** Le da al jugador una copia del diseño {@code idx}, gastando un diseño en blanco del casillero (la creativa no gasta). */
    public static boolean exportar(PlayerEntity jugador, List<ItemStack> lista, int idx, SimpleInventory blanco, boolean gratis) {
        if (idx < 0 || idx >= lista.size()) {
            jugador.sendMessage(Text.translatable("modamod.disenos.elegi"), true);
            return false;
        }
        if (!gratis) {
            if (blanco.getStack(0).isEmpty()) {
                jugador.sendMessage(Text.translatable("modamod.disenos.sin_blanco"), true);
                return false;
            }
            blanco.removeStack(0, 1);
        }
        ItemStack copia = lista.get(idx).copyWithCount(1);
        jugador.getInventory().offerOrDrop(copia);
        jugador.sendMessage(Text.translatable("modamod.disenos.exportado", copia.getName()), true);
        return true;
    }

    /** El slot del casillero en la pantalla de la máquina (va al final de la lista de slots). */
    public static class SlotBlanco extends net.minecraft.screen.slot.Slot {
        public SlotBlanco(net.minecraft.inventory.Inventory inv, int x, int y) { super(inv, 0, x, y); }

        @Override public boolean canInsert(ItemStack stack) { return esBlanco(stack); }
    }

    /** Avisa que no se guardó porque la máquina está trabajando. */
    public static void avisarTrabajando(PlayerEntity jugador) {
        jugador.sendMessage(Text.translatable("modamod.disenos.trabajando"), true);
    }
}
