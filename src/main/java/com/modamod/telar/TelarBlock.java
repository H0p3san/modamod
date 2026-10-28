package com.modamod.telar;

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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
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

/** El Telar automático: bloque direccional que dibuja GeckoLib desde el block entity (ver {@link TelarMod}). */
public class TelarBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final MapCodec<TelarBlock> CODEC = createCodec(TelarBlock::new);
    private static final VoxelShape FORMA = VoxelShapes.cuboid(0, 0, 0, 1, 1, 1);

    public TelarBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH)
                .with(com.modamod.util.LuzMaquina.LIT, false));
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, com.modamod.util.LuzMaquina.LIT);
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return FORMA; }

    @Override
    protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable net.minecraft.entity.LivingEntity placer,
                         ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        com.modamod.util.MaquinaCreativa.alColocar(world, pos, state, itemStack);
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new TelarBlockEntity(pos, state); }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, TelarMod.TELAR_BLOCK_ENTITY, TelarBlockEntity::tick);
    }

    /** Con lana o hilo en la mano, los carga (si hay lugar); con otra cosa, nada. */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Botón de la línea de producción en el frente (2026-10-08): va primero, con o sin ítem en la mano.
        if (com.modamod.util.BotonLinea.golpea(state.get(FACING), pos, hit)) {
            if (!world.isClient && world.getBlockEntity(pos) instanceof TelarBlockEntity botonLinea) {
                botonLinea.alternarLinea();
                com.modamod.util.BotonLinea.avisar(world, pos, player, botonLinea.linea());
            }
            return ItemActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof TelarBlockEntity be)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // Debajo de la tapa se abre la pantalla aunque tengas lana/hilo en la mano; arriba carga insumos.
        if (!com.modamod.util.ZonaMaquina.arriba(hit, pos, com.modamod.util.ZonaMaquina.ARRIBA_BAJA_PX)) {
            if (!world.isClient && !be.trabajando()) player.openHandledScreen(be);
            return ItemActionResult.SUCCESS;
        }
        int slot = be.isValid(TelarBlockEntity.SLOT_LANA, stack) ? TelarBlockEntity.SLOT_LANA
                : be.isValid(TelarBlockEntity.SLOT_HILO, stack) ? TelarBlockEntity.SLOT_HILO
                : be.isValid(TelarBlockEntity.SLOT_CUERO, stack) ? TelarBlockEntity.SLOT_CUERO : -1;
        if (slot < 0) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;
        ItemStack actual = be.getStack(slot);
        int caben = actual.isEmpty() ? stack.getMaxCount() : actual.getMaxCount() - actual.getCount();
        int n = Math.min(caben, stack.getCount());
        if (n <= 0) return ItemActionResult.FAIL;
        if (actual.isEmpty()) be.setStack(slot, stack.copyWithCount(n));
        else { actual.increment(n); be.setStack(slot, actual); }
        if (!player.isCreative()) stack.decrement(n);
        world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.BLOCKS, 0.8f, 1.2f);
        return ItemActionResult.SUCCESS;
    }

    /** Mano vacía: retira la prenda tejida si hay; si no, abre la pantalla. */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // Botón de la línea de producción en el frente (2026-10-08): va primero, con o sin ítem en la mano.
        if (com.modamod.util.BotonLinea.golpea(state.get(FACING), pos, hit)) {
            if (!world.isClient && world.getBlockEntity(pos) instanceof TelarBlockEntity botonLinea) {
                botonLinea.alternarLinea();
                com.modamod.util.BotonLinea.avisar(world, pos, player, botonLinea.linea());
            }
            return ActionResult.SUCCESS;
        }
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof TelarBlockEntity be)) return ActionResult.PASS;
        // Debajo de la tapa abre la pantalla; arriba queda para retirar la prenda o meter la mano en la lanzadera.
        if (!com.modamod.util.ZonaMaquina.arriba(hit, pos, com.modamod.util.ZonaMaquina.ARRIBA_BAJA_PX)) {
            player.openHandledScreen(be);
            return ActionResult.SUCCESS;
        }
        ItemStack salida = be.getStack(TelarBlockEntity.SLOT_SALIDA);
        if (!salida.isEmpty()) {
            player.getInventory().offerOrDrop(salida.copy());
            be.setStack(TelarBlockEntity.SLOT_SALIDA, ItemStack.EMPTY);
            world.playSound(null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundCategory.BLOCKS, 1.0f, 1.0f);
            return ActionResult.SUCCESS;
        }
        if (be.trabajando()) {
            // La lanzadera en marcha te engancha: daño y lentitud (2026-10-08, "las 5 maquinas... cierran y bloquean gui").
            player.damage(world.getDamageSources().generic(), 2.0f);
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS, 100, 1));
            world.playSound(null, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 1.0f, 0.8f);
            player.sendMessage(net.minecraft.text.Text.translatable("modamod.telar.aviso.procesando")
                    .formatted(net.minecraft.util.Formatting.RED), true);
            return ActionResult.SUCCESS;
        }
        player.openHandledScreen(be);
        return ActionResult.SUCCESS;
    }

    /** Cae con todo adentro, como una shulker (ver {@link com.modamod.util.DropMaquina}). */
    @Override
    protected java.util.List<ItemStack> getDroppedStacks(BlockState state,
            net.minecraft.loot.context.LootContextParameterSet.Builder builder) {
        return com.modamod.util.DropMaquina.drops(this, builder);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        com.modamod.util.DropMaquina.enCreativo(world, pos, player, this,
                !(world.getBlockEntity(pos) instanceof TelarBlockEntity be) || be.isEmpty());
        return super.onBreak(world, pos, state, player);
    }
}
