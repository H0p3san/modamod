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
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Empalme de cintas (2026-10-05, "un empalme que una hasta tres entradas laterales de conveyor belts y una superior
 * de tolva y una salida"): recibe por atrás, por los dos costados y por arriba (tolva) y entrega por el frente
 * ({@link #FACING}). Rotativo: alterna entre las entradas que tienen algo esperando.
 */
public class EmpalmeBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;

    private static final MapCodec<EmpalmeBlock> CODEC = createCodec(EmpalmeBlock::new);
    /**
     * Con la estructura de los filtros encima (2026-10-19; elevada el 2026-10-20, "elevame la plataforma con palitos soporte en las
     * esquinas para que se despegue de la cinta y pueda ver si hay un item atorado"): cuatro palitos en las esquinas suben la
     * plataforma a 11-13 px, y por debajo queda libre el paso del item para verlo.
     */
    private static final VoxelShape FORMA = net.minecraft.util.shape.VoxelShapes.union(
            Block.createCuboidShape(0, 0, 0, 16, 6, 16),
            // Los palitos se apoyan en las esquinitas de madera del modelo (2x2 px, hasta 5,5 de alto).
            Block.createCuboidShape(1, 5.5, 1, 2, 11, 2), Block.createCuboidShape(14, 5.5, 1, 15, 11, 2),
            Block.createCuboidShape(1, 5.5, 14, 2, 11, 15), Block.createCuboidShape(14, 5.5, 14, 15, 11, 15),
            Block.createCuboidShape(1, 11, 1, 15, 13, 15));
    /** Altura (en bloques) desde la que un click apunta a la plataforma y no a la base. */
    public static final float ALTO_BASE = 11f / 16f;

    /**
     * El casillero de filtro al que apunta el click, o -1 si no apunta a la estructura de arriba (la base de 6 px conserva
     * su comportamiento). El del medio es la tolva; alrededor, el costado hacia el que cae el punto.
     */
    public static int casilleroApuntado(EmpalmeBlockEntity be, BlockPos pos, net.minecraft.util.math.Vec3d punto) {
        if (punto.y - pos.getY() < ALTO_BASE - 0.001) return -1;
        double dx = punto.x - (pos.getX() + 0.5), dz = punto.z - (pos.getZ() + 0.5);
        if (Math.abs(dx) < 2.2 / 16.0 && Math.abs(dz) < 2.2 / 16.0) return EmpalmeBlockEntity.TOLVA;
        Direction cara = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? Direction.NORTH : Direction.SOUTH) : (dx < 0 ? Direction.WEST : Direction.EAST);
        return be.casilleroDe(cara);
    }

    public EmpalmeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(POWERED, false));
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, POWERED); }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return FORMA; }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing())
                .with(POWERED, CintaFisica.debePausar(ctx.getWorld(), ctx.getBlockPos()));
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) return;
        // La señal se contagia a las cintas encadenadas hasta 8 de distancia (2026-10-06).
        CintaFisica.refrescarCadena(world, pos);
    }

    /** Quien se sube es llevado (2026-10-06, "si el jugador se sube a un conveyor belt lo mueva"). */
    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, net.minecraft.entity.Entity entity) {
        CintaFisica.empujar(world, pos, state, entity);
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable net.minecraft.entity.LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient) CintaFisica.refrescarCadena(world, pos);
    }

    /** Click derecho: saca lo que lleva o pone un ítem (igual que la cinta; un bloque en la mano sin agacharse se coloca). */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.getBlockEntity(pos) instanceof EmpalmeBlockEntity filtros) {
            int c = casilleroApuntado(filtros, pos, hit.getPos());
            if (c >= 0) {
                if (!world.isClient) {
                    filtros.setFiltro(c, stack);
                    player.sendMessage(net.minecraft.text.Text.translatable("modamod.empalme.filtro.puesto",
                            net.minecraft.text.Text.translatable("modamod.empalme.casillero." + c), stack.getName()), true);
                }
                return ItemActionResult.SUCCESS;
            }
        }
        if (!player.isSneaking() && stack.getItem() instanceof BlockItem) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(world.getBlockEntity(pos) instanceof EmpalmeBlockEntity be) || !be.carga().isEmpty()) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!world.isClient) {
            be.setStack(0, stack);
            if (!player.isCreative()) stack.decrement(1);
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.getBlockEntity(pos) instanceof EmpalmeBlockEntity filtros) {
            int c = casilleroApuntado(filtros, pos, hit.getPos());
            if (c >= 0) {
                if (filtros.filtro(c).isEmpty()) return ActionResult.CONSUME;
                if (!world.isClient) {
                    // Shift + click: marco y filtro por tag; click solo: borra el filtro.
                    if (player.isSneaking()) {
                        filtros.alternarTag(c);
                        player.sendMessage(net.minecraft.text.Text.translatable(filtros.filtroPorTag(c) ? "modamod.empalme.filtro.tag" : "modamod.empalme.filtro.exacto",
                                net.minecraft.text.Text.translatable("modamod.empalme.casillero." + c), filtros.filtro(c).getName()), true);
                    } else {
                        filtros.setFiltro(c, ItemStack.EMPTY);
                        player.sendMessage(net.minecraft.text.Text.translatable("modamod.empalme.filtro.borrado",
                                net.minecraft.text.Text.translatable("modamod.empalme.casillero." + c)), true);
                    }
                }
                return ActionResult.SUCCESS;
            }
        }
        if (!(world.getBlockEntity(pos) instanceof EmpalmeBlockEntity be) || be.carga().isEmpty()) return ActionResult.PASS;
        if (!world.isClient) player.getInventory().offerOrDrop(be.removeStack(0));
        return ActionResult.SUCCESS;
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new EmpalmeBlockEntity(pos, state); }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, CintaMod.EMPALME_BLOCK_ENTITY, EmpalmeBlockEntity::tick);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof EmpalmeBlockEntity be) {
            ItemScatterer.spawn(world, pos, be);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
