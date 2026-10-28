package com.modamod.cinta;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Válvula antirretorno (2026-10-21, "no me cierra la dirección de los tubos, por ahí se me escapan ítems para arriba por una juntura
 * cuando quiero que bajen"): un tramo de tubo con sentido fijo. Recibe solo por atrás (la boca ancha) y entrega solo hacia
 * {@link #FACING} (el pico), con o sin agua. Se conecta con tubos y empalmes únicamente a lo largo de su eje.
 */
public class ValvulaBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.FACING;

    private static final MapCodec<ValvulaBlock> CODEC = createCodec(ValvulaBlock::new);
    private static final VoxelShape[] FORMAS = new VoxelShape[6];

    public ValvulaBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.DOWN));
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Direction f = state.get(FACING);
        VoxelShape s = FORMAS[f.getId()];
        if (s == null) {
            // Embudo como el modelo: ancho atrás, angosto hacia el pico.
            s = VoxelShapes.union(caja(f, 4, 12, 14, 16), caja(f, 3, 13, 8, 14), caja(f, 4, 12, 4, 8), caja(f, 5, 11, 0, 4));
            FORMAS[f.getId()] = s;
        }
        return s;
    }

    /** Una caja del modelo "mirando al norte" ({@code z} = distancia desde el pico) llevada al sentido {@code f}. */
    private static VoxelShape caja(Direction f, double lo, double hi, double z0, double z1) {
        return switch (f) {
            case NORTH -> Block.createCuboidShape(lo, lo, z0, hi, hi, z1);
            case SOUTH -> Block.createCuboidShape(lo, lo, 16 - z1, hi, hi, 16 - z0);
            case WEST -> Block.createCuboidShape(z0, lo, lo, z1, hi, hi);
            case EAST -> Block.createCuboidShape(16 - z1, lo, lo, 16 - z0, hi, hi);
            case UP -> Block.createCuboidShape(lo, 16 - z1, lo, hi, 16 - z0, hi);
            case DOWN -> Block.createCuboidShape(lo, z0, lo, hi, z1, hi);
        };
    }

    /** El pico apunta hacia donde mirás al ponerla (agachado, al revés). */
    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction mira = ctx.getPlayerLookDirection();
        return getDefaultState().with(FACING, ctx.shouldCancelInteraction() ? mira.getOpposite() : mira);
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new ValvulaBlockEntity(pos, state); }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, CintaMod.VALVULA_BLOCK_ENTITY, ValvulaBlockEntity::tick);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof ValvulaBlockEntity be) {
            ItemScatterer.spawn(world, pos, be);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
