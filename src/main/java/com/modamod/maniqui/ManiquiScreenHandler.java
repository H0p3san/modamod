package com.modamod.maniqui;

import com.modamod.guardarropas.GuardarropasBlockEntity;
import com.modamod.guardarropas.GuardarropasScreenHandler;
import com.modamod.screen.ModamodScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * Maniquí: 16 slots (4 categorías x 4 capas) + inventario del jugador, en
 * las MISMAS posiciones que el Guardarropas — así reusa su fondo
 * ({@code guardarropas.png}) sin arte nuevo.
 */
public class ManiquiScreenHandler extends ScreenHandler {

    public static final int M_MEDIO = GuardarropasScreenHandler.M_MEDIO;
    /** Ventana de 560x408 como las otras maquinas (2026-10-19, "hacela mas grande como las otras maquinas"). */
    public static final int ANCHO = 560, ALTO = 408;
    /** Grilla de 3 columnas como la pantalla de Ropa: 8 filas (cabeza, collar y capa, 5 prendas, abajo). */
    public static final int X_GRILLA = 240, Y_GRILLA = 24, PASO_X = 22, PASO_Y = 24, FILAS = 8;
    /** La armadura, en una columna al lado de la grilla. */
    public static final int X_ARMADURA = 330;

    /** x, y del lugar {@code i} de {@code slot} (nombre del slot de Trinkets), con el acomodo de {@code RopaScreenHandler#posicion}. */
    public static int[] xy(String slot, int i) {
        int[] p = com.modamod.ropa.RopaScreenHandler.posicion(slot);
        int fila = p[0] < 0 ? FILAS - 1 : p[0], celda = p[1] + i;
        return new int[]{X_GRILLA + (celda % 3) * PASO_X, Y_GRILLA + (fila + celda / 3) * PASO_Y};
    }
    public static final int X_INV = 172, Y_INV = 324, Y_HOTBAR = 382;
    private static final int INV_START = ManiquiBlockEntity.TAMANO;

    public final ManiquiBlockEntity be;
    private final PlayerEntity jugador;
    /** Estado del candado para ESTE jugador, sincronizado al cliente (bits de {@code Candado#estado}). */
    private int estado;

