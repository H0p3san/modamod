package com.modamod.guardarropas;

import com.modamod.item.ModamodComponents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Maletín de outfits (2026-10-21, "estilo maletín... que se diferencie del guardarropas en que no tiene vista previa solo outfits
 * swappeables y comparta almacenamiento con el guardarropas. no sirve para guardar y combinar solo para vestir"): se vincula a un
 * Guardarropas y viste con sus outfits desde cualquier lado. Shift + clic sobre el Guardarropas lo vincula; clic derecho abre la lista
 * de outfits para ponértelos; Shift + clic derecho te pone el siguiente al instante. Guardar y combinar se hace en el mueble.
 */
public class MaletinItem extends Item implements com.modamod.item.ZonasTenibles {

    public MaletinItem(Settings settings) { super(settings); }

    // ── Zonas (2026-10-23, "editable en la estacion de tintes y de apliques"): como la banda, guarda colores y patrones en los
    // mismos componentes que ella (colores_banda / patrones_banda); las cajas las dibuja render/MaletinRenderer.
    /** Cuero marrón, costuras y manija oscuras, herrajes dorados (lo que se veía con las texturas de antes). */
    public static final List<Integer> DE_FABRICA = List.of(0x7A4A2A, 0x2E1C10, 0xD4AF37);

    @Override
    public List<Integer> coloresDe(ItemStack stack) {
        List<Integer> c = stack.get(ModamodComponents.COLORES_BANDA);
        return c == null || c.size() < 3 ? DE_FABRICA : c;
    }

    @Override
    public ItemStack conColoresDe(ItemStack stack, int a, int b, int c) {
        stack.set(ModamodComponents.COLORES_BANDA, List.of(a, b, c));
        return stack;
    }

    @Override
    public List<com.modamod.item.SombreroPatron> patronesDe(ItemStack stack) {
        return com.modamod.item.BandaItem.patrones(stack);
    }

    @Override
    public ItemStack conPatronDe(ItemStack stack, int zona, com.modamod.item.SombreroPatron patron) {
        List<com.modamod.item.SombreroPatron> actuales = patronesDe(stack);
        int[] n = {actuales.get(0).ordinal(), actuales.get(1).ordinal(), actuales.get(2).ordinal()};
        n[zona] = patron.ordinal();
        if (n[0] == 0 && n[1] == 0 && n[2] == 0) stack.remove(ModamodComponents.PATRONES_BANDA);
        else stack.set(ModamodComponents.PATRONES_BANDA, List.of(n[0], n[1], n[2]));
        return stack;
    }

    @Override public String claveZona(ItemStack stack, int i) { return "modamod.maletin.zona." + (i + 1); }

    /** Cuelga de la mano derecha: los apliques se guardan en el marco del brazo derecho. */
    @Override public com.modamod.garment.Parte marco(ItemStack stack) { return com.modamod.garment.Parte.BRAZO_DER; }

    /** Abre la lista de outfits; {@code fuente} es de dónde sale el maletín (la mano o el que está apoyado). */
    static void abrir(PlayerEntity player, ItemStack maletin, int datoDeApertura, java.util.function.Supplier<ItemStack> fuente) {
        UUID coleccion = coleccionDe(maletin);
        if (coleccion == null) {
            player.sendMessage(Text.translatable("modamod.maletin.sin_vincular"), true);
            return;
        }
        player.openHandledScreen(new ExtendedScreenHandlerFactory<Integer>() {
            @Override public Integer getScreenOpeningData(ServerPlayerEntity p) { return datoDeApertura; }
            @Override public Text getDisplayName() { return maletin.getName(); }
            @Nullable
            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new MaletinScreenHandler(syncId, inv, fuente);
            }
        });
        if (player instanceof ServerPlayerEntity sp) Colecciones.enviar(sp, coleccion);
    }

    @Nullable
    public static UUID coleccionDe(ItemStack maletin) { return maletin.get(ModamodComponents.MALETIN_COLECCION); }

    /** Agachado sobre un Guardarropas: se vincula a sus outfits. Sobre cualquier otro bloque se apoya en el mundo. */
    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World mundo = ctx.getWorld();
        if (ctx.getPlayer() == null) return ActionResult.PASS;
        if (!ctx.getPlayer().isSneaking() || !(mundo.getBlockEntity(ctx.getBlockPos()) instanceof GuardarropasBlockEntity be)) {
            return MaletinBlock.apoyar(ctx);
        }
        if (!mundo.isClient) {
            ctx.getStack().set(ModamodComponents.MALETIN_COLECCION, be.coleccion());
            ctx.getPlayer().sendMessage(Text.translatable("modamod.maletin.vinculado"), true);
            mundo.playSound(null, ctx.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
        return ActionResult.success(mundo.isClient);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        UUID coleccion = coleccionDe(stack);
        if (coleccion == null) {
            player.sendMessage(Text.translatable("modamod.maletin.sin_vincular"), true);
            return TypedActionResult.fail(stack);
        }
        if (player.isSneaking()) {
            siguiente(player, stack);
        } else {
            abrir(player, stack, hand.ordinal(), () -> player.getStackInHand(hand));
        }
        return TypedActionResult.success(stack);
    }

    /** Te pone el primer outfit de la colección vinculada (el de la ronda) con un efecto de cambio de ropa. */
    public static void siguiente(PlayerEntity player, ItemStack maletin) {
        UUID coleccion = coleccionDe(maletin);
        if (coleccion == null || player.getServer() == null) return;
        var almacen = Colecciones.almacen(player.getServer(), coleccion);
        if (almacen.outfits().isEmpty()) {
            player.sendMessage(Text.translatable("modamod.maletin.vacio"), true);
            return;
        }
        ponerConEfecto(player, almacen, 0);
    }

    /** Se pone el outfit {@code idx} de la colección con humo, destellos y una campanita. */
    public static boolean ponerConEfecto(PlayerEntity player, AlmacenOutfits almacen, int idx) {
        if (idx < 0 || idx >= almacen.outfits().size()) return false;
        String nombre = almacen.outfits().get(idx).nombre();
        if (!Outfits.poner(almacen, player, idx)) return false;
        player.sendMessage(Text.translatable("modamod.maletin.puesto", nombre), true);
        if (player.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.POOF, player.getX(), player.getBodyY(0.5), player.getZ(), 24, 0.35, 0.6, 0.35, 0.03);
            sw.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getBodyY(0.6), player.getZ(), 14, 0.4, 0.7, 0.4, 0.04);
            sw.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.3f);
        }
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(coleccionDe(stack) != null ? "modamod.maletin.tooltip.vinculado" : "modamod.maletin.tooltip.sin_vincular")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("modamod.maletin.tooltip").formatted(Formatting.DARK_GRAY));
    }
}
