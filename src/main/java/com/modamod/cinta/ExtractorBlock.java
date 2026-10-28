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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
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
 * Extractor (2026-10-21, "faltaría un bloque que extraiga lateral o verticalmente hacia arriba cosas de contenedores"): pegado a un
 * contenedor, cada {@link ExtractorBlockEntity#ESPERA} ticks le saca un ítem por atrás y lo empuja hacia {@link #FACING}: a un tubo,
 * una cinta, un cofre o una máquina. Saca solo lo que el contenedor deja sacar (de las máquinas, su bandeja de salida). Tiene un
 * filtro (ítem en la mano lo pone, mano vacía lo borra, Shift + mano vacía filtra por tag) y se pausa con redstone.
 */
public class ExtractorBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;

    private static final MapCodec<ExtractorBlock> CODEC = createCodec(ExtractorBlock::new);
    private static final VoxelShape[] FORMAS = new VoxelShape[6];

    public ExtractorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.UP).with(POWERED, false));
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, POWERED); }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    /** Una caja del modelo "mirando al norte" ({@code z} = distancia desde el frente) llevada al sentido {@code f}. */
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

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Direction f = state.get(FACING);
        VoxelShape s = FORMAS[f.getId()];
        if (s == null) {
            // Placa contra el contenedor, cuerpo de rejilla y boquilla hacia adelante (igual que el modelo).
            s = VoxelShapes.union(caja(f, 2, 14, 12, 16), caja(f, 4, 12, 4, 12), caja(f, 5.5, 10.5, 0, 4));
            FORMAS[f.getId()] = s;
        }
        return s;
    }

    /** Se pega a la cara apuntada y empuja hacia afuera (agachado, al revés). */
    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction cara = ctx.getSide();
        return getDefaultState().with(FACING, ctx.shouldCancelInteraction() ? cara.getOpposite() : cara)
                .with(POWERED, ctx.getWorld().isReceivingRedstonePower(ctx.getBlockPos()));
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) return;
        boolean senal = world.isReceivingRedstonePower(pos);
        if (senal != state.get(POWERED)) world.setBlockState(pos, state.with(POWERED, senal), Block.NOTIFY_ALL);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty() || player.isSneaking() || !(world.getBlockEntity(pos) instanceof ExtractorBlockEntity be)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!world.isClient) {
            be.setFiltro(stack);
            player.sendMessage(Text.translatable("modamod.extractor.filtro.puesto", stack.getName()), true);
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof ExtractorBlockEntity be) || be.filtro().isEmpty()) return ActionResult.PASS;
        if (!world.isClient) {
            if (player.isSneaking()) {
                be.alternarTag();
                player.sendMessage(Text.translatable(be.filtroPorTag() ? "modamod.extractor.filtro.tag" : "modamod.extractor.filtro.exacto",
                        be.filtro().getName()), true);
            } else {
                be.setFiltro(ItemStack.EMPTY);
                player.sendMessage(Text.translatable("modamod.extractor.filtro.borrado"), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new ExtractorBlockEntity(pos, state); }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, CintaMod.EXTRACTOR_BLOCK_ENTITY, ExtractorBlockEntity::tick);
    }
}
