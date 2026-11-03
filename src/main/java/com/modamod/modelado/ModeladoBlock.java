package com.modamod.modelado;

import com.modamod.maquina.MaquinaBlock;
import com.modamod.maquina.MaquinaBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Mesa de Modelado: bloque simple (sin GeckoLib), direccional. Click derecho
 * con una prenda en mano aplica el molde de corte activo instantáneamente;
 * mano vacía abre la GUI de armado. Ver docs/archivo/MAQUINAS.md sección 3.
 */
public class ModeladoBlock extends MaquinaBlock {

    private static final MapCodec<ModeladoBlock> CODEC = createCodec(ModeladoBlock::new);

    public ModeladoBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

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
     * Lo que se carga a mano por la tapa: las tijeras al casillero invisible (con unas puestas no entran otras), la prenda
     * a su casillero si está libre (prendida o apagada) y un molde, con la máquina apagada, al Activo de la categoría
     * actual, al almacén de esa prenda o al general, el primero libre. Lo que no entra abre la pantalla.
     */
    @Override
    protected boolean alimentar(ItemStack stack, MaquinaBlockEntity m, World world, BlockPos pos, PlayerEntity player) {
        ModeladoBlockEntity be = (ModeladoBlockEntity) m;
        if (stack.isOf(net.minecraft.item.Items.SHEARS)) {
            if (!be.cargarTijera(player.isCreative() ? stack.copy() : stack)) return false;
            world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), SoundCategory.BLOCKS, 0.8f, 1.2f);
            return true;
        }
        if (ModeladoBlockEntity.esPrendaModelable(stack)) {
            if (!be.getStack(ModeladoBlockEntity.PRENDA).isEmpty()) return false;
            be.setStack(ModeladoBlockEntity.PRENDA, stack.copyWithCount(1));
            if (!player.isCreative()) stack.decrement(1);
            world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), SoundCategory.BLOCKS, 0.8f, 1.0f);
            return true;
        }
        if (!ModeladoBlockEntity.esMolde(stack) || be.encendida()) return false;
        int destino = destinoDelMolde(be, stack);
        if (destino < 0) return false;
        be.setStack(destino, stack.copyWithCount(1));
        if (!player.isCreative()) stack.decrement(1);
        world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 0.8f, 1.2f);
        return true;
    }

    /** El Activo de la categoría actual si es suyo y está libre; si no, su almacén por prenda; si no, el general. -1 si no hay lugar. */
    private static int destinoDelMolde(ModeladoBlockEntity be, ItemStack molde) {
        ModeladoBlockEntity.Categoria categoria = be.categoria();
        if (ModeladoBlockEntity.esMoldeDeCategoria(molde, categoria)) {
            int activo = ModeladoBlockEntity.activoSlot(categoria);
            if (be.getStack(activo).isEmpty()) return activo;
            for (int i = 0; i < ModeladoBlockEntity.PORPRENDA_TOTAL; i++) {
                int lugar = ModeladoBlockEntity.porPrendaSlot(categoria, i);
                if (be.getStack(lugar).isEmpty()) return lugar;
            }
        }
        for (int i = ModeladoBlockEntity.ALMACEN_INICIO; i < ModeladoBlockEntity.ALMACEN_FIN; i++) {
            if (be.getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    /** Agachado y con la mano vacía, la tijera vuelve al jugador (si no está cortando). */
    @Override
    protected boolean usarPropio(MaquinaBlockEntity m, World world, BlockPos pos, PlayerEntity player) {
        ModeladoBlockEntity be = (ModeladoBlockEntity) m;
        if (!player.isSneaking() || be.tijera().isEmpty() || be.trabajando()) return false;
        player.getInventory().offerOrDrop(be.sacarTijera());
        world.playSound(null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundCategory.BLOCKS, 1.0f, 1.2f);
        return true;
    }

    /** Las cuchillas en marcha: un tijeretazo de verdad. */
    @Override
    protected void castigar(World world, BlockPos pos, PlayerEntity player) {
        player.damage(world.getDamageSources().generic(), 3.0f);
        world.playSound(null, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 1.0f, 1.0f);
        player.sendMessage(net.minecraft.text.Text.translatable("modamod.modelado.aviso.procesando")
                .formatted(net.minecraft.util.Formatting.RED), true);
    }
}
