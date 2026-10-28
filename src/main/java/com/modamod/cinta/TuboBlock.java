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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Tubo de transporte (2026-10-20, "quiero hacer una cinta vertical... tubos de transporte"; 2026-10-21, "donde conecten con otro tubo
 * no haya borde, sea todo tubo de vidrio" + "que conecten con los costados" + "con agua se llenen y suban y sin agua bajen"): un caño
 * sin sentido propio. Se conecta solo con los tubos y empalmes que tiene al lado (los brazos de vidrio), y el ítem sigue por el lado
 * opuesto al que entró, dobla en los codos y, en los tramos verticales, sube si el tubo tiene agua y baja si no.
 * El agua es un estado del bloque (sin fluido real, así no se derrama): se pone con un balde y llena toda la columna conectada.
 */
public class TuboBlock extends BlockWithEntity {

    /**
     * Qué hay en cada cara (2026-10-21, "que las tapas tengan una abertura visible donde largan cosas o toman cosas y conecten
     * visualmente con el extractor"): sin brazo; brazo que sigue en otro tubo (abierto, sin borde); brazo con tapa de cobre cerrada
     * (punta libre); o brazo con boca (aro de cobre con agujero) donde da a un contenedor o a un extractor.
     */
    public enum Extremo implements net.minecraft.util.StringIdentifiable {
        SIN("sin"), ABIERTO("abierto"), TAPA("tapa"), BOCA("boca");
        private final String nombre;
        Extremo(String nombre) { this.nombre = nombre; }
        @Override public String asString() { return nombre; }
    }

    /** Indexados por {@link Direction#getId()}. */
    @SuppressWarnings("unchecked")
    public static final net.minecraft.state.property.EnumProperty<Extremo>[] EXTREMO = new net.minecraft.state.property.EnumProperty[6];
    public static final BooleanProperty AGUA = BooleanProperty.of("agua");
    /** Codo o nudo: el centro lleva las aristas recubiertas del cobre de las tapas (2026-10-21, "que las esquinas estén parcialmente recubiertas"). */
    public static final BooleanProperty ESQUINA = BooleanProperty.of("esquina");
    static {
        for (Direction d : Direction.values()) EXTREMO[d.getId()] = net.minecraft.state.property.EnumProperty.of("extremo_" + d.getName(), Extremo.class);
    }

    private static boolean brazo(BlockState s, Direction d) { return s.get(EXTREMO[d.getId()]) != Extremo.SIN; }

    private static final MapCodec<TuboBlock> CODEC = createCodec(TuboBlock::new);
    private static final VoxelShape[] FORMAS = new VoxelShape[64];
    private static final VoxelShape NUCLEO = Block.createCuboidShape(5, 5, 5, 11, 11, 11);

    public TuboBlock(Settings settings) {
        super(settings);
        BlockState s = getStateManager().getDefaultState().with(AGUA, false).with(ESQUINA, false);
        for (Direction d : Direction.values()) s = s.with(EXTREMO[d.getId()], Extremo.SIN);
        setDefaultState(s);
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AGUA, ESQUINA);
        for (Direction d : Direction.values()) builder.add(EXTREMO[d.getId()]);
    }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        int mascara = 0;
        for (Direction d : Direction.values()) if (brazo(state, d)) mascara |= 1 << d.getId();
        VoxelShape f = FORMAS[mascara];
        if (f == null) {
            f = NUCLEO;
            for (Direction d : Direction.values()) {
                if ((mascara & (1 << d.getId())) == 0) continue;
                double x0 = 5, y0 = 5, z0 = 5, x1 = 11, y1 = 11, z1 = 11;
                switch (d) {
                    case DOWN -> y0 = 0; case UP -> y1 = 16; case NORTH -> z0 = 0; case SOUTH -> z1 = 16; case WEST -> x0 = 0; case EAST -> x1 = 16;
                }
                f = VoxelShapes.union(f, Block.createCuboidShape(x0, y0, z0, x1, y1, z1));
            }
            FORMAS[mascara] = f;
        }
        return f;
    }

    /**
     * ¿Hay un tubo, un empalme de tubo o una válvula a lo largo de su eje en esa cara? (con quien se conecta el vidrio y por donde puede
     * seguir el ítem). No, si alguno de los dos cerró esa cara a mano.
     */
    public static boolean conecta(BlockView mundo, BlockPos pos, Direction d) {
        BlockPos v = pos.offset(d);
        BlockState s = mundo.getBlockState(v);
        Block b = s.getBlock();
        boolean tipo = b instanceof TuboBlock || b instanceof EmpalmeTuboBlock
                || b instanceof ValvulaBlock && s.get(ValvulaBlock.FACING).getAxis() == d.getAxis();
        return tipo && !cerrada(mundo, pos, d) && !cerrada(mundo, v, d.getOpposite());
    }

    /** ¿El tubo de {@code pos} tiene esa cara cerrada a mano? */
    public static boolean cerrada(BlockView mundo, BlockPos pos, Direction d) {
        return mundo.getBlockEntity(pos) instanceof TuboBlockEntity be && be.cerrada(d);
    }

    /** ¿En esa cara hay una válvula que apunta hacia acá? (por su frente no se le puede meter nada). */
    public static boolean valvulaContra(BlockView mundo, BlockPos pos, Direction d) {
        BlockState s = mundo.getBlockState(pos.offset(d));
        return s.getBlock() instanceof ValvulaBlock && s.get(ValvulaBlock.FACING) == d.getOpposite();
    }

    /**
     * Cuánto (en bloques) hay que alargar la boca de esa cara para tocar al contenedor de al lado (2026-10-21, "el tubo transportador tiene
     * que conectar físicamente con un contenedor al que le deposita, si no queda raro"): la distancia desde el borde del bloque hasta la
     * cara más cercana de la forma del vecino (0 en un cofre grande o una máquina; unos 5/16 en una tolva; casi todo el bloque sobre una
     * cinta). Solo visual: el manguito lo dibuja {@code TuboRenderer}.
     */
    public static float huecoHacia(BlockView mundo, BlockPos pos, Direction d) {
        BlockPos vecino = pos.offset(d);
        VoxelShape forma = mundo.getBlockState(vecino).getOutlineShape(mundo, vecino);
        if (forma.isEmpty()) return 0f;
        Direction.Axis eje = d.getAxis();
        double g = d.getDirection() == Direction.AxisDirection.POSITIVE ? forma.getMin(eje) : 1.0 - forma.getMax(eje);
        return (float) Math.max(0.0, Math.min(0.75, g));
    }

    /** ¿Hay un contenedor (cofre, tolva, cinta, máquina) en esa cara? */
    public static boolean contenedor(BlockView mundo, BlockPos pos, Direction d) {
        return mundo.getBlockEntity(pos.offset(d)) instanceof net.minecraft.inventory.Inventory;
    }

    /** ¿Hay un extractor que empuja hacia este tubo por esa cara? (abre brazo y boca, pero no es un camino para el ítem). */
    public static boolean extractorHacia(BlockView mundo, BlockPos pos, Direction d) {
        BlockState v = mundo.getBlockState(pos.offset(d));
        return v.getBlock() instanceof ExtractorBlock && v.get(ExtractorBlock.FACING) == d.getOpposite();
    }

    /**
     * ¿Hay una cinta que entrega hacia este tubo por esa cara? (2026-10-21, "los tubos que reciben de cintas transportadoras no
     * conectan"): abre brazo y boca como un extractor, aunque el tubo sea un tramo que sigue de largo.
     */
    public static boolean cintaHacia(BlockView mundo, BlockPos pos, Direction d) {
        BlockState v = mundo.getBlockState(pos.offset(d));
        return d.getAxis().isHorizontal() && v.getBlock() instanceof CintaBlock && v.get(CintaBlock.FACING) == d.getOpposite();
    }

    /**
     * Los brazos y sus puntas según los vecinos: con uno solo, el brazo se completa hacia el lado opuesto (un tramo recto); con dos,
     * recto o codo; con más, nudo. Sin vecinos se queda como estaba (el eje con que se puso). Un extractor que empuja hacia el tubo
     * también abre brazo. La punta es abierta hacia otro tubo, boca hacia un contenedor o un extractor, y tapa si no da a nada.
     * Y si arriba o abajo hay un tubo con agua, este también la tiene.
     */
    public static BlockState recalcular(BlockView mundo, BlockPos pos, BlockState estado) {
        boolean[] c = new boolean[6], ex = new boolean[6], brazo = new boolean[6], cer = new boolean[6];
        int n = 0;
        for (Direction d : Direction.values()) {
            int i = d.getId();
            cer[i] = cerrada(mundo, pos, d);
            c[i] = conecta(mundo, pos, d);
            ex[i] = !cer[i] && (extractorHacia(mundo, pos, d) || cintaHacia(mundo, pos, d));
            if (c[i] || ex[i]) n++;
        }
        for (Direction d : Direction.values()) {
            int i = d.getId();
            brazo[i] = n == 0 ? brazo(estado, d) : c[i] || ex[i];
            if (n == 1 && (c[i] || ex[i])) brazo[d.getOpposite().getId()] = true;
        }
        // Cerrada a mano: brazo con tapa de cobre, aunque al lado haya un tubo o un contenedor.
        for (int i = 0; i < 6; i++) if (cer[i]) brazo[i] = true;
        BlockState s = estado;
        int brazos = 0;
        boolean recto = false;
        for (Direction d : Direction.values()) {
            int i = d.getId();
            Extremo e = !brazo[i] ? Extremo.SIN : cer[i] ? Extremo.TAPA : c[i] ? Extremo.ABIERTO
                    : ex[i] || contenedor(mundo, pos, d) ? Extremo.BOCA : Extremo.TAPA;
            s = s.with(EXTREMO[i], e);
            if (brazo[i]) { brazos++; if (brazo[d.getOpposite().getId()]) recto = true; }
        }
        s = s.with(ESQUINA, brazos >= 3 || (brazos == 2 && !recto));
        for (Direction d : new Direction[]{Direction.UP, Direction.DOWN}) {
            BlockState v = mundo.getBlockState(pos.offset(d));
            if (v.getBlock() instanceof TuboBlock && v.get(AGUA)) s = s.with(AGUA, true);
        }
        return s;
    }

    /** Se pone a lo largo de la cara apuntada (agachado, a lo largo de la perpendicular al piso no cambia nada: el eje es el mismo). */
    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction cara = ctx.getSide();
        BlockState s = getDefaultState().with(EXTREMO[cara.getId()], Extremo.TAPA).with(EXTREMO[cara.getOpposite().getId()], Extremo.TAPA);
        return recalcular(ctx.getWorld(), ctx.getBlockPos(), s);
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) return;
        BlockState nuevo = recalcular(world, pos, state);
        if (nuevo != state) world.setBlockState(pos, nuevo, Block.NOTIFY_ALL);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient) neighborUpdate(state, world, pos, this, pos, false);
    }

    /** Pone o saca el agua de toda la columna de tubos conectada en vertical, arriba y abajo de {@code desde}. */
    public static void llenar(World mundo, BlockPos desde, boolean agua) {
        java.util.ArrayDeque<BlockPos> cola = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> visto = new java.util.HashSet<>();
        cola.add(desde);
        visto.add(desde);
        while (!cola.isEmpty()) {
            BlockPos p = cola.poll();
            BlockState s = mundo.getBlockState(p);
            boolean tubo = s.getBlock() instanceof TuboBlock;
            // Una válvula vertical no corta la columna: el agua sigue del otro lado.
            if (!tubo && !(s.getBlock() instanceof ValvulaBlock && s.get(ValvulaBlock.FACING).getAxis() == Direction.Axis.Y)) continue;
            if (tubo && s.get(AGUA) != agua) mundo.setBlockState(p, s.with(AGUA, agua), Block.NOTIFY_ALL);
            for (Direction d : new Direction[]{Direction.UP, Direction.DOWN}) {
                BlockPos q = p.offset(d);
                if (visto.add(q)) cola.add(q);
            }
        }
    }

    /** Balde de agua: llena la columna; balde vacío: la vacía. */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        boolean llenar = stack.isOf(Items.WATER_BUCKET) && !state.get(AGUA);
        boolean vaciar = stack.isOf(Items.BUCKET) && state.get(AGUA);
        if (!llenar && !vaciar) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!world.isClient) {
            llenar(world, pos, llenar);
            world.playSound(null, pos, llenar ? SoundEvents.ITEM_BUCKET_EMPTY : SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1f, 1f);
            if (!player.isCreative()) {
                player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(llenar ? Items.BUCKET : Items.WATER_BUCKET)));
            }
        }
        return ItemActionResult.SUCCESS;
    }

    /**
     * Shift + clic derecho con la mano vacía cierra o abre la cara apuntada (2026-10-21, "no me cierra la dirección de los tubos, por ahí se
     * me escapan ítems para arriba por una juntura"): una cara cerrada lleva tapa de cobre y no se conecta, no recibe ni entrega nada.
     */
    @Override
    protected net.minecraft.util.ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.isSneaking() || !(world.getBlockEntity(pos) instanceof TuboBlockEntity be)) return net.minecraft.util.ActionResult.PASS;
        if (!world.isClient) {
            // La cara del brazo apuntado: el eje en que el punto tocado más se aleja del centro.
            net.minecraft.util.math.Vec3d v = hit.getPos().subtract(net.minecraft.util.math.Vec3d.ofCenter(pos));
            double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
            Direction d = ax >= ay && ax >= az ? (v.x > 0 ? Direction.EAST : Direction.WEST)
                    : ay >= az ? (v.y > 0 ? Direction.UP : Direction.DOWN) : (v.z > 0 ? Direction.SOUTH : Direction.NORTH);
            boolean cerrada = be.alternar(d);
            world.setBlockState(pos, recalcular(world, pos, state), Block.NOTIFY_ALL);
            world.updateNeighborsAlways(pos, this);
            world.playSound(null, pos, cerrada ? SoundEvents.BLOCK_COPPER_TRAPDOOR_CLOSE : SoundEvents.BLOCK_COPPER_TRAPDOOR_OPEN,
                    SoundCategory.BLOCKS, 1f, 1f);
            player.sendMessage(net.minecraft.text.Text.translatable(cerrada ? "modamod.tubo.cara.cerrada" : "modamod.tubo.cara.abierta",
                    net.minecraft.text.Text.translatable("modamod.direccion." + d.getName())), true);
        }
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    /** Burbujitas subiendo por el tubo con agua. */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(AGUA) || random.nextInt(3) != 0) return;
        world.addParticle(ParticleTypes.BUBBLE, pos.getX() + 0.35 + random.nextDouble() * 0.3, pos.getY() + 0.3 + random.nextDouble() * 0.4,
                pos.getZ() + 0.35 + random.nextDouble() * 0.3, 0, 0.06, 0);
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new TuboBlockEntity(pos, state); }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, CintaMod.TUBO_BLOCK_ENTITY, TuboBlockEntity::tick);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof TuboBlockEntity be) {
            ItemScatterer.spawn(world, pos, be);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
