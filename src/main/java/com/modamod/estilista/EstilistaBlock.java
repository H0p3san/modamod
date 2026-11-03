package com.modamod.estilista;

import com.modamod.estilado.EstiladoBlockEntity;
import com.modamod.maquina.MaquinaBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** La Estilista automática: bloque direccional que dibuja GeckoLib desde el block entity (ver {@link EstilistaMod}). */
public class EstilistaBlock extends MaquinaBlock {

    private static final MapCodec<EstilistaBlock> CODEC = createCodec(EstilistaBlock::new);

    public EstilistaBlock(Settings settings) {
        super(settings);
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new EstilistaBlockEntity(pos, state); }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, EstilistaMod.ESTILISTA_BLOCK_ENTITY, EstilistaBlockEntity::tick);
    }

    /** Con una prenda en la mano, la deja cargada (si está libre); con la mano vacía ver {@link #usar}. */
    @Override
    protected ItemActionResult usarConItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
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
    protected ActionResult usar(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
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

}
