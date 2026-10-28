package com.modamod.maniqui;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Maniquí (2026-09-30). Modelo GeckoLib {@code mannequin} del zip: pedestal
 * de un bloque y la figura encima, que llega hasta y=27.2 — ocupa el bloque
 * de arriba sin ser un bloque doble: solo se deja poner si arriba hay lugar,
 * y la forma de selección/colisión sube hasta la cabeza (como un cerco).
 *
 * <ul>
 *   <li>click derecho con una prenda: se la pone (primera capa libre de su categoría);
 *       con una pieza de armadura, en su slot si está libre;</li>
 *   <li>click derecho con la mano vacía: abre la pantalla (16 slots, girar, intercambiar);</li>
 *   <li>agachado con la mano vacía: arranca/detiene el giro del plato.</li>
 * </ul>
 */
public class ManiquiBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final MapCodec<ManiquiBlock> CODEC = createCodec(ManiquiBlock::new);

    /**
     * Pedestal (y 0–6) + plato + figura (hasta y 27.2), medidas del
     * {@code mannequin.geo.json}. La columna de la figura es cuadrada porque
     * la figura gira con el plato: tiene que cubrirla en cualquier ángulo.
     */
    private static final VoxelShape FORMA = VoxelShapes.union(
            Block.createCuboidShape(0, 0, 0, 16, 6, 16),
            Block.createCuboidShape(3.8, 6, 3.8, 12.2, 7.6, 12.2),
            Block.createCuboidShape(4, 7.6, 4, 12, 27.2, 12));

    public ManiquiBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Frente en -Z como el Guardarropas → {@code getOpposite()}. Sin lugar arriba para la figura, no se pone. */
    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos arriba = ctx.getBlockPos().up();
        if (!ctx.getWorld().getBlockState(arriba).canReplace(ctx)) return null;
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return FORMA;
    }

    /** GeckoLib dibuja el bloque entero desde el block entity (ver trampa en CLAUDE.md). */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ManiquiBlockEntity(pos, state);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof ManiquiBlockEntity be)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (com.modamod.guardarropas.GuardarropasBlockEntity.categoriaDe(stack) < 0
                && com.modamod.guardarropas.GuardarropasBlockEntity.slotArmaduraDe(stack) < 0) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (!be.candado().puedeTocar(player)) {
            player.sendMessage(Text.translatable("modamod.candado.ajeno", be.candado().nombre()), true);
            return ItemActionResult.FAIL;
        }
        if (!be.ponerPrenda(stack)) {
            player.sendMessage(Text.translatable("modamod.maniqui.sin_lugar"), true);
            return ItemActionResult.FAIL;
        }
        if (!player.isCreative()) stack.decrement(1);
        world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.BLOCKS, 0.9f, 1.0f);
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof ManiquiBlockEntity be)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (player.isSneaking() && be.candado().puedeTocar(player)) {
            be.alternarGiro();
            world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, SoundCategory.BLOCKS, 0.6f, 1.0f);
            return ActionResult.SUCCESS;
        }
        player.openHandledScreen(be);
        return ActionResult.SUCCESS;
    }

    /** Guarda la skin de quien lo pone, para la figura "skin" (2026-09-30). */
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable net.minecraft.entity.LivingEntity placer,
                         ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && placer instanceof PlayerEntity jugador
                && world.getBlockEntity(pos) instanceof ManiquiBlockEntity be) {
            be.setDueno(jugador.getGameProfile());
            be.candado().ponerDueno(jugador);
        }
    }

    /** Con el candado cerrado, los ajenos no lo rompen (2026-10-04); creativo y operadores sí. */
    @Override
    protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof ManiquiBlockEntity be && !be.candado().puedeTocar(player)) return 0f;
        return super.calcBlockBreakingDelta(state, player, world, pos);
    }

    /** Las explosiones tampoco se lo llevan si está cerrado. */
    @Override
    protected void onExploded(BlockState state, World world, BlockPos pos, net.minecraft.world.explosion.Explosion explosion,
                              java.util.function.BiConsumer<ItemStack, BlockPos> stackMerger) {
        if (world.getBlockEntity(pos) instanceof ManiquiBlockEntity be && be.candado().cerrado()) return;
        super.onExploded(state, world, pos, explosion, stackMerger);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof ManiquiBlockEntity be) {
            ItemScatterer.spawn(world, pos, be);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
