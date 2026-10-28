package com.modamod.util;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Las máquinas se rompen con TODO adentro, como una caja de shulker —
 * a pedido (2026-09-30, "DECIME Q PUEDEN GUARDAR TODO"). El ítem que cae
 * lleva el NBT entero del block entity (inventario, cuadraditos, diseños
 * guardados, tinta, trabajo a medias) en {@code minecraft:block_entity_data}
 * más sus componentes (la tinta de Sublimadora/Tintes), y {@link BlockItem}
 * lo vuelve a cargar solo al colocarlo. Antes: Modeladora y Guardarropas
 * desparramaban el contenido, Sublimadora desparramaba y guardaba la tinta,
 * y Tintes y Guardarropas no tenían loot table (se perdía el bloque, y en
 * Tintes también todo lo de adentro).
 *
 * <p>Cada bloque usa {@link #drops} en {@code getDroppedStacks} (romper a
 * mano, explosiones) y {@link #enCreativo} en {@code onBreak}; ya no
 * desparrama nada en {@code onStateReplaced}.
 */
public final class DropMaquina {

    private DropMaquina() {}

    /** El ítem de la máquina con todo lo que tenía {@code be}. */
    public static ItemStack conContenido(Block bloque, BlockEntity be) {
        ItemStack stack = new ItemStack(bloque);
        if (be.getWorld() == null) return stack;
        var registros = be.getWorld().getRegistryManager();
        NbtCompound nbt = be.createComponentlessNbt(registros);
        if (!nbt.isEmpty()) BlockItem.setBlockEntityData(stack, be.getType(), nbt);
        stack.applyComponentsFrom(be.createComponentMap());
        return stack;
    }

    /** Para {@code AbstractBlock#getDroppedStacks}. */
    public static List<ItemStack> drops(Block bloque, LootContextParameterSet.Builder builder) {
        BlockEntity be = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
        return List.of(be == null ? new ItemStack(bloque) : conContenido(bloque, be));
    }

    /**
     * En creativo no hay drops: como la shulker, si la máquina tiene algo
     * cae igual con su contenido (vacía no cae nada).
     */
    public static void enCreativo(World world, BlockPos pos, PlayerEntity jugador, Block bloque, boolean vacia) {
        if (world.isClient || !jugador.isCreative() || vacia) return;
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) return;
        ItemEntity item = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                conContenido(bloque, be));
        item.setToDefaultPickupDelay();
        world.spawnEntity(item);
    }
}
