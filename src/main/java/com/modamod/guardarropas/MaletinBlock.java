package com.modamod.guardarropas;

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
import net.minecraft.item.ItemUsageContext;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
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

import java.util.List;

/**
 * El maletín apoyado en el mundo (2026-10-23, "queria que el maletin tambien sea colocable en el mundo"): parado sobre su
 * base, con la manija arriba, mirando a quien lo puso. Guarda el ítem entero (vínculo, colores, patrones y apliques) y lo
 * devuelve igual al romperlo o al levantarlo con Shift + clic derecho; clic derecho abre los outfits como en la mano.
 */
public class MaletinBlock extends BlockWithEntity {

    public static final MapCodec<MaletinBlock> CODEC = createCodec(MaletinBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    /** El modelo ocupa x 1..15, y 1..14 y z 5..11 (px), mirando al norte. */
    private static final VoxelShape NORTE_SUR = Block.createCuboidShape(1, 0, 5, 15, 14, 11);
    private static final VoxelShape ESTE_OESTE = Block.createCuboidShape(5, 0, 1, 11, 14, 15);

    public MaletinBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    public static Settings ajustes() {
        return Settings.create().strength(0.8f).sounds(BlockSoundGroup.WOOL).nonOpaque();
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // Trampa conocida: BlockWithEntity es INVISIBLE por defecto; lo dibuja MaletinBlockRenderer.
    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return state.get(FACING).getAxis() == Direction.Axis.Z ? NORTE_SUR : ESTE_OESTE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new MaletinBlockEntity(pos, state); }

    /** Lo que hace el ítem al usarse sobre un bloque: apoya una copia del maletín en el lugar de al lado. */
    static ActionResult apoyar(ItemUsageContext ctx) {
        ItemPlacementContext lugar = new ItemPlacementContext(ctx);
        World mundo = ctx.getWorld();
        BlockPos pos = lugar.getBlockPos();
        if (!lugar.canPlace() || !mundo.getBlockState(pos.down()).isSideSolidFullSquare(mundo, pos.down(), Direction.UP)) {
            return ActionResult.PASS;
        }
        if (!mundo.isClient) {
            BlockState estado = GuardarropasMod.MALETIN_BLOCK.getDefaultState().with(FACING, lugar.getHorizontalPlayerFacing().getOpposite());
            mundo.setBlockState(pos, estado);
            if (mundo.getBlockEntity(pos) instanceof MaletinBlockEntity be) be.poner(ctx.getStack().copyWithCount(1));
            mundo.playSound(null, pos, BlockSoundGroup.WOOL.getPlaceSound(), SoundCategory.BLOCKS, 1f, 0.9f);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().creativeMode) ctx.getStack().decrement(1);
        }
        return ActionResult.success(mundo.isClient);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof MaletinBlockEntity be) || be.maletin().isEmpty()) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (player.isSneaking()) {
            // Levantarlo: a la mano si está libre, si no al inventario (o al piso).
            ItemStack stack = be.maletin().copy();
            world.removeBlock(pos, false);
            if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
            return ActionResult.CONSUME;
        }
        MaletinItem.abrir(player, be.maletin(), -1, () -> player.squaredDistanceTo(pos.toCenterPos()) <= 64
                && world.getBlockEntity(pos) instanceof MaletinBlockEntity b ? b.maletin() : ItemStack.EMPTY);
        return ActionResult.CONSUME;
    }

    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        BlockEntity be = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
        return be instanceof MaletinBlockEntity m && !m.maletin().isEmpty() ? List.of(m.maletin().copy()) : List.of();
    }
}
