package com.modamod.estilista;

import com.modamod.estilista.EditorEstilo;
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

    /** El cuerpo es más bajo: el resto es el pórtico. */
    @Override
    protected double alturaTapaPx() { return 10.0; }

    /** Hilo y cuero van derecho a los contadores (si caben); una prenda que admite, a su casillero si está libre. */
    @Override
    protected boolean alimentar(ItemStack stack, MaquinaBlockEntity m, World world, BlockPos pos, PlayerEntity player) {
        EstilistaBlockEntity be = (EstilistaBlockEntity) m;
        if (stack.isOf(net.minecraft.item.Items.STRING) || stack.isOf(net.minecraft.item.Items.LEATHER)) {
            int n = be.absorber(player.isCreative() ? stack.copy() : stack);
            if (n <= 0) return false;
            world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.BLOCKS, 0.8f, 1.2f);
            return true;
        }
        if (!EditorEstilo.admite(stack) || !be.isValid(EstilistaBlockEntity.SLOT_PRENDA, stack)) return false;
        be.setStack(EstilistaBlockEntity.SLOT_PRENDA, stack.copyWithCount(1));
        if (!player.isCreative()) stack.decrement(1);
        world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), SoundCategory.BLOCKS, 0.8f, 1.0f);
        return true;
    }

    /** El pórtico en marcha golpea a quien meta la mano. */
    @Override
    protected void castigar(World world, BlockPos pos, PlayerEntity player) {
        player.damage(world.getDamageSources().generic(), 3.0f);
        world.playSound(null, pos, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 1.0f, 1.2f);
        player.sendMessage(net.minecraft.text.Text.translatable("modamod.estilista.aviso.procesando")
                .formatted(net.minecraft.util.Formatting.RED), true);
    }
}
