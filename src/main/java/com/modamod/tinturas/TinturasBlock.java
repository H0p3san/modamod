package com.modamod.tinturas;

import com.modamod.maquina.MaquinaBlock;
import com.modamod.maquina.MaquinaBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Estación de Tintes. Mismo espíritu de interacción que la Mesa de
 * Modelado ({@link com.modamod.modelado.ModeladoBlock}: click derecho
 * con el ítem correcto en mano lo carga, mano vacía abre la GUI de
 * config) — desde 2026-09-21 ("que cada maquina tome su tiempo") teñir
 * ya NO es instantáneo: arranca un ciclo de
 * {@link TinturasBlockEntity#TICKS_TENIDO} y el resultado sale por
 * {@link TinturasBlockEntity#SLOT_SALIDA}, mismo mecanismo que
 * Modeladora/Sublimadora.
 */
public class TinturasBlock extends MaquinaBlock {

    private static final MapCodec<TinturasBlock> CODEC = createCodec(TinturasBlock::new);

    public TinturasBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    @Nullable
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TinturasBlockEntity(pos, state);
    }

    /**
     * Ticker — a pedido (2026-09-21, "que cada maquina tome su tiempo"):
     * antes no hacía falta ninguno (aplicaba instantáneo), ahora
     * {@link TinturasBlockEntity#tick} avanza el ciclo de teñido, mismo
     * mecanismo que Modeladora/Sublimadora.
     */
    @Override
    @Nullable
    public <T extends BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(
            World world, BlockState state, net.minecraft.block.entity.BlockEntityType<T> type) {
        return validateTicker(type, TinturasMod.TINTURAS_BLOCK_ENTITY, TinturasBlockEntity::tick);
    }

    /** Mismo mapeo que {@code SublimadoraBlock#canalDeTinte}: solo estos 4 tintes cargan el tanque. */
    private static int canalDeTinte(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.CYAN_DYE) return TinturasBlockEntity.C;
        if (item == Items.MAGENTA_DYE) return TinturasBlockEntity.M;
        if (item == Items.YELLOW_DYE) return TinturasBlockEntity.Y;
        if (item == Items.BLACK_DYE) return TinturasBlockEntity.K;
        return -1;
    }

    // Mismas claves de traducción que ya usa la Sublimadora (modamod.sublimadora.tinta.*).
    private static final String[] NOMBRE_CANAL = { "cian", "magenta", "amarillo", "negro" };

    /**
     * Lo que se carga a mano por la tapa: un tinte CMYK al tanque (lleno, no entra y se abre la pantalla) o una prenda,
     * que arranca el teñido. Si no puede arrancar, avisa el motivo y la prenda queda en la mano.
     */
    @Override
    protected boolean alimentar(ItemStack stack, MaquinaBlockEntity m, World world, BlockPos pos, PlayerEntity player) {
        TinturasBlockEntity be = (TinturasBlockEntity) m;
        int canal = canalDeTinte(stack);
        if (canal >= 0) {
            if (!be.cargar(canal, 1)) return false;
            if (!player.isCreative()) stack.decrement(1);
            world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, 0.8f, 1.0f);
            player.sendMessage(Text.translatable("modamod.tinturas.aviso.cargado",
                    Text.translatable("modamod.sublimadora.tinta." + NOMBRE_CANAL[canal]),
                    be.carga(canal), TinturasBlockEntity.CARGA_MAXIMA), true);
            return true;
        }
        if (!TinturasBlockEntity.aceptaEntrada(stack)) return false;
        Text motivo = be.iniciarTenido(stack.copyWithCount(1));
        if (motivo != null) {
            player.sendMessage(motivo, true);
            return true;
        }
        if (!player.isCreative()) stack.decrement(1);
        world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.BLOCKS, 0.9f, 1.0f);
        return true;
    }

    /** Un chorro de tinte químico a presión: los vapores marean. */
    @Override
    protected void castigar(World world, BlockPos pos, PlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0));
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 1.0f, 0.8f);
        player.sendMessage(Text.translatable("modamod.tinturas.aviso.procesando").formatted(Formatting.RED), true);
    }
}
