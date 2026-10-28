package com.modamod.tinturas;

import com.modamod.item.ModamodDye;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
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
public class TinturasBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final MapCodec<TinturasBlock> CODEC = createCodec(TinturasBlock::new);
    private static final VoxelShape FORMA = VoxelShapes.cuboid(0, 0, 0, 1, 1, 1);

    public TinturasBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH)
                .with(com.modamod.util.LuzMaquina.LIT, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    /** Máquina creativa (2026-10-01): viene cargada al colocarla — ver {@code util.MaquinaCreativa}. */
    @Override
    public void onPlaced(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, BlockState state,
                         @org.jetbrains.annotations.Nullable net.minecraft.entity.LivingEntity placer,
                         net.minecraft.item.ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        com.modamod.util.MaquinaCreativa.alColocar(world, pos, state, itemStack);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, com.modamod.util.LuzMaquina.LIT);
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Como las demás máquinas (2026-10-15, "habria que resolver la inversion"): el modelo se giró 180° en el .geo.json y
        // ahora lleva el panel en -Z, la convención de GeckoLib; antes se colocaba SIN getOpposite() para compensar. Las
        // Tintes ya colocadas quedan dadas vuelta: hay que volver a ponerlas.
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return FORMA;
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TinturasBlockEntity(pos, state);
    }

    /** GeckoLib dibuja el bloque completo desde el block entity renderer (modelo dye_station). */
    /** Cae con todo adentro, como una shulker (ver {@link com.modamod.util.DropMaquina}). */
    @Override
    protected java.util.List<ItemStack> getDroppedStacks(BlockState state,
            net.minecraft.loot.context.LootContextParameterSet.Builder builder) {
        return com.modamod.util.DropMaquina.drops(this, builder);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        com.modamod.util.DropMaquina.enCreativo(world, pos, player, this,
                !(world.getBlockEntity(pos) instanceof TinturasBlockEntity be) || be.isEmpty());
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
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
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Botón de la línea de producción (2026-10-08): en la cara del panel, o sea FACING (2026-10-15: el modelo ya no está invertido).
        if (com.modamod.util.BotonLinea.golpea(state.get(FACING), pos, hit)) {
            if (!world.isClient && world.getBlockEntity(pos) instanceof TinturasBlockEntity botonLinea) {
                botonLinea.alternarLinea();
                com.modamod.util.BotonLinea.avisar(world, pos, player, botonLinea.linea());
            }
            return ItemActionResult.SUCCESS;
        }
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
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // Botón de la línea de producción (2026-10-08): en la cara del panel, o sea FACING (2026-10-15: el modelo ya no está invertido).
        if (com.modamod.util.BotonLinea.golpea(state.get(FACING), pos, hit)) {
            if (!world.isClient && world.getBlockEntity(pos) instanceof TinturasBlockEntity botonLinea) {
                botonLinea.alternarLinea();
                com.modamod.util.BotonLinea.avisar(world, pos, player, botonLinea.linea());
            }
            return ActionResult.SUCCESS;
        }
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
