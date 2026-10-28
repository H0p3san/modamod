package com.modamod.telar;

import com.modamod.Modamod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Registro del Telar automático (2026-10-07, "la idea es q produzca prendas basicas a base de lana e hilo"): la base de
 * la Estilista con la tapa del telar. Teje prendas lisas con lana (el color sale de la lana) e hilo.
 */
public final class TelarMod {

    public static final TelarBlock TELAR_BLOCK = new TelarBlock(
            AbstractBlock.Settings.create().strength(2.5f, 4.0f).sounds(BlockSoundGroup.WOOD).nonOpaque()
                    .luminance(com.modamod.util.LuzMaquina::luminancia));
    public static final TelarBlockItem TELAR_ITEM = new TelarBlockItem(TELAR_BLOCK, new Item.Settings());

    /** Versión creativa: sin espera ni insumos, como las otras máquinas ({@code util.MaquinaCreativa}). */
    public static final TelarBlock TELAR_CREATIVA = com.modamod.util.MaquinaCreativa.creativa(new TelarBlock(
            AbstractBlock.Settings.create().strength(2.5f, 4.0f).sounds(BlockSoundGroup.WOOD).nonOpaque()
                    .luminance(com.modamod.util.LuzMaquina::luminancia)));
    public static final TelarBlockItem TELAR_CREATIVA_ITEM = new TelarBlockItem(TELAR_CREATIVA, new Item.Settings());

    public static final BlockEntityType<TelarBlockEntity> TELAR_BLOCK_ENTITY =
            BlockEntityType.Builder.create(TelarBlockEntity::new, TELAR_BLOCK, TELAR_CREATIVA).build();

    public static void register() {
        Identifier id = Identifier.of(Modamod.MOD_ID, "telar");
        Registry.register(Registries.BLOCK, id, TELAR_BLOCK);
        Registry.register(Registries.ITEM, id, TELAR_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id, TELAR_BLOCK_ENTITY);
        Identifier creativa = Identifier.of(Modamod.MOD_ID, "telar_creativa");
        Registry.register(Registries.BLOCK, creativa, TELAR_CREATIVA);
        Registry.register(Registries.ITEM, creativa, TELAR_CREATIVA_ITEM);
    }

    private TelarMod() {}
}
