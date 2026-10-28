package com.modamod.guardarropas;

import com.modamod.item.CalientabrazosItem;
import com.modamod.item.ModamodItems;
import com.modamod.item.PantalonItem;
import com.modamod.item.PolleraItem;
import com.modamod.sublimadora.RemeraItem;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
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
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Guardarropas (2026-09-20, "quiero que el guardarropas me permita
 * combinar un croptop con un remeron largo de medias red o un pantalon
 * con una pollera y una calza"): cada categoría (remera/pantalón/medias/
 * calientabrazos) tiene {@link #POR_CATEGORIA} slots propios en vez de
 * uno solo — así entran varias prendas de la MISMA categoría a la vez
 * (croptop + remerón largo, o pantalón + pollera + calza) y de verdad
 * se pueden llevar puestas juntas, no solo previsualizar.
 *
 * <p>Esto necesitó subir {@code amount} de 1 a {@link #POR_CATEGORIA} en
 * las 4 definiciones de slot de Trinkets ({@code data/trinkets/slots/
 * torso/prenda.json} y las otras 3) — antes "torso/prenda" era un slot
 * único y mutuamente excluyente a propósito (ver el comentario viejo de
 * ese json, "dos prendas... se pisarian... mutuamente excluyentes"); a
 * pedido expreso ("hay q modificar algo en el trinkets? si hacelo") esa
 * exclusividad se levantó. El renderer ({@link com.modamod.render.GarmentFeatureRenderer#equipadas})
 * ya recorre {@code TrinketComponent#getAllEquipped()} (TODO lo puesto en
 * TODOS los slots, no solo el índice 0), así que no necesitó ningún
 * cambio para dibujar varias prendas de una misma categoría a la vez —
 * el orden entre ellas ya lo decide {@code Capa}, como con cualquier otro
 * par de prendas de distinta categoría.
 */
public class GuardarropasBlockEntity extends BlockEntity
        implements net.minecraft.inventory.SidedInventory, ExtendedScreenHandlerFactory<BlockPos>, GeoBlockEntity {

    public static final int POR_CATEGORIA = 3;

    public static final int REMERA = 0;
    public static final int PANTALON = 1;
    public static final int MEDIAS = 2;
    public static final int CALIENTABRAZOS = 3;
    /**
     * Chaquetas (2026-09-30): quinta categoría, al FINAL — sus 4 slots van
     * del 16 al 19, así los guardados de antes (0..15) no se corren.
     */
    public static final int CHAQUETA = 4;
    public static final int CATEGORIAS = 5;

    /** Slots de prenda (5 categorías x 4 capas); la armadura va después. */
    public static final int PRENDAS = CATEGORIAS * POR_CATEGORIA;

    /**
     * Columna de armadura (2026-09-30, "agreguemos una columna de slots de
     * armadura"): casco, pechera, pantalones, botas — al FINAL (20..23), así
     * los índices de las prendas no se corren. Entra en los outfits guardados
     * y "Equipar" la pone en los slots de armadura del jugador.
     */
    public static final int ARMADURA_INICIO = PRENDAS;
    public static final net.minecraft.entity.EquipmentSlot[] SLOTS_ARMADURA = {
            net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET,
    };

    /**
     * Sombrero de bruja (2026-10-05, "segunda tanda del sombrero... Maniquí/Guardarropas"): un slot más, al FINAL
     * (24), así los guardados de antes no se corren. Va en el slot de Trinkets {@code head/sombrero}.
     */
    public static final int SLOT_SOMBRERO = ARMADURA_INICIO + SLOTS_ARMADURA.length;

    /** Calzado (2026-10-08, "agreguemos un slot de calzado"): otro slot al FINAL, va en el slot de Trinkets {@code socks/calzado}. */
    public static final int SLOT_CALZADO = SLOT_SOMBRERO + 1;

    /** Delantal (2026-10-11): otro slot al FINAL, va en el slot de Trinkets {@code torso/delantal}. */
    public static final int SLOT_DELANTAL = SLOT_CALZADO + 1;

    public static final int TAMANO = SLOT_DELANTAL + 1;

    /** El inventario de Trinkets del delantal del jugador, o null si no existe. */
    @Nullable
    public static dev.emi.trinkets.api.TrinketInventory inventarioDelantal(dev.emi.trinkets.api.TrinketComponent componente) {
        var grupo = componente.getInventory().get("torso");
        return grupo == null ? null : grupo.get("delantal");
    }

    /** El inventario de Trinkets del calzado del jugador, o null si no existe. */
    @Nullable
    public static dev.emi.trinkets.api.TrinketInventory inventarioCalzado(dev.emi.trinkets.api.TrinketComponent componente) {
        var grupo = componente.getInventory().get("socks");
        return grupo == null ? null : grupo.get("calzado");
    }

    /** El inventario de Trinkets del sombrero del jugador, o null si no existe. */
    @Nullable
    public static dev.emi.trinkets.api.TrinketInventory inventarioSombrero(dev.emi.trinkets.api.TrinketComponent componente) {
        var grupo = componente.getInventory().get("head");
        return grupo == null ? null : grupo.get("sombrero");
    }

    /**
     * ¿Entra {@code stack} en el slot {@code slot}? Prendas por categoría,
     * armadura por su parte del cuerpo ({@code Equipment}: armaduras,
     * élitros, calabaza, cabezas). Compartido con el Maniquí.
     */
    public static boolean esValidoEn(int slot, ItemStack stack) {
        if (slot == SLOT_SOMBRERO) return stack.getItem() instanceof com.modamod.item.SombreroBrujaItem;
        if (slot == SLOT_CALZADO) return stack.getItem() instanceof com.modamod.item.BorcegosItem;
        if (slot == SLOT_DELANTAL) return stack.getItem() instanceof com.modamod.item.DelantalItem;
        if (slot >= ARMADURA_INICIO) {
            int i = slot - ARMADURA_INICIO;
            if (i >= SLOTS_ARMADURA.length) return false;
            net.minecraft.item.Equipment equipo = net.minecraft.item.Equipment.fromStack(stack);
            return equipo != null && equipo.getSlotType() == SLOTS_ARMADURA[i];
        }
        int categoria = categoriaDe(stack);
        // Un Top vale también en la columna de chaquetas (2026-10-07, "un solo top").
        if (categoria == REMERA && slot / POR_CATEGORIA == CHAQUETA) return true;
        return categoria >= 0 && categoria == slot / POR_CATEGORIA;
    }

    /** El slot de armadura que le toca a {@code stack}, o -1 si no es armadura. */
    public static int slotArmaduraDe(ItemStack stack) {
        if (stack.getItem() instanceof com.modamod.item.SombreroBrujaItem) return SLOT_SOMBRERO;
        if (stack.getItem() instanceof com.modamod.item.BorcegosItem) return SLOT_CALZADO;
        if (stack.getItem() instanceof com.modamod.item.DelantalItem) return SLOT_DELANTAL;
        for (int i = 0; i < SLOTS_ARMADURA.length; i++) {
            if (esValidoEn(ARMADURA_INICIO + i, stack)) return ARMADURA_INICIO + i;
        }
        return -1;
    }

    private static int base(int categoria) { return categoria * POR_CATEGORIA; }

    /**
     * Un outfit guardado con nombre (2026-10-21, "guardar con nombre el outfit del jugador con todas sus prendas sin acceso puntual a las
     * prendas sino a todo el outfit"): las piezas que llevaba el jugador, por clave ({@link #clave}): sus slots de ropa del mod, la
     * armadura real y la cosmética. Se MUEVEN al mueble al guardar y vuelven al jugador al ponérselo (nunca se copian).
     */
    public record Outfit(String nombre, java.util.LinkedHashMap<String, ItemStack> piezas) {}

    public static final int OUTFITS_MAXIMO = Outfits.MAXIMO;
    /**
     * La colección de outfits de este mueble (2026-10-21, "que comparta almacenamiento con el guardarropas"): los outfits viven en el mundo
     * ({@link OutfitsGuardados}) y se vinculan por este UUID, así un Maletín los usa desde cualquier lado. Se crea al usarlo y viaja con el
     * mueble al romperlo. {@code pendientes} son los de las versiones anteriores (guardados dentro del bloque): se pasan a la colección
     * la primera vez que el servidor la toca.
     */
    @Nullable private java.util.UUID coleccion;
    private final List<Outfit> pendientes = new ArrayList<>();
    private final java.util.Map<java.util.UUID, String> ultimosPendientes = new java.util.HashMap<>();

    public java.util.UUID coleccion() {
        if (coleccion == null) {
            coleccion = java.util.UUID.randomUUID();
            markDirty();
        }
        return coleccion;
    }

    /** Solo servidor: los outfits del mundo de este mueble (pasando antes lo que traía guardado de antes). */
    public AlmacenOutfits almacen() {
        AlmacenOutfits a = Colecciones.almacen(world.getServer(), coleccion());
        if (!pendientes.isEmpty() || !ultimosPendientes.isEmpty()) {
            a.outfits().addAll(pendientes);
            a.ultimos().putAll(ultimosPendientes);
            pendientes.clear();
            ultimosPendientes.clear();
            a.guardado();
            markDirty();
        }
        return a;
    }

    @Nullable
    public String ultimoDe(java.util.UUID jugador) { return world instanceof net.minecraft.server.world.ServerWorld ? almacen().ultimos().get(jugador) : null; }

    /** Lo viejo (la grilla y los outfits sin nombre) solo se lee para migrarlo a outfits con nombre. */
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(TAMANO, ItemStack.EMPTY);

    public GuardarropasBlockEntity(BlockPos pos, BlockState state) {
        super(GuardarropasMod.GUARDARROPAS_BLOCK_ENTITY, pos, state);
    }

    /**
     * Categoría de una prenda (REMERA/PANTALON/MEDIAS/CALIENTABRAZOS), o -1
     * si no entra en el Guardarropas. Mismo reparto que los tags de
     * Trinkets ({@code data/trinkets/tags/item/...}) — la pollera va con el
     * pantalón porque comparte su slot ({@code piernas/exterior}); antes
     * {@link #isValid} la rechazaba aunque el manual prometía "un pantalón
     * con una pollera y una calza". Compartido con el Maniquí.
     */
    public static int categoriaDe(ItemStack stack) {
        if (stack.getItem() instanceof RemeraItem) return REMERA;
        if (stack.getItem() instanceof PantalonItem || stack.getItem() instanceof PolleraItem) return PANTALON;
        if (stack.isOf(ModamodItems.SOCKS_SOLID)) return MEDIAS;
        if (stack.getItem() instanceof CalientabrazosItem) return CALIENTABRAZOS;
        return -1;
    }

    // ── puerta (modelo GeckoLib "wardrobe", 2026-09-30) ─────────────────
    private static final RawAnimation ABRIR = RawAnimation.begin()
            .thenPlay("animation.wardrobe.abrir")
            .thenLoop("animation.wardrobe.abierta");
    private static final RawAnimation CERRAR = RawAnimation.begin()
            .thenPlay("animation.wardrobe.cerrar")
            .thenLoop("animation.wardrobe.cerrada");
    private static final RawAnimation ABIERTA = RawAnimation.begin().thenLoop("animation.wardrobe.abierta");
    private static final RawAnimation CERRADA = RawAnimation.begin().thenLoop("animation.wardrobe.cerrada");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Mismo mecanismo que la tapa de {@code SublimadoraBlockEntity}: pose al cargar, transición solo en un cambio real. */
    private RawAnimation animacionPuerta = null;
    private boolean puertaAbierta = false;

    /** Cuántos jugadores tienen la pantalla abierta ahora — solo servidor, no se guarda. */
    private int mirando = 0;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "puerta", 0, state -> {
            boolean abierta = getCachedState().get(GuardarropasBlock.OPEN);
            if (animacionPuerta == null) {
                puertaAbierta = abierta;
                animacionPuerta = abierta ? ABIERTA : CERRADA;
            } else if (abierta != puertaAbierta) {
                puertaAbierta = abierta;
                animacionPuerta = abierta ? ABRIR : CERRAR;
                state.getController().forceAnimationReset();
            }
            return state.setAndContinue(animacionPuerta);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Llamado por {@code GuardarropasScreenHandler} al abrirse — abre la puerta con el primero que mira. */
    @Override
    public void onOpen(PlayerEntity player) {
        if (world == null || world.isClient || player.isSpectator()) return;
        mirando++;
        actualizarPuerta();
    }

    /** Llamado por {@code GuardarropasScreenHandler#onClosed} — cierra la puerta cuando ya no mira nadie. */
    @Override
    public void onClose(PlayerEntity player) {
        if (world == null || world.isClient || player.isSpectator()) return;
        mirando = Math.max(0, mirando - 1);
        actualizarPuerta();
    }

    private void actualizarPuerta() {
        BlockState estado = getCachedState();
        if (!estado.contains(GuardarropasBlock.OPEN)) return;
        boolean abrir = mirando > 0;
        if (estado.get(GuardarropasBlock.OPEN) == abrir) return;
        world.setBlockState(pos, estado.with(GuardarropasBlock.OPEN, abrir), 3);
        world.playSound(null, pos,
                abrir ? net.minecraft.sound.SoundEvents.BLOCK_BARREL_OPEN : net.minecraft.sound.SoundEvents.BLOCK_BARREL_CLOSE,
                net.minecraft.sound.SoundCategory.BLOCKS, 0.6f, 1.1f);
    }

    /** Solo servidor (en el cliente la lista le llega a la pantalla por {@link OutfitsPayload}). */
    public List<Outfit> outfits() { return world instanceof net.minecraft.server.world.ServerWorld ? almacen().outfits() : List.of(); }

    public static final int BTN_CANDADO = 10;
    /** Ids de botón de la pantalla: 0..7 son los interruptores cosméticos de Ropa. */
    public static final int BTN_PONER_BASE = 100, BTN_BORRAR_BASE = 140;

    /** {@code grupo/slot#i}, {@code armadura#zona} o {@code cosmetico#zona}: ver {@link Outfits}. */
    public static String clave(String grupo, String slot, int i) { return Outfits.clave(grupo, slot, i); }

    public boolean guardarOutfit(PlayerEntity player, String nombre) { return Outfits.guardar(almacen(), player, nombre); }
    public boolean ponerOutfit(PlayerEntity player, int idx) { return Outfits.poner(almacen(), player, idx); }
    public boolean desarmar(PlayerEntity player, int idx) { return Outfits.desarmar(almacen(), player, idx); }

    private void sincronizar() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    /** La clave del outfit de una posición del borrador de antes ({@code TAMANO} slots), para migrar lo guardado. */
    private static String claveVieja(int slot) {
        if (slot == SLOT_SOMBRERO) return clave("head", "sombrero", 0);
        if (slot == SLOT_CALZADO) return clave("socks", "calzado", 0);
        if (slot == SLOT_DELANTAL) return clave("torso", "delantal", 0);
        if (slot >= ARMADURA_INICIO) {
            int i = slot - ARMADURA_INICIO;
            int z = com.modamod.ropa.Cosmeticos.zonaDe(SLOTS_ARMADURA[i]);
            return "armadura#" + z;
        }
        int cat = slot / POR_CATEGORIA;
        return clave(GRUPO[cat], NOMBRE_SLOT[cat], slot % POR_CATEGORIA);
    }

    private final com.modamod.util.Candado candado = new com.modamod.util.Candado();

    public com.modamod.util.Candado candado() { return candado; }

    public boolean alternarCandado(PlayerEntity player) {
        if (!candado.alternar(player)) return false;
        markDirty();
        return true;
    }

    // ── SidedInventory: las tolvas respetan el candado ────────────────────
    @Override
    public int[] getAvailableSlots(net.minecraft.util.math.Direction side) { return new int[0]; }

    /** Las tolvas ya no meten ni sacan nada (2026-10-21): el mueble guarda outfits enteros, no prendas sueltas. */
    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable net.minecraft.util.math.Direction dir) { return false; }

    @Override
    public boolean canExtract(int slot, ItemStack stack, net.minecraft.util.math.Direction dir) { return false; }

    /** Grupo/slot de Trinkets de cada categoría, en el orden de REMERA..CALIENTABRAZOS — compartido con el Maniquí. */
    public static final String[] GRUPO = {"torso", "piernas", "socks", "arms", "torso"};
    public static final String[] NOMBRE_SLOT = {"prenda", "exterior", "pair", "armwarmer", "chaqueta"};

    /** El inventario de Trinkets de una categoría del jugador, o null si ese slot no existe (datapack sin cargar). */
    @Nullable
    public static dev.emi.trinkets.api.TrinketInventory inventarioTrinkets(dev.emi.trinkets.api.TrinketComponent componente,
                                                                             int categoria) {
        var grupoDeSlots = componente.getInventory().get(GRUPO[categoria]);
        return grupoDeSlots == null ? null : grupoDeSlots.get(NOMBRE_SLOT[categoria]);
    }

    @Override public int size() { return TAMANO; }

    @Override
    public boolean isEmpty() {
        if (!pendientes.isEmpty()) return false;
        if (coleccion != null && world instanceof net.minecraft.server.world.ServerWorld sw && OutfitsGuardados.de(sw.getServer()).tiene(coleccion)) return false;
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

    @Override public ItemStack removeStack(int slot) { return Inventories.removeStack(items, slot); }

    /** Una prenda por casillero (2026-10-08, "limitemos a 1"): se equipa tal cual en un slot de Trinkets, que admite una. */
    @Override
    public int getMaxCountPerStack() { return 1; }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > stack.getMaxCount()) stack.setCount(stack.getMaxCount());
        markDirty();
    }

    /** Las 4 capas de una misma categoría comparten el mismo chequeo de tipo — el slot solo cambia en qué capa queda. */
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return esValidoEn(slot, stack);
    }

    @Override
    public void clear() {
        items.clear();
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public Text getDisplayName() {
        return getCachedState().getBlock().getName();
    }

    @Override
    @Nullable
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new GuardarropasScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return pos;
    }

    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, items, registries);

        if (coleccion != null) nbt.putUuid("Coleccion", coleccion);
        // Lo que todavía no se pasó a la colección sigue guardado acá.
        if (!pendientes.isEmpty()) {
            NbtList lista = new NbtList();
            for (Outfit o : pendientes) {
                NbtCompound oc = new NbtCompound();
                oc.putString("Nombre", o.nombre());
                NbtList piezas = new NbtList();
                for (var e : o.piezas().entrySet()) {
                    if (e.getValue().isEmpty()) continue;   // encode() tira con un stack vacío
                    NbtCompound pc = new NbtCompound();
                    pc.putString("K", e.getKey());
                    pc.put("Item", e.getValue().encode(registries));
                    piezas.add(pc);
                }
                oc.put("Piezas", piezas);
                lista.add(oc);
            }
            nbt.put("OutfitsV2", lista);
        }
        candado.guardar(nbt);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        items.clear();
        Inventories.readNbt(nbt, items, registries);

        coleccion = nbt.containsUuid("Coleccion") ? nbt.getUuid("Coleccion") : null;
        pendientes.clear();
        ultimosPendientes.clear();
        NbtList ult = nbt.getList("Ultimos", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < ult.size(); i++) ultimosPendientes.put(ult.getCompound(i).getUuid("U"), ult.getCompound(i).getString("N"));
        if (nbt.contains("OutfitsV2")) {
            NbtList lista = nbt.getList("OutfitsV2", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < lista.size(); i++) {
                NbtCompound oc = lista.getCompound(i);
                var piezas = new java.util.LinkedHashMap<String, ItemStack>();
                NbtList pl = oc.getList("Piezas", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
                for (int j = 0; j < pl.size(); j++) {
                    ItemStack st = ItemStack.fromNbtOrEmpty(registries, pl.getCompound(j).getCompound("Item"));
                    if (!st.isEmpty()) piezas.put(pl.getCompound(j).getString("K"), st);
                }
                pendientes.add(new Outfit(oc.getString("Nombre"), piezas));
            }
        } else {
            // Migración (2026-10-21): la grilla de borrador y los outfits sin nombre de antes pasan a outfits con nombre.
            java.util.function.Function<List<ItemStack>, java.util.LinkedHashMap<String, ItemStack>> convertir = viejas -> {
                var piezas = new java.util.LinkedHashMap<String, ItemStack>();
                for (int k = 0; k < viejas.size() && k < TAMANO; k++) if (!viejas.get(k).isEmpty()) piezas.put(claveVieja(k), viejas.get(k));
                return piezas;
            };
            var borrador = convertir.apply(items);
            if (!borrador.isEmpty()) pendientes.add(new Outfit("Borrador", borrador));
            if (nbt.contains("Outfits")) {
                NbtList viejos = nbt.getList("Outfits", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < viejos.size() && pendientes.size() < OUTFITS_MAXIMO; i++) {
                    List<ItemStack> prendas = new ArrayList<>(TAMANO);
                    NbtList pn = viejos.getCompound(i).getList("Prendas", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
                    for (int j = 0; j < TAMANO; j++) prendas.add(j < pn.size() ? ItemStack.fromNbtOrEmpty(registries, pn.getCompound(j)) : ItemStack.EMPTY);
                    var piezas = convertir.apply(prendas);
                    if (!piezas.isEmpty()) pendientes.add(new Outfit("Outfit " + (i + 1), piezas));
                }
            }
            items.clear();
        }
        candado.leer(nbt);
    }
}
