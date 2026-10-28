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
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Empalme de tubo (2026-10-20, "tiene que aceptar filtros también, podemos hacer tubo y empalme de tubo"): un nudo con las 6 caras.
 * Recibe por cualquiera y entrega por las demás según los filtros, con las mismas reglas que el empalme de cintas. El filtro de una
 * salida se pone con un ítem en la mano apuntando a esa cara; con la mano vacía se borra, y con Shift + mano vacía pasa a filtrar
 * por tag.
 */
public class EmpalmeTuboBlock extends BlockWithEntity {

    /**
     * Por cara, igual que las puntas del tubo (2026-10-21): abierto hacia un tubo (sin anillo, el vidrio sigue de largo), boca (aro
     * con agujero) hacia un contenedor o un extractor, y tapa cerrada si no da a nada. Nunca {@code SIN}: el empalme tiene las 6.
     */
    @SuppressWarnings("unchecked")
    public static final net.minecraft.state.property.EnumProperty<TuboBlock.Extremo>[] EXTREMO = new net.minecraft.state.property.EnumProperty[6];
    static {
        for (Direction d : Direction.values()) EXTREMO[d.getId()] = net.minecraft.state.property.EnumProperty.of("extremo_" + d.getName(),
                TuboBlock.Extremo.class, TuboBlock.Extremo.ABIERTO, TuboBlock.Extremo.TAPA, TuboBlock.Extremo.BOCA);
    }

    private static final MapCodec<EmpalmeTuboBlock> CODEC = createCodec(EmpalmeTuboBlock::new);
    /** El núcleo de 8 px y un tramo de 6 px hacia cada cara (en cruz). */
    private static final VoxelShape FORMA = VoxelShapes.union(
            Block.createCuboidShape(4, 4, 4, 12, 12, 12),
            Block.createCuboidShape(5, 5, 0, 11, 11, 16),
            Block.createCuboidShape(0, 5, 5, 16, 11, 11),
            Block.createCuboidShape(5, 0, 5, 11, 16, 11));

    public EmpalmeTuboBlock(Settings settings) {
        super(settings);
        BlockState s = getStateManager().getDefaultState();
        for (Direction d : Direction.values()) s = s.with(EXTREMO[d.getId()], TuboBlock.Extremo.TAPA);
        setDefaultState(s);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { for (var p : EXTREMO) builder.add(p); }

    private static BlockState conexiones(BlockView mundo, BlockPos pos, BlockState estado) {
        for (Direction d : Direction.values()) {
            estado = estado.with(EXTREMO[d.getId()], TuboBlock.conecta(mundo, pos, d) ? TuboBlock.Extremo.ABIERTO
                    : TuboBlock.contenedor(mundo, pos, d) || TuboBlock.extractorHacia(mundo, pos, d) ? TuboBlock.Extremo.BOCA : TuboBlock.Extremo.TAPA);
        }
        return estado;
    }

    @Override
    @Nullable
    public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext ctx) { return conexiones(ctx.getWorld(), ctx.getBlockPos(), getDefaultState()); }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) return;
        BlockState nuevo = conexiones(world, pos, state);
        if (nuevo != state) world.setBlockState(pos, nuevo, Block.NOTIFY_ALL);
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return FORMA; }

    /** La cara a la que apunta el click: el eje en el que el punto se aleja más del centro del bloque. */
    private static Direction caraApuntada(BlockPos pos, Vec3d punto) {
        double dx = punto.x - (pos.getX() + 0.5), dy = punto.y - (pos.getY() + 0.5), dz = punto.z - (pos.getZ() + 0.5);
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ax >= ay && ax >= az) return dx < 0 ? Direction.WEST : Direction.EAST;
        if (ay >= az) return dy < 0 ? Direction.DOWN : Direction.UP;
        return dz < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private static Text cara(Direction d) { return Text.translatable("modamod.empalme_tubo.cara." + d.getName()); }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Agachado con el ítem en la mano se comporta como cualquier bloque (se puede apoyar algo contra el empalme).
        if (stack.isEmpty() || player.isSneaking() || !(world.getBlockEntity(pos) instanceof EmpalmeTuboBlockEntity be)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        Direction d = caraApuntada(pos, hit.getPos());
        if (!world.isClient) {
            be.setFiltro(d, stack);
            player.sendMessage(Text.translatable("modamod.empalme.filtro.puesto", cara(d), stack.getName()), true);
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof EmpalmeTuboBlockEntity be)) return ActionResult.PASS;
        Direction d = caraApuntada(pos, hit.getPos());
        if (be.filtro(d).isEmpty()) return ActionResult.PASS;
        if (!world.isClient) {
            if (player.isSneaking()) {
                be.alternarTag(d);
                player.sendMessage(Text.translatable(be.filtroPorTag(d) ? "modamod.empalme.filtro.tag" : "modamod.empalme.filtro.exacto",
                        cara(d), be.filtro(d).getName()), true);
            } else {
                be.setFiltro(d, ItemStack.EMPTY);
                player.sendMessage(Text.translatable("modamod.empalme.filtro.borrado", cara(d)), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new EmpalmeTuboBlockEntity(pos, state); }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, CintaMod.EMPALME_TUBO_BLOCK_ENTITY, EmpalmeTuboBlockEntity::tick);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof EmpalmeTuboBlockEntity be) {
            ItemScatterer.spawn(world, pos, be);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
