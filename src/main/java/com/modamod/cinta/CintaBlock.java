package com.modamod.cinta;

import com.modamod.util.ConSalida;
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
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

/**
 * Cinta transportadora (2026-10-04, "activemos la linea de produccion textil"
 * + "geometria de cinta transportadora... agreguemos las curvas"). Lleva UNA
 * prenda por bloque hacia adelante ({@link #FACING}) y la entrega al inventario
 * de enfrente: otra cinta, una máquina por su cara izquierda, un cofre o una
 * tolva. Acepta por atrás, por los costados y por arriba (tolvas), no por
 * adelante.
 *
 * <p>La forma se arma sola como un riel: si no la alimenta nada por atrás pero
 * sí otra cinta (o una máquina que empuja hacia acá) desde un costado, se
 * curva hacia ese lado.
 */
public class CintaBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<Forma> FORMA = EnumProperty.of("forma", Forma.class);
    /** Con señal de redstone (2026-10-05): la cinta se ve parada y no mueve nada. */
    public static final net.minecraft.state.property.BooleanProperty POWERED = Properties.POWERED;

    /**
     * Cómo entra la prenda: de frente, doblando desde el costado izquierdo/derecho, o por una rampa que entrega un
     * nivel más arriba o más abajo (2026-10-04, "que el conveyor belt conecte con un conveyor mas abajo o mas arriba").
     * Agregar valores nuevos siempre al final.
     */
    public enum Forma implements StringIdentifiable {
        RECTA("recta"), CURVA_IZQ("curva_izq"), CURVA_DER("curva_der"), RAMPA_SUBE("rampa_sube"), RAMPA_BAJA("rampa_baja");

        private final String nombre;
        Forma(String nombre) { this.nombre = nombre; }
        @Override public String asString() { return nombre; }
        public boolean esRampa() { return this == RAMPA_SUBE || this == RAMPA_BAJA; }
        public boolean esCurva() { return this == CURVA_IZQ || this == CURVA_DER; }
    }

    private static final MapCodec<CintaBlock> CODEC = createCodec(CintaBlock::new);
    private static final VoxelShape FORMA_CAJA = Block.createCuboidShape(0, 0, 0, 16, 6, 16);

    public CintaBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(FORMA, Forma.RECTA)
                .with(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMA, POWERED);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    /** Subida: cuatro escalones hacia adelante (-Z) hasta el borde de arriba del bloque. */
    private static final VoxelShape FORMA_SUBE = net.minecraft.util.shape.VoxelShapes.union(
            Block.createCuboidShape(0, 0, 12, 16, 6, 16), Block.createCuboidShape(0, 0, 8, 16, 10, 12),
            Block.createCuboidShape(0, 0, 4, 16, 14, 8), Block.createCuboidShape(0, 0, 0, 16, 16, 4));

    /** Bajada: los mismos escalones pero altos atrás (+Z) y bajos adelante. */
    private static final VoxelShape FORMA_BAJA = net.minecraft.util.shape.VoxelShapes.union(
            Block.createCuboidShape(0, 0, 0, 16, 6, 4), Block.createCuboidShape(0, 0, 4, 16, 10, 8),
            Block.createCuboidShape(0, 0, 8, 16, 14, 12), Block.createCuboidShape(0, 0, 12, 16, 16, 16));

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (!state.get(FORMA).esRampa()) return FORMA_CAJA;
        // La forma está dibujada mirando al norte: se gira como el modelo.
        VoxelShape base = state.get(FORMA) == Forma.RAMPA_SUBE ? FORMA_SUBE : FORMA_BAJA;
        return switch (state.get(FACING)) {
            case EAST -> girar(base, 1);
            case SOUTH -> girar(base, 2);
            case WEST -> girar(base, 3);
            default -> base;
        };
    }

    /** Gira una forma {@code veces} cuartos de vuelta en sentido horario (visto desde arriba), alrededor del centro. */
    private static VoxelShape girar(VoxelShape forma, int veces) {
        VoxelShape[] buf = {forma, net.minecraft.util.shape.VoxelShapes.empty()};
        for (int i = 0; i < veces; i++) {
            buf[1] = net.minecraft.util.shape.VoxelShapes.empty();
            buf[0].forEachBox((x1, y1, z1, x2, y2, z2) ->
                    buf[1] = net.minecraft.util.shape.VoxelShapes.union(buf[1],
                            net.minecraft.util.shape.VoxelShapes.cuboid(1 - z2, y1, x1, 1 - z1, y2, x2)));
            buf[0] = buf[1];
        }
        return buf[0];
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction frente = ctx.getHorizontalPlayerFacing();
        return getDefaultState().with(FACING, frente).with(FORMA, calcularForma(ctx.getWorld(), ctx.getBlockPos(), frente))
                .with(POWERED, CintaFisica.debePausar(ctx.getWorld(), ctx.getBlockPos()));
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                    WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        Forma forma = calcularForma(world, pos, state.get(FACING));
        return forma == state.get(FORMA) ? state : state.with(FORMA, forma);
    }

    /** Cambió la señal de redstone: se actualiza el estado (y con él el modelo parado/en marcha). */
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

    /** Vuelve a mirar el entorno: cambia la forma (curva, rampa) si hace falta. Lo llama la cinta cada tanto. */
    public static void recalcular(World mundo, BlockPos pos, BlockState estado) {
        Forma f = calcularForma(mundo, pos, estado.get(FACING));
        if (f != estado.get(FORMA)) mundo.setBlockState(pos, estado.with(FORMA, f), Block.NOTIFY_ALL);
    }

    /** Una cinta, un empalme o una de las máquinas: lo que puede recibir una prenda por esta cara. */
    private static boolean recibe(BlockView mundo, BlockPos pos) {
        BlockEntity be = mundo.getBlockEntity(pos);
        return be instanceof CintaBlockEntity || be instanceof EmpalmeBlockEntity || be instanceof ConSalida;
    }

    /**
     * Rampa de subida si adelante, a la misma altura, no hay nada que reciba pero sí un nivel arriba. Rampa de bajada
     * (2026-10-04, "se superpone la rampa sobre el bloque de abajo": ahora vive en el nivel de ABAJO, como espejo de
     * la de subida) si no llega nada por atrás a su altura pero sí una cinta que apunta hacia acá desde un nivel más
     * arriba. Si no, recta, salvo que no llegue nada por atrás y sí algo por un costado (curva).
     */
    private static Forma calcularForma(BlockView mundo, BlockPos pos, Direction frente) {
        BlockPos delante = pos.offset(frente);
        if (!recibe(mundo, delante) && recibe(mundo, delante.up())) return Forma.RAMPA_SUBE;
        if (!alimentaDesde(mundo, pos, frente.getOpposite(), frente) && !recibe(mundo, pos.up())) {
            BlockPos origen = pos.offset(frente.getOpposite()).up();
            BlockState arriba = mundo.getBlockState(origen);
            if (arriba.getBlock() instanceof CintaBlock && arriba.get(FACING) == frente && arriba.get(FORMA) != Forma.RAMPA_SUBE) {
                return Forma.RAMPA_BAJA;
            }
        }
        if (alimentaDesde(mundo, pos, frente.getOpposite(), frente)) return Forma.RECTA;
        if (alimentaDesde(mundo, pos, frente.rotateYCounterclockwise(), frente.rotateYClockwise())) return Forma.CURVA_IZQ;
        if (alimentaDesde(mundo, pos, frente.rotateYClockwise(), frente.rotateYCounterclockwise())) return Forma.CURVA_DER;
        return Forma.RECTA;
    }

    /** ¿El vecino de ese lado empuja hacia {@code pos}? ({@code haciaMi} = la dirección en que mira/empuja para llegar). */
    private static boolean alimentaDesde(BlockView mundo, BlockPos pos, Direction lado, Direction haciaMi) {
        BlockPos vecino = pos.offset(lado);
        BlockState estado = mundo.getBlockState(vecino);
        if (estado.getBlock() instanceof CintaBlock) {
            // Una rampa de subida a mi altura entrega un nivel más arriba, no a mí (la de bajada sí: su punta baja queda a mi altura).
            if (estado.get(FACING) == haciaMi && estado.get(FORMA) != Forma.RAMPA_SUBE) return true;
        } else if (estado.getBlock() instanceof EmpalmeBlock) {
            if (estado.get(EmpalmeBlock.FACING) == haciaMi) return true;
        } else if (mundo.getBlockEntity(vecino) instanceof ConSalida maquina && maquina.ladoSalida() == haciaMi) {
            return true;
        }
        // La rampa de subida que está un nivel más abajo sí entrega a este nivel.
        return rampaEntrega(mundo, vecino.down(), haciaMi, Forma.RAMPA_SUBE);
    }

    private static boolean rampaEntrega(BlockView mundo, BlockPos pos, Direction haciaMi, Forma forma) {
        BlockState estado = mundo.getBlockState(pos);
        return estado.getBlock() instanceof CintaBlock && estado.get(FORMA) == forma && estado.get(FACING) == haciaMi;
    }

    /** Recalcula las cintas de los 8 vecinos diagonales de nivel: las rampas dependen de lo que hay un nivel arriba/abajo. */
    private static void refrescarVecinas(World mundo, BlockPos pos) {
        for (Direction d : Direction.Type.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos p = pos.offset(d).up(dy);
                BlockState s = mundo.getBlockState(p);
                if (!(s.getBlock() instanceof CintaBlock)) continue;
                Forma f = calcularForma(mundo, p, s.get(FACING));
                if (f != s.get(FORMA)) mundo.setBlockState(p, s.with(FORMA, f), Block.NOTIFY_ALL);
            }
        }
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable net.minecraft.entity.LivingEntity placer,
                         net.minecraft.item.ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient) { refrescarVecinas(world, pos); CintaFisica.refrescarCadena(world, pos); }
    }

    /**
     * Click derecho (2026-10-05, "que se pueda sacar las cosas cliqueando en la cinta y poner cosas asi"): con la mano
     * vacía saca lo que lleva; con un ítem lo pone si está libre. Un bloque en la mano sin agacharse sigue
     * poniéndose como bloque (para poder construir al lado); agachado, pone el ítem igual.
     */
    @Override
    protected net.minecraft.util.ItemActionResult onUseWithItem(net.minecraft.item.ItemStack stack, BlockState state, World world,
            BlockPos pos, net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand,
            net.minecraft.util.hit.BlockHitResult hit) {
        if (stack.isEmpty()) return net.minecraft.util.ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!player.isSneaking() && stack.getItem() instanceof net.minecraft.item.BlockItem) {
            return net.minecraft.util.ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(world.getBlockEntity(pos) instanceof CintaBlockEntity be) || !be.carga().isEmpty()) {
            return net.minecraft.util.ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!world.isClient) {
            be.setStack(0, stack);
            if (!player.isCreative()) stack.decrement(1);
        }
        return net.minecraft.util.ItemActionResult.SUCCESS;
    }

    @Override
    protected net.minecraft.util.ActionResult onUse(BlockState state, World world, BlockPos pos,
                                                    net.minecraft.entity.player.PlayerEntity player,
                                                    net.minecraft.util.hit.BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof CintaBlockEntity be) || be.carga().isEmpty()) {
            return net.minecraft.util.ActionResult.PASS;
        }
        if (!world.isClient) player.getInventory().offerOrDrop(be.removeStack(0));
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CintaBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, CintaMod.CINTA_BLOCK_ENTITY, CintaBlockEntity::tick);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof CintaBlockEntity be) {
            ItemScatterer.spawn(world, pos, be);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
        if (!state.isOf(newState.getBlock()) && !world.isClient) refrescarVecinas(world, pos);
    }
}
