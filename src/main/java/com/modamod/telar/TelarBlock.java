package com.modamod.telar;

import com.modamod.maquina.MaquinaBlock;
import com.modamod.maquina.MaquinaBlockEntity;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** El Telar automático: máquina de la línea de producción que dibuja GeckoLib desde el block entity (ver {@link TelarMod}). */
public class TelarBlock extends MaquinaBlock {

    private static final MapCodec<TelarBlock> CODEC = createCodec(TelarBlock::new);

    public TelarBlock(Settings settings) {
        super(settings);
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new TelarBlockEntity(pos, state); }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, TelarMod.TELAR_BLOCK_ENTITY, TelarBlockEntity::tick);
    }

    /** El cuerpo es más bajo: el resto es el pórtico. */
    @Override
    protected double alturaTapaPx() { return 10.0; }

    /** Debajo de la tapa se abre la pantalla aunque haya una prenda lista (tejiendo, no): ahí se para el lote. */
    @Override
    protected boolean pantallaAbajo() { return true; }

    /** Lana, hilo o cuero van a su casillero si entran (del mismo tipo que lo que ya hay). */
    @Override
    protected boolean alimentar(ItemStack stack, MaquinaBlockEntity m, World world, BlockPos pos, PlayerEntity player) {
        TelarBlockEntity be = (TelarBlockEntity) m;
        int slot = be.isValid(TelarBlockEntity.SLOT_LANA, stack) ? TelarBlockEntity.SLOT_LANA
                : be.isValid(TelarBlockEntity.SLOT_HILO, stack) ? TelarBlockEntity.SLOT_HILO
                : be.isValid(TelarBlockEntity.SLOT_CUERO, stack) ? TelarBlockEntity.SLOT_CUERO : -1;
        if (slot < 0) return false;
        ItemStack actual = be.getStack(slot);
        if (!actual.isEmpty() && !ItemStack.areItemsAndComponentsEqual(actual, stack)) return false;
        int caben = actual.isEmpty() ? stack.getMaxCount() : actual.getMaxCount() - actual.getCount();
        int n = Math.min(caben, stack.getCount());
        if (n <= 0) return false;
        if (actual.isEmpty()) be.setStack(slot, stack.copyWithCount(n));
        else { actual.increment(n); be.setStack(slot, actual); }
        if (!player.isCreative()) stack.decrement(n);
        world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.BLOCKS, 0.8f, 1.2f);
        return true;
    }

    /** La lanzadera en marcha te engancha: daño y lentitud. */
    @Override
    protected void castigar(World world, BlockPos pos, PlayerEntity player) {
        player.damage(world.getDamageSources().generic(), 2.0f);
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.SLOWNESS, 100, 1));
        world.playSound(null, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 1.0f, 0.8f);
        player.sendMessage(net.minecraft.text.Text.translatable("modamod.telar.aviso.procesando")
                .formatted(net.minecraft.util.Formatting.RED), true);
    }
}