    /** Factory del lado del CLIENTE: busca el block entity REAL en la posición que mandó el servidor. */
    public static ManiquiScreenHandler deCliente(int syncId, PlayerInventory inv, BlockPos pos) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        ManiquiBlockEntity be = client.world != null && client.world.getBlockEntity(pos) instanceof ManiquiBlockEntity m
                ? m
                : new ManiquiBlockEntity(pos, ManiquiMod.MANIQUI_BLOCK.getDefaultState());
        return new ManiquiScreenHandler(syncId, inv, be);
    }

    public ManiquiScreenHandler(int syncId, PlayerInventory playerInventory, ManiquiBlockEntity be) {
        super(ModamodScreenHandlers.MANIQUI, syncId);
        this.be = be;
        this.jugador = playerInventory.player;
        addProperty(new net.minecraft.screen.Property() {
            @Override
            public int get() { return jugador.getWorld().isClient ? estado : be.candado().estado(jugador); }

            @Override
            public void set(int valor) { estado = valor; }
        });

        // Slot anónimo con canInsert → isValid: Slot.canInsert no consulta
        // Inventory.isValid por su cuenta (trampa conocida, ver CLAUDE.md).
        // Mismo acomodo que la pantalla de Ropa (2026-10-25, "tiene que ser igual que la de maniqui y guardarropas"): cabeza,
        // collar y capa, una fila por prenda y abajo delantal, cinto y calzado (ver RopaScreenHandler#posicion); la armadura al lado.
        String[] categoria = new String[ManiquiBlockEntity.CATEGORIAS];
        categoria[GuardarropasBlockEntity.REMERA] = "prenda";
        categoria[GuardarropasBlockEntity.PANTALON] = "exterior";
        categoria[GuardarropasBlockEntity.MEDIAS] = "pair";
        categoria[GuardarropasBlockEntity.CALIENTABRAZOS] = "armwarmer";
        categoria[GuardarropasBlockEntity.CHAQUETA] = "chaqueta";
        for (int cat = 0; cat < ManiquiBlockEntity.CATEGORIAS; cat++) {
            for (int capa = 0; capa < ManiquiBlockEntity.POR_CATEGORIA; capa++) {
                int[] p = xy(categoria[cat], capa);
                addSlot(slotPropio(cat * ManiquiBlockEntity.POR_CATEGORIA + capa, p[0], p[1], categoria[cat], ManiquiBlockEntity.POR_CATEGORIA));
            }
        }
        Object[][] sueltos = {
                {"sombrero", GuardarropasBlockEntity.SLOT_SOMBRERO}, {"mascara", ManiquiBlockEntity.SLOT_MASCARA},
                {"barbijo", ManiquiBlockEntity.SLOT_BARBIJO}, {"choker", ManiquiBlockEntity.SLOT_COLLAR},
                {"capa", ManiquiBlockEntity.SLOT_CAPA}, {"delantal", GuardarropasBlockEntity.SLOT_DELANTAL},
                {"cinto", ManiquiBlockEntity.SLOT_CINTO}, {"calzado", GuardarropasBlockEntity.SLOT_CALZADO}};
        for (Object[] s : sueltos) {
            int[] p = xy((String) s[0], 0);
            addSlot(slotPropio((Integer) s[1], p[0], p[1], (String) s[0], 1));
        }
        for (int i = 0; i < GuardarropasBlockEntity.SLOTS_ARMADURA.length; i++) {
            addSlot(slotPropio(GuardarropasBlockEntity.ARMADURA_INICIO + i, X_ARMADURA, Y_GRILLA + i * PASO_Y, null, 1));
        }

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlot(new Slot(playerInventory, j + i * 9 + 9, X_INV + j * 18, Y_INV + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(playerInventory, i, X_INV + i * 18, Y_HOTBAR));
        }
    }

    private static final net.minecraft.util.Identifier[] FONDO_ARMADURA = {
            net.minecraft.screen.PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE, net.minecraft.screen.PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
            net.minecraft.screen.PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE, net.minecraft.screen.PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE};

    /** El grupo de Trinkets de cada slot de ropa (para el hover). */
    private static String grupoDe(String slot) {
        return switch (slot) {
            case "sombrero", "mascara", "barbijo", "choker" -> "head";
            case "capa" -> "espalda";
            case "exterior" -> "piernas";
            case "pair", "calzado" -> "socks";
            case "armwarmer" -> "arms";
            default -> "torso";
        };
    }

    /** Un slot del maniqui que respeta el candado y las reglas de {@link ManiquiBlockEntity#isValid}; {@code ropa}: su slot de Trinkets (null = armadura). */
    private Slot slotPropio(int index, int x, int y, @org.jetbrains.annotations.Nullable String ropa, int lugares) {
        if (ropa != null) {
            class DeRopa extends Slot implements com.modamod.ropa.SlotDeRopa {
                DeRopa(int px, int py) { super(be, index, px, py); }
                @Override public String grupoDeRopa() { return grupoDe(ropa); }
                @Override public String slotDeRopa() { return ropa; }
                @Override public int lugaresDeRopa() { return lugares; }
                @Override public boolean canInsert(ItemStack stack) { return puedeTocar() && be.isValid(index, stack); }
                @Override public boolean canTakeItems(PlayerEntity player) { return puedeTocar(); }
                @Override public int getMaxItemCount() { return 1; }
            }
            return new DeRopa(x, y);
        }
        int zona = index - GuardarropasBlockEntity.ARMADURA_INICIO;
        return new Slot(be, index, x, y) {
            @Override
            public boolean canInsert(ItemStack stack) { return puedeTocar() && be.isValid(index, stack); }

            @Override
            public boolean canTakeItems(PlayerEntity player) { return puedeTocar(); }

            @Override
            public int getMaxItemCount() { return 1; }

            // La silueta de vanilla en el vacío, como la pantalla de Ropa (2026-10-25).
            @Override
            public com.mojang.datafixers.util.Pair<net.minecraft.util.Identifier, net.minecraft.util.Identifier> getBackgroundSprite() {
                if (zona < 0 || zona >= FONDO_ARMADURA.length) return null;
                return com.mojang.datafixers.util.Pair.of(net.minecraft.screen.PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, FONDO_ARMADURA[zona]);
            }
        };
    }

    /** Cliente: viene de la propiedad sincronizada; servidor: se calcula en el momento. */
    public int estado() { return jugador.getWorld().isClient ? estado : be.candado().estado(jugador); }

    public boolean puedeTocar() { return com.modamod.util.Candado.puedeTocar(estado()); }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        return be.onButtonClick(player, id);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return be.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        Slot clickedSlot = this.slots.get(slot);
        if (clickedSlot == null || !clickedSlot.hasStack()) return ItemStack.EMPTY;
        if (!puedeTocar()) return ItemStack.EMPTY;
        ItemStack stack = clickedSlot.getStack();
        ItemStack result = stack.copy();
        if (slot < INV_START) {
            if (!this.insertItem(stack, INV_START, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.insertItem(stack, 0, INV_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) clickedSlot.setStack(ItemStack.EMPTY);
        else clickedSlot.markDirty();
        return result;
    }
}
