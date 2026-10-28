package com.modamod.estilista;

import com.modamod.estilado.EstiladoBlockEntity;
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

/** La Estilista automática: bloque direccional que dibuja GeckoLib desde el block entity (ver {@link EstilistaMod}). */
public class EstilistaBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final MapCodec<EstilistaBlock> CODEC = createCodec(EstilistaBlock::new);
    private static final VoxelShape FORMA = VoxelShapes.cuboid(0, 0, 0, 1, 1, 1);

    public EstilistaBlock(Settings settings) {
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
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new EstilistaBlockEntity(pos, state); }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, EstilistaMod.ESTILISTA_BLOCK_ENTITY, EstilistaBlockEntity::tick);
    }

    /** Con una prenda en la mano, la deja cargada (si está libre); con la mano vacía ver {@link #onUse}. */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Botón de la línea de producción en el frente (2026-10-08): va primero, con o sin ítem en la mano.
        if (com.modamod.util.BotonLinea.golpea(state.get(FACING), pos, hit)) {
            if (!world.isClient && world.getBlockEntity(pos) instanceof EstilistaBlockEntity botonLinea) {
                botonLinea.alternarLinea();
                com.modamod.util.BotonLinea.avisar(world, pos, player, botonLinea.linea());
            }
            return ItemActionResult.SUCCESS;
        }
        if (world.getBlockEntity(pos) instanceof EstilistaBlockEntity carga
                && (stack.isOf(net.minecraft.item.Items.STRING) || stack.isOf(net.minecraft.item.Items.LEATHER))) {
            // Hilo y cuero a mano (2026-10-05): van derecho a los contadores.
            if (world.isClient) return ItemActionResult.SUCCESS;
            int n = player.isCreative() ? 0 : carga.absorber(stack);
            if (player.isCreative()) { ItemStack copia = stack.copy(); n = carga.absorber(copia); }
            if (n > 0) world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.BLOCKS, 0.8f, 1.2f);
            return n > 0 ? ItemActionResult.SUCCESS : ItemActionResult.FAIL;
        }
        if (!(world.getBlockEntity(pos) instanceof EstilistaBlockEntity be) || !EstiladoBlockEntity.admite(stack)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Toda la zona de arriba (el pórtico) toma la prenda; abajo se abre la pantalla (2026-10-08).
        if (!com.modamod.util.ZonaMaquina.arriba(hit, pos, com.modamod.util.ZonaMaquina.ARRIBA_BAJA_PX)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (!be.isValid(EstilistaBlockEntity.SLOT_PRENDA, stack)) return ItemActionResult.FAIL;
        be.setStack(EstilistaBlockEntity.SLOT_PRENDA, stack.copyWithCount(1));
        if (!player.isCreative()) stack.decrement(1);
        world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), SoundCategory.BLOCKS, 0.8f, 1.0f);
        return ItemActionResult.SUCCESS;
    }

    /** Mano vacía: retira la prenda terminada si hay; si no, abre la pantalla. */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // Botón de la línea de producción en el frente (2026-10-08): va primero, con o sin ítem en la mano.
        if (com.modamod.util.BotonLinea.golpea(state.get(FACING), pos, hit)) {
            if (!world.isClient && world.getBlockEntity(pos) instanceof EstilistaBlockEntity botonLinea) {
                botonLinea.alternarLinea();
                com.modamod.util.BotonLinea.avisar(world, pos, player, botonLinea.linea());
            }
            return ActionResult.SUCCESS;
        }
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof EstilistaBlockEntity be)) return ActionResult.PASS;
        ItemStack salida = be.getStack(EstilistaBlockEntity.SLOT_SALIDA);
        if (!salida.isEmpty()) {
            player.getInventory().offerOrDrop(salida.copy());
            be.setStack(EstilistaBlockEntity.SLOT_SALIDA, ItemStack.EMPTY);
            world.playSound(null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundCategory.BLOCKS, 1.0f, 1.0f);
            return ActionResult.SUCCESS;
        }
        if (be.trabajando()) {
            // El pórtico en marcha golpea a quien meta la mano (2026-10-08, "las 5 maquinas... cierran y bloquean gui").
            player.damage(world.getDamageSources().generic(), 3.0f);
            world.playSound(null, pos, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 1.0f, 1.2f);
            player.sendMessage(net.minecraft.text.Text.translatable("modamod.estilista.aviso.procesando")
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
                !(world.getBlockEntity(pos) instanceof EstilistaBlockEntity be) || be.isEmpty());
        return super.onBreak(world, pos, state, player);
    }
}
