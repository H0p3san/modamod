package com.modamod.maniqui;

import com.modamod.prenda.CategoriaDePrenda;
import com.modamod.prenda.Prendas;
import com.modamod.ropa.Cosmeticos;
import com.modamod.ropa.ListaDeRopa;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Maniquí (2026-09-30): la ropa queda PUESTA en la figura del modelo {@code mannequin} y se ve en el mundo; la dibuja
 * {@code ManiquiRenderer} con el mismo código que la ropa del jugador. Tiene los mismos lugares que la pantalla de Ropa
 * (rediseño, tanda 3): la lista de {@link ListaDeRopa#LUGARES} con su fila de armadura (acá, la armadura del maniquí) y
 * además las dos manos.
 *
 * <p>El plato gira si {@link #girando()} (click derecho agachado o botón de
 * la pantalla). El ángulo NO viaja por red: cada cliente lo acumula por su
 * cuenta ({@link #anguloVisible}), así que al detenerlo la figura se queda
 * donde estaba en vez de saltar al frente.
 *
 * <p>Se sincroniza al cliente ({@link #toUpdatePacket}): la ropa tiene que verse aunque nadie tenga la pantalla abierta.
 */
public class ManiquiBlockEntity extends BlockEntity
        implements net.minecraft.inventory.SidedInventory, ExtendedScreenHandlerFactory<BlockPos>, GeoBlockEntity {

    /** Los lugares de la lista (0..15), la armadura (casco, pechera, pantalón, botas) y las manos. */
    public static final int ARMADURA_INICIO = ListaDeRopa.LUGARES;
    public static final int SLOT_MANO_DER = ARMADURA_INICIO + Cosmeticos.ZONAS.length, SLOT_MANO_IZQ = SLOT_MANO_DER + 1;
    public static final int TAMANO = SLOT_MANO_IZQ + 1;

    public static final int BTN_GIRAR = 0;
    public static final int BTN_INTERCAMBIAR = 1;
    /** Pasa a la siguiente pose armada (2026-09-30, "que tenga poses"). */
    public static final int BTN_POSE = 2;
    /** Alterna figura de maniquí / skin de quien aprieta (2026-09-30, "dame las dos opciones"). */
    public static final int BTN_FIGURA = 3;
    /** Pasa al siguiente talle de busto de la figura (2026-10-02, "agregale la opcion de ponerle tetas"). */
    public static final int BTN_BUSTO = 4;
    /** Cierra/abre el candado (2026-10-04, "le pongamos un lock al maniqui"). */
    public static final int BTN_CANDADO = 5;
    /**
     * Botones de la ropa (2026-10-19, "agreguemos botones en la gui para abrir chaqueta, poner capucha y hacer el twirl en loop"):
     * abren/cierran el frente y suben/bajan la capucha de los Tops puestos (lo mismo que las teclas J y H) y prenden/apagan el
     * twirl de la pollera, el faldon y el delantal, que se repite con una pausa entre vueltas.
     */
    public static final int BTN_FRENTE = 6, BTN_CAPUCHA = 7, BTN_TWIRL = 8;
    /** Ticks de pausa entre una vuelta del twirl y la siguiente. */
    public static final int PAUSA_TWIRL = 40;

    /**
     * Figuras (2026-10-04, "un par de modelos mas aparte del propio un steve un alex"): 0 maniquí, 1 la skin de
     * quien aprieta, 2..10 las nueve skins de Mojang, 11 la skin de un jugador elegido por nombre.
     */
    public static final int FIGURA_MANIQUI = 0, FIGURA_TU_SKIN = 1, FIGURA_PREDEFINIDA_BASE = 2, FIGURA_JUGADOR = 11;
    public static final String[] PREDEFINIDAS = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};
    /** Las que Mojang trae con brazos finos (las demás son anchas). */
    private static final java.util.Set<String> FINAS = java.util.Set.of("alex", "efe", "makena", "noor");

    /** Una vuelta entera cada 14 s — lo mismo que {@code animation.mannequin.girar} del zip. */
    public static final float TICKS_POR_VUELTA = 14 * 20;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(TAMANO, ItemStack.EMPTY);
    private boolean girando = false;
    /** La fila de la armadura en la grilla, como la de la pantalla de Ropa ({@link Cosmeticos#fila}). */
    private int fila = Cosmeticos.FILA_DE_FABRICA;

    // ── pose y figura (2026-09-30) ────────────────────────────────────────
    private PoseManiqui pose = PoseManiqui.PARADO;
    private float[] angulos = PoseManiqui.PARADO.angulos();
    /** Ver {@code FIGURA_*}: qué cuerpo se dibuja. */
    private int figura = FIGURA_MANIQUI;
    /** Emote de Emotecraft que baila la figura (null = ninguno); lo evalúa cada cliente que tenga el mod. */
    @Nullable
    private java.util.UUID emote;
    private final com.modamod.util.Candado candado = new com.modamod.util.Candado();
    /** Talle de busto de la figura: 0 = sin, 1..{@code PerfilCuerpo.BUSTO_MAXIMO} como los de los Estrógenos. */
    private int busto = 0;
    /** Twirl en loop prendido. */
    private boolean twirl = false;
    @Nullable
    private net.minecraft.component.type.ProfileComponent dueno;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ── solo cliente: ángulo acumulado del plato ─────────────────────────
    private float anguloVisible = 0f;
    private double ultimoTiempo = Double.NaN;

    public ManiquiBlockEntity(BlockPos pos, BlockState state) {
        super(ManiquiMod.MANIQUI_BLOCK_ENTITY, pos, state);
    }

    public boolean girando() { return girando; }

    public PoseManiqui pose() { return pose; }

    /** Ángulo {@code i} de la pose, en grados (ver el orden en {@link PoseManiqui}). */
    public float angulo(int i) { return angulos[i]; }

    /** true si la figura lleva una skin (propia, de Mojang o de un jugador) y no la textura de maniquí. */
    public boolean figuraSkin() { return figura != FIGURA_MANIQUI; }

    public int figura() { return figura; }

    /** Nombre (clave de textura) de la skin de Mojang elegida, o null si la figura usa otra cosa. */
    @Nullable
    public String predefinida() {
        int i = figura - FIGURA_PREDEFINIDA_BASE;
        return i >= 0 && i < PREDEFINIDAS.length ? PREDEFINIDAS[i] : null;
    }

    public static boolean predefinidaEsFina(String nombre) { return FINAS.contains(nombre); }

    public com.modamod.util.Candado candado() { return candado; }

    @Nullable
    public java.util.UUID emote() { return emote; }

    public void setEmote(@Nullable java.util.UUID nuevo) {
        emote = nuevo;
        markDirty();
    }

    /** La skin de un jugador elegido por nombre (2026-10-04, "elegir skin escribiendo el nombre"). */
    public void elegirJugador(com.mojang.authlib.GameProfile perfil) {
        dueno = new net.minecraft.component.type.ProfileComponent(perfil);
        figura = FIGURA_JUGADOR;
        markDirty();
    }

    public int busto() { return busto; }

    public boolean twirl() { return twirl; }

    public int fila() { return fila; }

    /** Lo puesto en cada lugar de la lista, de afuera hacia adentro. */
    public List<ItemStack> lugares() {
        return items.subList(0, ListaDeRopa.LUGARES);
    }

    /** Los Tops puestos. */
    private List<ItemStack> tops() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : lugares()) if (Prendas.es(s, CategoriaDePrenda.REMERA)) out.add(s);
        return out;
    }

    /** ¿Algun Top puesto tiene el frente abierto? */
    public boolean frenteAbierto() {
        for (ItemStack t : tops()) if (com.modamod.item.TopCorte.frente(t).abre()) return true;
        return false;
    }

    /** ¿Alguna capucha puesta esta arriba? */
    public boolean capuchaArriba() {
        for (ItemStack t : tops()) if (com.modamod.item.TopCorte.tieneCapucha(t) && com.modamod.item.TopCorte.capuchaArriba(t)) return true;
        return false;
    }

    public boolean hayCapucha() {
        for (ItemStack t : tops()) if (com.modamod.item.TopCorte.tieneCapucha(t)) return true;
        return false;
    }

    @Nullable
    public net.minecraft.component.type.ProfileComponent dueno() { return dueno; }

    /** Guarda el perfil (con la skin) de quien lo puso o eligió "su skin". */
    public void setDueno(com.mojang.authlib.GameProfile perfil) {
        this.dueno = new net.minecraft.component.type.ProfileComponent(perfil);
        markDirty();
    }

    public void aplicarPose(PoseManiqui nueva) {
        float[] a = nueva.angulos();
        if (a == null) return;
        pose = nueva;
        angulos = a;
        markDirty();
    }

    /** Un slider de la pantalla: cambia un ángulo y la pose pasa a LIBRE. */
    public void setAngulo(int i, float grados) {
        if (i < 0 || i >= PoseManiqui.ANGULOS || Float.isNaN(grados)) return;
        float[] r = PoseManiqui.RANGO[i];
        angulos[i] = Math.max(r[0], Math.min(r[1], grados));
        pose = PoseManiqui.LIBRE;
        markDirty();
    }

    public void alternarGiro() {
        girando = !girando;
        markDirty();
    }

    /**
     * Avanza el ángulo del plato hasta {@code tiempo} (ticks del mundo +
     * tickDelta) y lo devuelve en radianes. Lo llama el renderer una vez
     * por frame ANTES de dibujar, así el hueso {@code turntable} y la ropa
     * usan el mismo valor.
     */
    public float avanzarAngulo(double tiempo) {
        if (!Double.isNaN(ultimoTiempo) && girando) {
            double delta = Math.max(0, tiempo - ultimoTiempo);
            anguloVisible = (float) ((anguloVisible + delta * (Math.PI * 2) / TICKS_POR_VUELTA) % (Math.PI * 2));
        }
        ultimoTiempo = tiempo;
        return anguloVisible;
    }

    public float anguloVisible() { return anguloVisible; }

    /** La ropa de cuerpo puesta, en el orden de dibujo (como la del jugador: {@link ListaDeRopa#paraDibujar}). */
    public List<ItemStack> prendasPuestas() {
        return ListaDeRopa.paraDibujar(lugares());
    }

    /** Cuántas de {@link #prendasPuestas} van por encima de la armadura. */
    public int prendasSobreLaArmadura() {
        return ListaDeRopa.prendasSobreLaArmadura(lugares(), fila);
    }

    private ItemStack primera(java.util.function.Predicate<ItemStack> cual) { return ListaDeRopa.primera(lugares(), cual); }

    public ItemStack sombrero() { return primera(s -> Prendas.es(s, CategoriaDePrenda.SOMBRERO)); }
    public ItemStack calzado() { return primera(s -> Prendas.es(s, CategoriaDePrenda.CALZADO)); }
    public ItemStack cinto() { return banda(com.modamod.item.BandaZona.CINTURA); }
    public ItemStack collar() { return banda(com.modamod.item.BandaZona.CUELLO); }
    public ItemStack mascara() { return primera(s -> s.getItem() instanceof com.modamod.item.CaraItem c && !c.barbijo); }
    public ItemStack barbijo() { return primera(s -> s.getItem() instanceof com.modamod.item.CaraItem c && c.barbijo); }
    public ItemStack manoDerecha() { return items.get(SLOT_MANO_DER); }
    public ItemStack manoIzquierda() { return items.get(SLOT_MANO_IZQ); }

    private ItemStack banda(com.modamod.item.BandaZona zona) {
        return primera(s -> Prendas.es(s, CategoriaDePrenda.BANDA) && com.modamod.item.BandaItem.zona(s) == zona);
    }

    /** La pieza de armadura de ese slot del cuerpo (HEAD/CHEST/LEGS/FEET), o vacío. */
    public ItemStack armadura(net.minecraft.entity.EquipmentSlot slot) {
        int z = Cosmeticos.zonaDe(slot);
        return z < 0 ? ItemStack.EMPTY : items.get(ARMADURA_INICIO + z);
    }

    /**
     * Click derecho con algo en la mano: la armadura a su pieza si está libre; la ropa al primer lugar libre de la lista
     * (de afuera hacia adentro). Devuelve false si no entra o no queda lugar.
     */
    public boolean ponerPrenda(ItemStack stack) {
        int z = Cosmeticos.zonaDeArmadura(stack);
        if (z >= 0 && items.get(ARMADURA_INICIO + z).isEmpty()) {
            setStack(ARMADURA_INICIO + z, stack.copyWithCount(1));
            return true;
        }
        if (!ListaDeRopa.entra(stack)) return false;
        for (int i = 0; i < ListaDeRopa.LUGARES; i++) {
            if (items.get(i).isEmpty()) {
                setStack(i, stack.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    /** ¿Se le puede poner esto con click derecho? (ropa de la lista o armadura) */
    public static boolean sePone(ItemStack stack) {
        return ListaDeRopa.entra(stack) || Cosmeticos.zonaDeArmadura(stack) >= 0;
    }

    /**
     * Intercambia con el jugador lugar por lugar (los mismos de la pantalla de Ropa), la fila de la armadura, la armadura
     * real y lo que tiene en las manos. Nada se pierde: lo de cada lado pasa al otro, vacíos incluidos.
     */
    public void intercambiarCon(PlayerEntity player) {
        dev.emi.trinkets.api.TrinketInventory lista = ListaDeRopa.de(player);
        if (lista != null) {
            for (int i = 0; i < ListaDeRopa.LUGARES && i < lista.size(); i++) {
                ItemStack delJugador = lista.getStack(i).copy();
                lista.setStack(i, items.get(i).copy());
                items.set(i, delJugador);
            }
            Cosmeticos c = Cosmeticos.de(player);
            int suya = c.fila();
            Cosmeticos.guardar(player, c.conFila(fila));
            fila = suya;
        }
        for (int z = 0; z < Cosmeticos.ZONAS.length; z++) {
            Cosmeticos c = Cosmeticos.de(player);
            ItemStack delJugador = c.pieza(z).copy();
            Cosmeticos.guardar(player, c.conPieza(z, items.get(ARMADURA_INICIO + z).copy()));
            items.set(ARMADURA_INICIO + z, delJugador);
        }
        for (var mano : new Object[][]{{net.minecraft.entity.EquipmentSlot.MAINHAND, SLOT_MANO_DER}, {net.minecraft.entity.EquipmentSlot.OFFHAND, SLOT_MANO_IZQ}}) {
            net.minecraft.entity.EquipmentSlot parte = (net.minecraft.entity.EquipmentSlot) mano[0];
            int mio = (Integer) mano[1];
            ItemStack delJugador = player.getEquippedStack(parte).copy();
            player.equipStack(parte, items.get(mio).copy());
            items.set(mio, delJugador);
        }
        markDirty();
    }

    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == BTN_CANDADO) {
            if (!candado.alternar(player)) return false;
            markDirty();
            return true;
        }
        // Con el candado cerrado, los ajenos no tocan nada (ni siquiera "Intercambiar", que se llevaría la ropa).
        if (!candado.puedeTocar(player)) return false;
        if (id == BTN_GIRAR) {
            alternarGiro();
            return true;
        }
        if (id == BTN_INTERCAMBIAR) {
            intercambiarCon(player);
            return true;
        }
        if (id == BTN_POSE) {
            aplicarPose(pose.siguiente());
            return true;
        }
        if (id == BTN_BUSTO) {
            busto = (busto + 1) % (com.modamod.body.PerfilCuerpo.BUSTO_MAXIMO + 1);
            markDirty();
            return true;
        }
        if (id == BTN_FRENTE) {
            boolean abrir = !frenteAbierto();
            for (ItemStack t : tops()) com.modamod.item.TopCorte.setFrenteAbierto(t, abrir);
            markDirty();
            return true;
        }
        if (id == BTN_CAPUCHA) {
            boolean subir = !capuchaArriba();
            for (ItemStack t : tops()) if (com.modamod.item.TopCorte.tieneCapucha(t)) com.modamod.item.TopCorte.setCapuchaArriba(t, subir);
            markDirty();
            return true;
        }
        int nuevaFila = com.modamod.ropa.GrillaDeRopa.filaTrasBoton(fila, id);
        if (nuevaFila >= 0) {
            fila = nuevaFila;
            markDirty();
            return true;
        }
        if (id == BTN_TWIRL) {
            twirl = !twirl;
            markDirty();
            return true;
        }
        if (id == BTN_FIGURA) {
            figura = figura == FIGURA_PREDEFINIDA_BASE + PREDEFINIDAS.length - 1 || figura == FIGURA_JUGADOR
                    ? FIGURA_MANIQUI : figura + 1;
            // "Tu skin" = la de quien aprieta (así sirve también en maniquíes
            // puestos antes de esto, sin dueño guardado).
            if (figura == FIGURA_TU_SKIN) dueno = new net.minecraft.component.type.ProfileComponent(player.getGameProfile());
            markDirty();
            return true;
        }
        return false;
    }

    // ── GeckoLib ──────────────────────────────────────────────────────────
    /**
     * Sin controladores a propósito: el giro del hueso {@code turntable} lo
     * pone {@code ManiquiGeoModel} a mano con {@link #anguloVisible}, para
     * que la ropa (que no es parte del modelo GeckoLib) gire exactamente igual.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ── Inventory ─────────────────────────────────────────────────────────
    @Override public int size() { return TAMANO; }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getStack(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(items, slot);
        markDirty();
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
        markDirty();
    }

    /** Una prenda por capa — el maniquí la tiene puesta, no apilada. */
    @Override
    public int getMaxCountPerStack() { return 1; }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot < ListaDeRopa.LUGARES) return ListaDeRopa.entra(stack);
        if (slot < SLOT_MANO_DER) return Cosmeticos.zonaDeArmadura(stack) == slot - ARMADURA_INICIO;
        return slot < TAMANO;
    }

    @Override
    public void clear() {
        items.clear();
        markDirty();
    }

    /** Además de guardar, avisa a los clientes: la ropa se ve en el mundo, no solo en la pantalla. */
    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    // ── SidedInventory: las tolvas respetan el candado ────────────────────
    private static final int[] TODOS = java.util.stream.IntStream.range(0, TAMANO).toArray();

    @Override
    public int[] getAvailableSlots(net.minecraft.util.math.Direction side) { return TODOS; }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable net.minecraft.util.math.Direction dir) {
        return !candado.cerrado() && isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, net.minecraft.util.math.Direction dir) {
        return !candado.cerrado();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // ── pantalla ──────────────────────────────────────────────────────────
    @Override
    public Text getDisplayName() {
        return getCachedState().getBlock().getName();
    }

    @Override
    @Nullable
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new ManiquiScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return pos;
    }

    // ── persistencia y sincronización ─────────────────────────────────────
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        // Los slots vacíos no se escriben: el clear() del readNbt de abajo
        // ya los deja vacíos del lado del cliente.
        Inventories.writeNbt(nbt, items, registries);
        nbt.putBoolean("Girando", girando);
        nbt.putInt("Fila", fila);
        nbt.putString("Pose", pose.name());
        net.minecraft.nbt.NbtList lista = new net.minecraft.nbt.NbtList();
        for (float a : angulos) lista.add(net.minecraft.nbt.NbtFloat.of(a));
        nbt.put("Angulos", lista);
        nbt.putInt("Figura", figura);
        candado.guardar(nbt);
        if (emote != null) nbt.putUuid("Emote", emote);
        nbt.putInt("Busto", busto);
        nbt.putBoolean("Twirl", twirl);
        if (dueno != null) {
            net.minecraft.component.type.ProfileComponent.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, dueno)
                    .result().ifPresent(e -> nbt.put("Dueno", e));
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        items.clear();
        Inventories.readNbt(nbt, items, registries);
        girando = nbt.getBoolean("Girando");
        fila = nbt.contains("Fila") ? nbt.getInt("Fila") : Cosmeticos.FILA_DE_FABRICA;
        pose = nbt.contains("Pose") ? PoseManiqui.porNombre(nbt.getString("Pose")) : PoseManiqui.PARADO;
        float[] base = PoseManiqui.PARADO.angulos();
        net.minecraft.nbt.NbtList lista = nbt.getList("Angulos", net.minecraft.nbt.NbtElement.FLOAT_TYPE);
        for (int i = 0; i < base.length && i < lista.size(); i++) base[i] = lista.getFloat(i);
        angulos = base;
        // Los guardados de antes tenían solo "FiguraSkin" (true = tu skin).
        figura = nbt.contains("Figura") ? nbt.getInt("Figura") : (nbt.getBoolean("FiguraSkin") ? FIGURA_TU_SKIN : FIGURA_MANIQUI);
        candado.leer(nbt);
        emote = nbt.containsUuid("Emote") ? nbt.getUuid("Emote") : null;
        busto = nbt.getInt("Busto");
        twirl = nbt.getBoolean("Twirl");
        dueno = nbt.contains("Dueno")
                ? net.minecraft.component.type.ProfileComponent.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, nbt.get("Dueno"))
                        .result().orElse(null)
                : null;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
