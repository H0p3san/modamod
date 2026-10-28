package com.modamod.modelado;

import com.modamod.item.ModamodDye;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ItemActionResult;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

/**
 * Mesa de Modelado: bloque simple (sin GeckoLib), direccional. Click derecho
 * con una prenda en mano aplica el molde de corte activo instantáneamente;
 * mano vacía abre la GUI de armado. Ver docs/archivo/MAQUINAS.md sección 3.
 */
public class ModeladoBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final MapCodec<ModeladoBlock> CODEC = createCodec(ModeladoBlock::new);
    private static final VoxelShape FORMA = VoxelShapes.cuboid(0, 0, 0, 1, 1, 1);

    public ModeladoBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH)
                .with(com.modamod.util.LuzMaquina.LIT, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    /** Máquina creativa (2026-10-01): viene cargada al colocarla — ver {@code util.MaquinaCreativa}. */
    @Override
    public void onPlaced(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, BlockState state,
                         @org.jetbrains.annotations.Nullable net.minecraft.entity.LivingEntity placer,
                         net.minecraft.item.ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        com.modamod.util.MaquinaCreativa.alColocar(world, pos, state, itemStack);
    }

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
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return FORMA;
    }

    /** GeckoLib dibuja el bloque completo desde el block entity renderer. */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ModeladoBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(
            World world, BlockState state, net.minecraft.block.entity.BlockEntityType<T> type) {
        return validateTicker(type, ModeladoMod.MODELADO_BLOCK_ENTITY, ModeladoBlockEntity::tick);
    }

    /**
     * "Algunos recursos pueden cargarse rápidamente mediante click secundario
     * sobre la máquina" (docs/archivo/PRODUCCION_TEXTIL.md §2): con una prenda en
     * mano, la coloca en el slot físico (funciona prendida o apagada — la
     * línea de producción también entra por acá, ver docs/archivo/MAQUINAS.md §6,
     * el costado de entrada todavía no está cableado a esto); con un molde
     * en mano, lo deposita en el ACTIVO o si no en el almacén (solo si está
     * apagada — {@code isValid} ya lo exige).
     */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                              PlayerEntity player, Hand hand, net.minecraft.util.hit.BlockHitResult hit) {
        // Botón de la línea de producción en el frente (2026-10-08): va primero, con o sin ítem en la mano.
        if (com.modamod.util.BotonLinea.golpea(state.get(FACING), pos, hit)) {
            if (!world.isClient && world.getBlockEntity(pos) instanceof ModeladoBlockEntity botonLinea) {
                botonLinea.alternarLinea();
                com.modamod.util.BotonLinea.avisar(world, pos, player, botonLinea.linea());
            }
            return ItemActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof ModeladoBlockEntity be)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (stack.isOf(net.minecraft.item.Items.SHEARS)) {
            // Tijeras a mano (2026-10-05): van al casillero invisible; con una ya puesta no entran.
            if (world.isClient) return ItemActionResult.SUCCESS;
            if (!be.cargarTijera(player.isCreative() ? stack.copy() : stack)) return ItemActionResult.FAIL;
            world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), SoundCategory.BLOCKS, 0.8f, 1.2f);
            return ItemActionResult.SUCCESS;
        }
        boolean esPrenda = ModeladoBlockEntity.esPrendaModelable(stack);
        boolean esMolde = ModeladoBlockEntity.esMolde(stack);
        if (!esPrenda && !esMolde) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // Abajo se abre la pantalla aunque tengas la prenda o el molde en la mano (2026-10-08).
        if (!com.modamod.util.ZonaMaquina.arriba(hit, pos, com.modamod.util.ZonaMaquina.ARRIBA_PX)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (world.isClient) return ItemActionResult.SUCCESS;

        if (esPrenda) {
            if (!be.getStack(ModeladoBlockEntity.PRENDA).isEmpty()) return ItemActionResult.FAIL;
            be.setStack(ModeladoBlockEntity.PRENDA, stack.copyWithCount(1));
            if (!player.isCreative()) stack.decrement(1);
            world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), SoundCategory.BLOCKS, 0.8f, 1.0f);
            return ItemActionResult.SUCCESS;
        }

        if (be.encendida()) return ItemActionResult.FAIL; // config bloqueada mientras produce

        // Va al Activo de la categoría actual si entra y está libre; si no,
        // al storage-por-prenda de esa categoría; si tampoco, al almacén
        // compartido (acepta cualquier molde, sea o no de esta categoría).
        ModeladoBlockEntity.Categoria categoria = be.categoria();
        int destino = -1;
        if (ModeladoBlockEntity.esMoldeDeCategoria(stack, categoria)) {
            int activo = ModeladoBlockEntity.activoSlot(categoria);
            if (be.getStack(activo).isEmpty()) {
                destino = activo;
            } else {
                for (int i = 0; i < ModeladoBlockEntity.PORPRENDA_TOTAL; i++) {
                    int lugar = ModeladoBlockEntity.porPrendaSlot(categoria, i);
                    if (be.getStack(lugar).isEmpty()) { destino = lugar; break; }
                }
            }
        }
        if (destino == -1) {
            for (int i = ModeladoBlockEntity.ALMACEN_INICIO; i < ModeladoBlockEntity.ALMACEN_FIN; i++) {
                if (be.getStack(i).isEmpty()) { destino = i; break; }
            }
        }
        if (destino == -1) return ItemActionResult.FAIL; // sin lugar
        be.setStack(destino, stack.copyWithCount(1));
        if (!player.isCreative()) stack.decrement(1);
        world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 0.8f, 1.2f);
        return ItemActionResult.SUCCESS;
    }

    /**
     * Mano vacía: primero retirar la salida si hay algo listo (funciona
     * prendida o apagada, para no interrumpir la producción); si no,
     * prendida APAGA (toggle); apagada abre la GUI.
     */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                  net.minecraft.util.hit.BlockHitResult hit) {
        // Botón de la línea de producción en el frente (2026-10-08): va primero, con o sin ítem en la mano.
        if (com.modamod.util.BotonLinea.golpea(state.get(FACING), pos, hit)) {
            if (!world.isClient && world.getBlockEntity(pos) instanceof ModeladoBlockEntity botonLinea) {
                botonLinea.alternarLinea();
                com.modamod.util.BotonLinea.avisar(world, pos, player, botonLinea.linea());
            }
            return ActionResult.SUCCESS;
        }
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof ModeladoBlockEntity be)) return ActionResult.PASS;

        // Agachado y con la mano vacía, la tijera vuelve al jugador (2026-10-05).
        if (player.isSneaking() && !be.tijera().isEmpty() && be.estado() != ModeladoBlockEntity.Estado.PROCESANDO) {
            player.getInventory().offerOrDrop(be.sacarTijera());
            world.playSound(null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundCategory.BLOCKS, 1.0f, 1.2f);
            return ActionResult.SUCCESS;
        }
        ItemStack salida = be.getStack(ModeladoBlockEntity.SALIDA);
        if (!salida.isEmpty()) {
            player.getInventory().offerOrDrop(salida.copy());
            be.setStack(ModeladoBlockEntity.SALIDA, ItemStack.EMPTY);
            world.playSound(null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundCategory.BLOCKS, 1.0f, 1.0f);
            return ActionResult.SUCCESS;
        }

        // Bug real (2026-09-21, "la modeladora no se puede abrir es como
        // que esta siempre funcionando"): encenderAlCerrar() prende
        // encendida SIEMPRE al cerrar la pantalla, aun sin prenda ni
        // fijadas cargadas — tick() entonces NUNCA pasa a PROCESANDO
        // (ver su condición) y encendida se queda en true PARA SIEMPRE.
        // Bloquear la interacción cada vez que encendida() es true (lo
        // que hacía esta rama hasta ahora) dejaba la máquina trabada sin
        // salida apenas alguien abría y cerraba la GUI sin cargar nada.
        // El peligro real (cortarPorInterrumpir) tiene que ser SOLO
        // mientras está PROCESANDO de verdad; "encendida pero sin nada
        // que hacer" sigue pudiendo cancelarse con un click, como antes.
        if (be.estado() == ModeladoBlockEntity.Estado.PROCESANDO) {
            cortarPorInterrumpir(world, pos, player);
            return ActionResult.SUCCESS;
        }

        if (be.encendida()) {
            be.onButtonClick(ModeladoBlockEntity.BTN_ENCENDER);
            world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.BLOCKS, 0.6f, 0.8f);
            player.sendMessage(net.minecraft.text.Text.translatable("modamod.maquina.aviso.apagada"), true);
            return ActionResult.SUCCESS;
        }

        player.openHandledScreen(be);
        return ActionResult.SUCCESS;
    }

    /**
     * Consecuencia de meter mano en la máquina mientras procesa
     * (2026-09-21) — cuchillas de corte activas: un tijeretazo de
     * verdad. Mismo espíritu que {@code SublimadoraBlock}/
     * {@code TinturasBlock}, cada una con su propio sabor de peligro.
     */
    private static void cortarPorInterrumpir(World world, BlockPos pos, PlayerEntity player) {
        player.damage(world.getDamageSources().generic(), 3.0f);
        world.playSound(null, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 1.0f, 1.0f);
        player.sendMessage(net.minecraft.text.Text.translatable("modamod.modelado.aviso.procesando")
                .formatted(net.minecraft.util.Formatting.RED), true);
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
                !(world.getBlockEntity(pos) instanceof ModeladoBlockEntity be) || be.isEmpty());
        return super.onBreak(world, pos, state, player);
    }
}
