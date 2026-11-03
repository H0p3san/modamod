package com.modamod.tinturas;

import com.modamod.maquina.MaquinaBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Estación de Tintes. Mismo espíritu de interacción que la Mesa de
 * Modelado ({@link com.modamod.modelado.ModeladoBlock}: click derecho
 * con el ítem correcto en mano lo carga, mano vacía abre la GUI de
 * config) — desde 2026-09-21 ("que cada maquina tome su tiempo") teñir
 * ya NO es instantáneo: arranca un ciclo de
 * {@link TinturasBlockEntity#TICKS_TENIDO} y el resultado sale por
 * {@link TinturasBlockEntity#SLOT_SALIDA}, mismo mecanismo que
 * Modeladora/Sublimadora.
 */
public class TinturasBlock extends MaquinaBlock {

    private static final MapCodec<TinturasBlock> CODEC = createCodec(TinturasBlock::new);

    public TinturasBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TinturasBlockEntity(pos, state);
    }

    /**
     * Ticker — a pedido (2026-09-21, "que cada maquina tome su tiempo"):
     * antes no hacía falta ninguno (aplicaba instantáneo), ahora
     * {@link TinturasBlockEntity#tick} avanza el ciclo de teñido, mismo
     * mecanismo que Modeladora/Sublimadora.
     */
    @Override
    @Nullable
    public <T extends BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(
            World world, BlockState state, net.minecraft.block.entity.BlockEntityType<T> type) {
        return validateTicker(type, TinturasMod.TINTURAS_BLOCK_ENTITY, TinturasBlockEntity::tick);
    }

    /** Mismo mapeo que {@code SublimadoraBlock#canalDeTinte}: solo estos 4 tintes cargan el tanque. */
    private static int canalDeTinte(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.CYAN_DYE) return TinturasBlockEntity.C;
        if (item == Items.MAGENTA_DYE) return TinturasBlockEntity.M;
        if (item == Items.YELLOW_DYE) return TinturasBlockEntity.Y;
        if (item == Items.BLACK_DYE) return TinturasBlockEntity.K;
        return -1;
    }

    // Mismas claves de traducción que ya usa la Sublimadora (modamod.sublimadora.tinta.*).
    private static final String[] NOMBRE_CANAL = { "cian", "magenta", "amarillo", "negro" };

    /**
     * Click derecho con el ítem correcto: tinte CMYK carga el tanque, una
     * prenda reconocida se tiñe/estampa al toque con la fijada
     * seleccionada. El patrón YA NO se "aprende" con click derecho (v2,
     * a pedido) — va en el slot Activo de la pantalla, como el molde de
     * la Modeladora, así que no necesita rama acá.
     */
    @Override
    protected ItemActionResult usarConItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof TinturasBlockEntity be)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        int canal = canalDeTinte(stack);
        if (canal >= 0) {
            if (world.isClient) return ItemActionResult.SUCCESS;
            if (!be.cargar(canal, 1)) {
                player.sendMessage(Text.translatable("modamod.tinturas.aviso.tanque_lleno"), true);
                return ItemActionResult.FAIL;
            }
            if (!player.isCreative()) stack.decrement(1);
            world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, 0.8f, 1.0f);
            player.sendMessage(Text.translatable("modamod.tinturas.aviso.cargado",
                    Text.translatable("modamod.sublimadora.tinta." + NOMBRE_CANAL[canal]),
                    be.carga(canal), TinturasBlockEntity.CARGA_MAXIMA), true);
            return ItemActionResult.SUCCESS;
        }

        boolean esPrenda = TinturasBlockEntity.aceptaEntrada(stack);
        if (!esPrenda) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // Abajo se abre la pantalla aunque tengas la prenda en la mano (2026-10-08).
        if (!com.modamod.util.ZonaMaquina.arriba(hit, pos, com.modamod.util.ZonaMaquina.ARRIBA_PX)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;

        // 2026-09-21, "que cada maquina tome su tiempo": ya no tiñe al
        // toque — arranca el ciclo de 10s y el resultado sale por
        // SLOT_SALIDA. El stack en mano NO se toca si iniciarTenido()
        // rechaza (a diferencia del slot/hopper, que se queda con la
        // prenda cruda trabada esperando) — un intento fallido con la
        // mano no debería costarte la prenda.
        ItemStack copia = stack.copyWithCount(1);
        Text motivo = be.iniciarTenido(copia);
        if (motivo != null) {
            player.sendMessage(motivo, true);
            return ItemActionResult.FAIL;
        }
        if (!player.isCreative()) stack.decrement(1);
        world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.BLOCKS, 0.9f, 1.0f);
        return ItemActionResult.SUCCESS;
    }

    /**
     * Mano vacía: retira la salida si hay algo listo (funciona tiñendo o
     * no, para no interrumpir); si no y está TIÑENDO, mensaje + peligro
     * en vez de abrir nada (2026-09-21, "mientras la maquina funciona no
     * se puede abrir la gui ni sacar la prenda"); si no, abre la GUI de
     * config (agachado, resetea los 4 sliders a 0 en vez de abrir).
     */
    @Override
    protected ActionResult usar(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof TinturasBlockEntity be)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;

        ItemStack salida = be.getStack(TinturasBlockEntity.SLOT_SALIDA);
        if (!salida.isEmpty()) {
            player.getInventory().offerOrDrop(salida.copy());
            be.setStack(TinturasBlockEntity.SLOT_SALIDA, ItemStack.EMPTY);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 1.0f, 1.2f);
            return ActionResult.SUCCESS;
        }

        if (be.estado() == TinturasBlockEntity.Estado.TINIENDO) {
            salpicarPorInterrumpir(world, pos, player);
            return ActionResult.SUCCESS;
        }

        if (player.isSneaking()) {
            be.resetearBorrador();
            be.sonarClick();
            return ActionResult.SUCCESS;
        }

        player.openHandledScreen(be);
        return ActionResult.SUCCESS;
    }

    /**
     * Consecuencia de meter mano en la máquina mientras tiñe (2026-09-21,
     * "la estacion de tintes no se, dame una idea"): un chorro de tinte
     * químico a presión — Náusea (los vapores, mareo) en vez de daño
     * directo como las otras dos, con el sonido de líquido salpicando.
     * Distinto sabor por máquina: Modeladora corta (daño físico),
     * Sublimadora quema (fuego), acá te intoxica.
     */
    private static void salpicarPorInterrumpir(World world, BlockPos pos, PlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0));
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 1.0f, 0.8f);
        player.sendMessage(Text.translatable("modamod.tinturas.aviso.procesando")
                .formatted(Formatting.RED), true);
    }
}
