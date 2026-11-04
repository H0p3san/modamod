package com.modamod.guardarropas;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Guardarropas: un mueble que guarda outfits enteros con nombre (2026-10-21). La pantalla muestra la Ropa del propio jugador
 * (los mismos lugares que su pantalla de Ropa) y la lista de outfits de la colección del mueble ({@link OutfitsGuardados}).
 */
public class GuardarropasBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, GeoBlockEntity {

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
     * mueble al romperlo.
     */
    @Nullable private java.util.UUID coleccion;

    public java.util.UUID coleccion() {
        if (coleccion == null) {
            coleccion = java.util.UUID.randomUUID();
            markDirty();
        }
        return coleccion;
    }

    /** Solo servidor: los outfits del mundo de este mueble. */
    public AlmacenOutfits almacen() {
        return Colecciones.almacen(world.getServer(), coleccion());
    }

    @Nullable
    public String ultimoDe(java.util.UUID jugador) { return world instanceof net.minecraft.server.world.ServerWorld ? almacen().ultimos().get(jugador) : null; }

    public GuardarropasBlockEntity(BlockPos pos, BlockState state) {
        super(GuardarropasMod.GUARDARROPAS_BLOCK_ENTITY, pos, state);
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
    public void onOpen(PlayerEntity player) {
        if (world == null || world.isClient || player.isSpectator()) return;
        mirando++;
        actualizarPuerta();
    }

    /** Llamado por {@code GuardarropasScreenHandler#onClosed} — cierra la puerta cuando ya no mira nadie. */
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
    /** Ids de botón de la pantalla; los de menos de 100 que no son el candado son los de la parte de Ropa. */
    public static final int BTN_PONER_BASE = 100, BTN_BORRAR_BASE = 140;

    /** {@code grupo/slot#i}, {@code armadura#zona} o {@code cosmetico#zona}: ver {@link Outfits}. */
    public static String clave(String grupo, String slot, int i) { return Outfits.clave(grupo, slot, i); }

    public boolean guardarOutfit(PlayerEntity player, String nombre) { return Outfits.guardar(almacen(), player, nombre); }
    public boolean ponerOutfit(PlayerEntity player, int idx) { return Outfits.poner(almacen(), player, idx); }
    public boolean desarmar(PlayerEntity player, int idx) { return Outfits.desarmar(almacen(), player, idx); }

    private final com.modamod.util.Candado candado = new com.modamod.util.Candado();

    public com.modamod.util.Candado candado() { return candado; }

    public boolean alternarCandado(PlayerEntity player) {
        if (!candado.alternar(player)) return false;
        markDirty();
        return true;
    }

    /** Sin outfits en su colección (al romperlo en creativo no deja el ítem). */
    public boolean isEmpty() {
        return coleccion == null || !(world instanceof net.minecraft.server.world.ServerWorld sw) || !OutfitsGuardados.de(sw.getServer()).tiene(coleccion);
    }

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
        if (coleccion != null) nbt.putUuid("Coleccion", coleccion);
        candado.guardar(nbt);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        coleccion = nbt.containsUuid("Coleccion") ? nbt.getUuid("Coleccion") : null;
        candado.leer(nbt);
    }
}
