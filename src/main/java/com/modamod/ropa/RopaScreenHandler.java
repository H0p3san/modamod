package com.modamod.ropa;

import com.modamod.screen.ModamodScreenHandlers;
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

import java.util.Map;

/**
 * Pantalla de Ropa (2026-10-05, "limpiar la gui de tanto ruido... un boton de ropa que te lleve a una gui
 * especifica con los slots de ropa"): los slots de Trinkets del mod, los 4 de armadura real, los 4 cosméticos y el
 * inventario. Los botones de ver cosmético / ocultar son {@code onButtonClick}: 0..3 ver, 4..7 ocultar.
 */
public class RopaScreenHandler extends ScreenHandler {

    /** Los slots de Trinkets que son del mod (grupo, slot): los mismos se esconden del inventario de siempre. */
    public static final String[][] SLOTS_DEL_MOD = {
            {"head", "sombrero"}, {"head", "mascara"}, {"head", "barbijo"}, {"head", "choker"}, {"espalda", "capa"},
            {"torso", "chaqueta"}, {"torso", "prenda"}, {"arms", "armwarmer"}, {"piernas", "exterior"}, {"socks", "pair"},
            {"torso", "delantal"}, {"torso", "cinto"}, {"socks", "calzado"},
    };

    /**
     * Dónde va cada slot (2026-10-25, "tienen que estar todos en la pantalla de ropa del personaje y tiene que ser igual que
     * la de maniqui y guardarropas"): {fila, columna} en la grilla de 3 (las prendas de varios lugares siguen de a 3 desde
     * ahí); fila -1 = la fila de abajo del muñeco. El Maniquí usa el mismo acomodo.
     */
    public static int[] posicion(String slot) {
        return switch (slot) {
            case "sombrero" -> new int[]{0, 0};
            case "mascara" -> new int[]{0, 1};
            case "barbijo" -> new int[]{0, 2};
            case "choker" -> new int[]{1, 0};
            case "capa" -> new int[]{1, 1};
            case "chaqueta" -> new int[]{2, 0};
            case "prenda" -> new int[]{3, 0};
            case "armwarmer" -> new int[]{4, 0};
            case "exterior" -> new int[]{5, 0};
            case "pair" -> new int[]{6, 0};
            case "delantal" -> new int[]{-1, 0};
            case "cinto" -> new int[]{-1, 1};
            case "calzado" -> new int[]{-1, 2};
            default -> new int[]{-1, 0};
        };
    }

    /** x, y (px) del lugar {@code i} de {@code slot}. */
    public static int[] xy(String slot, int i) {
        int[] p = posicion(slot);
        if (p[0] < 0) return new int[]{X_ABAJO + p[1] * 22, Y_ABAJO};
        int celda = p[1] + i;
        return new int[]{X_MOD + (celda % 3) * 22, Y_MOD + (p[0] + celda / 3) * PASO_FILA};
    }

    public static boolean esDelMod(String grupo, String slot) {
        for (String[] s : SLOTS_DEL_MOD) if (s[0].equals(grupo) && s[1].equals(slot)) return true;
        return false;
    }

    /** Medidas de la pantalla (px). */
    // Alto +20 (2026-10-25): una fila más para máscara y barbijo; el inventario baja.
    public static final int ANCHO = 284, ALTO = 258;
    public static final int X_MOD = 74, Y_MOD = 26, PASO_FILA = 20;
    /** La fila de abajo del muñeco (delantal, cinto y calzado: van solos, no entran en las filas de a 3). */
    public static final int X_ABAJO = 8, Y_ABAJO = 130;
    public static final int X_REAL = 150, X_COSM = 172, Y_ARMADURA = 26, PASO_ARMADURA = 22;
    public static final int X_INV = 61, Y_INV = 172;

    private static final Identifier[] FONDO_ARMADURA = {
            PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE};

    private final PlayerEntity jugador;
    /** Cuántos slots hay antes del inventario: para el shift-click. */
    private int hastaArmadura;
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

        var comp = TrinketsApi.getTrinketComponent(jugador);
        if (comp.isPresent()) {
            Map<String, SlotGroup> grupos = TrinketsApi.getPlayerSlots(jugador);
            for (String[] s : SLOTS_DEL_MOD) {
                Map<String, TrinketInventory> delGrupo = comp.get().getInventory().get(s[0]);
                TrinketInventory ti = delGrupo == null ? null : delGrupo.get(s[1]);
                SlotGroup grupo = grupos.get(s[0]);
                if (ti == null || grupo == null) continue;
                // Acomodo fijo por slot (ver posicion): cabeza, collar y capa, una fila por prenda y abajo del muñeco
                // delantal, cinto y calzado.
                for (int i = 0; i < ti.size(); i++) {
                    int[] p = xy(s[1], i);
                    addSlot(new SurvivalTrinketSlot(ti, i, p[0] + dx, p[1] + dy, grupo, ti.getSlotType(), i, true));
                }
            }
        }

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
                    return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, FONDO_ARMADURA[zona]);
                }
            });
        }
        hastaArmadura = slots.size();

        CosmeticosInventario cosm = new CosmeticosInventario(jugador);
        for (int z = 0; z < 4; z++) {
            int zona = z;
            addSlot(new Slot(cosm, z, X_COSM + dx, Y_ARMADURA + z * PASO_ARMADURA + dy) {
                @Override public int getMaxItemCount() { return 1; }

                @Override
                public boolean canInsert(ItemStack stack) { return cosm.isValid(zona, stack); }

                @Override
                public Pair<Identifier, Identifier> getBackgroundSprite() {
                    return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, FONDO_ARMADURA[zona]);
                }
            });
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
        if (id >= 0 && id < 4) {
            Cosmeticos.guardar(player, c.alternarVer(id));
            return true;
        }
        if (id >= 4 && id < 8) {
            Cosmeticos.guardar(player, c.alternarOcultar(id - 4));
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
        } else if (!insertItem(stack, 0, hastaArmadura, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) s.setStack(ItemStack.EMPTY);
        else s.markDirty();
        return copia;
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }
}
