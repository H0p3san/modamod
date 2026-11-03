package com.modamod.maquina;

import com.modamod.util.BotonLinea;
import com.modamod.util.DropMaquina;
import com.modamod.util.LuzMaquina;
import com.modamod.util.MaquinaCreativa;
import com.modamod.util.ZonaMaquina;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
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

import java.util.List;

/**
 * La base de las máquinas de la línea de producción (Modeladora, Tintes, Telar, Sublimadora, Estilista): un cubo
 * entero que dibuja GeckoLib desde el block entity, mirando hacia {@link #FACING} (el panel va de frente al jugador) y
 * con la luz del LED en {@link LuzMaquina#LIT}. Resuelve lo que antes repetía cada una: la orientación al colocar, el
 * botón de la línea de producción del frente (va primero, con o sin ítem en la mano), la carga de la versión creativa
 * el drop con todo adentro y el esquema del click: alimentar, retirar, castigar o abrir. Cada máquina dice con qué se
 * alimenta ({@link #alimentar}) y cómo castiga ({@link #castigar}); la Sublimadora, con su tapa, reescribe el click entero.
 *
 * <p>Su block entity es un {@link MaquinaBlockEntity}.
 */
public abstract class MaquinaBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape FORMA = VoxelShapes.fullCube();

    protected MaquinaBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LuzMaquina.LIT, false));
    }

    /** Las máquinas con más propiedades (la tapa de la Sublimadora) llaman a {@code super} y agregan las suyas. */
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LuzMaquina.LIT);
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return FORMA;
    }

    /** GeckoLib dibuja el bloque completo desde el block entity renderer. */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    /** Una máquina creativa nueva viene cargada al colocarla (ver {@link MaquinaCreativa}). */
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        MaquinaCreativa.alColocar(world, pos, state, itemStack);
    }

    // --- Interacción ---

    /** El botón de la línea de producción (en el frente) va primero; lo demás lo decide cada máquina. */
    @Override
    protected final ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                   PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (tocarBotonLinea(state, world, pos, player, hit)) return ItemActionResult.SUCCESS;
        return usarConItem(stack, state, world, pos, player, hand, hit);
    }

    @Override
    protected final ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (tocarBotonLinea(state, world, pos, player, hit)) return ActionResult.SUCCESS;
        return usar(state, world, pos, player, hit);
    }

    private static boolean tocarBotonLinea(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!BotonLinea.golpea(state.get(FACING), pos, hit)) return false;
        if (!world.isClient && world.getBlockEntity(pos) instanceof MaquinaBlockEntity m) {
            m.alternarLinea();
            BotonLinea.avisar(world, pos, player, m.linea());
        }
        return true;
    }

    /**
     * Click derecho con algo en la mano (el botón de la línea ya se descartó): arriba (la tapa) la máquina intenta
     * alimentarse con lo que tenés; si no lo toma, o si clickeaste abajo, sigue como con la mano vacía ({@link #usar}).
     */
    protected ItemActionResult usarConItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                           PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!ZonaMaquina.arriba(hit, pos, alturaTapaPx())) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (world.getBlockEntity(pos) instanceof MaquinaBlockEntity be && alimentar(stack, be, world, pos, player)) {
            return ItemActionResult.SUCCESS;
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Click derecho que no alimenta la máquina: retira el resultado si hay; si está trabajando, castiga a quien mete la
     * mano y no abre nada; si no, abre la pantalla.
     */
    protected ActionResult usar(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof MaquinaBlockEntity be)) return ActionResult.PASS;
        if (usarPropio(be, world, pos, player)) return ActionResult.SUCCESS;
        ItemStack salida = be.getStack(be.casilleroSalida());
        if (!salida.isEmpty()) {
            player.getInventory().offerOrDrop(salida.copy());
            be.setStack(be.casilleroSalida(), ItemStack.EMPTY);
            world.playSound(null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundCategory.BLOCKS, 1.0f, 1.0f);
            return ActionResult.SUCCESS;
        }
        if (be.trabajando()) {
            castigar(world, pos, player);
            return ActionResult.SUCCESS;
        }
        if (be instanceof NamedScreenHandlerFactory pantalla) player.openHandledScreen(pantalla);
        return ActionResult.SUCCESS;
    }

    /** Hasta qué altura (px) llega el lateral: de ahí para arriba es la tapa, donde se carga. */
    protected double alturaTapaPx() {
        return 11.0;
    }

    /** Toma lo que el jugador tiene en la mano (solo servidor). Devuelve si lo tomó; si no, el click abre la pantalla. */
    protected boolean alimentar(ItemStack stack, MaquinaBlockEntity be, World world, BlockPos pos, PlayerEntity player) {
        return false;
    }

    /** Lo que le pasa a quien mete la mano mientras la máquina trabaja; cada una tiene el suyo. */
    protected abstract void castigar(World world, BlockPos pos, PlayerEntity player);

    /** Un click de la mano vacía que solo entiende esta máquina; devuelve si lo atendió. */
    protected boolean usarPropio(MaquinaBlockEntity be, World world, BlockPos pos, PlayerEntity player) {
        return false;
    }

    // --- Al romperla ---

    /** Cae con todo adentro, como una shulker (ver {@link DropMaquina}). */
    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        return DropMaquina.drops(this, builder);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        DropMaquina.enCreativo(world, pos, player, this,
                !(world.getBlockEntity(pos) instanceof Inventory inv) || inv.isEmpty());
        return super.onBreak(world, pos, state, player);
    }
}
