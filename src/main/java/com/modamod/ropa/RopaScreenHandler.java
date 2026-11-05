package com.modamod.ropa;

import com.modamod.ModamodScreenHandlers;
import com.mojang.datafixers.util.Pair;
import dev.emi.trinkets.SurvivalTrinketSlot;
import dev.emi.trinkets.api.SlotGroup;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;


/**
 * Pantalla de Ropa (2026-10-05, "limpiar la gui de tanto ruido... un boton de ropa que te lleve a una gui
 * especifica con los slots de ropa"): los slots de Trinkets del mod, los 4 de armadura real, los 4 cosméticos y el
 * inventario. Los botones de ocultar la armadura real son {@code onButtonClick} 0..3.
 */
public class RopaScreenHandler extends ScreenHandler {

    /** Los slots de Trinkets que son del mod (grupo, slot): los mismos se esconden del inventario de siempre. */
    public static final String[][] SLOTS_DEL_MOD = {{ListaDeRopa.GRUPO, ListaDeRopa.SLOT}};

    public static boolean esDelMod(String grupo, String slot) {
        for (String[] s : SLOTS_DEL_MOD) if (s[0].equals(grupo) && s[1].equals(slot)) return true;
        return false;
    }

    /** Medidas de la pantalla (px). */
    public static final int ANCHO = 284, ALTO = 258;
    /** La grilla de ropa ({@link GrillaDeRopa}) y, a su derecha, la armadura real con su Ocultar. */
    public static final int X_MOD = 88, Y_MOD = 26;
    public static final int X_REAL = 182, Y_ARMADURA = 26, PASO_ARMADURA = 22;
    public static final int X_INV = 61, Y_INV = 172;

    private final PlayerEntity jugador;
    /** Dónde empieza el inventario: para el shift-click (lo del inventario va primero a la armadura real, después a la grilla). */
    private int inicioInventario;

    public RopaScreenHandler(int syncId, PlayerInventory inv) {
        this(ModamodScreenHandlers.ROPA, syncId, inv, 0, 0);
    }

    /**
     * Los mismos slots corridos {@code dx}, {@code dy}: el Guardarropas (2026-10-21, "tiene que tener los mismos slots que la
     * pantalla de ropa del jugador") los reutiliza dentro de su propia pantalla.
     */
    protected RopaScreenHandler(net.minecraft.screen.ScreenHandlerType<?> tipo, int syncId, PlayerInventory inv, int dx, int dy) {
        super(tipo, syncId);
        this.jugador = inv.player;

        EquipmentSlot[] reales = Cosmeticos.ZONAS;
        for (int z = 0; z < 4; z++) {
            int zona = z;
            int y = Y_ARMADURA + z * PASO_ARMADURA;
            addSlot(new Slot(inv, 39 - z, X_REAL + dx, y + dy) {
                @Override public int getMaxItemCount() { return 1; }

                @Override
                public boolean canInsert(ItemStack stack) { return jugador.getPreferredEquipmentSlot(stack) == reales[zona]; }

                @Override
                public boolean canTakeItems(PlayerEntity player) {
                    ItemStack s = getStack();
                    return (s.isEmpty() || player.isCreative() || !net.minecraft.enchantment.EnchantmentHelper.hasAnyEnchantmentsWith(
                            s, net.minecraft.component.EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) && super.canTakeItems(player);
                }

                @Override
                public Pair<Identifier, Identifier> getBackgroundSprite() {
                    return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, GrillaDeRopa.FONDO_ARMADURA[zona]);
                }
            });
        }

        // La grilla: la lista de ropa (slots de Trinkets) y la fila de la armadura cosmética.
        TrinketInventory lista = ListaDeRopa.de(jugador);
        SlotGroup grupo = TrinketsApi.getPlayerSlots(jugador).get(ListaDeRopa.GRUPO);
        CosmeticosInventario cosm = new CosmeticosInventario(jugador);
        if (lista != null && grupo != null) {
            GrillaDeRopa.armar(X_MOD + dx, Y_MOD + dy, this::fila,
                    (i, x, y, visible) -> new SurvivalTrinketSlot(lista, i, x, y, grupo, lista.getSlotType(), i, true) {
                        @Override
                        public boolean isEnabled() { return super.isEnabled() && visible.getAsBoolean(); }
                    },
                    (z, x, y, visible) -> new Slot(cosm, z, x, y) {
                        @Override public int getMaxItemCount() { return 1; }

                        @Override
                        public boolean canInsert(ItemStack stack) { return cosm.isValid(z, stack); }

                        @Override
                        public boolean isEnabled() { return visible.getAsBoolean(); }

                        @Override
                        public Pair<Identifier, Identifier> getBackgroundSprite() {
                            return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, GrillaDeRopa.FONDO_ARMADURA[z]);
                        }
                    },
                    this::addSlot);
        }

        inicioInventario = slots.size();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) addSlot(new Slot(inv, j + i * 9 + 9, X_INV + j * 18 + dx, Y_INV + i * 18 + dy));
        }
        for (int i = 0; i < 9; i++) addSlot(new Slot(inv, i, X_INV + i * 18 + dx, Y_INV + 58 + dy));
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        Cosmeticos c = Cosmeticos.de(player);
        if (id >= 0 && id < Cosmeticos.ZONAS.length) {
            Cosmeticos.guardar(player, c.alternarOcultar(id));
            return true;
        }
        int fila = GrillaDeRopa.filaTrasBoton(c.fila(), id);
        if (fila >= 0) {
            Cosmeticos.guardar(player, c.conFila(fila));
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot s = slots.get(index);
        if (s == null || !s.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = s.getStack();
        ItemStack copia = stack.copy();
        if (index < inicioInventario) {
            if (!insertItem(stack, inicioInventario, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!insertItem(stack, 0, inicioInventario, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) s.setStack(ItemStack.EMPTY);
        else s.markDirty();
        return copia;
    }

    /** La fila de la armadura cosmética de quien tiene abierta la pantalla. */
    public int fila() {
        return Cosmeticos.de(jugador).fila();
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }
}
