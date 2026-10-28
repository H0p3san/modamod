package com.modamod.estilado;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Mesa de estilado (2026-09-30): modelo GeckoLib {@code styling_table} del
 * zip, orientable. Desde el 2026-10-01 pone apliques 3D en las prendas — ver
 * {@link EstiladoBlockEntity}.
 */
public class EstiladoBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final MapCodec<EstiladoBlock> CODEC = createCodec(EstiladoBlock::new);

    /** Cuerpo de la mesa hasta la tapa (y 10) — el atril de atrás sube a y 16 pero es finito. */
    private static final VoxelShape FORMA = Block.createCuboidShape(0, 0, 0, 16, 10.4, 16);

    public EstiladoBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
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
        builder.add(FACING);
    }

    /** Frente (cajones) en -Z como el Guardarropas → {@code getOpposite()}. */
    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
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
        return new EstiladoBlockEntity(pos, state);
    }

    /** Abre la mesa (2026-10-01): prenda, molde y retazo, y click en la vista 3D. */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof EstiladoBlockEntity be) player.openHandledScreen(be);
        return ActionResult.SUCCESS;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof EstiladoBlockEntity be) {
            net.minecraft.util.ItemScatterer.spawn(world, pos, be);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
